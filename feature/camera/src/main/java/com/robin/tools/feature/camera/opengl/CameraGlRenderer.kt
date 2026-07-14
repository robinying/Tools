package com.robin.tools.feature.camera.opengl

import android.graphics.Bitmap
import android.graphics.SurfaceTexture
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import android.util.Log
import com.robin.tools.feature.camera.filter.CameraFilter
import com.robin.tools.feature.camera.filter.FilterFactory
import com.robin.tools.feature.camera.filter.FilterType
import com.robin.tools.feature.camera.filter.GpuImageFilter
import com.robin.tools.feature.camera.filter.NoFilter
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

class CameraGlRenderer : GLSurfaceView.Renderer {
    var surfaceTexture: SurfaceTexture? = null
        private set
    private var surfaceTextureId: Int = 0

    private val cameraFilter: CameraFilter
    private var currentEffectFilter: GpuImageFilter
    private val displayFilter: NoFilter

    private val textureTransform = FloatArray(16)
    /** Center-crop scale only; content rotation is applied via UV. */
    private val displayMatrix = FloatArray(16)

    private var viewportWidth: Int = 0
    private var viewportHeight: Int = 0
    private var previewWidth: Int = 1280
    private var previewHeight: Int = 720

    private var surfaceCreated: Boolean = false
    private var pendingFilterType: FilterType? = null
    private var pendingPreviewResize: Boolean = false
    private var filterResources: android.content.res.Resources? = null

    private var offscreenFbo: GlFrameBuffer? = null
    private var offscreenFboCreated: Boolean = false

    var onSurfaceCreated: ((SurfaceTexture) -> Unit)? = null
    var onFrameAvailable: ((SurfaceTexture) -> Unit)? = null

    private var isFrontCamera: Boolean = false
    private var sensorOrientation: Int = 90
    private var displayRotationDegrees: Int = 0

    /**
     * Extra CW content rotation on the FBO→screen pass (beyond SurfaceTexture).
     *
     * Camera2 + SurfaceTexture already encodes buffer layout; Camera2Basic's
     * TextureView path applies **no** extra sensor rotation for portrait
     * (ROTATION_0). Adding Camera1 relative (90°) made the preview 90° CW off.
     *
     * Only compensate when the *display* itself is rotated (user holds phone in
     * landscape / upside-down). Front camera still gets a horizontal mirror.
     */
    private var contentRotationDegrees: Int = 0

    @Volatile
    var currentFilterType: FilterType = FilterType.NONE
        set(value) {
            field = value
            pendingFilterType = value
        }

    /** One-shot still capture from the preview FBO (GL thread). */
    @Volatile
    private var pendingCapture: ((Bitmap?) -> Unit)? = null

    fun setCameraOrientation(
        isFront: Boolean,
        sensorOrientation: Int,
        displayRotationDegrees: Int
    ) {
        this.isFrontCamera = isFront
        this.sensorOrientation = sensorOrientation
        this.displayRotationDegrees = displayRotationDegrees
        cameraFilter.setCameraFacing(isFront)
        updateDisplayTransform()
        Log.i(
            TAG,
            "orient front=$isFront sensor=$sensorOrientation display=$displayRotationDegrees " +
                "contentRot=$contentRotationDegrees preview=${previewWidth}x$previewHeight " +
                "viewport=${viewportWidth}x$viewportHeight"
        )
    }

