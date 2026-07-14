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

/** Remove audio from video (video stream copy when possible). */
class StripAudioDelegate : CompressionDelegate {
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
        // Prefer stream copy for speed; falls back not attempted here for simplicity.
        val arguments = arrayOf(
            "-i", inputFile.absolutePath,
            "-c:v", "copy",
            "-an",
            outputFile.absolutePath,
            "-y"
        )
        onProgress(0.2f, "去除音轨…")
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
                    // Retry with re-encode if copy failed (incompatible container).
                    val retryOut = FileUtils.createOutputFile(context, "mp4")
                    val retryArgs = arrayOf(
                        "-i", inputFile.absolutePath,
                        "-c:v", "mpeg4",
                        "-q:v", "5",
                        "-an",
                        retryOut.absolutePath,
                        "-y"
                    )
                    val retry = FFmpegKit.executeWithArguments(retryArgs)
                    if (ReturnCode.isSuccess(retry.returnCode)) {
                        val galleryUri = FileUtils.saveVideoToGallery(context, retryOut)
                        continuation.resume(
                            Result.success(galleryUri?.toString() ?: retryOut.absolutePath)
                        )
                        if (galleryUri != null) retryOut.delete()
                        outputFile.delete()
                    } else {
                        continuation.resume(Result.failure(Exception("FFmpeg failed")))
                        outputFile.delete()
                        retryOut.delete()
                    }
                }
            } finally {
                inputFile.delete()
            }
        }, { log -> Log.d(TAG, log.message) }, null)

        continuation.invokeOnCancellation { FFmpegKit.cancel(session.sessionId) }
    }

    companion object {
        private const val TAG = "StripAudio"
    }
}
