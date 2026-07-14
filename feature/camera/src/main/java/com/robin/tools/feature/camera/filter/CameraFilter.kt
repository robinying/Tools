package com.robin.tools.feature.camera.filter

import android.content.res.Resources
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.opengl.Matrix
import com.robin.tools.feature.camera.opengl.GlUtil
import com.robin.tools.feature.camera.opengl.TextureRotation
import java.nio.FloatBuffer

class CameraFilter(resources: Resources, private var cameraFacingFront: Boolean = false) : GpuImageFilter(resources) {

    private val textureTransform = FloatArray(16)

    /**
     * OES sampling UVs in [0,1] without pre Y-flip.
     * [android.graphics.SurfaceTexture.getTransformMatrix] already encodes buffer layout / flip.
     */
    private val oesTexCoords: FloatBuffer =
        GlUtil.createFloatBuffer(TextureRotation.OES_NO_ROTATION)

    init {
        Matrix.setIdentityM(textureTransform, 0)
    }

    fun setCameraFacing(front: Boolean) {
        cameraFacingFront = front
    }

    fun setTextureTransform(matrix: FloatArray) {
        System.arraycopy(matrix, 0, textureTransform, 0, 16)
    }

    override fun getVertexShader(): String = """
        attribute vec4 vPosition;
        attribute vec2 vCoord;
        uniform mat4 vMatrix;
        uniform mat4 uTextureTransform;
        varying vec2 textureCoordinate;
        void main() {
            gl_Position = vMatrix * vPosition;
            textureCoordinate = (uTextureTransform * vec4(vCoord, 0.0, 1.0)).xy;
        }
    """.trimIndent()

    override fun getFragmentShader(): String = """
        #extension GL_OES_EGL_image_external : require
        precision mediump float;
        varying vec2 textureCoordinate;
        uniform samplerExternalOES inputImageTexture;
        void main() {
            gl_FragColor = texture2D(inputImageTexture, textureCoordinate);
        }
    """.trimIndent()

    private var texTransformHandle: Int = -1

    override fun onInit() {
        super.onInit()
        texTransformHandle = GLES20.glGetUniformLocation(programId, "uTextureTransform")
    }

    override fun draw(textureId: Int) {
        GLES20.glUseProgram(programId)

        val positionHandle = GLES20.glGetAttribLocation(programId, "vPosition")
        val texCoordHandle = GLES20.glGetAttribLocation(programId, "vCoord")
        val mvpMatrixHandle = GLES20.glGetUniformLocation(programId, "vMatrix")
        val textureHandle = GLES20.glGetUniformLocation(programId, "inputImageTexture")

        GLES20.glVertexAttribPointer(positionHandle, 2, GLES20.GL_FLOAT, false, 8, glCubeBuffer)
        GLES20.glEnableVertexAttribArray(positionHandle)
        // Must use OES [0,1] coords — not the Y-flipped 2D filter buffer.
        GLES20.glVertexAttribPointer(texCoordHandle, 2, GLES20.GL_FLOAT, false, 8, oesTexCoords)
        GLES20.glEnableVertexAttribArray(texCoordHandle)

        GLES20.glUniformMatrix4fv(mvpMatrixHandle, 1, false, mvpMatrix, 0)
        if (texTransformHandle >= 0) {
            GLES20.glUniformMatrix4fv(texTransformHandle, 1, false, textureTransform, 0)
        }

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId)
        GLES20.glUniform1i(textureHandle, 0)

        onDraw()
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        GLES20.glDisableVertexAttribArray(positionHandle)
        GLES20.glDisableVertexAttribArray(texCoordHandle)
    }
}
