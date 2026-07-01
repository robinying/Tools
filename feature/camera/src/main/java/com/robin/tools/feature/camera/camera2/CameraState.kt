package com.robin.tools.feature.camera.camera2

enum class CameraFacing { FRONT, BACK }

data class CameraState(
    val isOpen: Boolean = false,
    val facing: CameraFacing = CameraFacing.BACK,
    val isRecording: Boolean = false,
    val currentFilter: com.robin.tools.feature.camera.filter.FilterType = com.robin.tools.feature.camera.filter.FilterType.NONE,
    val error: String? = null
)
