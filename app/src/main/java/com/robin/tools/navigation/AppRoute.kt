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
}
