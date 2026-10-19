package com.robin.tools.feature.face.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.net.Uri
import com.robin.tools.core.media.BitmapLoader
import com.robin.tools.core.media.MediaStoreSaver
import com.robin.tools.core.media.SavedMedia
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** How detected faces are obscured. */
enum class FaceBlurMode { MOSAIC, SOFT }

/**
 * Detects every face in an image and obscures the bounding region.
 *
 * The image is orientation-corrected by [BitmapLoader] before detection, so no
 * extra rotation is passed to ML Kit.
 */
object FaceBlurProcessor {

    private const val MAX_DIMENSION = 2048
    private const val OUTPUT_QUALITY = 92

    suspend fun process(
        context: Context,
        uri: Uri,
        mode: FaceBlurMode,
        strength: Int
    ): Result<Pair<SavedMedia, Int>> = withContext(Dispatchers.IO) {
        runCatching {
            val source = BitmapLoader.load(context, uri, MAX_DIMENSION)
                ?: error("Cannot decode the selected image")
            val faces = FaceDetector().detect(source, 0)
            if (faces.isEmpty()) {
                source.recycle()
                error("No face detected")
            }
            val output = source.copy(Bitmap.Config.ARGB_8888, true)
            try {
                faces.forEach { face ->
                    blurRegion(output, face.boundingBox, mode, strength)
                }
                val saved = MediaStoreSaver.saveImage(
                    context = context,
                    bitmap = output,
                    format = Bitmap.CompressFormat.JPEG,
                    mimeType = "image/jpeg",
                    fileName = "face_blur_${System.currentTimeMillis()}.jpg",
                    quality = OUTPUT_QUALITY
                ).getOrThrow()
                saved to faces.size
            } finally {
                output.recycle()
                source.recycle()
            }
        }
    }

    private fun blurRegion(bitmap: Bitmap, boundingBox: Rect, mode: FaceBlurMode, strength: Int) {
        val padding = (maxOf(boundingBox.width(), boundingBox.height()) * 0.08f).toInt()
        val left = (boundingBox.left - padding).coerceIn(0, bitmap.width - 1)
        val top = (boundingBox.top - padding).coerceIn(0, bitmap.height - 1)
        val right = (boundingBox.right + padding).coerceIn(left + 1, bitmap.width)
        val bottom = (boundingBox.bottom + padding).coerceIn(top + 1, bitmap.height)
        val width = right - left
        val height = bottom - top
        if (width <= 1 || height <= 1) return

        val divisor = when (mode) {
            FaceBlurMode.MOSAIC -> 6 + strength.coerceIn(1, 5) * 6
            FaceBlurMode.SOFT -> 4 + strength.coerceIn(1, 5) * 3
        }
        val smallWidth = (width / divisor).coerceAtLeast(1)
        val smallHeight = (height / divisor).coerceAtLeast(1)

        val region = Bitmap.createBitmap(bitmap, left, top, width, height)
        val small = Bitmap.createScaledBitmap(region, smallWidth, smallHeight, true)
        val restored = Bitmap.createScaledBitmap(
            small,
            width,
            height,
            mode == FaceBlurMode.SOFT
        )
        try {
            Canvas(bitmap).drawBitmap(
                restored,
                left.toFloat(),
                top.toFloat(),
                Paint().apply { isFilterBitmap = mode == FaceBlurMode.SOFT }
            )
        } finally {
            if (!region.isRecycled) region.recycle()
            if (!small.isRecycled) small.recycle()
            if (!restored.isRecycled) restored.recycle()
        }
    }
}
