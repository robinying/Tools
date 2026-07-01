package com.robin.tools.feature.camera.filter

import android.content.res.Resources
import android.opengl.GLES11Ext
import android.opengl.GLES20

class OesFilter(resources: Resources) : GpuImageFilter(resources) {
    override fun getVertexShader(): String = NoFilter.DEFAULT_VERTEX_SHADER

    override fun getFragmentShader(): String = """
        #extension GL_OES_EGL_image_external : require
        precision mediump float;
        varying vec2 textureCoordinate;
        uniform samplerExternalOES inputImageTexture;
        void main() {
            gl_FragColor = texture2D(inputImageTexture, textureCoordinate);
        }
    """.trimIndent()

    override fun draw(textureId: Int) {
        GLES20.glUseProgram(programId)

        val pHandle = GLES20.glGetAttribLocation(programId, "vPosition")
        val tHandle = GLES20.glGetAttribLocation(programId, "vCoord")
        val mHandle = GLES20.glGetUniformLocation(programId, "vMatrix")
        val sHandle = GLES20.glGetUniformLocation(programId, "inputImageTexture")

        GLES20.glVertexAttribPointer(pHandle, 2, GLES20.GL_FLOAT, false, 8, glCubeBuffer)
        GLES20.glEnableVertexAttribArray(pHandle)
        GLES20.glVertexAttribPointer(tHandle, 2, GLES20.GL_FLOAT, false, 8, glTextureBuffer)
        GLES20.glEnableVertexAttribArray(tHandle)
        GLES20.glUniformMatrix4fv(mHandle, 1, false, mvpMatrix, 0)

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId)
        GLES20.glUniform1i(sHandle, 0)

        onDraw()
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        GLES20.glDisableVertexAttribArray(pHandle)
        GLES20.glDisableVertexAttribArray(tHandle)
    }
}
