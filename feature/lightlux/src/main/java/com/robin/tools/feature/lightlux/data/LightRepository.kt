package com.robin.tools.feature.lightlux.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class LightRepository(private val dao: LightEntryDao) {

    fun getRecentEntries(limit: Int = 20): Flow<List<LightEntry>> {
        return dao.getRecentEntries(limit)
    }

    suspend fun insertEntry(entry: LightEntry) = withContext(Dispatchers.IO) {
        dao.insert(entry)
    }

    suspend fun getRecentEntriesOnce(limit: Int = 20): List<LightEntry> = withContext(Dispatchers.IO) {
        dao.getRecentEntriesOnce(limit)
    }

    fun getAllEntries(): Flow<List<LightEntry>> = dao.getAllEntries()

    suspend fun deleteAllEntries() = withContext(Dispatchers.IO) {
        dao.deleteAll()
    }

    suspend fun deleteEntry(entry: LightEntry) = withContext(Dispatchers.IO) {
        dao.delete(entry)
    }
}
