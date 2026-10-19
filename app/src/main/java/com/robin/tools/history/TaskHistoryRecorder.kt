package com.robin.tools.history

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Fire-and-forget recorder used by feature wrappers. */
class TaskHistoryRecorder(
    private val repository: TaskHistoryRepository,
    private val scope: CoroutineScope
) {
    fun record(
        toolId: String,
        title: String,
        detail: String? = null,
        outputUri: String? = null,
        isSuccess: Boolean = true
    ) {
        scope.launch(Dispatchers.IO) {
            repository.record(toolId, title, detail, outputUri, isSuccess)
        }
    }
}
