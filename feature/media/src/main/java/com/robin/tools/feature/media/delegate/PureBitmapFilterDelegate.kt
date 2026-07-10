package com.robin.tools.feature.media.delegate

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import com.robin.tools.feature.media.data.FilterType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Pure-Android fallback filter delegate that works without OpenCV.
 *
 * Only [FilterType.GRAYSCALE] is implemented here.
 * For the full 6-filter suite (grayscale, blur, edge detection,
 * cartoon, sharpen, sketch), download the OpenCV Android SDK AAR
 * and switch to [OpenCVFilterDelegate].
 *
 * @see OpenCVFilterDelegate for the OpenCV-powered implementation.
 */
class PureBitmapFilterDelegate : FilterDelegate {

    override suspend fun applyFilter(
        input: Bitmap,
        type: FilterType,
        onProgress: (Float, String) -> Unit
    ): Result<Bitmap> {
        onProgress(0f, "Processing…")
        val result = withContext(Dispatchers.Default) {
            when (type) {
                FilterType.GRAYSCALE -> applyGrayscalePinBackgroundThread(input)
                else -> input.copy(Bitmap.Config.ARGB_8888, false)
            }
        }
        onProgress(1f, "Done")
        return Result.success(result)
    }

    private fun applyGrayscalePinBackgroundThread(source: Bitmap): Bitmap {
        val out = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val paint = Paint()
        val matrix = ColorMatrix()
        matrix.setSaturation(0f)
        paint.colorFilter = ColorMatrixColorFilter(matrix)
        canvas.drawBitmap(source, 0f, 0f, paint)
        return out
    }
}
