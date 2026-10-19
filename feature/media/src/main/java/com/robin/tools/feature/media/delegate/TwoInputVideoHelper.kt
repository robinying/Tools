package com.robin.tools.feature.media.delegate

import android.content.Context
import android.net.Uri
import android.util.Log
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import com.robin.tools.feature.media.utils.FileUtils
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import kotlin.coroutines.resume

/** Shared plumbing for tools that combine a primary video with a second input. */
internal object TwoInputVideoHelper {

    private const val TAG = "TwoInputVideo"

    suspend fun run(
        context: Context,
        primary: Uri,
        secondary: Uri,
        onProgress: (Float, String) -> Unit,
        label: String,
        buildArgs: (File, File, File) -> Array<String>
    ): Result<String> = suspendCancellableCoroutine { continuation ->
        val primaryFile = FileUtils.getFileFromUri(context, primary)
        val secondaryFile = FileUtils.getFileFromUri(context, secondary)
        if (primaryFile == null || secondaryFile == null) {
            primaryFile?.delete()
            secondaryFile?.delete()
            continuation.resume(Result.failure(Exception("Cannot read the selected files")))
            return@suspendCancellableCoroutine
        }
        val outputFile = FileUtils.createOutputFile(context, "mp4")
        onProgress(0.05f, label)
        val arguments = buildArgs(primaryFile, secondaryFile, outputFile)
        val session = FFmpegKit.executeWithArgumentsAsync(arguments, { session ->
            try {
                if (ReturnCode.isSuccess(session.returnCode)) {
                    val galleryUri = FileUtils.saveVideoToGallery(context, outputFile)
                    continuation.resume(
                        Result.success(galleryUri?.toString() ?: outputFile.absolutePath)
                    )
                    if (galleryUri != null) outputFile.delete()
                } else if (ReturnCode.isCancel(session.returnCode)) {
                    continuation.resume(Result.failure(Exception("Cancelled")))
                    outputFile.delete()
                } else {
                    Log.e(TAG, "ffmpeg failed: ${session.allLogsAsString}")
                    continuation.resume(Result.failure(Exception("FFmpeg failed")))
                    outputFile.delete()
                }
            } finally {
                primaryFile.delete()
                secondaryFile.delete()
            }
        }, { log ->
            if (Log.isLoggable(TAG, Log.DEBUG)) Log.d(TAG, log.message)
        }) { statistics ->
            onProgress(
                (0.2 + statistics.time / 60000.0).toFloat().coerceIn(0.2f, 0.95f),
                "$label ${statistics.time / 1000}s"
            )
        }

        continuation.invokeOnCancellation {
            FFmpegKit.cancel(session.sessionId)
        }
    }
}
