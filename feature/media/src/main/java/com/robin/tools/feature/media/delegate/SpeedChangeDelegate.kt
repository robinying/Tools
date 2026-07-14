package com.robin.tools.feature.media.delegate

import android.content.Context
import android.net.Uri
import com.robin.tools.feature.media.data.CompressionLevel

/**
 * Change playback speed. Level maps to: LOW=0.5x, MEDIUM=1.5x, HIGH=2.0x.
 * Video via setpts; audio via atempo when an audio track exists.
 */
class SpeedChangeDelegate : CompressionDelegate {
    override suspend fun process(
        context: Context,
        uri: Uri,
        level: CompressionLevel,
        onProgress: (Float, String) -> Unit
    ): Result<String> {
        val speed = when (level) {
            CompressionLevel.LOW -> 0.5
            CompressionLevel.MEDIUM -> 1.5
            CompressionLevel.HIGH -> 2.0
        }
        val setpts = 1.0 / speed
        return FfmpegVideoHelper.runVideoJob(context, uri, onProgress, "变速 ${speed}x…") { input, output ->
            val hasAudio = FfmpegVideoHelper.hasAudioTrack(input.absolutePath)
            if (hasAudio) {
                arrayOf(
                    "-i", input.absolutePath,
                    "-filter_complex",
                    "[0:v]setpts=${setpts}*PTS[v];[0:a]atempo=$speed[a]",
                    "-map", "[v]",
                    "-map", "[a]",
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
                    "-filter:v", "setpts=${setpts}*PTS",
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
