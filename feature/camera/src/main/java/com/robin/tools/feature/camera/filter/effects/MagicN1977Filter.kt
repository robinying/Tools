package com.robin.tools.feature.camera.filter.effects

import android.content.res.Resources
import com.robin.tools.feature.camera.filter.GpuImageFilter
import com.robin.tools.feature.camera.R
import com.robin.tools.feature.camera.filter.NoFilter

class MagicN1977Filter(resources: Resources) : GpuImageFilter(resources) {
    private var fragmentShaderSrc: String = ""

    override fun getVertexShader(): String = NoFilter.DEFAULT_VERTEX_SHADER

    override fun getFragmentShader(): String {
        if (fragmentShaderSrc.isEmpty()) {
            fragmentShaderSrc = try {
                resources.openRawResource(R.raw.n1977).bufferedReader().use { it.readText() }
            } catch (e: Exception) {
                NoFilter.DEFAULT_FRAGMENT_SHADER
            }
        }
        return fragmentShaderSrc
    }
}
