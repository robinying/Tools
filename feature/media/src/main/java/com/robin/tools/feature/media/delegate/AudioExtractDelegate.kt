package com.robin.tools.feature.media.delegate

import android.content.Context
import android.net.Uri
import android.util.Log
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import com.robin.tools.feature.media.data.CompressionLevel
import com.robin.tools.feature.media.utils.FileUtils
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/** Extract audio track from video → M4A (AAC). */
class AudioExtractDelegate : CompressionDelegate {
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
        val bitrate = when (level) {
            CompressionLevel.LOW -> "96k"
            CompressionLevel.MEDIUM -> "128k"
            CompressionLevel.HIGH -> "192k"
        }
        val outputFile = FileUtils.createOutputFile(context, "m4a")
        val arguments = arrayOf(
            "-i", inputFile.absolutePath,
            "-vn",
            "-c:a", "aac",
            "-b:a", bitrate,
            outputFile.absolutePath,
            "-y"
        )
        onProgress(0.1f, "提取音频…")
        val session = FFmpegKit.executeWithArgumentsAsync(arguments, { s ->
            try {
                if (ReturnCode.isSuccess(s.returnCode)) {
                    val galleryUri = FileUtils.saveAudioToGallery(context, outputFile)
                    continuation.resume(
                        Result.success(galleryUri?.toString() ?: outputFile.absolutePath)
                    )
                    if (galleryUri != null) outputFile.delete()
                } else if (ReturnCode.isCancel(s.returnCode)) {
                    continuation.resume(Result.failure(Exception("Cancelled")))
                    outputFile.delete()
                } else {
                    continuation.resume(Result.failure(Exception("FFmpeg failed")))
                    outputFile.delete()
                }
            } finally {
                inputFile.delete()
            }
        }, { log -> Log.d(TAG, log.message) }, null)

        continuation.invokeOnCancellation { FFmpegKit.cancel(session.sessionId) }
    }

    companion object {
        private const val TAG = "AudioExtract"
    }
}
