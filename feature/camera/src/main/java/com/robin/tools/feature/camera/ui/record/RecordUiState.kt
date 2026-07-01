package com.robin.tools.feature.camera.ui.record

import android.graphics.SurfaceTexture
import com.robin.tools.feature.camera.filter.FilterType

data class RecordUiState(
    val isRecording: Boolean = false,
    val isCountingDown: Boolean = false,
    val countdownValue: Int = 3,
    val currentFilter: FilterType = FilterType.NONE,
    val segmentCount: Int = 0,
    val totalRecordedMs: Long = 0,
    val isFrontCamera: Boolean = false,
    val isCameraReady: Boolean = false,
    val isProcessing: Boolean = false,
    val outputVideoPath: String? = null,
    val error: String? = null
)
