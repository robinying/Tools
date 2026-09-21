package com.robin.tools.feature.storage.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface BenchmarkResultDao {

    @Insert
    suspend fun insert(entity: BenchmarkResultEntity): Long

    /** 按时间倒序观察历史记录。 */
    @Query("SELECT * FROM storage_benchmark_results ORDER BY startedAtEpochMillis DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<BenchmarkResultEntity>>

    @Query("SELECT * FROM storage_benchmark_results ORDER BY startedAtEpochMillis DESC LIMIT :limit")
    suspend fun getRecent(limit: Int): List<BenchmarkResultEntity>

    /**
     * 只保留最近的 [keep] 条，避免数据库随使用次数无限增长。
     *
     * 用子查询按时间排序取前 [keep] 个 id，而不是"删除早于某个时间戳的记录"：
     * 后者在时间戳相同或系统时间被回拨时会误删。
     */
    @Query(
        "DELETE FROM storage_benchmark_results WHERE id NOT IN " +
            "(SELECT id FROM storage_benchmark_results ORDER BY startedAtEpochMillis DESC LIMIT :keep)"
    )
    suspend fun trimTo(keep: Int)

    @Query("DELETE FROM storage_benchmark_results")
    suspend fun deleteAll()
}
