package com.robin.tools.feature.camera.filter

import android.opengl.GLES20

class FilterChain {
    private val filters = mutableListOf<GpuImageFilter>()
    private var initialized = false

    fun addFilter(filter: GpuImageFilter) {
        filters.add(filter)
    }

    fun clear() {
        filters.forEach { it.destroy() }
        filters.clear()
        initialized = false
    }

    fun init() {
        filters.forEach { it.init() }
        initialized = true
    }

    fun processFrame(inputTextureId: Int, outputFboId: Int = 0): Int {
        if (filters.isEmpty()) return inputTextureId
        var currentTexture = inputTextureId
        var currentFbo = outputFboId
        for (i in filters.indices) {
            val filter = filters[i]
            val isLast = i == filters.size - 1
            if (isLast && outputFboId != 0) {
                GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, outputFboId)
            }
            filter.draw(currentTexture)
            if (isLast && outputFboId != 0) {
                GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
            }
        }
        return currentTexture
    }

    fun release() {
        clear()
    }
}
