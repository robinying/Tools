package com.robin.tools.feature.camera.opengl

import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLExt
import android.opengl.EGLSurface
import android.util.Log
import android.view.Surface

class EglCore {
    private var eglDisplay: EGLDisplay = EGL14.EGL_NO_DISPLAY
    private var eglContext: EGLContext = EGL14.EGL_NO_CONTEXT
    private var eglConfig: EGLConfig? = null
    private var glVersion: Int = -1

    constructor() : this(null, 0)

    constructor(sharedContext: EGLContext?, flags: Int) {
        var sharedCtx = sharedContext ?: EGL14.EGL_NO_CONTEXT
        eglDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        if (eglDisplay == EGL14.EGL_NO_DISPLAY) {
            throw RuntimeException("unable to get EGL14 display")
        }
        val version = IntArray(2)
        if (!EGL14.eglInitialize(eglDisplay, version, 0, version, 1)) {
            eglDisplay = EGL14.EGL_NO_DISPLAY
            throw RuntimeException("unable to initialize EGL14")
        }

        if ((flags and FLAG_TRY_GLES3) != 0) {
            val config = getConfig(flags, 3)
            if (config != null) {
                val attrib3List = intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 3, EGL14.EGL_NONE)
                val context = EGL14.eglCreateContext(eglDisplay, config, sharedCtx, attrib3List, 0)
                if (EGL14.eglGetError() == EGL14.EGL_SUCCESS) {
                    eglConfig = config
                    eglContext = context
                    glVersion = 3
                }
            }
        }

        if (eglContext == EGL14.EGL_NO_CONTEXT) {
            val config = getConfig(flags, 2)
                ?: throw RuntimeException("Unable to find a suitable EGLConfig")
            val attrib2List = intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE)
            val context = EGL14.eglCreateContext(eglDisplay, config, sharedCtx, attrib2List, 0)
            checkEglError("eglCreateContext")
            eglConfig = config
            eglContext = context
            glVersion = 2
        }

        val values = IntArray(1)
        EGL14.eglQueryContext(eglDisplay, eglContext, EGL14.EGL_CONTEXT_CLIENT_VERSION, values, 0)
        Log.d(TAG, "EGLContext created, client version ${values[0]}")
    }

    private fun getConfig(flags: Int, version: Int): EGLConfig? {
        var renderableType = EGL14.EGL_OPENGL_ES2_BIT
        if (version >= 3) {
            renderableType = renderableType or EGLExt.EGL_OPENGL_ES3_BIT_KHR
        }
        val attribList = intArrayOf(
            EGL14.EGL_RED_SIZE, 8,
            EGL14.EGL_GREEN_SIZE, 8,
            EGL14.EGL_BLUE_SIZE, 8,
            EGL14.EGL_ALPHA_SIZE, 8,
            EGL14.EGL_RENDERABLE_TYPE, renderableType,
            EGL14.EGL_NONE, 0,
            EGL14.EGL_NONE
        )
        if ((flags and FLAG_RECORDABLE) != 0) {
            attribList[attribList.size - 3] = EGL_RECORDABLE_ANDROID
            attribList[attribList.size - 2] = 1
        }
        val configs = arrayOfNulls<EGLConfig>(1)
        val numConfigs = IntArray(1)
        if (!EGL14.eglChooseConfig(eglDisplay, attribList, 0, configs, 0, configs.size, numConfigs, 0)) {
            Log.w(TAG, "unable to find RGB8888 / $version EGLConfig")
            return null
        }
        return configs[0]
    }

    fun release() {
        if (eglDisplay != EGL14.EGL_NO_DISPLAY) {
            EGL14.eglMakeCurrent(eglDisplay, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
            EGL14.eglDestroyContext(eglDisplay, eglContext)
            EGL14.eglReleaseThread()
            EGL14.eglTerminate(eglDisplay)
        }
        eglDisplay = EGL14.EGL_NO_DISPLAY
        eglContext = EGL14.EGL_NO_CONTEXT
        eglConfig = null
    }

    fun releaseSurface(eglSurface: EGLSurface) {
        EGL14.eglDestroySurface(eglDisplay, eglSurface)
    }

    fun createWindowSurface(surface: Any): EGLSurface {
        val surfaceAttribs = intArrayOf(EGL14.EGL_NONE)
        val eglSurface = EGL14.eglCreateWindowSurface(eglDisplay, eglConfig, surface, surfaceAttribs, 0)
        checkEglError("eglCreateWindowSurface")
        return eglSurface ?: throw RuntimeException("surface was null")
    }

    fun createOffscreenSurface(width: Int, height: Int): EGLSurface {
        val surfaceAttribs = intArrayOf(EGL14.EGL_WIDTH, width, EGL14.EGL_HEIGHT, height, EGL14.EGL_NONE)
        val eglSurface = EGL14.eglCreatePbufferSurface(eglDisplay, eglConfig, surfaceAttribs, 0)
        checkEglError("eglCreatePbufferSurface")
        return eglSurface ?: throw RuntimeException("surface was null")
    }

    fun makeCurrent(eglSurface: EGLSurface) {
        if (!EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)) {
            throw RuntimeException("eglMakeCurrent failed")
        }
    }

    fun makeCurrent(drawSurface: EGLSurface, readSurface: EGLSurface) {
        if (!EGL14.eglMakeCurrent(eglDisplay, drawSurface, readSurface, eglContext)) {
            throw RuntimeException("eglMakeCurrent(draw,read) failed")
        }
    }

    fun makeNothingCurrent() {
        if (!EGL14.eglMakeCurrent(eglDisplay, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)) {
            throw RuntimeException("eglMakeCurrent failed")
        }
    }

    fun swapBuffers(eglSurface: EGLSurface): Boolean {
        return EGL14.eglSwapBuffers(eglDisplay, eglSurface)
    }

    fun setPresentationTime(eglSurface: EGLSurface, nsecs: Long) {
        EGLExt.eglPresentationTimeANDROID(eglDisplay, eglSurface, nsecs)
    }

    fun isCurrent(eglSurface: EGLSurface): Boolean {
        return eglContext == EGL14.eglGetCurrentContext() &&
                eglSurface == EGL14.eglGetCurrentSurface(EGL14.EGL_DRAW)
    }

    fun querySurface(eglSurface: EGLSurface, what: Int): Int {
        val value = IntArray(1)
        EGL14.eglQuerySurface(eglDisplay, eglSurface, what, value, 0)
        return value[0]
    }

    fun queryString(what: Int): String {
        return EGL14.eglQueryString(eglDisplay, what)
    }

    fun getGlVersion(): Int = glVersion

    protected fun finalize() {
        if (eglDisplay != EGL14.EGL_NO_DISPLAY) {
            Log.w(TAG, "WARNING: EglCore was not explicitly released -- state may be leaked")
            release()
        }
    }

    private fun checkEglError(msg: String) {
        val error = EGL14.eglGetError()
        if (error != EGL14.EGL_SUCCESS) {
            throw RuntimeException("$msg: EGL error: 0x${error.toString(16)}")
        }
    }

    companion object {
        private const val TAG = "EglCore"
        const val FLAG_RECORDABLE = 0x01
        const val FLAG_TRY_GLES3 = 0x02
        private const val EGL_RECORDABLE_ANDROID = 0x3142

        fun logCurrent(msg: String) {
            val display = EGL14.eglGetCurrentDisplay()
            val context = EGL14.eglGetCurrentContext()
            val surface = EGL14.eglGetCurrentSurface(EGL14.EGL_DRAW)
            Log.i(TAG, "Current EGL ($msg): display=$display, context=$context, surface=$surface")
        }
    }
}
