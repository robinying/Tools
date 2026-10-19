package com.robin.tools.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class TaskHistoryViewModel(
    private val repository: TaskHistoryRepository
) : ViewModel() {

    val entries: StateFlow<List<TaskHistoryEntity>> = repository
        .observeRecent()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun clear() {
        viewModelScope.launch { repository.clear() }
    }

    class Factory(private val repository: TaskHistoryRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            TaskHistoryViewModel(repository) as T
    }
}
