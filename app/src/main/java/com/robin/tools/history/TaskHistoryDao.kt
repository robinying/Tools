package com.robin.tools.history

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskHistoryDao {

    @Query("SELECT * FROM task_history ORDER BY createdAt DESC LIMIT :limit")
    fun observeRecent(limit: Int = MAX_ROWS): Flow<List<TaskHistoryEntity>>

    @Insert
    suspend fun insert(entity: TaskHistoryEntity): Long

    @Query("DELETE FROM task_history")
    suspend fun deleteAll()

    @Query(
        "DELETE FROM task_history WHERE id NOT IN " +
            "(SELECT id FROM task_history ORDER BY createdAt DESC LIMIT :limit)"
    )
    suspend fun trimTo(limit: Int)

    companion object {
        const val MAX_ROWS = 200
    }
}
