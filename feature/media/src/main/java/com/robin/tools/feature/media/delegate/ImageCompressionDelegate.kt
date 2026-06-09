package com.robin.tools.feature.media.delegate

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.robin.tools.feature.media.data.CompressionLevel
import com.robin.tools.feature.media.utils.FileUtils
import java.io.FileOutputStream

class ImageCompressionDelegate : CompressionDelegate {

    companion object {
        private const val MAX_DIMENSION = 1920

        /**
         * 根据目标尺寸计算 Bitmap 下采样比例，避免加载超大图片导致 OOM
         */
        private fun calculateInSampleSize(width: Int, height: Int, reqWidth: Int, reqHeight: Int): Int {
            var inSampleSize = 1
            if (height > reqHeight || width > reqWidth) {
                val halfHeight = height / 2
                val halfWidth = width / 2
                while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                    inSampleSize *= 2
                }
            }
            return inSampleSize
        }
    }

    override suspend fun process(
        context: Context,
        uri: Uri,
        level: CompressionLevel,
        onProgress: (Float, String) -> Unit
    ): Result<String> {
        onProgress(0.05f, "正在读取图片...")

        // 第一步：仅获取图片尺寸，不加载到内存
        val decodeBoundsOptions = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, decodeBoundsOptions)
        }

        // 第二步：根据实际尺寸计算采样率，避免 OOM
        val sampleSize = calculateInSampleSize(
            decodeBoundsOptions.outWidth,
            decodeBoundsOptions.outHeight,
            MAX_DIMENSION,
            MAX_DIMENSION
        )

        // 第三步：使用采样率重新打开流并解码
        val bitmap = context.contentResolver.openInputStream(uri)?.use { stream ->
            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
            }
            BitmapFactory.decodeStream(stream, null, decodeOptions)
        } ?: return Result.failure(Exception("无法读取或解析图片"))

        val outputFile = FileUtils.createOutputFile(context, "jpg")
        val quality = when (level) {
            CompressionLevel.LOW -> 80
            CompressionLevel.MEDIUM -> 50
            CompressionLevel.HIGH -> 20
        }

        onProgress(0.5f, "正在压缩图片...")

        return try {
            FileOutputStream(outputFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
                out.flush()
            }

            // 释放 Bitmap 内存
            bitmap.recycle()

            val galleryUri = FileUtils.saveImageToGallery(context, outputFile)
            if (galleryUri != null) {
                outputFile.delete()
                Result.success(galleryUri.toString())
            } else {
                Result.success(outputFile.absolutePath)
            }
        } catch (e: Exception) {
            outputFile.delete()
            Result.failure(e)
        }
    }
}
