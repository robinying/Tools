package com.robin.tools.feature.camera.ui.record

import android.app.Application
import android.graphics.SurfaceTexture
import android.opengl.EGL14
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import android.view.Surface
import com.robin.tools.feature.camera.camera2.Camera2Controller
import com.robin.tools.feature.camera.camera2.CameraConfig
import com.robin.tools.feature.camera.camera2.CameraFacing
import com.robin.tools.feature.camera.encoder.EncoderConfig
import com.robin.tools.feature.camera.encoder.TextureMovieEncoder
import com.robin.tools.feature.camera.filter.FilterType
import com.robin.tools.feature.camera.opengl.CameraGlRenderer
import com.robin.tools.feature.camera.segment.SegmentMerger
import com.robin.tools.feature.camera.segment.SegmentRecorder
import com.robin.tools.feature.camera.storage.CameraFileManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

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
    private var isRecordingInProgress: Boolean = false

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

    private suspend fun openCamera(front: Boolean) {
        try {
            val cameraId = cameraController.getCameraId(front)
                ?: run { _uiState.update { it.copy(error = "No camera found") }; return }
            val device = cameraController.openCamera(cameraId)
            val st = currentSurfaceTexture ?: return

            val previewSize = cameraController.getPreviewSize(cameraId)
            st.setDefaultBufferSize(previewSize.width, previewSize.height)
            renderer.setPreviewSize(previewSize.width, previewSize.height)
            val surface = Surface(st)
            val session = cameraController.createCaptureSession(device, listOf(surface))
            val request = cameraController.createPreviewRequest(device, surface)

            renderer.isFrontCamera = front
            renderer.sensorOrientation = cameraController.getSensorOrientation(cameraId)
            renderer.setFilterResources(getApplication<Application>().resources)

            cameraController.startRepeatingRequest(session, request)
            _uiState.update {
                it.copy(isCameraReady = true, isFrontCamera = front)
            }
        } catch (e: Exception) {
            _uiState.update { it.copy(error = "Camera error: ${e.message}") }
        }
    }

    fun switchCamera() {
        viewModelScope.launch {
            cameraController.close()
            val newFront = !_uiState.value.isFrontCamera
            openCamera(newFront)
        }
    }

    fun startCountdown() {
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
        val config = EncoderConfig(outputPath, cameraConfig.videoWidth, cameraConfig.videoHeight, cameraConfig.videoBitRate)

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
        cameraController.release()
        encoder.stopRecording()
        renderer.release()
        super.onCleared()
    }
}
