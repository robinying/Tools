package com.robin.tools.feature.camera.opengl

import android.opengl.EGL14
import android.opengl.EGLSurface
import android.view.Surface

class WindowSurface(eglCore: EglCore, surface: Surface, released: Boolean) {
    private var eglCore: EglCore
    private var eglSurface: EGLSurface = EGL14.EGL_NO_SURFACE
    private var released: Boolean = false

    init {
        this.eglCore = eglCore
        eglSurface = eglCore.createWindowSurface(surface)
        this.released = released
    }

    fun recreate(newEglCore: EglCore) {
        check(!released) { "Already released" }
        eglCore.releaseSurface(eglSurface)
        eglCore = newEglCore
        eglSurface = eglCore.createWindowSurface(eglSurface)
    }

    fun release() {
        if (!released) {
            eglCore.releaseSurface(eglSurface)
            eglSurface = EGL14.EGL_NO_SURFACE
            released = true
        }
    }

    fun releaseEglSurface() {
        eglCore.releaseSurface(eglSurface)
        eglSurface = EGL14.EGL_NO_SURFACE
    }

    fun makeCurrent() {
        eglCore.makeCurrent(eglSurface)
    }

    fun makeCurrentReadFrom(readSurface: EGLSurface) {
        eglCore.makeCurrent(eglSurface, readSurface)
    }

    fun makeNothingCurrent() {
        eglCore.makeNothingCurrent()
    }

    fun swapBuffers(): Boolean {
        return eglCore.swapBuffers(eglSurface)
    }

    fun setPresentationTime(nsecs: Long) {
        eglCore.setPresentationTime(eglSurface, nsecs)
    }

    fun getWidth(): Int = eglCore.querySurface(eglSurface, EGL14.EGL_WIDTH)
    fun getHeight(): Int = eglCore.querySurface(eglSurface, EGL14.EGL_HEIGHT)
}
