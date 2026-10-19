package com.robin.tools.feature.camera.filter.effects

import android.content.res.Resources
import com.robin.tools.feature.camera.filter.GpuImageFilter
import com.robin.tools.feature.camera.filter.NoFilter

/**
 * GPU filter backed by a GLSL fragment shader stored in `res/raw`.
 *
 * All built-in color filters share the same vertex shader and only differ by the
 * fragment shader resource, so this single class replaces the per-effect
 * subclasses that used to duplicate the same loading code.
 */
class RawShaderFilter(
    resources: Resources,
    private val fragmentShaderResId: Int
) : GpuImageFilter(resources) {

    private var fragmentShaderSrc: String? = null

    override fun getVertexShader(): String = NoFilter.DEFAULT_VERTEX_SHADER

    override fun getFragmentShader(): String {
        return fragmentShaderSrc ?: loadFragmentShader().also { fragmentShaderSrc = it }
    }

    private fun loadFragmentShader(): String = try {
        resources.openRawResource(fragmentShaderResId).bufferedReader().use { it.readText() }
    } catch (e: Exception) {
        NoFilter.DEFAULT_FRAGMENT_SHADER
    }
}
