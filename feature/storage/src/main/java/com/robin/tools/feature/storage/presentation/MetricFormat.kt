package com.robin.tools.feature.storage.presentation

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.robin.tools.feature.storage.R
import com.robin.tools.feature.storage.data.BenchmarkPhase
import com.robin.tools.feature.storage.data.KB
import com.robin.tools.feature.storage.data.MB
import java.util.Locale

/**
 * 展示层的数值格式化。
 *
 * 这一层刻意与 [com.robin.tools.feature.storage.data.BenchmarkMath] 分开：
 * 那里只做纯数学（吞吐、百分位、稳态裁剪），这里负责"怎么呈现给人看"。
 *
 * **数字与单位分开处理**：
 * - 数字用 [Locale.getDefault] 格式化，法语等语言会正确显示成 `1,5 Mo`；
 * - 单位走字符串资源，因为不同语言的单位写法真的不同（法语是 Mo/Go，不是 MB/GB）。
 *
 * 唯一的例外是 [scaledRate] 里的 `k` / `M` —— 那是贴在数字上的数量级前缀
 * （类似科学计数法），不是计量单位，因此不入资源。
 */
object MetricFormat {

    /**
     * 字节量级。
     *
     * @property divisor 该量级相对于字节的除数
     */
    enum class ByteMagnitude(@StringRes val unitRes: Int, val divisor: Double) {
        BYTES(R.string.storage_unit_bytes, 1.0),
        KILOBYTES(R.string.storage_unit_kilobytes, KB.toDouble()),
        MEGABYTES(R.string.storage_unit_megabytes, MB.toDouble()),
        GIGABYTES(R.string.storage_unit_gigabytes, (KB * MB).toDouble())
    }

    /** 字节速率量级。单位比字节多一个 `/s`，因此需要独立的一组资源。 */
    enum class ByteRateMagnitude(@StringRes val unitRes: Int, val divisor: Double) {
        BYTES_PER_SECOND(R.string.storage_unit_bytes_per_second, 1.0),
        KILOBYTES_PER_SECOND(R.string.storage_unit_kilobytes_per_second, KB.toDouble()),
        MEGABYTES_PER_SECOND(R.string.storage_unit_megabytes_per_second, MB.toDouble()),
        GIGABYTES_PER_SECOND(R.string.storage_unit_gigabytes_per_second, (KB * MB).toDouble())
    }

    /** 延迟量级。 */
    enum class LatencyMagnitude(@StringRes val unitRes: Int, val divisor: Double) {
        MICROSECONDS(R.string.storage_unit_microseconds, 1.0),
        MILLISECONDS(R.string.storage_unit_milliseconds, 1_000.0),
        SECONDS(R.string.storage_unit_seconds, 1_000_000.0)
    }

    /** 选出合适的字节量级。非正数按 0 处理。 */
    fun byteMagnitude(bytes: Long): ByteMagnitude = when {
        bytes >= (KB * MB) -> ByteMagnitude.GIGABYTES
        bytes >= MB -> ByteMagnitude.MEGABYTES
        bytes >= KB -> ByteMagnitude.KILOBYTES
        else -> ByteMagnitude.BYTES
    }

    /** 选出合适的字节速率量级。非有限值或非正数按 0 处理。 */
    fun byteRateMagnitude(bytesPerSecond: Double): ByteRateMagnitude = when {
        !bytesPerSecond.isFinite() || bytesPerSecond <= 0.0 -> ByteRateMagnitude.BYTES_PER_SECOND
        bytesPerSecond >= (KB * MB) -> ByteRateMagnitude.GIGABYTES_PER_SECOND
        bytesPerSecond >= MB -> ByteRateMagnitude.MEGABYTES_PER_SECOND
        bytesPerSecond >= KB -> ByteRateMagnitude.KILOBYTES_PER_SECOND
        else -> ByteRateMagnitude.BYTES_PER_SECOND
    }

    /** 选出合适的延迟量级。非正数按 0 处理。 */
    fun latencyMagnitude(micros: Long): LatencyMagnitude = when {
        micros >= 1_000_000L -> LatencyMagnitude.SECONDS
        micros >= 1_000L -> LatencyMagnitude.MILLISECONDS
        else -> LatencyMagnitude.MICROSECONDS
    }

    /**
     * 格式化缩放后的数值。
     *
     * 超过 [LARGE_VALUE_THRESHOLD] 时省略小数位以缩短文本：`412` 比 `412.0` 更好读，
     * 而这个量级上小数位本来也没有意义。
     *
     * @param locale 默认使用系统区域，以便正确显示小数分隔符；测试传入固定区域以获得确定性
     */
    fun scaledNumber(value: Double, locale: Locale = Locale.getDefault()): String {
        if (!value.isFinite()) {
            return "0"
        }
        val safeValue = value.coerceAtLeast(0.0)
        return if (safeValue >= LARGE_VALUE_THRESHOLD) {
            String.format(locale, "%.0f", safeValue)
        } else {
            String.format(locale, "%.1f", safeValue)
        }
    }

