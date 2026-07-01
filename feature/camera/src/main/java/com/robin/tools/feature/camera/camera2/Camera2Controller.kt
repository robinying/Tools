package com.robin.tools.feature.camera.camera2

import android.content.Context
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.params.OutputConfiguration
import android.hardware.camera2.params.SessionConfiguration
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.util.Size
import android.view.Surface
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class Camera2Controller(private val context: Context) {
    private val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    private var cameraDevice: CameraDevice? = null
    private var captureSession: CameraCaptureSession? = null
    private var cameraThread = HandlerThread("Camera2Thread").apply { start() }
    private val cameraHandler = Handler(cameraThread.looper)
    private var currentCameraId: String? = null

    val isFrontCamera: Boolean
        get() {
            val id = currentCameraId ?: return false
            val characteristics = cameraManager.getCameraCharacteristics(id)
            return characteristics.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_FRONT
        }

    fun getCameraId(facingFront: Boolean): String? {
        val cameraIds = cameraManager.cameraIdList
        val facing = if (facingFront) CameraCharacteristics.LENS_FACING_FRONT
                     else CameraCharacteristics.LENS_FACING_BACK
        for (id in cameraIds) {
            val characteristics = cameraManager.getCameraCharacteristics(id)
            val lensFacing = characteristics.get(CameraCharacteristics.LENS_FACING)
            if (lensFacing == facing) return id
        }
        return cameraIds.firstOrNull()
    }

    fun getSensorOrientation(cameraId: String): Int {
        val characteristics = cameraManager.getCameraCharacteristics(cameraId)
        return characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 0
    }

    fun getPreviewSize(cameraId: String): Size {
        val characteristics = cameraManager.getCameraCharacteristics(cameraId)
        val map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
            ?: throw RuntimeException("Cannot get camera config map")
        return map.getOutputSizes(SurfaceTexture::class.java)
            .firstOrNull { it.width >= 720 }
            ?: map.getOutputSizes(SurfaceTexture::class.java).first()
    }

    suspend fun openCamera(cameraId: String): CameraDevice {
        cameraDevice?.close()
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
                        device.close()
                        cameraDevice = null
                        if (!resumed) {
                            resumed = true
                            cont.resumeWithException(RuntimeException("Camera disconnected"))
                        }
                    }
                    override fun onError(device: CameraDevice, error: Int) {
                        device.close()
                        cameraDevice = null
                        if (!resumed) {
                            resumed = true
                            cont.resumeWithException(RuntimeException("Camera error: $error"))
                        }
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
            device.createCaptureSession(surfaces, sessionCallback, cameraHandler)
        }
    }

    fun createPreviewRequest(device: CameraDevice, surface: Surface): CaptureRequest.Builder {
        return device.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
            addTarget(surface)
            set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
        }
    }

    fun startRepeatingRequest(session: CameraCaptureSession, requestBuilder: CaptureRequest.Builder) {
        session.setRepeatingRequest(requestBuilder.build(), null, cameraHandler)
    }

    fun close() {
        captureSession?.close()
        captureSession = null
        cameraDevice?.close()
        cameraDevice = null
        currentCameraId = null
    }

    fun release() {
        close()
        cameraThread.quitSafely()
    }
}
