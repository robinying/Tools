package com.robin.tools.feature.camera.filter

import android.content.res.Resources
import android.graphics.Bitmap
import android.opengl.GLES20
import android.opengl.GLUtils

class WatermarkFilter(resources: Resources) : GpuImageFilter(resources) {
    private var watermarkTextureId: Int = -1

    fun setWatermark(bitmap: Bitmap) {
        if (watermarkTextureId != -1) {
            GLES20.glDeleteTextures(1, intArrayOf(watermarkTextureId), 0)
        }
        val textures = IntArray(1)
        GLES20.glGenTextures(1, textures, 0)
        watermarkTextureId = textures[0]
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, watermarkTextureId)
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
    }

    override fun getVertexShader(): String = NoFilter.DEFAULT_VERTEX_SHADER
    override fun getFragmentShader(): String = NoFilter.DEFAULT_FRAGMENT_SHADER

    override fun destroy() {
        if (watermarkTextureId != -1) {
            GLES20.glDeleteTextures(1, intArrayOf(watermarkTextureId), 0)
            watermarkTextureId = -1
        }
        super.destroy()
    }
}
