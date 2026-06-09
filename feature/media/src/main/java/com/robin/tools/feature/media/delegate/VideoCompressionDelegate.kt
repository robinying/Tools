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

class VideoCompressionDelegate : CompressionDelegate {
    private val TAG = "VideoCompression"

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
        val outputFile = FileUtils.createOutputFile(context, "mp4")

        var totalDurationMs = 0L
        var inputWidth = 0
        var inputHeight = 0
        try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(inputFile.absolutePath)
            totalDurationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            inputWidth = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
            inputHeight = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
            val rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
            if (rotation == 90 || rotation == 270) {
                val temp = inputWidth
                inputWidth = inputHeight
                inputHeight = temp
            }
            retriever.release()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get video metadata", e)
        }

        val minDim = if (inputWidth > 0 && inputHeight > 0) Math.min(inputWidth, inputHeight) else 720
        val (targetMinDim, bitrate) = when (level) {
            CompressionLevel.LOW -> 1080 to "4000k"
            CompressionLevel.MEDIUM -> 720 to "2000k"
            CompressionLevel.HIGH -> 480 to "750k"
        }

        val scaleFactor = if (minDim > targetMinDim) targetMinDim.toFloat() / minDim else 1f
        val newWidth = if (inputWidth > 0) ((inputWidth * scaleFactor).toInt() / 2) * 2 else -2
        val newHeight = if (inputHeight > 0) ((inputHeight * scaleFactor).toInt() / 2) * 2 else -2
        
        val scaleFilter = if (inputWidth > 0 && inputHeight > 0) "scale=$newWidth:$newHeight" else "scale=-2:$targetMinDim"

        // 使用数组参数形式避免命令注入，并确保更好的兼容性
        val arguments = arrayOf(
            "-i", inputFile.absolutePath,
            "-b:v", bitrate,
            "-vf", scaleFilter,
            "-c:a", "aac",
            outputFile.absolutePath,
            "-y"
        )

        onProgress(0f, "正在准备压缩...")

        val session = FFmpegKit.executeWithArgumentsAsync(arguments, { session ->
            val returnCode = session.returnCode
            try {
                if (ReturnCode.isSuccess(returnCode)) {
                    val galleryUri = FileUtils.saveVideoToGallery(context, outputFile)
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
            onProgress(progress, "正在压缩... 已处理: $timeString")
        }

        continuation.invokeOnCancellation {
            FFmpegKit.cancel(session.sessionId)
        }
    }
}
