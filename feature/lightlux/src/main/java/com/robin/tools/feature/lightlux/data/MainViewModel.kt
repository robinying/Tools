package com.robin.tools.feature.lightlux.data

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ChartDataPoint(
    val timestamp: Long,
    val luxValue: Float
)

class MainViewModel(private val repository: LightRepository) : ViewModel() {

    private val _currentLux = MutableStateFlow(0f)
    val currentLux: StateFlow<Float> = _currentLux.asStateFlow()

    private val _realtimeChartData = MutableStateFlow<List<ChartDataPoint>>(emptyList())
    val realtimeChartData: StateFlow<List<ChartDataPoint>> = _realtimeChartData.asStateFlow()

    private val _saveStatus = MutableStateFlow<String?>(null)
    val saveStatus: StateFlow<String?> = _saveStatus.asStateFlow()

    private companion object {
        const val ONE_MINUTE_MS = 60_000L
    }

    fun updateLuxFromSensor(lux: Float) {
        val now = System.currentTimeMillis()
        _currentLux.value = lux
        _realtimeChartData.update { list ->
            val newPoint = ChartDataPoint(now, lux)
            val filtered = list.filter { now - it.timestamp <= ONE_MINUTE_MS }
            (listOf(newPoint) + filtered).sortedBy { it.timestamp }
        }
    }

    fun saveSnapshot() {
        viewModelScope.launch {
            val lux = _currentLux.value
            val timestamp = System.currentTimeMillis()
            repository.insertEntry(LightEntry(timestamp = timestamp, luxValue = lux))
            _saveStatus.value = "Saved: ${"%.1f".format(lux)} lux"
        }
    }

    fun clearSaveStatus() {
        _saveStatus.value = null
    }

    class Factory(private val repository: LightRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return MainViewModel(repository) as T
        }
    }
}
