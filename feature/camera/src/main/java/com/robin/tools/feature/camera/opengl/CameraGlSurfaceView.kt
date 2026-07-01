package com.robin.tools.feature.camera.opengl

import android.content.Context
import android.graphics.SurfaceTexture
import android.opengl.GLSurfaceView
import android.util.AttributeSet

class CameraGlSurfaceView(
    context: Context,
    attrs: AttributeSet? = null
) : GLSurfaceView(context, attrs) {

    var glRenderer: CameraGlRenderer? = null
        private set

    init {
        setEGLContextClientVersion(2)
    }

    fun setRenderer(renderer: CameraGlRenderer) {
        glRenderer = renderer
        super.setRenderer(renderer)
        renderMode = RENDERMODE_CONTINUOUSLY
    }

    fun getSurfaceTexture(callback: (SurfaceTexture) -> Unit) {
        queueEvent {
            glRenderer?.surfaceTexture?.let { callback(it) }
        }
    }
}