    private fun updateDisplayTransform() {
        Matrix.setIdentityM(displayMatrix, 0)

        // Match Camera2Basic TextureView for natural portrait: no extra sensor turn.
        // When the display is rotated, rotate content the other way to stay upright.
        contentRotationDegrees = (360 - displayRotationDegrees) % 360

        if (viewportWidth <= 0 || viewportHeight <= 0 || previewWidth <= 0 || previewHeight <= 0) {
            return
        }

        // Sensor buffers are typically landscape; for portrait upright framing
        // (display 0°/180°) swap axes for center-crop. When the phone is in
        // landscape (display 90°/270°), use native buffer aspect.
        val portraitFraming =
            displayRotationDegrees == 0 || displayRotationDegrees == 180
        val aspectW = if (portraitFraming) previewHeight.toFloat() else previewWidth.toFloat()
        val aspectH = if (portraitFraming) previewWidth.toFloat() else previewHeight.toFloat()
        val viewAspect = viewportWidth.toFloat() / viewportHeight.toFloat()
        val contentAspect = aspectW / aspectH

        var scaleX = 1f
        var scaleY = 1f
        if (contentAspect > viewAspect) {
            scaleX = contentAspect / viewAspect
        } else {
            scaleY = viewAspect / contentAspect
        }
        Matrix.scaleM(displayMatrix, 0, scaleX, scaleY, 1f)
    }

    fun setFilterResources(resources: android.content.res.Resources) {
        filterResources = resources
    }

    fun setPreviewSize(width: Int, height: Int) {
        if (width <= 0 || height <= 0) return
        // Skip FBO rebuild when switching cameras that share the same size.
        if (width == previewWidth && height == previewHeight) {
            updateDisplayTransform()
            return
        }
        previewWidth = width
        previewHeight = height
        pendingPreviewResize = true
        updateDisplayTransform()
    }

    private fun recreateEffectFilter() {
        val res = filterResources ?: return
        currentEffectFilter.destroy()
        currentEffectFilter = FilterFactory.create(currentFilterType, res)
        currentEffectFilter.init()
        currentEffectFilter.onInputSizeChanged(previewWidth, previewHeight)
        currentEffectFilter.onDisplaySizeChanged(viewportWidth, viewportHeight)
    }

    private fun ensureOffscreenFbo() {
        if (!offscreenFboCreated) {
            offscreenFbo = GlFrameBuffer()
            offscreenFbo!!.create(previewWidth, previewHeight)
            offscreenFboCreated = true
        }
    }

    init {
        cameraFilter = CameraFilter(android.content.res.Resources.getSystem(), false)
        currentEffectFilter = NoFilter(android.content.res.Resources.getSystem())
        displayFilter = NoFilter(android.content.res.Resources.getSystem())
        Matrix.setIdentityM(textureTransform, 0)
        Matrix.setIdentityM(displayMatrix, 0)
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(0f, 0f, 0f, 1f)
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)

        surfaceCreated = true
        surfaceTextureId = GlTexture.createOesTexture()

        cameraFilter.init()
        cameraFilter.onInputSizeChanged(previewWidth, previewHeight)
        cameraFilter.onDisplaySizeChanged(viewportWidth, viewportHeight)

        displayFilter.init()

        ensureOffscreenFbo()

        if (filterResources != null) {
            recreateEffectFilter()
        }

        updateDisplayTransform()

        surfaceTexture = SurfaceTexture(surfaceTextureId).apply {
            setOnFrameAvailableListener {
                onFrameAvailable?.invoke(this)
            }
        }

