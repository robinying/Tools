package com.robin.tools.feature.media.delegate

import android.content.Context
import android.net.Uri
import com.robin.tools.feature.media.data.CompressionLevel

/**
 * Speed change with an arbitrary multiplier.
 *
 * atempo only accepts 0.5–2.0 per instance, so larger factors are chained
 * (for example 4x → atempo=2,atempo=2).
 */
class CustomSpeedDelegate(private val speed: Double) : CompressionDelegate {

    override suspend fun process(
        context: Context,
        uri: Uri,
        level: CompressionLevel,
        onProgress: (Float, String) -> Unit
    ): Result<String> {
        val factor = speed.coerceIn(0.125, 16.0)
        val setpts = 1.0 / factor
        val atempo = buildAtempo(factor)
        return FfmpegVideoHelper.runVideoJob(
            context, uri, onProgress, "Changing speed to ${factor}x…"
        ) { input, output ->
            val hasAudio = FfmpegVideoHelper.hasAudioTrack(input.absolutePath)
            if (hasAudio) {
                arrayOf(
                    "-i", input.absolutePath,
                    "-filter_complex",
                    "[0:v]setpts=${setpts}*PTS[v];[0:a]$atempo[a]",
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

    private fun buildAtempo(factor: Double): String {
        val filters = mutableListOf<String>()
        var remaining = factor
        while (remaining > 2.0) {
            filters += "atempo=2.0"
            remaining /= 2.0
        }
        while (remaining < 0.5) {
            filters += "atempo=0.5"
            remaining /= 0.5
        }
        filters += "atempo=${"%.4f".format(java.util.Locale.US, remaining)}"
        return filters.joinToString(",")
    }
}
