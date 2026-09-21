package com.robin.tools.feature.storage.data

/**
 * 吞吐时间线采样器。
 *
 * 采样规则：**距上次采样累计传输达到 [minSampleBytes] 或 距上次采样超过 [minSampleIntervalNanos]**，
 * 二者满足其一即记录一点，总点数不超过 [MAX_POINTS]。
 *
 * 之所以不单纯按时间采样：快速档在高端设备上可能 200 ms 内就跑完，
 * 纯时间采样会只剩 0～1 个点，曲线与稳态值都无从谈起。
 * 加入字节阈值后，无论设备快慢都能得到形状可用的曲线。
 *
 * 本类不持有 Android 依赖，可在 JVM 单元测试中直接使用。
 */
class ThroughputTimelineRecorder(
    private val minSampleIntervalNanos: Long = DEFAULT_MIN_SAMPLE_INTERVAL_NANOS,
    private val minSampleBytes: Long = DEFAULT_MIN_SAMPLE_BYTES
) {

    private val samples = ArrayList<ThroughputSample>(MAX_POINTS)

    private var lastSampleNanos = 0L
    private var lastSampleBytes = 0L
    private var started = false

    /** 记录一次进度检查点。未达到任一采样阈值时静默跳过。 */
    fun record(transferredBytes: Long, nowNanos: Long) {
        if (!started) {
            started = true
            lastSampleNanos = nowNanos
            lastSampleBytes = transferredBytes
            return
        }
        if (samples.size >= MAX_POINTS) {
            return
        }

        val elapsedNanos = nowNanos - lastSampleNanos
        val transferredDelta = transferredBytes - lastSampleBytes
        if (elapsedNanos < minSampleIntervalNanos && transferredDelta < minSampleBytes) {
            return
        }
        if (elapsedNanos <= 0L) {
            return
        }

        samples.add(
            ThroughputSample(
                elapsedMillis = elapsedNanos / NANOS_PER_MILLI,
                bytesPerSecond = transferredDelta.toDouble() * NANOS_PER_SECOND / elapsedNanos
            )
        )
        lastSampleNanos = nowNanos
        lastSampleBytes = transferredBytes
    }

    /**
     * 当前未完成窗口的瞬时速率，用于界面上的实时读数。
     *
     * @return 字节每秒；窗口尚未产生数据时返回 0
     */
    fun currentBytesPerSecond(transferredBytes: Long, nowNanos: Long): Double {
        if (!started) {
            return 0.0
        }
        val elapsedNanos = nowNanos - lastSampleNanos
        val transferredDelta = transferredBytes - lastSampleBytes
        if (elapsedNanos <= 0L || transferredDelta <= 0L) {
            return 0.0
        }
        return transferredDelta.toDouble() * NANOS_PER_SECOND / elapsedNanos
    }

    /** 已记录的采样点快照。 */
    fun snapshot(): List<ThroughputSample> = samples.toList()

    companion object {
        /** 时间线最多保留的点数，限制图表与稳态计算的开销。 */
        const val MAX_POINTS = 200

        /** 两次采样之间的最大时间间隔。 */
        const val DEFAULT_MIN_SAMPLE_INTERVAL_NANOS = 500_000_000L

        /** 两次采样之间的最小传输字节数。 */
        const val DEFAULT_MIN_SAMPLE_BYTES = 4L * MB

        private const val NANOS_PER_MILLI = 1_000_000L
        private const val NANOS_PER_SECOND = 1_000_000_000.0
    }
}
