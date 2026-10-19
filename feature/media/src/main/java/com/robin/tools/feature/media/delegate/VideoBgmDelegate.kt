package com.robin.tools.feature.media.delegate

import android.content.Context
import android.net.Uri
import com.robin.tools.feature.media.data.CompressionLevel

/** Replaces or mixes the video's audio with a chosen music file. */
class VideoBgmDelegate(private val mixWithOriginal: Boolean) : CompressionDelegate {

    override suspend fun process(
        context: Context,
        uri: Uri,
        level: CompressionLevel,
        onProgress: (Float, String) -> Unit
    ): Result<String> = Result.failure(Exception("Two inputs required"))

    suspend fun processPair(
        context: Context,
        videoUri: Uri,
        audioUri: Uri,
        onProgress: (Float, String) -> Unit
    ): Result<String> = TwoInputVideoHelper.run(
        context = context,
        primary = videoUri,
        secondary = audioUri,
        onProgress = onProgress,
        label = if (mixWithOriginal) "Mixing background music…" else "Replacing audio…"
    ) { video, audio, output ->
        if (mixWithOriginal) {
            arrayOf(
                "-i", video.absolutePath,
                "-i", audio.absolutePath,
                "-filter_complex",
                "[0:a]volume=0.6[a0];[1:a]volume=0.6[a1];[a0][a1]amix=inputs=2:duration=first:dropout_transition=2[a]",
                "-map", "0:v",
                "-map", "[a]",
                "-c:v", "copy",
                "-c:a", "aac",
                "-b:a", "160k",
                "-shortest",
                "-movflags", "+faststart",
                output.absolutePath,
                "-y"
            )
        } else {
            arrayOf(
                "-i", video.absolutePath,
                "-i", audio.absolutePath,
                "-map", "0:v",
                "-map", "1:a",
                "-c:v", "copy",
                "-c:a", "aac",
                "-b:a", "160k",
                "-shortest",
                "-movflags", "+faststart",
                output.absolutePath,
                "-y"
            )
        }
    }
}