        onSurfaceCreated?.invoke(surfaceTexture!!)
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        viewportWidth = width
        viewportHeight = height
        GLES20.glViewport(0, 0, width, height)
        cameraFilter.onDisplaySizeChanged(width, height)
        currentEffectFilter.onDisplaySizeChanged(width, height)
        displayFilter.onDisplaySizeChanged(width, height)
        updateDisplayTransform()
    }

    override fun onDrawFrame(gl: GL10?) {
        if (pendingPreviewResize) {
            pendingPreviewResize = false
            offscreenFbo?.release()
            offscreenFboCreated = false
            ensureOffscreenFbo()
            cameraFilter.onInputSizeChanged(previewWidth, previewHeight)
        }

        val pending = pendingFilterType
        if (pending != null && filterResources != null) {
            pendingFilterType = null
            recreateEffectFilter()
        }

        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)

        surfaceTexture?.let { st ->
            st.updateTexImage()
            st.getTransformMatrix(textureTransform)

            // Pass 1: OES → FBO. ST matrix + non-flipped OES UVs only.
            offscreenFbo?.bind()
            GLES20.glViewport(0, 0, previewWidth, previewHeight)
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
            Matrix.setIdentityM(cameraFilter.mvpMatrix, 0)
            cameraFilter.setTextureTransform(textureTransform)
            cameraFilter.draw(surfaceTextureId)
            offscreenFbo?.unbind()

            // Pass 2: FBO → screen. GL-native UV rotation + center-crop scale + optional front mirror.
            GLES20.glViewport(0, 0, viewportWidth, viewportHeight)
            val camera2DTexture = offscreenFbo!!.frameBufferTextureId
            val outputFilter =
                if (currentFilterType == FilterType.NONE) displayFilter else currentEffectFilter

            outputFilter.setTextureCoordinates(
                TextureRotation.asFloatArray(
                    rotationDegrees = contentRotationDegrees,
                    flipHorizontal = isFrontCamera,
                    flipVertical = false
                )
            )
            System.arraycopy(displayMatrix, 0, outputFilter.mvpMatrix, 0, 16)
            outputFilter.draw(camera2DTexture)

            // Optional still: re-draw oriented frame into FBO-sized capture via viewport bitmap.
            val captureCb = pendingCapture
            if (captureCb != null) {
                pendingCapture = null
                captureCb(readViewportBitmap())
            }
        }
    }

    /**
     * Request a still bitmap of the current preview (with filter / mirror / crop).
     * Callback is invoked on the GL thread — hop to main/IO before heavy work.
     */
    fun requestStillCapture(callback: (Bitmap?) -> Unit) {
        pendingCapture = callback
    }

    private fun readViewportBitmap(): Bitmap? {
        if (viewportWidth <= 0 || viewportHeight <= 0) return null
        return try {
            val w = viewportWidth
            val h = viewportHeight
            val buf = ByteBuffer.allocateDirect(w * h * 4).order(ByteOrder.nativeOrder())
            GLES20.glReadPixels(0, 0, w, h, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, buf)
            val pixels = IntArray(w * h)
            buf.asIntBuffer().get(pixels)
            // GL is bottom-up + RGBA → ARGB + flip Y
            val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            for (y in 0 until h) {
                for (x in 0 until w) {
                    val px = pixels[y * w + x]
                    // ABGR packed from RGBA bytes on little-endian
                    val r = px and 0xff
                    val g = (px shr 8) and 0xff
                    val b = (px shr 16) and 0xff
                    val a = (px shr 24) and 0xff
                    pixels[(h - 1 - y) * w + x] = (a shl 24) or (r shl 16) or (g shl 8) or b
                }
            }
            // Second pass: we overwrote while reading — redo cleanly
            buf.rewind()
            val raw = IntArray(w * h)
            buf.asIntBuffer().get(raw)
            val argb = IntArray(w * h)
            for (y in 0 until h) {
                for (x in 0 until w) {
                    val px = raw[y * w + x]
                    val r = px and 0xff
                    val g = (px shr 8) and 0xff
                    val b = (px shr 16) and 0xff
                    val a = (px shr 24) and 0xff
                    argb[(h - 1 - y) * w + x] = (a shl 24) or (r shl 16) or (g shl 8) or b
                }
            }
            bitmap.setPixels(argb, 0, w, 0, 0, w, h)
            bitmap
        } catch (e: Exception) {
            Log.e(TAG, "readViewportBitmap failed", e)
            null
        }
    }

    fun release() {
        cameraFilter.destroy()
        currentEffectFilter.destroy()
        displayFilter.destroy()
        offscreenFbo?.release()
        offscreenFboCreated = false
        surfaceTexture?.release()
        surfaceTexture = null
        surfaceCreated = false
        if (surfaceTextureId != 0) {
            GlTexture.deleteTexture(surfaceTextureId)
            surfaceTextureId = 0
        }
    }

    companion object {
        private const val TAG = "CameraGlRenderer"
    }
}
