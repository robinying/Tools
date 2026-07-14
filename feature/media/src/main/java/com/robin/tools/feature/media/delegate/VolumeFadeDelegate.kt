package com.robin.tools.feature.media.delegate

import android.content.Context
import android.net.Uri
import com.robin.tools.feature.media.data.CompressionLevel

/**
 * Adjust volume and apply 0.8s fade in/out.
 * Level: LOW=0.5, MEDIUM=1.0, HIGH=1.5 volume gain.
 */
class VolumeFadeDelegate : CompressionDelegate {
    override suspend fun process(
        context: Context,
        uri: Uri,
        level: CompressionLevel,
        onProgress: (Float, String) -> Unit
    ): Result<String> {
        val volume = when (level) {
            CompressionLevel.LOW -> 0.5
            CompressionLevel.MEDIUM -> 1.0
            CompressionLevel.HIGH -> 1.5
        }
        return FfmpegVideoHelper.runVideoJob(context, uri, onProgress, "音量/淡入淡出…") { input, output ->
            val duration = FfmpegVideoHelper.videoDurationSec(input.absolutePath)
            val fadeOutStart = (duration - 0.8).coerceAtLeast(0.0)
            val hasAudio = FfmpegVideoHelper.hasAudioTrack(input.absolutePath)
            if (!hasAudio) {
                // No audio: copy video only
                arrayOf(
                    "-i", input.absolutePath,
                    "-c:v", "copy",
                    "-an",
                    output.absolutePath,
                    "-y"
                )
            } else {
                val af =
                    "volume=$volume,afade=t=in:st=0:d=0.8,afade=t=out:st=$fadeOutStart:d=0.8"
                arrayOf(
                    "-i", input.absolutePath,
                    "-c:v", "copy",
                    "-af", af,
                    "-c:a", "aac",
                    "-b:a", "128k",
                    "-movflags", "+faststart",
                    output.absolutePath,
                    "-y"
                )
            }
        }
    }
}
