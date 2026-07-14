package com.robin.tools.feature.media.delegate

import android.content.Context
import android.net.Uri
import com.robin.tools.feature.media.data.CompressionLevel

/**
 * Center-crop to aspect ratio.
 * LOW=1:1, MEDIUM=9:16, HIGH=16:9.
 */
class CropAspectDelegate : CompressionDelegate {
    override suspend fun process(
        context: Context,
        uri: Uri,
        level: CompressionLevel,
        onProgress: (Float, String) -> Unit
    ): Result<String> {
        // Target aspect as w/h decimal used in ffmpeg crop expression
        val (label, target) = when (level) {
            CompressionLevel.LOW -> "1:1" to "1"
            CompressionLevel.MEDIUM -> "9:16" to "9/16"
            CompressionLevel.HIGH -> "16:9" to "16/9"
        }
        // If input aspect > target: crop width; else crop height. Centered.
        val crop =
            "crop=" +
                "if(gt(a\\,$target)\\,ih*$target\\,iw):" +
                "if(gt(a\\,$target)\\,ih\\,iw/($target))"
        return FfmpegVideoHelper.runVideoJob(context, uri, onProgress, "裁切 $label…") { input, output ->
            val hasAudio = FfmpegVideoHelper.hasAudioTrack(input.absolutePath)
            if (hasAudio) {
                arrayOf(
                    "-i", input.absolutePath,
                    "-vf", crop,
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
                    "-vf", crop,
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
