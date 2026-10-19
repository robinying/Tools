package com.robin.tools.feature.face.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.segmentation.Segmentation
import com.google.mlkit.vision.segmentation.selfie.SelfieSegmenterOptions
import com.robin.tools.core.media.BitmapLoader
import com.robin.tools.core.media.MediaStoreSaver
import com.robin.tools.core.media.SavedMedia
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

enum class IdPhotoSize(val width: Int, val height: Int) {
    ONE_INCH(295, 413),
    TWO_INCH(413, 579)
}

enum class IdPhotoBackground(val color: Int) {
    WHITE(Color.WHITE),
    BLUE(Color.rgb(67, 142, 219)),
    RED(Color.rgb(214, 64, 69))
}

/**
 * Produces an ID-style portrait: the largest face is centered, the frame is
 * cropped to a document size, and the background is replaced using ML Kit's
 * selfie segmentation mask.
 */
object IdPhotoProcessor {

    private const val MAX_DIMENSION = 2560
    private const val MASK_THRESHOLD = 128

    suspend fun process(
        context: Context,
        uri: Uri,
        size: IdPhotoSize,
        background: IdPhotoBackground
    ): Result<SavedMedia> = withContext(Dispatchers.IO) {
        runCatching {
            val source = BitmapLoader.load(context, uri, MAX_DIMENSION)
                ?: error("Cannot decode the selected image")
            try {
                val faces = FaceDetector().detect(source, 0)
                val face = faces.maxByOrNull { it.boundingBox.width() * it.boundingBox.height() }
                    ?: error("No face detected")

                val crop = computeCropRect(
                    sourceWidth = source.width,
                    sourceHeight = source.height,
                    faceLeft = face.boundingBox.left,
                    faceTop = face.boundingBox.top,
                    faceWidth = face.boundingBox.width(),
                    faceHeight = face.boundingBox.height(),
                    aspect = size.width.toFloat() / size.height
                )
                val cropped = Bitmap.createBitmap(
                    source,
                    crop[0],
                    crop[1],
                    crop[2],
                    crop[3]
                )
                val scaled = Bitmap.createScaledBitmap(cropped, size.width, size.height, true)
                if (cropped !== scaled) cropped.recycle()

                val output = try {
                    replaceBackground(context, source, scaled, background)
                } finally {
                    if (!scaled.isRecycled) scaled.recycle()
                }

                try {
                    MediaStoreSaver.saveImage(
                        context = context,
                        bitmap = output,
                        format = Bitmap.CompressFormat.JPEG,
                        mimeType = "image/jpeg",
                        fileName = "id_photo_${System.currentTimeMillis()}.jpg",
                        quality = 94
                    ).getOrThrow()
                } finally {
                    output.recycle()
                }
            } finally {
                source.recycle()
            }
        }
    }

    private suspend fun replaceBackground(
        context: Context,
        original: Bitmap,
        portrait: Bitmap,
        background: IdPhotoBackground
    ): Bitmap {
        val options = SelfieSegmenterOptions.Builder()
            .setDetectorMode(SelfieSegmenterOptions.SINGLE_IMAGE_MODE)
            .build()
        val segmenter = Segmentation.getClient(options)
        val mask = try {
            segmenter.process(InputImage.fromBitmap(original, 0)).await()
        } finally {
            segmenter.close()
        }

        val output = Bitmap.createBitmap(portrait.width, portrait.height, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(output)
        canvas.drawColor(background.color)

        val pixels = IntArray(portrait.width * portrait.height)
        portrait.getPixels(pixels, 0, portrait.width, 0, 0, portrait.width, portrait.height)
        val maskBuffer = mask.buffer
        val maskWidth = mask.width
        val maskHeight = mask.height
        // ML Kit returns one 32-bit float confidence per pixel (0..1), so use a
        // float view rather than treating the buffer as 8-bit alpha.
        maskBuffer.order(java.nio.ByteOrder.nativeOrder())
        val maskFloats = maskBuffer.asFloatBuffer()
        val foreground = IntArray(pixels.size)

        for (y in 0 until portrait.height) {
            val maskY = (y.toLong() * maskHeight / portrait.height).toInt().coerceIn(0, maskHeight - 1)
            val row = y * portrait.width
            for (x in 0 until portrait.width) {
                val maskX = (x.toLong() * maskWidth / portrait.width).toInt()
                    .coerceIn(0, maskWidth - 1)
                val confidence = maskFloats.get(maskY * maskWidth + maskX)
                foreground[row + x] = if (confidence >= 0.5f) {
                    pixels[row + x]
                } else {
                    Color.TRANSPARENT
                }
            }
        }
        val foregroundBitmap = Bitmap.createBitmap(
            foreground,
            portrait.width,
            portrait.height,
            Bitmap.Config.ARGB_8888
        )
        canvas.drawBitmap(foregroundBitmap, 0f, 0f, null)
        foregroundBitmap.recycle()
        return output
    }

    /**
     * Places the face in the upper third and expands the frame to [aspect].
     * The rect is clamped to the source bounds.
     */
    internal fun computeCropRect(
        sourceWidth: Int,
        sourceHeight: Int,
        faceLeft: Int,
        faceTop: Int,
        faceWidth: Int,
        faceHeight: Int,
        aspect: Float
    ): IntArray {
        val centerX = faceLeft + faceWidth / 2f
        val desiredHeight = (faceHeight * 4.2f).coerceAtLeast(faceWidth * 3.2f)
        var cropWidth = (desiredHeight * aspect).toInt()
        var cropHeight = desiredHeight.toInt()
        if (cropWidth > sourceWidth) {
            cropWidth = sourceWidth
            cropHeight = (cropWidth / aspect).toInt()
        }
        if (cropHeight > sourceHeight) {
            cropHeight = sourceHeight
            cropWidth = (cropHeight * aspect).toInt()
        }
        val left = (centerX - cropWidth / 2f).toInt()
            .coerceIn(0, (sourceWidth - cropWidth).coerceAtLeast(0))
        val top = (faceTop - cropHeight * 0.18f).toInt()
            .coerceIn(0, (sourceHeight - cropHeight).coerceAtLeast(0))
        return intArrayOf(left, top, cropWidth.coerceAtLeast(1), cropHeight.coerceAtLeast(1))
    }
}
