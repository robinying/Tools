package com.robin.tools.feature.storage.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * 历史记录条目：落库字段的领域读模型。
 *
 * 不含吞吐时间线与百分位尾部，理由见 [BenchmarkResultEntity]。
 */
data class BenchmarkHistoryEntry(
    val id: Long,
    val startedAtEpochMillis: Long,
    val target: BenchmarkTarget,
    val preset: BenchmarkPreset?,
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

/** 基准测试历史的读写入口。 */
class BenchmarkRepository(private val dao: BenchmarkResultDao) {

    /** 按时间倒序观察最近的历史记录。 */
    fun observeRecent(limit: Int = MAX_HISTORY_ROWS): Flow<List<BenchmarkHistoryEntry>> =
        dao.observeRecent(limit).map { entities -> entities.map { it.toHistoryEntry() } }

    /** 只保留最近 [MAX_HISTORY_ROWS] 条。 */
    suspend fun getRecent(limit: Int = MAX_HISTORY_ROWS): List<BenchmarkHistoryEntry> =
        withContext(Dispatchers.IO) {
            dao.getRecent(limit).map { it.toHistoryEntry() }
        }

    /**
     * 保存一次测试结果，并把历史裁剪到上限。
     *
     * 裁剪在插入之后执行，因此单次插入不会因为要先腾位置而失败。
     */
    suspend fun save(result: BenchmarkResult): Long = withContext(Dispatchers.IO) {
        val id = dao.insert(result.toEntity())
        dao.trimTo(MAX_HISTORY_ROWS)
        id
    }

    suspend fun deleteAll() = withContext(Dispatchers.IO) {
        dao.deleteAll()
    }

    companion object {
        /** 历史记录上限，避免数据库随使用次数无限增长。 */
        const val MAX_HISTORY_ROWS = 50
    }
}

/**
 * 结果转实体。
 *
 * 枚举一律写名称而不是序号，保证将来重排枚举不会让历史记录错位。
 */
internal fun BenchmarkResult.toEntity(): BenchmarkResultEntity = BenchmarkResultEntity(
    startedAtEpochMillis = startedAtEpochMillis,
    targetName = target.name,
    presetName = preset?.name,
    coldReadAttempted = coldReadAttempted,
    sequentialWriteBytesPerSecond = sequentialWrite.averageBytesPerSecond,
    sequentialReadBytesPerSecond = sequentialReadFirstPass.averageBytesPerSecond,
    randomReadIops = randomRead.operationsPerSecond,
    randomWriteIops = randomWrite.operationsPerSecond,
    smallFilesPerSecond = smallFile.operationsPerSecond,
    sqliteRowsPerSecond = sqliteInsert?.operationsPerSecond,
    totalElapsedMillis = totalElapsedMillis,
    deviceModel = device.model
)

/**
 * 实体转历史条目。
 *
 * 枚举名无法识别时回退到安全默认值而不是抛异常：一条来自旧版本、枚举已被重命名的记录
 * 不应该让整个历史列表打不开。
 */
internal fun BenchmarkResultEntity.toHistoryEntry(): BenchmarkHistoryEntry = BenchmarkHistoryEntry(
    id = id,
    startedAtEpochMillis = startedAtEpochMillis,
    target = BenchmarkTarget.entries.firstOrNull { it.name == targetName }
        ?: BenchmarkTarget.INTERNAL_FILES,
    preset = presetName?.let { name -> BenchmarkPreset.entries.firstOrNull { it.name == name } },
    coldReadAttempted = coldReadAttempted,
    sequentialWriteBytesPerSecond = sequentialWriteBytesPerSecond,
    sequentialReadBytesPerSecond = sequentialReadBytesPerSecond,
    randomReadIops = randomReadIops,
    randomWriteIops = randomWriteIops,
    smallFilesPerSecond = smallFilesPerSecond,
    sqliteRowsPerSecond = sqliteRowsPerSecond,
    totalElapsedMillis = totalElapsedMillis,
    deviceModel = deviceModel
)
