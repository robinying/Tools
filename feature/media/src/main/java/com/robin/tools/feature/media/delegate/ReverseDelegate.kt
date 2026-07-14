package com.robin.tools.feature.media.delegate

import android.content.Context
import android.net.Uri
import com.robin.tools.feature.media.data.CompressionLevel

/**
 * Reverse video. Level: LOW/HIGH = mute reverse; MEDIUM = reverse audio when present.
 */
class ReverseDelegate : CompressionDelegate {
    override suspend fun process(
        context: Context,
        uri: Uri,
        level: CompressionLevel,
        onProgress: (Float, String) -> Unit
    ): Result<String> {
        val keepAudio = level == CompressionLevel.MEDIUM
        return FfmpegVideoHelper.runVideoJob(context, uri, onProgress, "倒放…") { input, output ->
            val hasAudio = keepAudio && FfmpegVideoHelper.hasAudioTrack(input.absolutePath)
            if (hasAudio) {
                arrayOf(
                    "-i", input.absolutePath,
                    "-vf", "reverse",
                    "-af", "areverse",
                    "-c:v", "mpeg4",
                    "-q:v", "5",
                    "-c:a", "aac",
                    "-b:a", "128k",
                    "-movflags", "+faststart",
                    output.absolutePath,
                    "-y"
                )
            } else {
                arrayOf(
                    "-i", input.absolutePath,
                    "-vf", "reverse",
                    "-an",
                    "-c:v", "mpeg4",
                    "-q:v", "5",
                    "-movflags", "+faststart",
                    output.absolutePath,
                    "-y"
                )
            }
        }
    }
}