    /**
     * 每秒操作数的数量级前缀形式，例如 `12.4 k`、`320`、`1.2 M`。
     *
     * 只产出数字与数量级前缀，**单位由调用方用字符串资源拼上**：
     * 随机 IO 是 `ops/s`，小文件是 `files/s`，SQLite 是 `rows/s`。
     */
    fun scaledRate(operationsPerSecond: Double, locale: Locale = Locale.getDefault()): String {
        if (!operationsPerSecond.isFinite() || operationsPerSecond <= 0.0) {
            return "0"
        }
        return when {
            operationsPerSecond >= OPERATIONS_PER_MILLION ->
                scaledNumber(operationsPerSecond / OPERATIONS_PER_MILLION, locale) + " M"

            operationsPerSecond >= OPERATIONS_PER_THOUSAND ->
                scaledNumber(operationsPerSecond / OPERATIONS_PER_THOUSAND, locale) + " k"

            else -> scaledNumber(operationsPerSecond, locale)
        }
    }

    private const val LARGE_VALUE_THRESHOLD = 100.0
    private const val OPERATIONS_PER_THOUSAND = 1_000.0
    private const val OPERATIONS_PER_MILLION = 1_000_000.0
}

/** 字节数的可读文本，例如 `205 GB`。 */
@Composable
fun bytesText(bytes: Long): String {
    val magnitude = MetricFormat.byteMagnitude(bytes)
    return stringResource(
        magnitude.unitRes,
        MetricFormat.scaledNumber(bytes.coerceAtLeast(0L) / magnitude.divisor)
    )
}

/** 字节速率的可读文本，例如 `1.1 GB/s`。 */
@Composable
fun byteRateText(bytesPerSecond: Double): String {
    val magnitude = MetricFormat.byteRateMagnitude(bytesPerSecond)
    val scaled = if (bytesPerSecond.isFinite() && bytesPerSecond > 0.0) {
        bytesPerSecond / magnitude.divisor
    } else {
        0.0
    }
    return stringResource(magnitude.unitRes, MetricFormat.scaledNumber(scaled))
}

/** 延迟的可读文本，例如 `128 µs`、`2.8 ms`。 */
@Composable
fun latencyText(micros: Long): String {
    val magnitude = MetricFormat.latencyMagnitude(micros)
    return stringResource(
        magnitude.unitRes,
        MetricFormat.scaledNumber(micros.coerceAtLeast(0L) / magnitude.divisor)
    )
}

/**
 * 时长的可读文本。
 *
 * 一分钟以内保留一位小数（`0.2 s`、`42.0 s`）：快设备上顺序读写会在亚秒级完成，
 * 若统一格式化成 `mm:ss` 就只剩 `00:00`，反而看不出实际耗时。
 */
@Composable
fun durationText(millis: Long): String {
    val safeMillis = millis.coerceAtLeast(0L)
    return when {
        safeMillis < MILLIS_PER_MINUTE ->
            stringResource(R.string.storage_unit_seconds, MetricFormat.scaledNumber(safeMillis / 1000.0))

        safeMillis < MILLIS_PER_HOUR -> {
            val totalSeconds = safeMillis / MILLIS_PER_SECOND
            stringResource(
                R.string.storage_duration_minutes_seconds,
                totalSeconds / SECONDS_PER_MINUTE,
                totalSeconds % SECONDS_PER_MINUTE
            )
        }

        else -> {
            val totalSeconds = safeMillis / MILLIS_PER_SECOND
            stringResource(
                R.string.storage_duration_hours_minutes_seconds,
                totalSeconds / SECONDS_PER_HOUR,
                (totalSeconds / SECONDS_PER_MINUTE) % MINUTES_PER_HOUR,
                totalSeconds % SECONDS_PER_MINUTE
            )
        }
    }
}

/**
 * 计次类速率的可读文本。
 *
 * 单位随阶段变化：小文件是"个/秒"、SQLite 是"行/秒"，统一按 `ops/s` 展示会与
 * 历史卡片里的单位自相矛盾，也会让用户以为小文件阶段测的是随机 IO。
 */
@Composable
fun operationRateText(phase: BenchmarkPhase, ratePerSecond: Double): String {
    val scaled = MetricFormat.scaledRate(ratePerSecond)
    return when (phase) {
        BenchmarkPhase.SMALL_FILE -> stringResource(R.string.storage_rate_files, scaled)
        BenchmarkPhase.SQLITE_INSERT -> stringResource(R.string.storage_rate_rows, scaled)
        else -> stringResource(R.string.storage_rate_operations, scaled)
    }
}

/**
 * 运行中的实时速率。单位随阶段变化：顺序读写是字节每秒，其余阶段交给
 * [operationRateText]——把 12.4k IOPS 按字节格式化会得到毫无意义的 "50 KB/s"。
 */
@Composable
fun liveRateText(phase: BenchmarkPhase, ratePerSecond: Double): String = when (phase) {
    BenchmarkPhase.SEQUENTIAL_WRITE, BenchmarkPhase.SEQUENTIAL_READ, BenchmarkPhase.CACHE_FLUSH ->
        byteRateText(ratePerSecond)

    else -> operationRateText(phase, ratePerSecond)
}

private const val MILLIS_PER_SECOND = 1_000L
private const val MILLIS_PER_MINUTE = 60_000L
private const val MILLIS_PER_HOUR = 3_600_000L
private const val SECONDS_PER_MINUTE = 60L
private const val SECONDS_PER_HOUR = 3_600L
private const val MINUTES_PER_HOUR = 60L
