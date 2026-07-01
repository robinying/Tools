package com.robin.tools.feature.camera.encoder

sealed class EncoderState {
    data object Idle : EncoderState()
    data object Preparing : EncoderState()
    data object Recording : EncoderState()
    data object Paused : EncoderState()
    data object Stopping : EncoderState()
    data class Error(val message: String) : EncoderState()
}
