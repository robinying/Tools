package com.robin.tools.feature.media.delegate

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Log
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import com.robin.tools.feature.media.data.CompressionLevel
import com.robin.tools.feature.media.utils.FileUtils
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class GifConversionDelegate : CompressionDelegate {
    private val TAG = "GifConversion"

    override suspend fun process(
        context: Context,
        uri: Uri,
        level: CompressionLevel,
        onProgress: (Float, String) -> Unit
    ): Result<String> = suspendCancellableCoroutine { continuation ->
        val inputFile = FileUtils.getFileFromUri(context, uri) ?: run {
            continuation.resume(Result.failure(Exception("无法读取文件")))
            return@suspendCancellableCoroutine
        }
        val outputFile = FileUtils.createOutputFile(context, "gif")

        var totalDurationMs = 0L
        try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(inputFile.absolutePath)
            totalDurationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            retriever.release()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get video duration", e)
        }

        val (fps, targetWidth) = when (level) {
            CompressionLevel.LOW -> 5 to 240
            CompressionLevel.MEDIUM -> 10 to 320
            CompressionLevel.HIGH -> 15 to 480
        }

        val filter = "fps=$fps,scale=$targetWidth:-1:flags=lanczos,split[s0][s1];[s0]palettegen[p];[s1][p]paletteuse"
        // 使用数组参数形式避免命令注入
        val arguments = arrayOf(
            "-i", inputFile.absolutePath,
            "-vf", filter,
            "-f", "gif",
            outputFile.absolutePath,
            "-y"
        )

        onProgress(0f, "正在准备转换...")

        val session = FFmpegKit.executeWithArgumentsAsync(arguments, { session ->
            val returnCode = session.returnCode
            try {
                if (ReturnCode.isSuccess(returnCode)) {
                    val galleryUri = FileUtils.saveGifToGallery(context, outputFile)
                    if (galleryUri != null) {
                        continuation.resume(Result.success(galleryUri.toString()))
                        outputFile.delete()
                    } else {
                        continuation.resume(Result.success(outputFile.absolutePath))
                    }
                } else if (ReturnCode.isCancel(returnCode)) {
                    continuation.resume(Result.failure(Exception("Cancelled")))
                } else {
                    continuation.resume(Result.failure(Exception("FFmpeg failed: ${session.getLogsAsString()}")))
                }
            } finally {
                inputFile.delete()
                if (!ReturnCode.isSuccess(returnCode)) outputFile.delete()
            }
        }, { log ->
            Log.d(TAG, log.message)
        }) { statistics ->
            val timeInMs = statistics.time
            val progress = if (totalDurationMs > 0) (timeInMs.toFloat() / totalDurationMs.toFloat()).coerceIn(0f, 0.99f) else 0.5f
            val processedSeconds = (timeInMs / 1000).toInt()
            val totalSeconds = (totalDurationMs / 1000).toInt()
            val timeString = if (totalDurationMs > 0) "${processedSeconds}s / ${totalSeconds}s" else "${processedSeconds}s"
            onProgress(progress, "正在转换... 已处理: $timeString")
        }

        continuation.invokeOnCancellation {
            FFmpegKit.cancel(session.sessionId)
        }
    }
}
