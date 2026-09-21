package com.robin.tools.feature.storage.data

import androidx.annotation.StringRes
import com.robin.tools.feature.storage.R

/**
 * 1024 进制的字节常量。
 *
 * 展示层沿用 Android 与业界习惯写作 KB/MB/GB，但换算基数为 1024，
 * 与 [BenchmarkMath.bytesPerSecond] 的口径保持一致。
 */
internal const val KB = 1024L

/** 见 [KB]。 */
internal const val MB = 1024L * KB

/**
 * 测试目标。
 *
 * 三个目标都位于应用自己的沙箱内，因此全部不需要任何运行时权限，
 * 也不需要在 AndroidManifest 中声明任何东西。
 */
enum class BenchmarkTarget(@StringRes val labelRes: Int) {
    /** 应用内部私有目录：`/data/data/<pkg>/files`，反映裸介质能力。 */
    INTERNAL_FILES(R.string.storage_target_internal_files),

    /** 应用内部缓存目录：`/data/data/<pkg>/cache`，与内部私有目录同卷。 */
    INTERNAL_CACHE(R.string.storage_target_internal_cache),

    /** 应用专属外部目录：`/storage/emulated/0/Android/data/<pkg>/files`，经由系统模拟层。 */
    EXTERNAL_APP(R.string.storage_target_external_app)
}

/**
 * 测量阶段。枚举声明顺序与执行顺序一致，便于对照。
 */
enum class BenchmarkPhase(@StringRes val labelRes: Int) {
    SEQUENTIAL_WRITE(R.string.storage_phase_sequential_write),

    /** 写入丢弃数据以制造脏页回写压力，尽力驱逐页缓存；只在开启冷读时执行，无评分。 */
    CACHE_FLUSH(R.string.storage_phase_cache_flush),

    SEQUENTIAL_READ(R.string.storage_phase_sequential_read),
    RANDOM_READ(R.string.storage_phase_random_read),
    RANDOM_WRITE(R.string.storage_phase_random_write),
    SMALL_FILE(R.string.storage_phase_small_file),
    SQLITE_INSERT(R.string.storage_phase_sqlite_insert)
}

/** 失败原因。每个变体都对应一条可供用户采取行动的文案。 */
enum class BenchmarkFailure(@StringRes val messageRes: Int) {
    NOT_ENOUGH_SPACE(R.string.storage_failure_not_enough_space),
    TARGET_UNAVAILABLE(R.string.storage_failure_target_unavailable),
    IO_ERROR(R.string.storage_failure_io_error),
    DATABASE_ERROR(R.string.storage_failure_database_error),
    UNKNOWN(R.string.storage_failure_unknown)
}

/**
 * 测试档位。
 *
 * 载荷按"能否越过页缓存、能否触发回写节流"来确定，而不是按固定耗时：
 * 快设备上小载荷几乎全在页缓存里完成，测出来接近内存速度是正常现象，
 * 主口径（含 fsync 的平均值）仍然有效。
 *
 * @property sqliteRows SQLite 阶段插入的行数；**为 0 表示跳过该阶段**，不再单设开关。
 */
enum class BenchmarkPreset(
    @StringRes val labelRes: Int,
    @StringRes val detailRes: Int,
    val sequentialBytes: Long,
    val randomRegionBytes: Long,
    val randomOps: Int,
    val smallFileCount: Int,
    val smallFileBytes: Int,
    val sqliteRows: Int,
    val cacheFlushBytes: Long
) {
    /** 最容易通过容量校验的档位，适合先快速看一眼。 */
    QUICK(
        labelRes = R.string.storage_preset_quick,
        detailRes = R.string.storage_preset_quick_detail,
        sequentialBytes = 128L * MB,
        randomRegionBytes = 32L * MB,
        randomOps = 500,
        smallFileCount = 100,
        smallFileBytes = 4 * KB.toInt(),
        sqliteRows = 0,
        cacheFlushBytes = 128L * MB
    ),

    /** 默认档位：足以产生可观测的回写，容量要求仍然适中。 */
    STANDARD(
        labelRes = R.string.storage_preset_standard,
        detailRes = R.string.storage_preset_standard_detail,
        sequentialBytes = 512L * MB,
        randomRegionBytes = 128L * MB,
        randomOps = 2_000,
        smallFileCount = 300,
        smallFileBytes = 4 * KB.toInt(),
        sqliteRows = 2_000,
        cacheFlushBytes = 512L * MB
    ),

    /** 在大内存设备上能越过脏页阈值、触发回写节流，数值最接近稳态。 */
    THOROUGH(
        labelRes = R.string.storage_preset_thorough,
        detailRes = R.string.storage_preset_thorough_detail,
        sequentialBytes = 2048L * MB,
        randomRegionBytes = 512L * MB,
        randomOps = 8_000,
        smallFileCount = 1_000,
        smallFileBytes = 4 * KB.toInt(),
        sqliteRows = 5_000,
        cacheFlushBytes = 1024L * MB
    )
}

