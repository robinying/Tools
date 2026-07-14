package com.robin.tools.feature.camera.camera2

import android.content.Context
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.util.Range
import android.util.Size
import android.view.Surface
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class Camera2Controller(private val context: Context) {
    private val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    private var cameraDevice: CameraDevice? = null
    private var captureSession: CameraCaptureSession? = null
    private var cameraThread = HandlerThread("Camera2Thread").apply { start() }
    private val cameraHandler = Handler(cameraThread.looper)
    private var currentCameraId: String? = null

    /** Completed when the currently opened device delivers [CameraDevice.StateCallback.onClosed]. */
    private var closeSignal: CompletableDeferred<Unit>? = null

    /** Cached facing → id so switch does not re-scan characteristics. */
    private val cameraIdByFacing: Map<Boolean, String> by lazy {
        val map = mutableMapOf<Boolean, String>()
        for (id in cameraManager.cameraIdList) {
            val facing = cameraManager.getCameraCharacteristics(id)
                .get(CameraCharacteristics.LENS_FACING) ?: continue
            when (facing) {
                CameraCharacteristics.LENS_FACING_FRONT -> map.putIfAbsent(true, id)
                CameraCharacteristics.LENS_FACING_BACK -> map.putIfAbsent(false, id)
            }
        }
        map
    }

    private val characteristicsCache = mutableMapOf<String, CameraCharacteristics>()
    private val previewSizeCache = mutableMapOf<String, Size>()

    val isFrontCamera: Boolean
        get() {
            val id = currentCameraId ?: return false
            return characteristicsOf(id).get(CameraCharacteristics.LENS_FACING) ==
                CameraCharacteristics.LENS_FACING_FRONT
        }

    fun getCameraId(facingFront: Boolean): String? =
        cameraIdByFacing[facingFront] ?: cameraIdByFacing.values.firstOrNull()

    fun getSensorOrientation(cameraId: String): Int =
        characteristicsOf(cameraId).get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 0

    fun getPreviewSize(cameraId: String): Size {
        previewSizeCache[cameraId]?.let { return it }
        val map = characteristicsOf(cameraId)
            .get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
            ?: throw RuntimeException("Cannot get camera config map")
        val sizes = map.getOutputSizes(SurfaceTexture::class.java)
            ?: throw RuntimeException("No SurfaceTexture output sizes")
        val targetLong = 1280
        val targetAspect = 16f / 9f
        val chosen = sizes.minByOrNull { size ->
            val longEdge = maxOf(size.width, size.height)
            val shortEdge = minOf(size.width, size.height)
            val aspect = longEdge.toFloat() / shortEdge.toFloat()
            kotlin.math.abs(aspect - targetAspect) +
                kotlin.math.abs(longEdge - targetLong) / 2000f
        } ?: sizes.first()
        previewSizeCache[cameraId] = chosen
        return chosen
    }

    private fun characteristicsOf(cameraId: String): CameraCharacteristics =
        characteristicsCache.getOrPut(cameraId) {
            cameraManager.getCameraCharacteristics(cameraId)
        }

    /**
     * Stop preview, close session + device, and wait for [CameraDevice.StateCallback.onClosed]
     * (with a short timeout). Waiting avoids HAL contention that makes the next open slow.
     */
    suspend fun closeAsync() {
        val session = captureSession
        val device = cameraDevice
        captureSession = null
        cameraDevice = null
        currentCameraId = null

        if (session == null && device == null) return

        val signal = CompletableDeferred<Unit>()
        closeSignal = signal

        try {
            session?.stopRepeating()
        } catch (_: Exception) {
        }
        try {
            session?.abortCaptures()
        } catch (_: Exception) {
        }
        try {
            session?.close()
        } catch (_: Exception) {
        }

        if (device != null) {
            try {
                device.close()
            } catch (e: Exception) {
                Log.w(TAG, "device.close: ${e.message}")
                closeSignal = null
                signal.complete(Unit)
                return
            }
            // onClosed on the open-time callback completes [closeSignal].
            withTimeoutOrNull(CLOSE_TIMEOUT_MS) { signal.await() }
        } else {
            signal.complete(Unit)
        }
        closeSignal = null
    }

    /** Fire-and-forget close for [release]. Prefer [closeAsync] when switching. */
    fun close() {
        val session = captureSession
        val device = cameraDevice
        captureSession = null
        cameraDevice = null
        currentCameraId = null
        try {
            session?.stopRepeating()
        } catch (_: Exception) {
        }
        try {
            session?.close()
        } catch (_: Exception) {
        }
        try {
            device?.close()
        } catch (_: Exception) {
        }
        closeSignal?.complete(Unit)
        closeSignal = null
    }

    suspend fun openCamera(cameraId: String): CameraDevice {
        if (cameraDevice != null || captureSession != null) {
            closeAsync()
        }
        return suspendCancellableCoroutine { cont ->
            var resumed = false
            try {
                cameraManager.openCamera(cameraId, object : CameraDevice.StateCallback() {
                    override fun onOpened(device: CameraDevice) {
                        if (!resumed) {
                            resumed = true
                            cameraDevice = device
                            currentCameraId = cameraId
                            cont.resume(device)
                        }
                    }

                    override fun onDisconnected(device: CameraDevice) {
                        try {
                            device.close()
                        } catch (_: Exception) {
                        }
                        if (cameraDevice === device) {
                            cameraDevice = null
                            currentCameraId = null
                        }
                        closeSignal?.complete(Unit)
                        if (!resumed) {
                            resumed = true
                            cont.resumeWithException(RuntimeException("Camera disconnected"))
                        }
                    }

                    override fun onError(device: CameraDevice, error: Int) {
                        try {
                            device.close()
                        } catch (_: Exception) {
                        }
                        if (cameraDevice === device) {
                            cameraDevice = null
                            currentCameraId = null
                        }
                        closeSignal?.complete(Unit)
                        if (!resumed) {
                            resumed = true
                            cont.resumeWithException(RuntimeException("Camera error: $error"))
                        }
                    }

                    override fun onClosed(device: CameraDevice) {
                        if (cameraDevice === device) {
                            cameraDevice = null
                            currentCameraId = null
                        }
                        closeSignal?.complete(Unit)
                    }
                }, cameraHandler)
            } catch (e: SecurityException) {
                if (!resumed) {
                    resumed = true
                    cont.resumeWithException(e)
                }
            }
        }
    }

    suspend fun createCaptureSession(
        device: CameraDevice,
        surfaces: List<Surface>
    ): CameraCaptureSession {
        return suspendCancellableCoroutine { cont ->
            val sessionCallback = object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(session: CameraCaptureSession) {
                    captureSession = session
                    cont.resume(session)
                }

                override fun onConfigureFailed(session: CameraCaptureSession) {
                    cont.resumeWithException(RuntimeException("Session configuration failed"))
                }
            }
            try {
                device.createCaptureSession(surfaces, sessionCallback, cameraHandler)
            } catch (e: Exception) {
                cont.resumeWithException(e)
            }
        }
    }

    fun createPreviewRequest(device: CameraDevice, surface: Surface): CaptureRequest.Builder {
        return device.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
            addTarget(surface)
            set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
            pickFpsRange(device.id)?.let { set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, it) }
        }
    }

    private fun pickFpsRange(cameraId: String): Range<Int>? {
        val ranges = characteristicsOf(cameraId)
            .get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES) ?: return null
        return ranges.firstOrNull { it.lower >= 24 && it.upper <= 30 }
            ?: ranges.maxByOrNull { it.upper }
    }

    fun startRepeatingRequest(session: CameraCaptureSession, requestBuilder: CaptureRequest.Builder) {
        session.setRepeatingRequest(requestBuilder.build(), null, cameraHandler)
    }

    fun release() {
        close()
        cameraThread.quitSafely()
    }

    companion object {
        private const val TAG = "Camera2Controller"
        private const val CLOSE_TIMEOUT_MS = 600L
    }
}
