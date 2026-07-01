package com.robin.tools.feature.camera.opengl

import android.opengl.GLES20
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

object GlUtil {
    val cubeVertices: FloatBuffer = ByteBuffer.allocateDirect(32)
        .order(ByteOrder.nativeOrder())
        .asFloatBuffer()
        .apply {
            put(floatArrayOf(
                -1.0f, -1.0f,
                 1.0f, -1.0f,
                -1.0f,  1.0f,
                 1.0f,  1.0f
            ))
            position(0)
        }

    val textureVertices: FloatBuffer = ByteBuffer.allocateDirect(32)
        .order(ByteOrder.nativeOrder())
        .asFloatBuffer()
        .apply {
            put(floatArrayOf(
                0.0f, 1.0f,
                1.0f, 1.0f,
                0.0f, 0.0f,
                1.0f, 0.0f
            ))
            position(0)
        }

    val cubeVerticesFlipHorizontal: FloatBuffer = ByteBuffer.allocateDirect(32)
        .order(ByteOrder.nativeOrder())
        .asFloatBuffer()
        .apply {
            put(floatArrayOf(
                -1.0f, -1.0f,
                 1.0f, -1.0f,
                -1.0f,  1.0f,
                 1.0f,  1.0f
            ))
            position(0)
        }

    val textureVerticesFlipHorizontal: FloatBuffer = ByteBuffer.allocateDirect(32)
        .order(ByteOrder.nativeOrder())
        .asFloatBuffer()
        .apply {
            put(floatArrayOf(
                1.0f, 1.0f,
                0.0f, 1.0f,
                1.0f, 0.0f,
                0.0f, 0.0f
            ))
            position(0)
        }

    fun checkGlError(op: String) {
        val error = GLES20.glGetError()
        if (error != GLES20.GL_NO_ERROR) {
            throw RuntimeException("$op: glError 0x${error.toString(16)}")
        }
    }

    fun createFloatBuffer(coords: FloatArray): FloatBuffer {
        return ByteBuffer.allocateDirect(coords.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .apply {
                put(coords)
                position(0)
            }
    }
}
