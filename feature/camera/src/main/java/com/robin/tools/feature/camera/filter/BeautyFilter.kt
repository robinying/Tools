package com.robin.tools.feature.camera.filter

import android.content.res.Resources
import android.opengl.GLES20
import com.robin.tools.feature.camera.R

/**
 * Skin-smoothing beauty filter driven by [R.raw.beauty] GLSL.
 *
 * Uniforms:
 * - `params` — strength (higher → more smooth; typical 0.3–0.8)
 * - `singleStepOffset` — texel size for blur sampling
 */
class BeautyFilter(resources: Resources) : GpuImageFilter(resources) {
    private var fragmentShaderSrc: String = ""
    private var beautyLevel: Float = 0.55f

    fun setBeautyLevel(level: Float) {
        beautyLevel = level.coerceIn(0f, 1f)
    }

    override fun getVertexShader(): String = NoFilter.DEFAULT_VERTEX_SHADER

    override fun getFragmentShader(): String {
        if (fragmentShaderSrc.isEmpty()) {
            fragmentShaderSrc = try {
                resources.openRawResource(R.raw.beauty).bufferedReader().use { it.readText() }
            } catch (_: Exception) {
                // Fallback soft-smooth if raw shader missing
                """
                precision mediump float;
                varying vec2 textureCoordinate;
                uniform sampler2D inputImageTexture;
                uniform float params;
                void main() {
                    vec4 c = texture2D(inputImageTexture, textureCoordinate);
                    float blur = params * 0.25;
                    gl_FragColor = mix(c, vec4(vec3(dot(c.rgb, vec3(0.299,0.587,0.114))), c.a), blur * 0.3)
                        + vec4(blur * 0.05);
                }
                """.trimIndent()
            }
        }
        return fragmentShaderSrc
    }

    override fun onDraw() {
        // Map 0..1 UI level → shader params. Lower params → stronger smooth in beauty.glsl
        // (alpha = pow(luminance, params)); clamp to a usable range.
        val params = (1.0f - beautyLevel) * 0.6f + 0.2f
        setUniform1f("params", params)
        val stepX = if (inputWidth > 0) 2f / inputWidth else 0.002f
        val stepY = if (inputHeight > 0) 2f / inputHeight else 0.002f
        val location = GLES20.glGetUniformLocation(programId, "singleStepOffset")
        if (location >= 0) {
            GLES20.glUniform2f(location, stepX, stepY)
        }
    }
}
