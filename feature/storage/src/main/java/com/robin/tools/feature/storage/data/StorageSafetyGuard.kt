package com.robin.tools.feature.storage.data

import android.util.Log
import java.io.File
import java.io.IOException
import kotlin.math.ceil

/**
 * 空间不足。[requiredAvailableBytes] 是"要跑这个档位至少需要多少可用空间"，
 * 与 [availableBytes] 一起供界面给出可操作的提示，而不是一句笼统的失败。
 */
class InsufficientStorageException(
    val requiredAvailableBytes: Long,
    val availableBytes: Long,
    message: String
) : IOException(message)

/**
 * 写入安全护栏。
 *
 * 这是本功能唯一的真实风险点：把用户手机写满会导致系统异常，
 * 极端情况下设备无法正常启动。因此所有写入路径都必须先通过 [verifyCapacity]，
 * 运行期间按 [HEADROOM_CHECK_INTERVAL_BYTES] 间隔调用 [verifyRuntimeHeadroom]。
 *
 * 本对象只做策略判断，不接触 Android 的存储 API，
 * 目的卷的实际容量由 [StorageTargetResolver] 探测后传入，因此可完整单元测试。
 */
object StorageSafetyGuard {

    /** 测试结束后必须保留的最小可用空间。 */
    const val MIN_FREE_BYTES_AFTER_RUN = 200L * MB

    /** 测试载荷不得超过当前可用空间的比例，避免在低空间设备上冒险。 */
    const val MAX_ALLOCATION_RATIO = 0.10

    /** 运行中巡检可用空间的间隔字节数。 */
    const val HEADROOM_CHECK_INTERVAL_BYTES = 16L * MB

    /**
     * 估算一次测试的**峰值**占用字节数。
     *
     * 顺序文件贯穿整轮测试，其余产物各自只在自己那个阶段存在（随机区域在大区域写完后即删，
     * 小文件阶段结束后整体删除），因此峰值是顺序文件加上最大的那个阶段性产物，而不是它们的总和。
     * 按总和估算会大幅高估需求，让中等容量设备上的档位被无谓地拒绝。
     *
     * 冷读关闭时不会写丢弃文件，因此不为它预留空间——判据与 [BenchmarkSpec.phasePlan]
     * 是否包含 `CACHE_FLUSH` 保持一致。
     *
     * SQLite 阶段按每行 [SQLITE_BYTES_PER_ROW_ESTIMATE] 粗估，含 WAL 与索引开销。
     */
    fun peakPayloadBytes(spec: BenchmarkSpec): Long {
        val largestPhaseArtifact = maxOf(
            if (spec.coldRead) spec.cacheFlushBytes else 0L,
            spec.randomRegionBytes,
            spec.smallFileCount.toLong() * spec.smallFileBytes,
            spec.sqliteRows.toLong() * SQLITE_BYTES_PER_ROW_ESTIMATE
        )
        return spec.sequentialBytes + largestPhaseArtifact
    }

    /**
     * 写入前的容量校验。
     *
     * 两条都必须满足：
     * 1. 载荷不超过可用空间的 [MAX_ALLOCATION_RATIO]；
     * 2. 载荷写完后仍剩余至少 [MIN_FREE_BYTES_AFTER_RUN]。
     *
     * @param payloadBytes 本次测试将占用的最大字节数
     * @param availableBytes 目标卷当前可用字节数（优先取 `getAllocatableBytes`）
     * @throws InsufficientStorageException 任一条件不满足
     */
    @Throws(InsufficientStorageException::class)
    fun verifyCapacity(payloadBytes: Long, availableBytes: Long) {
        if (payloadBytes <= 0L) {
            return
        }

        val ratioBudget = (availableBytes * MAX_ALLOCATION_RATIO).toLong()
        if (payloadBytes > ratioBudget) {
            // 比例上限起作用时，用户需要腾出多少空间才能让该档位变得安全。
            val required = ceil(payloadBytes / MAX_ALLOCATION_RATIO).toLong()
            throw InsufficientStorageException(
                requiredAvailableBytes = required,
                availableBytes = availableBytes,
                message = "payload ${payloadBytes}B exceeds ${(MAX_ALLOCATION_RATIO * 100).toInt()}% " +
                    "of ${availableBytes}B available (ratio budget ${ratioBudget}B)"
            )
        }

        val requiredWithHeadroom = payloadBytes + MIN_FREE_BYTES_AFTER_RUN
        if (requiredWithHeadroom > availableBytes) {
            throw InsufficientStorageException(
                requiredAvailableBytes = requiredWithHeadroom,
                availableBytes = availableBytes,
                message = "need ${requiredWithHeadroom}B available, only ${availableBytes}B free"
            )
        }
    }

    /**
     * 运行中的余量巡检。
     *
     * @param freeBytes 目标卷当前可用字节数
     * @throws InsufficientStorageException 跌破 [MIN_FREE_BYTES_AFTER_RUN]
     */
    @Throws(InsufficientStorageException::class)
    fun verifyRuntimeHeadroom(freeBytes: Long) {
        if (freeBytes < MIN_FREE_BYTES_AFTER_RUN) {
            throw InsufficientStorageException(
                requiredAvailableBytes = MIN_FREE_BYTES_AFTER_RUN,
                availableBytes = freeBytes,
                message = "free space dropped to ${freeBytes}B, below the " +
                    "${MIN_FREE_BYTES_AFTER_RUN}B safety floor"
            )
        }
    }

    /**
     * 清空测试目录下的全部产物。
     *
     * 只删除目录内的内容，**保留工作目录本身**：引擎在开始时会先调用本方法清理上次残留，
     * 若连目录一起删掉，紧接着的写入就会因父目录不存在而失败。
     *
     * 清理是尽力而为：失败只记录日志并返回 false，不抛异常，
     * 避免清理期的错误掩盖真正的测试失败原因。调用方在取消路径上
     * 必须用 `NonCancellable` 包裹本调用，否则清理会被一并取消。
     *
     * @return 目录不存在或已无内容时为 true
     */
    fun purgeTestArtifacts(workspace: File): Boolean {
        if (!workspace.exists()) {
            return true
        }
        val remaining = workspace.listFiles()?.toList().orEmpty()
        var allRemoved = true
        remaining.forEach { entry ->
            val removed = runCatching { entry.deleteRecursively() }
                .onFailure { error -> Log.w(TAG, "failed to delete ${entry.name}", error) }
                .getOrDefault(false)
            if (!removed) {
                allRemoved = false
                Log.w(TAG, "test artifact left behind: ${entry.absolutePath}")
            }
        }
        return allRemoved
    }

    private const val TAG = "StorageSafetyGuard"

    /** SQLite 阶段每行的空间粗估：载荷 + 行与索引开销 + WAL。 */
    private const val SQLITE_BYTES_PER_ROW_ESTIMATE = 512L
}
