package com.robin.tools.feature.storage.data

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.Environment
import android.util.Log
import java.io.File

/**
 * 设备与卷快照采集。
 *
 * 只采集硬件与容量参数，用于给测试结果做归因。
 * 刻意不采集序列号、IMEI、账号、`Settings.Secure.ANDROID_ID` 等任何标识符。
 */
class DeviceSnapshotProvider(private val context: Context) {

    /**
     * 采集当前设备快照。
     *
     * @param dir 测试目标目录，用于判断是否位于模拟外部存储
     * @param volumeTotalBytes 目标卷总字节数
     * @param freeBytesAtStart 开始测试时的可用字节数
     */
    fun snapshot(dir: File, volumeTotalBytes: Long, freeBytesAtStart: Long): DeviceSnapshot =
        DeviceSnapshot(
            model = Build.MODEL.orEmpty(),
            apiLevel = Build.VERSION.SDK_INT,
            abi = Build.SUPPORTED_ABIS.firstOrNull().orEmpty(),
            totalRamBytes = totalRamBytes(),
            volumeTotalBytes = volumeTotalBytes,
            volumeFreeBytesAtStart = freeBytesAtStart,
            isExternalEmulated = isExternalEmulated(dir)
        )

    private fun totalRamBytes(): Long {
        val activityManager = context.getSystemService(ActivityManager::class.java) ?: return 0L
        val memoryInfo = ActivityManager.MemoryInfo()
        return try {
            activityManager.getMemoryInfo(memoryInfo)
            memoryInfo.totalMem
        } catch (e: RuntimeException) {
            // 部分定制 ROM 的 getMemoryInfo 实现会抛异常，快照缺失不应中断测试。
            Log.w(TAG, "getMemoryInfo failed, RAM size omitted", e)
            0L
        }
    }

    private fun isExternalEmulated(dir: File): Boolean = runCatching {
        Environment.isExternalStorageEmulated(dir)
    }.getOrDefault(false)

    private companion object {
        const val TAG = "DeviceSnapshotProvider"
    }
}