/**
 * 一次测试的显式载荷描述。
 *
 * 与 [BenchmarkPreset] 分开，使引擎可以按任意载荷运行：
 * 档位只是载荷的一个来源，设备测试因此无需为小载荷在正式档位中伪造条目。
 *
 * @property preset 来源档位；由任意载荷直接构造时为 null
 * @property coldRead 是否在读取前尽力驱逐页缓存
 */
data class BenchmarkSpec(
    val target: BenchmarkTarget,
    val sequentialBytes: Long,
    val randomRegionBytes: Long,
    val randomOps: Int,
    val randomBlockBytes: Int,
    val smallFileCount: Int,
    val smallFileBytes: Int,
    val sqliteRows: Int,
    val cacheFlushBytes: Long,
    val coldRead: Boolean = false,
    val preset: BenchmarkPreset? = null
) {

    /**
     * 本次运行实际执行的阶段，顺序即执行顺序。
     *
     * 引擎用它计算进度分母，因此**只有在载荷真的会让某阶段运行时才包含该阶段**——
     * 否则进度条会永远到不了 100%。
     */
    val phasePlan: List<BenchmarkPhase>
        get() = buildList {
            add(BenchmarkPhase.SEQUENTIAL_WRITE)
            if (coldRead && cacheFlushBytes > 0L) {
                add(BenchmarkPhase.CACHE_FLUSH)
            }
            add(BenchmarkPhase.SEQUENTIAL_READ)
            add(BenchmarkPhase.RANDOM_READ)
            add(BenchmarkPhase.RANDOM_WRITE)
            add(BenchmarkPhase.SMALL_FILE)
            if (sqliteRows > 0) {
                add(BenchmarkPhase.SQLITE_INSERT)
            }
        }
}

/** 档位到显式载荷的映射。 */
fun BenchmarkPreset.toSpec(target: BenchmarkTarget, coldRead: Boolean = false): BenchmarkSpec = BenchmarkSpec(
    target = target,
    sequentialBytes = sequentialBytes,
    randomRegionBytes = randomRegionBytes,
    randomOps = randomOps,
    randomBlockBytes = DEFAULT_RANDOM_BLOCK_BYTES,
    smallFileCount = smallFileCount,
    smallFileBytes = smallFileBytes,
    sqliteRows = sqliteRows,
    cacheFlushBytes = cacheFlushBytes,
    coldRead = coldRead,
    preset = this
)

/** 随机 IO 的块大小：4 KB，与文件系统与页缓存的常见粒度一致。 */
const val DEFAULT_RANDOM_BLOCK_BYTES = 4 * 1024

/** 吞吐时间线采样点。 */
data class ThroughputSample(
    val elapsedMillis: Long,
    val bytesPerSecond: Double
)

/**
 * 吞吐类指标。
 *
 * @property bufferedBytesPerSecond 仅顺序写有值：不含落盘同步的对照值。
 *   它反映页缓存写入速度，**不是**介质速度，UI 必须与主口径区分呈现。
 * @property steadyStateBytesPerSecond 剔除首尾采样后的稳态吞吐；采样点不足时为 null。
 *   采样点只覆盖 `fsync` 之前的循环，因此它同样属于页缓存侧，不能与平均值直接比较。
 */
data class ThroughputMetric(
    val phase: BenchmarkPhase,
    val bytes: Long,
    val elapsedNanos: Long,
    val averageBytesPerSecond: Double,
    val steadyStateBytesPerSecond: Double?,
    val bufferedBytesPerSecond: Double?,
    val timeline: List<ThroughputSample>
)

/**
 * 随机 IO 与逐次同步的小文件类指标。
 *
 * @property syncEveryOps 同步策略：每多少次操作调用一次 `force(false)`；
 *   小文件阶段为 1，表示每个文件都单独 `fdatasync`。同步本身不计入单次延迟统计，
 *   但计入总耗时，因此 [operationsPerSecond] 反映的是带同步的真实吞吐。
 */
