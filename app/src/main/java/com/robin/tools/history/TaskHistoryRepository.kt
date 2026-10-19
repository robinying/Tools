package com.robin.tools.history

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

/** Read/write entry point for task history. */
class TaskHistoryRepository(private val dao: TaskHistoryDao) {

    fun observeRecent(): Flow<List<TaskHistoryEntity>> = dao.observeRecent()

    suspend fun record(
        toolId: String,
        title: String,
        detail: String?,
        outputUri: String?,
        isSuccess: Boolean
    ) = withContext(Dispatchers.IO) {
        dao.insert(
            TaskHistoryEntity(
                toolId = toolId,
                title = title,
                detail = detail,
                outputUri = outputUri,
                isSuccess = isSuccess,
                createdAt = System.currentTimeMillis()
            )
        )
        dao.trimTo(TaskHistoryDao.MAX_ROWS)
    }

    suspend fun clear() = withContext(Dispatchers.IO) { dao.deleteAll() }
}
