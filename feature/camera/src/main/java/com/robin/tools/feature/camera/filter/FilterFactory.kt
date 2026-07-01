package com.robin.tools.feature.camera.filter

import android.content.res.Resources
import com.robin.tools.feature.camera.filter.effects.*

object FilterFactory {
    fun create(type: FilterType, resources: Resources): GpuImageFilter {
        return when (type) {
            FilterType.NONE -> NoFilter(resources)
            FilterType.WARM -> MagicWarmFilter(resources)
            FilterType.ANTIQUE -> MagicAntiqueFilter(resources)
            FilterType.COOL -> MagicCoolFilter(resources)
            FilterType.BRANNAN -> MagicBrannanFilter(resources)
            FilterType.FREUD -> MagicFreudFilter(resources)
            FilterType.HEFE -> MagicHefeFilter(resources)
            FilterType.HUDSON -> MagicHudsonFilter(resources)
            FilterType.INKWELL -> MagicInkwellFilter(resources)
            FilterType.N1977 -> MagicN1977Filter(resources)
            FilterType.NASHVILLE -> MagicNashvilleFilter(resources)
        }
    }
}
