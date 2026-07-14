package com.robin.tools.feature.camera.ui.record

import android.app.Application
import android.graphics.SurfaceTexture
import android.opengl.EGL14
import android.util.Log
import android.view.Surface
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.robin.tools.feature.camera.camera2.Camera2Controller
import com.robin.tools.feature.camera.camera2.CameraConfig
import com.robin.tools.feature.camera.encoder.EncoderConfig
import com.robin.tools.feature.camera.encoder.TextureMovieEncoder
import com.robin.tools.feature.camera.filter.FilterType
import com.robin.tools.feature.camera.opengl.CameraGlRenderer
import com.robin.tools.feature.camera.opengl.CameraOrientation
import com.robin.tools.feature.camera.segment.SegmentMerger
import com.robin.tools.feature.camera.segment.SegmentRecorder
import com.robin.tools.feature.camera.storage.CameraFileManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class RecordViewModel(application: Application) : AndroidViewModel(application) {
    private val _uiState = MutableStateFlow(RecordUiState())
    val uiState: StateFlow<RecordUiState> = _uiState.asStateFlow()

    val renderer = CameraGlRenderer()
    private val cameraController = Camera2Controller(application)
    private val fileManager = CameraFileManager(application)
    private val segmentRecorder = SegmentRecorder(fileManager.segmentDir)
    private val encoder = TextureMovieEncoder()
    private val cameraConfig = CameraConfig()

    private var currentSurfaceTexture: SurfaceTexture? = null
    /** Reused across camera switches — recreating Surface is expensive. */
    private var previewSurface: Surface? = null
    private var isRecordingInProgress: Boolean = false

    private val cameraMutex = Mutex()
    private var switchJob: Job? = null

    init {
        renderer.setFilterResources(application.resources)
        renderer.onSurfaceCreated = { surfaceTexture ->
            currentSurfaceTexture = surfaceTexture
            viewModelScope.launch {
                openCamera(front = false)
            }
        }
        renderer.onFrameAvailable = { st ->
            if (isRecordingInProgress) {
                encoder.frameAvailable(st.hashCode(), st.timestamp)
            }
        }
    }

    private suspend fun openCamera(front: Boolean) = cameraMutex.withLock {
        try {
            val cameraId = cameraController.getCameraId(front)
                ?: run {
                    _uiState.update { it.copy(error = "No camera found", isSwitchingCamera = false) }
                    return
                }

            // Warm characteristics / size from cache before open to minimize gap after open.
            val previewSize = cameraController.getPreviewSize(cameraId)
            val sensorOrientation = cameraController.getSensorOrientation(cameraId)
            val displayRotation = CameraOrientation.displayRotationDegrees(getApplication())

            val st = currentSurfaceTexture ?: return
            st.setDefaultBufferSize(previewSize.width, previewSize.height)
            renderer.setPreviewSize(previewSize.width, previewSize.height)
            renderer.setCameraOrientation(
                isFront = front,
                sensorOrientation = sensorOrientation,
                displayRotationDegrees = displayRotation
            )

            val surface = previewSurface?.takeIf { it.isValid } ?: Surface(st).also {
                previewSurface = it
            }

            val device = cameraController.openCamera(cameraId)
            val session = cameraController.createCaptureSession(device, listOf(surface))
            val request = cameraController.createPreviewRequest(device, surface)
            cameraController.startRepeatingRequest(session, request)

            _uiState.update {
                it.copy(
                    isCameraReady = true,
                    isFrontCamera = front,
                    isSwitchingCamera = false,
                    error = null
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "openCamera failed", e)
            _uiState.update {
                it.copy(
                    error = "Camera error: ${e.message}",
                    isCameraReady = false,
                    isSwitchingCamera = false
                )
            }
        }
    }

    /** Call when activity/display rotation may have changed so preview stays upright. */
    fun refreshDisplayOrientation() {
        val isFront = _uiState.value.isFrontCamera
        val sensorOrientation = cameraController.getCameraId(isFront)?.let { id ->
            cameraController.getSensorOrientation(id)
        } ?: 90
        renderer.setCameraOrientation(
            isFront = isFront,
            sensorOrientation = sensorOrientation,
            displayRotationDegrees = CameraOrientation.displayRotationDegrees(getApplication())
        )
    }

    fun switchCamera() {
        if (isRecordingInProgress || _uiState.value.isCountingDown) return
        if (_uiState.value.isSwitchingCamera) return

        // Cancel a stale switch (rapid taps) and start fresh.
        switchJob?.cancel()
        switchJob = viewModelScope.launch {
            val newFront = !_uiState.value.isFrontCamera
            _uiState.update {
                it.copy(isSwitchingCamera = true, isCameraReady = false, error = null)
            }
            try {
                // Close on background path; wait for onClosed before open to avoid HAL stalls.
                withContext(Dispatchers.Default) {
                    cameraController.closeAsync()
                }
                openCamera(newFront)
            } catch (e: Exception) {
                Log.e(TAG, "switchCamera failed", e)
                _uiState.update {
                    it.copy(
                        isSwitchingCamera = false,
                        error = "Camera switch failed: ${e.message}"
                    )
                }
            }
        }
    }

    fun startCountdown() {
        if (_uiState.value.isSwitchingCamera) return
        viewModelScope.launch {
            _uiState.update { it.copy(isCountingDown = true, countdownValue = 3) }
            repeat(3) { i ->
                delay(1000)
                _uiState.update { it.copy(countdownValue = 2 - i) }
            }
            delay(500)
            _uiState.update { it.copy(isCountingDown = false) }
            startRecordingInternal()
        }
    }

    private fun startRecordingInternal() {
        val outputPath = segmentRecorder.startNewSegment()
        val config = EncoderConfig(
            outputPath,
            cameraConfig.videoWidth,
            cameraConfig.videoHeight,
            cameraConfig.videoBitRate
        )

        val eglContext = EGL14.eglGetCurrentContext()
        encoder.startRecording(config, eglContext)
        isRecordingInProgress = true
        _uiState.update {
            it.copy(isRecording = true, segmentCount = segmentRecorder.segmentCount + 1)
        }
    }

    fun startRecording() {
        startCountdown()
    }

    fun stopRecording() {
        isRecordingInProgress = false
        encoder.stopRecording()
        segmentRecorder.onSegmentComplete(0)
        _uiState.update { it.copy(isRecording = false) }
    }

    fun deleteLastSegment() {
        if (segmentRecorder.deleteLastSegment()) {
            _uiState.update { it.copy(segmentCount = segmentRecorder.segmentCount) }
        }
    }

    fun setFilter(type: FilterType) {
        _uiState.update { it.copy(currentFilter = type) }
        renderer.currentFilterType = type
    }

    fun finishRecording(onComplete: (String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isProcessing = true) }
            val segments = segmentRecorder.getSegments()
            val outputFile = fileManager.createOutputFile()
            val success = SegmentMerger.mergeSegments(segments, outputFile.absolutePath)
            _uiState.update { it.copy(isProcessing = false) }
            if (success) {
                launch(Dispatchers.Main) { onComplete(outputFile.absolutePath) }
            } else {
                _uiState.update { it.copy(error = "Failed to merge segments") }
            }
        }
    }

    override fun onCleared() {
        switchJob?.cancel()
        cameraController.release()
        encoder.stopRecording()
        try {
            previewSurface?.release()
        } catch (_: Exception) {
        }
        previewSurface = null
        renderer.release()
        super.onCleared()
    }

    companion object {
        private const val TAG = "RecordViewModel"
    }
}