data class RandomIoMetric(
    val phase: BenchmarkPhase,
    val operationCount: Int,
    val blockBytes: Int,
    val elapsedNanos: Long,
    val operationsPerSecond: Double,
    val bytesPerSecond: Double,
    val latencyP50Micros: Long,
    val latencyP95Micros: Long,
    val latencyP99Micros: Long,
    val syncEveryOps: Int
)

/**
 * 计次类指标，用于按"次/秒"衡量且逐次延迟没有意义的阶段（SQLite 事务写入）。
 *
 * 事务内逐行计时会被两次 `nanoTime()` 调用的开销污染，而真正的成本集中在事务提交上，
 * 因此这里只保留次数与总耗时。
 */
data class OperationRateMetric(
    val phase: BenchmarkPhase,
    val operationCount: Int,
    val elapsedNanos: Long,
    val operationsPerSecond: Double
)

/**
 * 设备与卷快照，用于给结果做归因。
 *
 * 只包含硬件与容量参数，刻意不采集序列号、IMEI、账号、
 * `Settings.Secure.ANDROID_ID` 等任何标识符。
 */
data class DeviceSnapshot(
    val model: String,
    val apiLevel: Int,
    val abi: String,
    val totalRamBytes: Long,
    val volumeTotalBytes: Long,
    val volumeFreeBytesAtStart: Long,
    val isExternalEmulated: Boolean
)

/**
 * 一次完整测试的结果。
 *
 * @property preset 来源档位；由任意载荷直接运行时为 null
 * @property sequentialReadFirstPass 首轮读取。Android 公共 API 无法驱逐页缓存，
 *   该数值可能包含页缓存命中，口径偏乐观。
 * @property sequentialReadRepeat 次轮读取。与首轮接近说明首轮已全部命中缓存。
 * @property coldReadAttempted 是否在读取前执行过尽力而为的缓存驱逐。
 *   即使执行了也不保证生效，因此读取结果仍需按偏乐观解读。
 * @property sqliteInsert 未启用 SQLite 阶段时为 null
 */
data class BenchmarkResult(
    val target: BenchmarkTarget,
    val preset: BenchmarkPreset?,
    val sequentialWrite: ThroughputMetric,
    val sequentialReadFirstPass: ThroughputMetric,
    val sequentialReadRepeat: ThroughputMetric,
    val randomRead: RandomIoMetric,
    val randomWrite: RandomIoMetric,
    val smallFile: RandomIoMetric,
    val sqliteInsert: OperationRateMetric?,
    val coldReadAttempted: Boolean,
    val device: DeviceSnapshot,
    val startedAtEpochMillis: Long,
    val totalElapsedMillis: Long
)

/** 引擎向调用方汇报的进度快照。 */
data class EngineProgress(
    val phase: BenchmarkPhase,
    val phaseIndex: Int,
    val phaseCount: Int,
    val phaseFraction: Float,
    val overallFraction: Float,
    /**
     * 当前阶段的实时速率。顺序读写阶段是字节每秒，随机 IO 是小文件是次每秒，
     * 单位由 [phase] 决定，展示层需按阶段选择格式化方式。
     */
    val liveRatePerSecond: Double,
    val elapsedMillis: Long
)

/** 界面消费的测试状态。 */
sealed interface BenchmarkProgress {

    /** 尚未开始，或上一次结果已被新的选择清空。 */
    data object Idle : BenchmarkProgress

    data class Running(
        val phase: BenchmarkPhase,
        val phaseIndex: Int,
        val phaseCount: Int,
        val phaseFraction: Float,
        val overallFraction: Float,
        val liveRatePerSecond: Double,
        val elapsedMillis: Long
    ) : BenchmarkProgress

    data class Finished(val result: BenchmarkResult) : BenchmarkProgress

    /**
     * @property requiredBytes 空间不足时"至少需要多少可用空间"，用于给出可操作提示
     * @property availableBytes 空间不足时的实际可用空间
     */
    data class Failed(
        val phase: BenchmarkPhase?,
        val reason: BenchmarkFailure,
        val requiredBytes: Long? = null,
        val availableBytes: Long? = null,
        val cause: Throwable? = null
    ) : BenchmarkProgress

    data object Cancelled : BenchmarkProgress
}

/** 目标卷的容量概况，供开始前展示。 */
data class StorageTargetInfo(
    val target: BenchmarkTarget,
    val availableBytes: Long,
    val totalBytes: Long
)
