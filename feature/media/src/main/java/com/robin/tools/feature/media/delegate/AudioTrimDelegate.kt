package com.robin.tools.feature.media.delegate

import android.content.Context
import android.net.Uri
import android.util.Log
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import com.robin.tools.feature.media.data.CompressionLevel
import com.robin.tools.feature.media.utils.FileUtils
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.Locale
import kotlin.coroutines.resume

/**
 * Trims an audio file to `[startMs, endMs]` and re-encodes it as AAC/M4A.
 *
 * Input seeking (`-ss` before `-i`) keeps the seek fast; `-t duration` is used
 * instead of `-to` so the range stays correct after the input seek.
 */
class AudioTrimDelegate(
    private val startMs: Long = 0L,
    private val endMs: Long = Long.MAX_VALUE
) : CompressionDelegate {

    override suspend fun process(
        context: Context,
        uri: Uri,
        level: CompressionLevel,
        onProgress: (Float, String) -> Unit
    ): Result<String> = suspendCancellableCoroutine { continuation ->
        val inputFile = FileUtils.getFileFromUri(context, uri) ?: run {
            continuation.resume(Result.failure(Exception("Cannot read audio file")))
            return@suspendCancellableCoroutine
        }
        val outputFile = FileUtils.createOutputFile(context, "m4a")

        val startSeconds = (startMs.coerceAtLeast(0L) / 1000.0)
        val durationSeconds = if (endMs == Long.MAX_VALUE || endMs <= startMs) {
            null
        } else {
            (endMs - startMs) / 1000.0
        }

        val arguments = buildList {
            add("-ss")
            add(String.format(Locale.US, "%.3f", startSeconds))
            add("-i")
            add(inputFile.absolutePath)
            durationSeconds?.let {
                add("-t")
                add(String.format(Locale.US, "%.3f", it))
            }
            addAll(
                listOf(
                    "-vn",
                    "-c:a", "aac",
                    "-b:a", "192k",
                    outputFile.absolutePath,
                    "-y"
                )
            )
        }.toTypedArray()

        onProgress(0f, "正在裁剪音频...")

        val session = FFmpegKit.executeWithArgumentsAsync(arguments, { session ->
            val returnCode = session.returnCode
            try {
                if (ReturnCode.isSuccess(returnCode)) {
                    val galleryUri = FileUtils.saveAudioToGallery(context, outputFile)
                    if (galleryUri != null) {
                        continuation.resume(Result.success(galleryUri.toString()))
                        outputFile.delete()
                    } else {
                        continuation.resume(Result.success(outputFile.absolutePath))
                    }
                } else if (ReturnCode.isCancel(returnCode)) {
                    continuation.resume(Result.failure(Exception("Cancelled")))
                } else {
                    continuation.resume(
                        Result.failure(Exception("FFmpeg failed: ${session.getLogsAsString()}"))
                    )
                }
            } finally {
                inputFile.delete()
                if (!ReturnCode.isSuccess(returnCode)) outputFile.delete()
            }
        }, { log ->
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, log.message)
            }
        }) { statistics ->
            val fraction = when {
                durationSeconds == null || durationSeconds <= 0 -> 0.5f
                else -> (statistics.time / 1000.0 / durationSeconds)
                    .toFloat()
                    .coerceIn(0f, 0.99f)
            }
            onProgress(fraction, "正在裁剪音频...")
        }

        continuation.invokeOnCancellation {
            FFmpegKit.cancel(session.sessionId)
        }
    }

    private companion object {
        const val TAG = "AudioTrimDelegate"
    }
}
