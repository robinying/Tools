package com.robin.tools.feature.camera.filter

import android.content.res.Resources
import android.opengl.GLES20
import android.opengl.Matrix
import com.robin.tools.feature.camera.opengl.GlProgram
import com.robin.tools.feature.camera.opengl.GlUtil
import java.nio.FloatBuffer

abstract class GpuImageFilter(protected val resources: Resources) {
    protected var programId: Int = 0
    protected var glProgram: GlProgram? = null

    protected var inputWidth: Int = 0
    protected var inputHeight: Int = 0
    protected var outputWidth: Int = 0
    protected var outputHeight: Int = 0

    protected var glCubeBuffer: FloatBuffer = GlUtil.cubeVertices
    protected var glTextureBuffer: FloatBuffer = GlUtil.textureVertices

    private var positionHandle: Int = -1
    private var texCoordHandle: Int = -1
    private var mvpMatrixHandle: Int = -1
    private var textureHandle: Int = -1

    var mvpMatrix: FloatArray = FloatArray(16)

    init {
        Matrix.setIdentityM(mvpMatrix, 0)
    }

    abstract fun getVertexShader(): String
    abstract fun getFragmentShader(): String

    open fun init() {
        val vertexSrc = getVertexShader()
        val fragmentSrc = getFragmentShader()
        glProgram = GlProgram(vertexSrc, fragmentSrc).apply { create() }
        programId = glProgram!!.programId

        positionHandle = GLES20.glGetAttribLocation(programId, "vPosition")
        texCoordHandle = GLES20.glGetAttribLocation(programId, "vCoord")
        mvpMatrixHandle = GLES20.glGetUniformLocation(programId, "vMatrix")
        textureHandle = GLES20.glGetUniformLocation(programId, "inputImageTexture")

        onInit()
    }

    open fun onInit() {}
    open fun onInputSizeChanged(width: Int, height: Int) {
        inputWidth = width
        inputHeight = height
    }
    open fun onDisplaySizeChanged(width: Int, height: Int) {
        outputWidth = width
        outputHeight = height
    }

    open fun draw(textureId: Int) {
        GLES20.glUseProgram(programId)

        GLES20.glVertexAttribPointer(positionHandle, 2, GLES20.GL_FLOAT, false, 8, glCubeBuffer)
        GLES20.glEnableVertexAttribArray(positionHandle)

        GLES20.glVertexAttribPointer(texCoordHandle, 2, GLES20.GL_FLOAT, false, 8, glTextureBuffer)
        GLES20.glEnableVertexAttribArray(texCoordHandle)

        GLES20.glUniformMatrix4fv(mvpMatrixHandle, 1, false, mvpMatrix, 0)

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId)
        GLES20.glUniform1i(textureHandle, 0)

        onDraw()

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)

        GLES20.glDisableVertexAttribArray(positionHandle)
        GLES20.glDisableVertexAttribArray(texCoordHandle)
    }

    open fun onDraw() {}

    fun setUniform1f(name: String, value: Float) {
        val location = GLES20.glGetUniformLocation(programId, name)
        GLES20.glUniform1f(location, value)
    }

    fun setUniform3f(name: String, x: Float, y: Float, z: Float) {
        val location = GLES20.glGetUniformLocation(programId, name)
        GLES20.glUniform3f(location, x, y, z)
    }

    fun setUniformMatrix4fv(name: String, matrix: FloatArray) {
        val location = GLES20.glGetUniformLocation(programId, name)
        GLES20.glUniformMatrix4fv(location, 1, false, matrix, 0)
    }

    open fun destroy() {
        glProgram?.release()
        glProgram = null
        programId = 0
    }
}
