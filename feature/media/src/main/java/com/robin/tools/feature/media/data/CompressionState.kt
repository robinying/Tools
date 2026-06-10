package com.robin.tools.feature.media.data

import android.net.Uri
import androidx.annotation.StringRes
import com.robin.tools.feature.media.R

enum class CompressionType {
    VIDEO, IMAGE, GIF
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
