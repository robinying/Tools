package com.robin.tools.feature.camera.filter

import android.content.res.Resources

class NoFilter(resources: Resources) : GpuImageFilter(resources) {
    override fun getVertexShader(): String = DEFAULT_VERTEX_SHADER
    override fun getFragmentShader(): String = DEFAULT_FRAGMENT_SHADER

    companion object {
        val DEFAULT_VERTEX_SHADER = """
            attribute vec4 vPosition;
            attribute vec2 vCoord;
            uniform mat4 vMatrix;
            varying vec2 textureCoordinate;
            void main() {
                gl_Position = vMatrix * vPosition;
                textureCoordinate = vCoord;
            }
        """.trimIndent()

        val DEFAULT_FRAGMENT_SHADER = """
            precision mediump float;
            varying vec2 textureCoordinate;
            uniform sampler2D inputImageTexture;
            void main() {
                gl_FragColor = texture2D(inputImageTexture, textureCoordinate);
            }
        """.trimIndent()
    }
}
