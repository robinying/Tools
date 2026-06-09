package com.robin.tools.feature.lightlux.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface LightEntryDao {

    @Insert
    suspend fun insert(entry: LightEntry)

    @Delete
    suspend fun delete(entry: LightEntry)

    @Query("SELECT * FROM light_entries ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentEntries(limit: Int = 20): Flow<List<LightEntry>>

    @Query("SELECT * FROM light_entries ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentEntriesOnce(limit: Int = 20): List<LightEntry>

    @Query("SELECT * FROM light_entries ORDER BY timestamp DESC")
    fun getAllEntries(): Flow<List<LightEntry>>

    @Query("DELETE FROM light_entries")
    suspend fun deleteAll()
}
