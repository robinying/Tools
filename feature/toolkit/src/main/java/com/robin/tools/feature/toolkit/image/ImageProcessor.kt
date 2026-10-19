package com.robin.tools.feature.toolkit.image

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import com.robin.tools.core.media.BitmapLoader
import com.robin.tools.core.media.MediaStoreSaver
import com.robin.tools.core.media.SavedMedia
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

/** Metadata surfaced by the EXIF tool. */
data class ExifData(
    val width: Int,
    val height: Int,
    val mimeType: String?,
    val orientation: Int,
    val make: String?,
    val model: String?,
    val dateTime: String?,
    val hasLocation: Boolean
)

/**
 * Bitmap operations for the image workbench.
 *
 * Every operation decodes with a bounded sample size and writes through
 * [MediaStoreSaver]; callers only deal with [SavedMedia] or an error.
 */
object ImageProcessor {

    private const val MAX_DIMENSION = 4096
    private const val LOSSLESS_QUALITY = 100

    suspend fun convert(
        context: Context,
        uri: Uri,
        format: ImageFormat,
        quality: Int
    ): Result<SavedMedia> = transform(context, uri, format, quality) { it }

    suspend fun resize(
        context: Context,
        uri: Uri,
        scalePercent: Int,
        format: ImageFormat,
        quality: Int
    ): Result<SavedMedia> = transform(context, uri, format, quality) { bitmap ->
        val scale = scalePercent.coerceIn(5, 100) / 100f
        val width = (bitmap.width * scale).toInt().coerceAtLeast(1)
        val height = (bitmap.height * scale).toInt().coerceAtLeast(1)
        Bitmap.createScaledBitmap(bitmap, width, height, true)
    }

    suspend fun rotate(
        context: Context,
        uri: Uri,
        degrees: Int,
        format: ImageFormat,
        quality: Int
    ): Result<SavedMedia> = transform(context, uri, format, quality) { bitmap ->
        val normalized = ((degrees % 360) + 360) % 360
        if (normalized == 0) {
            bitmap
        } else {
            val matrix = Matrix().apply { setRotate(normalized.toFloat()) }
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        }
    }

    /**
     * Re-encodes the image without copying EXIF, which drops camera/GPS metadata.
     */
    suspend fun stripMetadata(
        context: Context,
        uri: Uri,
        quality: Int = 95
    ): Result<SavedMedia> = transform(context, uri, ImageFormat.JPEG, quality) { it }

    suspend fun toPdf(context: Context, uri: Uri): Result<SavedMedia> =
        withContext(Dispatchers.IO) {
            runCatching {
                val bitmap = requireBitmap(context, uri)
                try {
                    val document = PdfDocument()
                    try {
                        val pageInfo = PdfDocument.PageInfo.Builder(
                            bitmap.width,
                            bitmap.height,
                            1
                        ).create()
                        val page = document.startPage(pageInfo)
                        page.canvas.drawColor(android.graphics.Color.WHITE)
                        page.canvas.drawBitmap(bitmap, 0f, 0f, null)
                        document.finishPage(page)

                        val bytes = ByteArrayOutputStream().use { out ->
                            document.writeTo(out)
                            out.toByteArray()
                        }
                        MediaStoreSaver.savePdf(
                            context,
                            bytes,
                            "tools_${System.currentTimeMillis()}.pdf"
                        ).getOrThrow()
                    } finally {
                        document.close()
                    }
                } finally {
                    bitmap.recycle()
                }
            }
        }

    suspend fun readExif(context: Context, uri: Uri): ExifData? =
        withContext(Dispatchers.IO) {
            runCatching {
                val resolver = context.contentResolver
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }

                var orientation = 1
                var make: String? = null
                var model: String? = null
                var dateTime: String? = null
                var hasLocation = false
                resolver.openInputStream(uri)?.use { input ->
                    val exif = ExifInterface(input)
                    orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, 1)
                    make = exif.getAttribute(ExifInterface.TAG_MAKE)
                    model = exif.getAttribute(ExifInterface.TAG_MODEL)
                    dateTime = exif.getAttribute(ExifInterface.TAG_DATETIME)
                    hasLocation = exif.latLong != null
                }
                ExifData(
                    width = bounds.outWidth,
                    height = bounds.outHeight,
                    mimeType = resolver.getType(uri),
                    orientation = orientation,
                    make = make,
                    model = model,
                    dateTime = dateTime,
                    hasLocation = hasLocation
                )
            }.getOrNull()
        }

    private suspend fun transform(
        context: Context,
        uri: Uri,
        format: ImageFormat,
        quality: Int,
        operation: (Bitmap) -> Bitmap
    ): Result<SavedMedia> = withContext(Dispatchers.IO) {
        runCatching {
            val source = requireBitmap(context, uri)
            val output = try {
                operation(source)
            } catch (e: Exception) {
                source.recycle()
                throw e
            }
            try {
                val fileName = "tools_${System.currentTimeMillis()}.${format.extension}"
                MediaStoreSaver.saveImage(
                    context = context,
                    bitmap = output,
                    format = format.compressFormat,
                    mimeType = format.mimeType,
                    fileName = fileName,
                    quality = format.effectiveQuality(quality)
                ).getOrThrow()
            } finally {
                output.recycle()
                if (output !== source && !source.isRecycled) {
                    source.recycle()
                }
            }
        }
    }

    private suspend fun requireBitmap(context: Context, uri: Uri): Bitmap {
        kotlinx.coroutines.currentCoroutineContext().ensureActive()
        return BitmapLoader.load(context, uri, MAX_DIMENSION)
            ?: error("Cannot decode the selected image")
    }
}
