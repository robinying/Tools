package com.robin.tools.navigation

import com.robin.tools.feature.media.data.CompressionType
import kotlinx.serialization.Serializable

@Serializable
sealed interface AppRoute {
    @Serializable
    data object Home : AppRoute

    @Serializable
    data object MediaMain : AppRoute

    @Serializable
    data class Compression(val type: CompressionType) : AppRoute

    @Serializable
    data object Ebook : AppRoute

    @Serializable
    data object LightLux : AppRoute

    @Serializable
    data object FaceCompare : AppRoute

    // Camera feature routes
    @Serializable
    data object CameraMain : AppRoute

    @Serializable
    data object CameraRecord : AppRoute

    @Serializable
    data class VideoEdit(val videoPath: String) : AppRoute

    @Serializable
    data class VideoTrim(val videoPath: String) : AppRoute

    @Serializable
    data object CoverSelect(val videoPath: String) : AppRoute

    // Media tool routes — filter
    @Serializable
    data object Filter : AppRoute
}
