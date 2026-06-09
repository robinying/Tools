package com.robin.tools.feature.media.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object CompressionManager {
    private val _taskState = MutableStateFlow<CompressionTaskState>(CompressionTaskState.Idle)
    val taskState: StateFlow<CompressionTaskState> = _taskState.asStateFlow()

    @Volatile
    private var isCancelled = false

    fun updateState(state: CompressionTaskState) {
        _taskState.value = state
    }

    fun startTask() {
        isCancelled = false
    }

    fun cancelTask() {
        isCancelled = true
    }

    fun isCancelled(): Boolean = isCancelled

    fun reset() {
        _taskState.value = CompressionTaskState.Idle
        isCancelled = false
    }
}
