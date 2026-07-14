package com.robin.tools.feature.media.data

import android.net.Uri
import androidx.annotation.StringRes
import com.robin.tools.feature.media.R
import kotlinx.serialization.Serializable

@Serializable
enum class CompressionType {
    VIDEO,
    IMAGE,
    GIF,
    /** Extract audio from video → M4A */
    EXTRACT_AUDIO,
    /** Remove audio track from video */
    STRIP_AUDIO,
    /** Transcode to MP4 */
    TRANSCODE,
    /** Speed change: 0.5x / 1.5x / 2x */
    SPEED,
    /** Reverse video */
    REVERSE,
    /** Concatenate multiple videos */
    CONCAT,
    /** Center crop to 1:1 / 9:16 / 16:9 */
    CROP,
    /** Volume + fade in/out */
    VOLUME_FADE
}

enum class CompressionLevel(@StringRes val labelRes: Int) {
    LOW(R.string.compression_level_low),
    MEDIUM(R.string.compression_level_medium),
    HIGH(R.string.compression_level_high)
}

sealed class CompressionTaskState {
    object Idle : CompressionTaskState()
    data class Processing(
        val progress: Float,
        val message: String,
        val currentFile: Int,
        val totalFiles: Int
    ) : CompressionTaskState()
    data class Finished(
        val isSuccess: Boolean,
        val message: String,
        val outputUri: String? = null
    ) : CompressionTaskState()
}
