package com.robin.tools.feature.camera.filter

import android.content.res.Resources
import com.robin.tools.feature.camera.R
import com.robin.tools.feature.camera.filter.effects.RawShaderFilter

object FilterFactory {
    fun create(type: FilterType, resources: Resources): GpuImageFilter {
        return when (type) {
            FilterType.NONE -> NoFilter(resources)
            FilterType.BEAUTY -> BeautyFilter(resources)
            FilterType.WARM -> RawShaderFilter(resources, R.raw.warm)
            FilterType.ANTIQUE -> RawShaderFilter(resources, R.raw.antique)
            FilterType.COOL -> RawShaderFilter(resources, R.raw.cool)
            FilterType.BRANNAN -> RawShaderFilter(resources, R.raw.brannan)
            FilterType.FREUD -> RawShaderFilter(resources, R.raw.freud)
            FilterType.HEFE -> RawShaderFilter(resources, R.raw.hefe)
            FilterType.HUDSON -> RawShaderFilter(resources, R.raw.hudson)
            FilterType.INKWELL -> RawShaderFilter(resources, R.raw.inkwell)
            FilterType.N1977 -> RawShaderFilter(resources, R.raw.n1977)
            FilterType.NASHVILLE -> RawShaderFilter(resources, R.raw.nashville)
        }
    }
}
