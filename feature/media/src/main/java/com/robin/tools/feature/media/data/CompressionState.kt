package com.robin.tools.feature.media.data

import android.net.Uri

enum class CompressionType {
    VIDEO, IMAGE, GIF
}

enum class CompressionLevel(val labelResId: Int) {
    LOW(com.robin.videoeditor.R.string.compression_level_low),
    MEDIUM(com.robin.videoeditor.R.string.compression_level_medium),
    HIGH(com.robin.videoeditor.R.string.compression_level_high)
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
