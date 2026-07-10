package com.robin.tools.feature.media.data

import android.graphics.Bitmap

/**
 * Represents the state of an ongoing or completed filter operation.
 *
 * Mirrors the [CompressionTaskState] pattern used across the media module.
 */
sealed class FilterState {
    /** No filter operation is active. UI resets to picker state. */
    data object Idle : FilterState()

    /**
     * A filter is currently being applied.
     *
     * @param progress 0.0f to 1.0f completion ratio.
     * @param message  Human-readable status label (e.g. "正在处理...").
     */
    data class Processing(
        val progress: Float,
        val message: String
    ) : FilterState()

    /**
     * The filter operation has finished (success or failure).
     *
     * @param isSuccess Whether the operation produced a valid result.
     * @param message   Human-readable outcome label.
     * @param result    The filtered bitmap on success, null on failure.
     */
    data class Finished(
        val isSuccess: Boolean,
        val message: String,
        val result: Bitmap? = null
    ) : FilterState()
}
