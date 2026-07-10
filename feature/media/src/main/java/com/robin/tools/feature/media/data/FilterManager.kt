package com.robin.tools.feature.media.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Thread-safe singleton state manager for filter operations.
 *
 * Mirrors the [CompressionManager] pattern: single [MutableStateFlow]
 * that drives UI state changes, with a paired cancellation flag.
 */
object FilterManager {
    private val _state = MutableStateFlow<FilterState>(FilterState.Idle)
    val state: StateFlow<FilterState> = _state.asStateFlow()

    @Volatile
    private var isCancelled = false

    fun updateState(state: FilterState) {
        _state.value = state
    }

    fun startTask() {
        isCancelled = false
    }

    fun cancelTask() {
        isCancelled = true
    }

    fun isCancelled(): Boolean = isCancelled

    fun reset() {
        _state.value = FilterState.Idle
        isCancelled = false
    }
}
