package com.robin.tools.feature.lightlux.data

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SnapshotListViewModel(private val repository: LightRepository) : ViewModel() {

    private val _entries = MutableStateFlow<List<LightEntry>>(emptyList())
    val entries: StateFlow<List<LightEntry>> = _entries.asStateFlow()

    init {
        loadEntries()
    }

    private fun loadEntries() {
        viewModelScope.launch {
            repository.getAllEntries().collect { list ->
                _entries.value = list
            }
        }
    }

    fun deleteAll() {
        viewModelScope.launch {
            repository.deleteAllEntries()
        }
    }

    fun deleteEntry(entry: LightEntry) {
        viewModelScope.launch {
            repository.deleteEntry(entry)
        }
    }

    class Factory(private val repository: LightRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return SnapshotListViewModel(repository) as T
        }
    }
}
