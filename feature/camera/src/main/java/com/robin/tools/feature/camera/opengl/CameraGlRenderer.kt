package com.robin.tools.feature.camera.opengl

import android.graphics.SurfaceTexture
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import com.robin.tools.feature.camera.filter.CameraFilter
import com.robin.tools.feature.camera.filter.FilterType
import com.robin.tools.feature.camera.filter.GpuImageFilter
import com.robin.tools.feature.camera.filter.NoFilter
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
    private val displayMatrix = FloatArray(16)
    private var viewportWidth: Int = 0
    private var viewportHeight: Int = 0
    private var previewWidth: Int = 720
    private var previewHeight: Int = 1280

    private var surfaceCreated: Boolean = false
    private var pendingFilterType: FilterType? = null
    private var pendingPreviewResize: Boolean = false
    private var filterResources: android.content.res.Resources? = null

    private var offscreenFbo: GlFrameBuffer? = null
    private var offscreenFboCreated: Boolean = false

    var onSurfaceCreated: ((SurfaceTexture) -> Unit)? = null
    var onFrameAvailable: ((SurfaceTexture) -> Unit)? = null
    var isFrontCamera: Boolean = false
        set(value) {
            field = value
            cameraFilter.setCameraFacing(value)
        }

    var sensorOrientation: Int = 90
        set(value) {
            field = value
            updateDisplayMatrix()
        }

    @Volatile var currentFilterType: FilterType = FilterType.NONE
        set(value) {
            field = value
            pendingFilterType = value
        }

    /**
     * Display matrix rotates the already-oriented FBO image to fill the portrait viewport.
     * After the OES→FBO pass (which applies SurfaceTexture's transform), the FBO image is
     * already in the sensor's natural orientation. For a typical 90° back sensor, the FBO
     * holds a landscape image; we rotate it to portrait. Front camera adds a horizontal mirror.
     */
    private fun updateDisplayMatrix() {
        Matrix.setIdentityM(displayMatrix, 0)
        // Rotate the FBO image to match display orientation.
        // sensorOrientation is how much the sensor image is rotated relative to the display.
        // We undo it with the opposite rotation on the quad.
        Matrix.rotateM(displayMatrix, 0, -sensorOrientation.toFloat(), 0f, 0f, 1f)
        if (isFrontCamera) {
            // Mirror horizontally for natural selfie behavior.
            Matrix.scaleM(displayMatrix, 0, -1f, 1f, 1f)
        }
    }

    fun setFilterResources(resources: android.content.res.Resources) {
        filterResources = resources
    }

    fun setPreviewSize(width: Int, height: Int) {
        previewWidth = width
        previewHeight = height
        pendingPreviewResize = true
    }

    private fun recreateEffectFilter() {
        val res = filterResources ?: return
        currentEffectFilter.destroy()
        currentEffectFilter = com.robin.tools.feature.camera.filter.FilterFactory.create(currentFilterType, res)
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

        updateDisplayMatrix()

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

            // Step 1: Camera OES → FBO.
            // SurfaceTexture's transform matrix encodes the sensor orientation and Y-flip.
            // Apply it to texture coordinates (not vertex positions) via CameraFilter.
            offscreenFbo?.bind()
            GLES20.glViewport(0, 0, previewWidth, previewHeight)
            // Identity MVP for the camera filter — full-screen quad.
            Matrix.setIdentityM(cameraFilter.mvpMatrix, 0)
            cameraFilter.setTextureTransform(textureTransform)
            cameraFilter.draw(surfaceTextureId)
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)

            // Step 2: FBO → screen. The FBO now holds an upright (sensor-oriented) image.
            // Apply displayMatrix to rotate it into portrait and mirror for front camera.
            GLES20.glViewport(0, 0, viewportWidth, viewportHeight)
            val camera2DTexture = offscreenFbo!!.frameBufferTextureId
            val outputFilter = if (currentFilterType == FilterType.NONE) displayFilter else currentEffectFilter
            outputFilter.mvpMatrix = displayMatrix
            outputFilter.draw(camera2DTexture)
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
}
