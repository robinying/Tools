package com.robin.tools.feature.media.delegate

import android.content.Context
import android.net.Uri
import android.util.Log
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import com.robin.tools.feature.media.data.CompressionLevel
import com.robin.tools.feature.media.utils.FileUtils
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import kotlin.coroutines.resume

/**
 * Concatenate multiple videos in order.
 * Prefer stream copy; fall back to re-encode. [level] selects encode quality on fallback.
 */
class ConcatDelegate : CompressionDelegate {
    /** Single-uri entry (unused for real concat — service calls [processAll]). */
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
    ): Result<String> = suspendCancellableCoroutine { cont ->
        if (uris.size < 2) {
            cont.resume(Result.failure(Exception("请至少选择 2 个视频")))
            return@suspendCancellableCoroutine
        }
        val inputs = mutableListOf<File>()
        try {
            onProgress(0.05f, "准备文件…")
            for (uri in uris) {
                val f = FileUtils.getFileFromUri(context, uri)
                    ?: throw IllegalStateException("无法读取文件")
                inputs.add(f)
            }
            val listFile = File(context.cacheDir, "concat_${System.currentTimeMillis()}.txt")
            listFile.writeText(
                inputs.joinToString("\n") { "file '${it.absolutePath.replace("'", "'\\''")}'" }
            )
            val outputFile = FileUtils.createOutputFile(context, "mp4")
            onProgress(0.15f, "拼接视频…")

            fun finishSuccess() {
                val galleryUri = FileUtils.saveVideoToGallery(context, outputFile)
                cont.resume(Result.success(galleryUri?.toString() ?: outputFile.absolutePath))
                if (galleryUri != null) outputFile.delete()
                listFile.delete()
                inputs.forEach { it.delete() }
            }

            fun finishFail(msg: String) {
                cont.resume(Result.failure(Exception(msg)))
                outputFile.delete()
                listFile.delete()
                inputs.forEach { it.delete() }
            }

            // Try stream copy first
            val copyArgs = arrayOf(
                "-f", "concat",
                "-safe", "0",
                "-i", listFile.absolutePath,
                "-c", "copy",
                "-movflags", "+faststart",
                outputFile.absolutePath,
                "-y"
            )
            val session = FFmpegKit.executeWithArgumentsAsync(copyArgs, { s ->
                if (ReturnCode.isSuccess(s.returnCode)) {
                    finishSuccess()
                } else if (ReturnCode.isCancel(s.returnCode)) {
                    finishFail("Cancelled")
                } else {
                    // Re-encode fallback
                    Log.w(TAG, "concat copy failed, re-encoding: ${s.allLogsAsString}")
                    val q = when (level) {
                        CompressionLevel.LOW -> "8"
                        CompressionLevel.MEDIUM -> "5"
                        CompressionLevel.HIGH -> "3"
                    }
                    val retryOut = FileUtils.createOutputFile(context, "mp4")
                    val reArgs = arrayOf(
                        "-f", "concat",
                        "-safe", "0",
                        "-i", listFile.absolutePath,
                        "-c:v", "mpeg4",
                        "-q:v", q,
                        "-c:a", "aac",
                        "-b:a", "128k",
                        "-movflags", "+faststart",
                        retryOut.absolutePath,
                        "-y"
                    )
                    val retry = FFmpegKit.executeWithArguments(reArgs)
                    outputFile.delete()
                    if (ReturnCode.isSuccess(retry.returnCode)) {
                        val galleryUri = FileUtils.saveVideoToGallery(context, retryOut)
                        cont.resume(
                            Result.success(galleryUri?.toString() ?: retryOut.absolutePath)
                        )
                        if (galleryUri != null) retryOut.delete()
                        listFile.delete()
                        inputs.forEach { it.delete() }
                    } else {
                        cont.resume(Result.failure(Exception("FFmpeg failed")))
                        retryOut.delete()
                        listFile.delete()
                        inputs.forEach { it.delete() }
                    }
                }
            }, { log -> Log.d(TAG, log.message) }, null)

            cont.invokeOnCancellation {
                FFmpegKit.cancel(session.sessionId)
                listFile.delete()
                inputs.forEach { it.delete() }
            }
        } catch (e: Exception) {
            inputs.forEach { it.delete() }
            cont.resume(Result.failure(e))
        }
    }

    companion object {
        private const val TAG = "Concat"
    }
}
