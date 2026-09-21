package com.robin.tools.feature.storage.data

import android.content.Context
import android.os.Environment
import android.os.StatFs
import android.os.storage.StorageManager
import android.util.Log
import java.io.File
import java.io.IOException

/**
 * 目标目录解析与容量探测。
 *
 * 本类是唯一接触 Android 存储 API 的地方，把探测结果交给
 * [StorageSafetyGuard] 做策略判断，两者职责不重叠。
 */
class StorageTargetResolver(private val context: Context) {

    /**
     * 解析目标在应用沙箱中的根目录，**不创建任何内容**。
     *
     * @return 目标不可用时（例如设备没有外部存储）返回 null
     */
    fun resolveRootDirectory(target: BenchmarkTarget): File? = when (target) {
        BenchmarkTarget.INTERNAL_FILES -> context.filesDir
        BenchmarkTarget.INTERNAL_CACHE -> context.cacheDir
        BenchmarkTarget.EXTERNAL_APP -> context.getExternalFilesDir(null)
    }

    /**
     * 解析并创建测试工作目录。
     *
     * 所有测试产物都放在这个专用子目录下，便于统一清理，
     * 也不会污染目标目录根部已有的内容。
     *
     * @throws TargetUnavailableException 根目录不可用，或工作目录无法创建
     */
    @Throws(TargetUnavailableException::class)
    fun requireWorkspace(target: BenchmarkTarget): File {
        val root = resolveRootDirectory(target)
            ?: throw TargetUnavailableException("target $target has no directory on this device")

        val workspace = File(root, WORKSPACE_DIR_NAME)
        if (!workspace.exists() && !workspace.mkdirs()) {
            throw TargetUnavailableException("cannot create workspace at ${workspace.absolutePath}")
        }
        if (!workspace.isDirectory) {
            throw TargetUnavailableException("workspace path is not a directory: ${workspace.absolutePath}")
        }
        return workspace
    }

    /**
     * 目标卷当前可用字节数。
     *
     * 探测失败时返回 0：宁可让 [StorageSafetyGuard.verifyCapacity] 拒绝启动，
     * 也不在无法确认剩余空间的情况下开始写入。
     */
    fun availableBytes(dir: File): Long = runCatching {
        StatFs(dir.absolutePath).availableBytes
    }.getOrElse { error ->
        Log.w(TAG, "StatFs.availableBytes failed for ${dir.absolutePath}", error)
        0L
    }

    /** 目标卷总字节数。探测失败时返回 0。 */
    fun totalBytes(dir: File): Long = runCatching {
        StatFs(dir.absolutePath).totalBytes
    }.getOrElse { error ->
        Log.w(TAG, "StatFs.totalBytes failed for ${dir.absolutePath}", error)
        0L
    }

    /**
     * 目标卷还能创建多大的文件。
     *
     * 优先使用 [StorageManager.getAllocatableBytes]，它扣除了系统保留空间，
     * 比 `StatFs` 的可用字节数更权威；不可用时降级为 [availableBytes]。
     */
    fun allocatableBytes(dir: File): Long {
        val storageManager = context.getSystemService(StorageManager::class.java)
        if (storageManager != null) {
            try {
                val volumeUuid = storageManager.getUuidForPath(dir)
                return storageManager.getAllocatableBytes(volumeUuid)
            } catch (e: IOException) {
                Log.w(TAG, "getAllocatableBytes failed for ${dir.absolutePath}, using StatFs", e)
            }
        }
        return availableBytes(dir)
    }

    /** 目标是否位于系统模拟的外部存储上（性能低于裸介质属正常现象）。 */
    fun isExternalEmulated(dir: File): Boolean = runCatching {
        Environment.isExternalStorageEmulated(dir)
    }.getOrDefault(false)

    companion object {
        /** 测试产物专用子目录名。改名会让上一次运行残留的文件失去兜底清理，不要随意修改。 */
        const val WORKSPACE_DIR_NAME = "storage-bench"

        private const val TAG = "StorageTargetResolver"
    }
}
