package com.robin.tools.feature.lightlux.data

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.robin.tools.feature.lightlux.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ChartDataPoint(
    val timestamp: Long,
    val luxValue: Float
)

class MainViewModel(
    application: Application,
    private val repository: LightRepository
) : AndroidViewModel(application) {
    private val appContext get() = getApplication<Application>()

    private val _currentLux = MutableStateFlow(0f)
    val currentLux: StateFlow<Float> = _currentLux.asStateFlow()

    private val _chartWindow = MutableStateFlow(ChartWindow.SEC_60)
    val chartWindow: StateFlow<ChartWindow> = _chartWindow.asStateFlow()

    /** Full recent samples (capped); UI filters by [chartWindow]. */
    private val _sampleBuffer = MutableStateFlow<List<ChartDataPoint>>(emptyList())

    private val _realtimeChartData = MutableStateFlow<List<ChartDataPoint>>(emptyList())
    val realtimeChartData: StateFlow<List<ChartDataPoint>> = _realtimeChartData.asStateFlow()

    private val _chartStats = MutableStateFlow(ChartStats())
    val chartStats: StateFlow<ChartStats> = _chartStats.asStateFlow()

    private val _saveStatus = MutableStateFlow<String?>(null)
    val saveStatus: StateFlow<String?> = _saveStatus.asStateFlow()

    private companion object {
        /** Keep slightly more than the longest window so switching windows still has history. */
        const val MAX_BUFFER_MS = 300_000L
        const val MAX_POINTS = 3_000
    }

    fun setChartWindow(window: ChartWindow) {
        if (_chartWindow.value == window) return
        _chartWindow.value = window
        recomputeVisible()
    }

    fun updateLuxFromSensor(lux: Float) {
        val now = System.currentTimeMillis()
        _currentLux.value = lux
        _sampleBuffer.update { list ->
            val newPoint = ChartDataPoint(now, lux)
            val filtered = list.filter { now - it.timestamp <= MAX_BUFFER_MS }
            (filtered + newPoint)
                .sortedBy { it.timestamp }
                .takeLast(MAX_POINTS)
        }
        recomputeVisible(now)
    }

    /**
     * @param note optional free-text note stored with the snapshot
     */
    fun saveSnapshot(note: String = "") {
        viewModelScope.launch {
            val lux = _currentLux.value
            val timestamp = System.currentTimeMillis()
            val trimmed = note.trim().take(200)
            repository.insertEntry(
                LightEntry(timestamp = timestamp, luxValue = lux, note = trimmed)
            )
            _saveStatus.value = if (trimmed.isEmpty()) {
                appContext.getString(R.string.saved_snapshot, lux)
            } else {
                appContext.getString(R.string.saved_snapshot_with_note, lux, trimmed)
            }
        }
    }

    fun clearSaveStatus() {
        _saveStatus.value = null
    }

    private fun recomputeVisible(now: Long = System.currentTimeMillis()) {
        val windowMs = _chartWindow.value.durationMs
        val visible = _sampleBuffer.value.filter { now - it.timestamp <= windowMs }
        _realtimeChartData.value = visible
        _chartStats.value = ChartStats.from(visible)
    }

    class Factory(
        private val application: Application,
        private val repository: LightRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return MainViewModel(application, repository) as T
        }
    }
}
