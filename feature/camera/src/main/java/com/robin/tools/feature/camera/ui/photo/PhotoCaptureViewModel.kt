package com.robin.tools.feature.camera.ui.photo

import android.app.Application
import android.graphics.Bitmap
import android.graphics.SurfaceTexture
import android.util.Log
import android.view.Surface
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.robin.tools.feature.camera.camera2.Camera2Controller
import com.robin.tools.feature.camera.filter.FilterType
import com.robin.tools.feature.camera.opengl.CameraGlRenderer
import com.robin.tools.feature.camera.opengl.CameraOrientation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class PhotoUiState(
    val isReady: Boolean = false,
    val isFrontCamera: Boolean = false,
    val isSwitching: Boolean = false,
    val currentFilter: FilterType = FilterType.NONE,
    val error: String? = null
)

class PhotoCaptureViewModel(application: Application) : AndroidViewModel(application) {
    private val _uiState = MutableStateFlow(PhotoUiState())
    val uiState: StateFlow<PhotoUiState> = _uiState.asStateFlow()

    val renderer = CameraGlRenderer()
    private val cameraController = Camera2Controller(application)
    private var surfaceTexture: SurfaceTexture? = null
    private var previewSurface: Surface? = null
    private val mutex = Mutex()

    init {
        renderer.setFilterResources(application.resources)
        renderer.onSurfaceCreated = { st ->
            surfaceTexture = st
            viewModelScope.launch { openCamera(front = false) }
        }
    }

    private suspend fun openCamera(front: Boolean) = mutex.withLock {
        try {
            val id = cameraController.getCameraId(front)
                ?: run {
                    _uiState.update { it.copy(error = "No camera", isSwitching = false) }
                    return
                }
            val size = cameraController.getPreviewSize(id)
            val st = surfaceTexture ?: return
            st.setDefaultBufferSize(size.width, size.height)
            renderer.setPreviewSize(size.width, size.height)
            renderer.setCameraOrientation(
                isFront = front,
                sensorOrientation = cameraController.getSensorOrientation(id),
                displayRotationDegrees = CameraOrientation.displayRotationDegrees(getApplication())
            )
            val surface = previewSurface?.takeIf { it.isValid } ?: Surface(st).also { previewSurface = it }
            val device = cameraController.openCamera(id)
            val session = cameraController.createCaptureSession(device, listOf(surface))
            val req = cameraController.createPreviewRequest(device, surface)
            cameraController.startRepeatingRequest(session, req)
            _uiState.update {
                it.copy(isReady = true, isFrontCamera = front, isSwitching = false, error = null)
            }
        } catch (e: Exception) {
            Log.e(TAG, "openCamera", e)
            _uiState.update { it.copy(isReady = false, isSwitching = false, error = e.message) }
        }
    }

    fun switchCamera() {
        if (_uiState.value.isSwitching) return
        viewModelScope.launch {
            val next = !_uiState.value.isFrontCamera
            _uiState.update { it.copy(isSwitching = true, isReady = false) }
            cameraController.closeAsync()
            openCamera(next)
        }
    }

    fun setFilter(type: FilterType) {
        _uiState.update { it.copy(currentFilter = type) }
        renderer.currentFilterType = type
    }

    fun captureStill(onResult: (Bitmap?) -> Unit) {
        renderer.requestStillCapture(onResult)
    }

    fun releaseCamera() {
        cameraController.close()
        try {
            previewSurface?.release()
        } catch (_: Exception) {
        }
        previewSurface = null
    }

    override fun onCleared() {
        releaseCamera()
        renderer.release()
        super.onCleared()
    }

    companion object {
        private const val TAG = "PhotoCaptureVM"
    }
}
