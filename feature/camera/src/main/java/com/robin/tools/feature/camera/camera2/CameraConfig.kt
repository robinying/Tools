package com.robin.tools.feature.camera.camera2

data class CameraConfig(
    val videoWidth: Int = 720,
    val videoHeight: Int = 1280,
    val videoBitRate: Int = 6_000_000,
    val maxSegmentDurationMs: Long = 30_000L
)
