package com.robin.tools.feature.camera.opengl

import android.opengl.GLES20

class GlFrameBuffer {
    var frameBufferId: Int = 0
        private set
    var frameBufferTextureId: Int = 0
        private set
    private var width: Int = 0
    private var height: Int = 0

    fun create(width: Int, height: Int) {
        this.width = width
        this.height = height

        val fbs = IntArray(1)
        GLES20.glGenFramebuffers(1, fbs, 0)
        frameBufferId = fbs[0]

        frameBufferTextureId = GlTexture.createTexture2D()
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, frameBufferTextureId)
        GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, width, height, 0,
            GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null)

        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, frameBufferId)
        GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0,
            GLES20.GL_TEXTURE_2D, frameBufferTextureId, 0)

        val status = GLES20.glCheckFramebufferStatus(GLES20.GL_FRAMEBUFFER)
        if (status != GLES20.GL_FRAMEBUFFER_COMPLETE) {
            throw RuntimeException("Framebuffer incomplete: $status")
        }
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
    }

    fun bind() {
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, frameBufferId)
        GLES20.glViewport(0, 0, width, height)
    }

    fun unbind() {
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
    }

    fun release() {
        if (frameBufferId != 0) {
            GLES20.glDeleteFramebuffers(1, intArrayOf(frameBufferId), 0)
            frameBufferId = 0
        }
        if (frameBufferTextureId != 0) {
            GlTexture.deleteTexture(frameBufferTextureId)
            frameBufferTextureId = 0
        }
    }
}
