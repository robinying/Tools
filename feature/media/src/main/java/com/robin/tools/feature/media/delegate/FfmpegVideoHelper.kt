package com.robin.tools.feature.media.delegate

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Log
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import com.robin.tools.feature.media.utils.FileUtils
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import kotlin.coroutines.resume

internal object FfmpegVideoHelper {
    private const val TAG = "FfmpegVideo"

    suspend fun runVideoJob(
        context: Context,
        uri: Uri,
        onProgress: (Float, String) -> Unit,
        progressLabel: String,
        buildArgs: (input: File, output: File) -> Array<String>
    ): Result<String> = suspendCancellableCoroutine { cont ->
        val inputFile = FileUtils.getFileFromUri(context, uri) ?: run {
            cont.resume(Result.failure(Exception("无法读取文件")))
            return@suspendCancellableCoroutine
        }
        val outputFile = FileUtils.createOutputFile(context, "mp4")
        val arguments = buildArgs(inputFile, outputFile)
        onProgress(0.08f, progressLabel)
        val session = FFmpegKit.executeWithArgumentsAsync(arguments, { s ->
            try {
                if (ReturnCode.isSuccess(s.returnCode)) {
                    val galleryUri = FileUtils.saveVideoToGallery(context, outputFile)
                    cont.resume(Result.success(galleryUri?.toString() ?: outputFile.absolutePath))
                    if (galleryUri != null) outputFile.delete()
                } else if (ReturnCode.isCancel(s.returnCode)) {
                    cont.resume(Result.failure(Exception("Cancelled")))
                    outputFile.delete()
                } else {
                    Log.e(TAG, "ffmpeg failed: ${s.allLogsAsString}")
                    cont.resume(Result.failure(Exception("FFmpeg failed")))
                    outputFile.delete()
                }
            } finally {
                inputFile.delete()
            }
        }, { log -> Log.d(TAG, log.message) }) { stats ->
            val t = stats.time
            if (t > 0) onProgress(0.45f.coerceAtMost(0.9f), "$progressLabel ${t / 1000}s")
        }
        cont.invokeOnCancellation { FFmpegKit.cancel(session.sessionId) }
    }

    fun videoDurationSec(path: String): Double {
        val r = MediaMetadataRetriever()
        return try {
            r.setDataSource(path)
            val ms = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            (ms / 1000.0).coerceAtLeast(0.1)
        } catch (_: Exception) {
            3.0
        } finally {
            try {
                r.release()
            } catch (_: Exception) {
            }
        }
    }

    fun hasAudioTrack(path: String): Boolean {
        val r = MediaMetadataRetriever()
        return try {
            r.setDataSource(path)
            r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO) == "yes"
        } catch (_: Exception) {
            false
        } finally {
            try {
                r.release()
            } catch (_: Exception) {
            }
        }
    }
}
