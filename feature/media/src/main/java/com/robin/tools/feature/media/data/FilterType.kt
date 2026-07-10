package com.robin.tools.feature.media.data

import androidx.annotation.StringRes
import com.robin.tools.feature.media.R

/**
 * Defines the available image stylization filter types.
 *
 * Each filter corresponds to one OpenCV image processing algorithm
 * implemented in [com.robin.tools.feature.media.delegate.OpenCVFilterDelegate].
 */
enum class FilterType(@StringRes val labelRes: Int) {
    GRAYSCALE(R.string.filter_grayscale),
    BLUR(R.string.filter_blur),
    EDGE_DETECTION(R.string.filter_edge_detection),
    CARTOON(R.string.filter_cartoon),
    SHARPEN(R.string.filter_sharpen),
    SKETCH(R.string.filter_sketch)
}
