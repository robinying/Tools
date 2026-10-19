package com.robin.tools.feature.storage.data

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class StorageCleanupViewModel(application: Application) : AndroidViewModel(application) {

    data class UiState(
        val isScanning: Boolean = false,
        val thresholdBytes: Long = StorageCleanupRepository.THRESHOLD_OPTIONS.first(),
        val report: CleanupReport? = null,
        val message: String? = null
    )

    private val repository = StorageCleanupRepository(application.applicationContext)

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    fun setThreshold(bytes: Long) {
        if (_uiState.value.isScanning) return
        _uiState.update { it.copy(thresholdBytes = bytes, report = null) }
    }

    fun scan() {
        if (_uiState.value.isScanning) return
        val threshold = _uiState.value.thresholdBytes
        viewModelScope.launch {
            _uiState.update { it.copy(isScanning = true, report = null, message = null) }
            val report = runCatching { repository.scan(threshold) }
            _uiState.update {
                it.copy(
                    isScanning = false,
                    report = report.getOrNull(),
                    message = report.exceptionOrNull()?.message
                )
            }
        }
    }

    fun clearMessage() {
        _uiState.update { it.copy(message = null) }
    }

    class Factory(private val application: Application) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            StorageCleanupViewModel(application) as T
    }
}
