package com.robin.tools.feature.camera.filter

import android.content.res.Resources
import android.opengl.GLES20
import com.robin.tools.feature.camera.opengl.GlFrameBuffer

class GpuImageFilterGroup(resources: Resources) : GpuImageFilter(resources) {
    private val childFilters = mutableListOf<GpuImageFilter>()
    private val frameBuffers = mutableListOf<GlFrameBuffer>()

    fun addFilter(filter: GpuImageFilter) {
        childFilters.add(filter)
    }

    override fun init() {
        super.init()
        childFilters.forEach { it.init() }
    }

    override fun onInputSizeChanged(width: Int, height: Int) {
        super.onInputSizeChanged(width, height)
        childFilters.forEach { it.onInputSizeChanged(width, height) }
        frameBuffers.forEach { it.release() }
        frameBuffers.clear()
        repeat(childFilters.size - 1) {
            val fbo = GlFrameBuffer()
            fbo.create(width, height)
            frameBuffers.add(fbo)
        }
    }

    override fun onDisplaySizeChanged(width: Int, height: Int) {
        super.onDisplaySizeChanged(width, height)
        childFilters.forEach { it.onDisplaySizeChanged(width, height) }
    }

    override fun draw(textureId: Int) {
        if (childFilters.isEmpty()) return
        var currentTex = textureId
        for (i in childFilters.indices) {
            val isLast = i == childFilters.size - 1
            if (!isLast) {
                frameBuffers[i].bind()
                childFilters[i].draw(currentTex)
                GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
            } else {
                childFilters[i].draw(currentTex)
            }
        }
    }

    override fun getVertexShader(): String = NoFilter.DEFAULT_VERTEX_SHADER
    override fun getFragmentShader(): String = NoFilter.DEFAULT_FRAGMENT_SHADER

    override fun destroy() {
        childFilters.forEach { it.destroy() }
        frameBuffers.forEach { it.release() }
        super.destroy()
    }
}
