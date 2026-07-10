package com.robin.tools.feature.media.data

/**
 * Represents the state of an ongoing or completed filter operation.
 *
 * Mirrors the [CompressionTaskState] pattern used across the media module.
 * Note: [Bitmap] is NOT held in state — the caller owns and manages it.
 */
sealed class FilterState {
    /** No filter operation is active. UI resets to picker state. */
    data object Idle : FilterState()

    /**
     * A filter is currently being applied.
     *
     * @param progress 0.0f to 1.0f completion ratio.
     * @param message  Human-readable status label.
     */
    data class Processing(
        val progress: Float,
        val message: String
    ) : FilterState()

    /**
     * The filter operation has finished (success or failure).
     * Does NOT hold the Bitmap — caller retains ownership.
     */
    data class Finished(
        val isSuccess: Boolean,
        val message: String
    ) : FilterState()
}
