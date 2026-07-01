package com.robin.tools.feature.camera.filter

import android.content.res.Resources
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.opengl.Matrix

class CameraFilter(resources: Resources, private var cameraFacingFront: Boolean = false) : GpuImageFilter(resources) {

    private val localMvpMatrix = FloatArray(16)
    private val textureTransform = FloatArray(16)

    init {
        Matrix.setIdentityM(localMvpMatrix, 0)
        Matrix.setIdentityM(textureTransform, 0)
    }

    fun setCameraFacing(front: Boolean) {
        cameraFacingFront = front
    }

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

        val positionHandle = GLES20.glGetAttribLocation(programId, "vPosition")
        val texCoordHandle = GLES20.glGetAttribLocation(programId, "vCoord")
        val mvpMatrixHandle = GLES20.glGetUniformLocation(programId, "vMatrix")
        val textureHandle = GLES20.glGetUniformLocation(programId, "inputImageTexture")

        GLES20.glVertexAttribPointer(positionHandle, 2, GLES20.GL_FLOAT, false, 8, glCubeBuffer)
        GLES20.glEnableVertexAttribArray(positionHandle)
        GLES20.glVertexAttribPointer(texCoordHandle, 2, GLES20.GL_FLOAT, false, 8, glTextureBuffer)
        GLES20.glEnableVertexAttribArray(texCoordHandle)
        GLES20.glUniformMatrix4fv(mvpMatrixHandle, 1, false, this.mvpMatrix, 0)

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId)
        GLES20.glUniform1i(textureHandle, 0)

        onDraw()
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        GLES20.glDisableVertexAttribArray(positionHandle)
        GLES20.glDisableVertexAttribArray(texCoordHandle)
    }
}
