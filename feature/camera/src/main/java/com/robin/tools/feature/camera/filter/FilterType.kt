package com.robin.tools.feature.camera.filter

import com.robin.tools.feature.camera.R

enum class FilterType {
    NONE,
    BEAUTY,
    WARM,
    ANTIQUE,
    COOL,
    BRANNAN,
    FREUD,
    HEFE,
    HUDSON,
    INKWELL,
    N1977,
    NASHVILLE
}

fun FilterType.stringRes(): Int = when (this) {
    FilterType.NONE -> R.string.filter_none
    FilterType.BEAUTY -> R.string.filter_beauty
    FilterType.WARM -> R.string.filter_warm
    FilterType.ANTIQUE -> R.string.filter_antique
    FilterType.COOL -> R.string.filter_cool
    FilterType.BRANNAN -> R.string.filter_brannan
    FilterType.FREUD -> R.string.filter_freud
    FilterType.HEFE -> R.string.filter_hefe
    FilterType.HUDSON -> R.string.filter_hudson
    FilterType.INKWELL -> R.string.filter_inkwell
    FilterType.N1977 -> R.string.filter_n1977
    FilterType.NASHVILLE -> R.string.filter_nashville
}
