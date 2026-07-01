package com.robin.tools.feature.camera.opengl

import android.opengl.GLES20
import android.util.Log

class GlProgram(private val vertexShaderSrc: String, private val fragmentShaderSrc: String) {
    var programId: Int = 0
        private set

    fun create() {
        programId = GLES20.glCreateProgram()
        val vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, vertexShaderSrc)
        val fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentShaderSrc)
        GLES20.glAttachShader(programId, vertexShader)
        GLES20.glAttachShader(programId, fragmentShader)
        GLES20.glLinkProgram(programId)
        val linkStatus = IntArray(1)
        GLES20.glGetProgramiv(programId, GLES20.GL_LINK_STATUS, linkStatus, 0)
        if (linkStatus[0] == 0) {
            val log = GLES20.glGetProgramInfoLog(programId)
            GLES20.glDeleteProgram(programId)
            programId = 0
            throw RuntimeException("Program link failed: $log")
        }
        GLES20.glDeleteShader(vertexShader)
        GLES20.glDeleteShader(fragmentShader)
    }

    fun use() {
        GLES20.glUseProgram(programId)
    }

    fun getUniformLocation(name: String): Int {
        return GLES20.glGetUniformLocation(programId, name)
    }

    fun getAttribLocation(name: String): Int {
        return GLES20.glGetAttribLocation(programId, name)
    }

    fun release() {
        if (programId != 0) {
            GLES20.glDeleteProgram(programId)
            programId = 0
        }
    }

    companion object {
        private const val TAG = "GlProgram"

        fun loadShader(type: Int, source: String): Int {
            val shader = GLES20.glCreateShader(type)
            GLES20.glShaderSource(shader, source)
            GLES20.glCompileShader(shader)
            val compiled = IntArray(1)
            GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compiled, 0)
            if (compiled[0] == 0) {
                val log = GLES20.glGetShaderInfoLog(shader)
                GLES20.glDeleteShader(shader)
                throw RuntimeException("Shader compile failed: $log")
            }
            return shader
        }
    }
}
