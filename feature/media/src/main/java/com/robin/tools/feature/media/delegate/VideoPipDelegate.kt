package com.robin.tools.feature.media.delegate

import android.content.Context
import android.net.Uri
import com.robin.tools.feature.media.data.CompressionLevel

enum class PipPosition { TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT }

/** Overlays a second video on top of the primary video. */
class VideoPipDelegate(private val position: PipPosition) : CompressionDelegate {

    override suspend fun process(
        context: Context,
        uri: Uri,
        level: CompressionLevel,
        onProgress: (Float, String) -> Unit
    ): Result<String> = Result.failure(Exception("Two inputs required"))

    suspend fun processPair(
        context: Context,
        videoUri: Uri,
        overlayUri: Uri,
        onProgress: (Float, String) -> Unit
    ): Result<String> = TwoInputVideoHelper.run(
        context = context,
        primary = videoUri,
        secondary = overlayUri,
        onProgress = onProgress,
        label = "Compositing picture-in-picture…"
    ) { video, overlay, output ->
        val xy = when (position) {
            PipPosition.TOP_LEFT -> "20:20"
            PipPosition.TOP_RIGHT -> "W-w-20:20"
            PipPosition.BOTTOM_LEFT -> "20:H-h-20"
            PipPosition.BOTTOM_RIGHT -> "W-w-20:H-h-20"
        }
        arrayOf(
            "-i", video.absolutePath,
            "-i", overlay.absolutePath,
            "-filter_complex",
            "[1:v]scale=iw/3:ih/3[pip];[0:v][pip]overlay=$xy[out]",
            "-map", "[out]",
            "-map", "0:a?",
            "-c:v", "mpeg4",
            "-q:v", "5",
            "-c:a", "aac",
            "-movflags", "+faststart",
            output.absolutePath,
            "-y"
        )
    }
}
