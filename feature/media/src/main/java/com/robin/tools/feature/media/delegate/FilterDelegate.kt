package com.robin.tools.feature.media.delegate

import android.graphics.Bitmap
import com.robin.tools.feature.media.data.FilterType

/**
 * Applies a single image filter to the given bitmap.
 *
 * Implementations are stateless and safe for concurrent calls.
 * The caller owns the returned [Bitmap] and must recycle when done.
 *
 * @see [OpenCVFilterDelegate] for the OpenCV-based implementation.
 */
interface FilterDelegate {
    /**
     * Apply the specified filter to [input].
     *
     * @param input      Source bitmap (ARGB_8888). Must be non-null, non-recycled.
     * @param type       Which filter to apply.
     * @param onProgress 0.0f–1.0f progress callback with human-readable label.
     * @return Filtered bitmap on success, failure otherwise.
     */
    suspend fun applyFilter(
        input: Bitmap,
        type: FilterType,
        onProgress: (Float, String) -> Unit
    ): Result<Bitmap>
}
