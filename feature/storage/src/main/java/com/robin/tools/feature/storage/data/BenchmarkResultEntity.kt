package com.robin.tools.feature.storage.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 基准测试结果的持久化实体。
 *
 * 只落库历史列表真正展示的字段：吞吐时间线、百分位尾部和设备细节都属于"将来可能要看"的
 * 投机性存储，等真有详情页时再做一次迁移不迟。当前这套列已经足够支撑列表展示与横向对比。
 *
 * 枚举以**名称字符串**存储而非序号：将来在枚举中间插入新档位时，历史记录不会整体错位。
 */
@Entity(tableName = "storage_benchmark_results")
data class BenchmarkResultEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val startedAtEpochMillis: Long,
    val targetName: String,
    val presetName: String?,
    val coldReadAttempted: Boolean,
    val sequentialWriteBytesPerSecond: Double,
    val sequentialReadBytesPerSecond: Double,
    val randomReadIops: Double,
    val randomWriteIops: Double,
    val smallFilesPerSecond: Double,
    val sqliteRowsPerSecond: Double?,
    val totalElapsedMillis: Long,
    val deviceModel: String
)
