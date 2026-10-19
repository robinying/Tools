package com.robin.tools.feature.toolkit.image

import android.graphics.Bitmap
import com.robin.tools.feature.toolkit.R

/** Tools offered by the image workbench. */
enum class ImageToolType(val titleRes: Int, val descriptionRes: Int) {
    CONVERT(R.string.image_tool_convert, R.string.image_tool_convert_desc),
    RESIZE(R.string.image_tool_resize, R.string.image_tool_resize_desc),
    ROTATE(R.string.image_tool_rotate, R.string.image_tool_rotate_desc),
    EXIF(R.string.image_tool_exif, R.string.image_tool_exif_desc),
    TO_PDF(R.string.image_tool_pdf, R.string.image_tool_pdf_desc);

    companion object {
        fun fromKey(key: String?): ImageToolType =
            entries.firstOrNull { it.name == key } ?: CONVERT
    }
}

enum class ImageFormat(
    val mimeType: String,
    val extension: String,
    val compressFormat: Bitmap.CompressFormat,
    val labelRes: Int
) {
    JPEG("image/jpeg", "jpg", Bitmap.CompressFormat.JPEG, R.string.image_format_jpeg),
    PNG("image/png", "png", Bitmap.CompressFormat.PNG, R.string.image_format_png),
    WEBP("image/webp", "webp", Bitmap.CompressFormat.WEBP, R.string.image_format_webp);

    /** PNG ignores quality, so only compress formats with lossy options use it. */
    fun effectiveQuality(quality: Int): Int =
        if (this == PNG) 100 else quality.coerceIn(10, 100)
}
