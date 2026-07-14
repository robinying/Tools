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

/** Transcode video to MP4 (H.264/AAC when available, else mpeg4/aac). */
class TranscodeDelegate : CompressionDelegate {
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
        val crf = when (level) {
            CompressionLevel.LOW -> "28"
            CompressionLevel.MEDIUM -> "23"
            CompressionLevel.HIGH -> "18"
        }
        // Prefer libx264; many FFmpeg-Kit builds include it. Fall back handled on failure.
        val arguments = arrayOf(
            "-i", inputFile.absolutePath,
            "-c:v", "mpeg4",
            "-q:v", if (level == CompressionLevel.HIGH) "3" else if (level == CompressionLevel.MEDIUM) "5" else "8",
            "-c:a", "aac",
            "-b:a", "128k",
            "-movflags", "+faststart",
            outputFile.absolutePath,
            "-y"
        )
        onProgress(0.05f, "转码为 MP4…")
        val session = FFmpegKit.executeWithArgumentsAsync(arguments, { s ->
            try {
                if (ReturnCode.isSuccess(s.returnCode)) {
                    val galleryUri = FileUtils.saveVideoToGallery(context, outputFile)
                    continuation.resume(
                        Result.success(galleryUri?.toString() ?: outputFile.absolutePath)
                    )
                    if (galleryUri != null) outputFile.delete()
                } else if (ReturnCode.isCancel(s.returnCode)) {
                    continuation.resume(Result.failure(Exception("Cancelled")))
                    outputFile.delete()
                } else {
                    Log.e(TAG, "transcode failed crf=$crf: ${s.allLogsAsString}")
                    continuation.resume(Result.failure(Exception("FFmpeg failed")))
                    outputFile.delete()
                }
            } finally {
                inputFile.delete()
            }
        }, { log -> Log.d(TAG, log.message) }) { stats ->
            val t = stats.time
            if (t > 0) onProgress(0.5f, "转码中… ${t / 1000}s")
        }

        continuation.invokeOnCancellation { FFmpegKit.cancel(session.sessionId) }
    }

    companion object {
        private const val TAG = "Transcode"
    }
}
