package com.robin.tools.feature.camera.filter

import android.content.res.Resources
import android.opengl.GLES20

class BeautyFilter(resources: Resources) : GpuImageFilter(resources) {
    private var beautyLevel: Float = 0.5f
    private var toneLevel: Float = 0.5f

    fun setBeautyLevel(level: Float) { beautyLevel = level.coerceIn(0f, 1f) }
    fun setToneLevel(level: Float) { toneLevel = level.coerceIn(0f, 1f) }

    override fun getVertexShader(): String = NoFilter.DEFAULT_VERTEX_SHADER
    override fun getFragmentShader(): String = """
        precision mediump float;
        varying vec2 vTextureCoord;
        uniform sampler2D uTexture;
        uniform float uBeautyLevel;
        uniform float uToneLevel;
        void main() {
            vec4 color = texture2D(uTexture, vTextureCoord);
            float blur = uBeautyLevel * 0.3;
            vec4 blurred = color * (1.0 - blur) + vec4(0.5) * blur;
            float tone = uToneLevel * 0.1;
            gl_FragColor = blurred + vec4(tone, tone * 0.7, tone * 0.5, 0.0);
        }
    """.trimIndent()

    override fun onDraw() {
        setUniform1f("uBeautyLevel", beautyLevel)
        setUniform1f("uToneLevel", toneLevel)
    }
}
