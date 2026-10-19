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
import java.io.File
import kotlin.coroutines.resume

/** Concatenates audio files in order and re-encodes them as AAC/M4A. */
class AudioConcatDelegate : CompressionDelegate {

    override suspend fun process(
        context: Context,
        uri: Uri,
        level: CompressionLevel,
        onProgress: (Float, String) -> Unit
    ): Result<String> = processAll(context, listOf(uri), level, onProgress)

    suspend fun processAll(
        context: Context,
        uris: List<Uri>,
        level: CompressionLevel,
        onProgress: (Float, String) -> Unit
    ): Result<String> = suspendCancellableCoroutine { continuation ->
        if (uris.size < 2) {
            continuation.resume(Result.failure(Exception("Select at least 2 audio files")))
            return@suspendCancellableCoroutine
        }
        val inputs = mutableListOf<File>()
        try {
            onProgress(0.05f, "Preparing audio…")
            for (uri in uris) {
                inputs += FileUtils.getFileFromUri(context, uri)
                    ?: throw IllegalStateException("Cannot read audio file")
            }
            val listFile = File(context.cacheDir, "audio_concat_${System.currentTimeMillis()}.txt")
            listFile.writeText(
                inputs.joinToString("\n") { "file '${it.absolutePath.replace("'", "'\\''")}'" }
            )
            val outputFile = FileUtils.createOutputFile(context, "m4a")
            onProgress(0.15f, "Joining audio…")

            fun cleanup() {
                listFile.delete()
                inputs.forEach { it.delete() }
            }

            fun finish(result: Result<String>) {
                continuation.resume(result)
                cleanup()
            }

            val arguments = arrayOf(
                "-f", "concat",
                "-safe", "0",
                "-i", listFile.absolutePath,
                "-vn",
                "-c:a", "aac",
                "-b:a", "192k",
                outputFile.absolutePath,
                "-y"
            )
            val session = FFmpegKit.executeWithArgumentsAsync(arguments, { s ->
                if (ReturnCode.isSuccess(s.returnCode)) {
                    val galleryUri = FileUtils.saveAudioToGallery(context, outputFile)
                    if (galleryUri != null) {
                        finish(Result.success(galleryUri.toString()))
                        outputFile.delete()
                    } else {
                        finish(Result.success(outputFile.absolutePath))
                    }
                } else {
                    outputFile.delete()
                    finish(
                        Result.failure(
                            Exception("FFmpeg failed: ${s.getLogsAsString()}")
                        )
                    )
                }
            }, { log ->
                if (Log.isLoggable(TAG, Log.DEBUG)) Log.d(TAG, log.message)
            }) { statistics ->
                val seconds = statistics.time / 1000.0
                onProgress(
                    (0.15 + (seconds / 60.0)).toFloat().coerceIn(0.15f, 0.99f),
                    "Joining audio…"
                )
            }

            continuation.invokeOnCancellation {
                FFmpegKit.cancel(session.sessionId)
                cleanup()
            }
        } catch (e: Exception) {
            inputs.forEach { it.delete() }
            continuation.resume(Result.failure(e))
        }
    }

    private companion object {
        const val TAG = "AudioConcat"
    }
}
