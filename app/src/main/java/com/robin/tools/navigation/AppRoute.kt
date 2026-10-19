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
    data class Compression(
        val type: CompressionType,
        val initialUri: String? = null
    ) : AppRoute

    @Serializable
    data object Ebook : AppRoute

    @Serializable
    data object LightLux : AppRoute

    @Serializable
    data object FaceCompare : AppRoute

    @Serializable
    data class FaceBlur(val initialUri: String? = null) : AppRoute

    @Serializable
    data class FaceIdPhoto(val initialUri: String? = null) : AppRoute

    @Serializable
    data object FaceGroup : AppRoute

    @Serializable
    data object StorageBench : AppRoute

    @Serializable
    data object StorageCleanup : AppRoute

    // Camera feature routes
    @Serializable
    data object CameraMain : AppRoute

    @Serializable
    data object CameraRecord : AppRoute

    @Serializable
    data object CameraPhoto : AppRoute

    @Serializable
    data object TextToVideo : AppRoute

    @Serializable
    data object Slideshow : AppRoute

    @Serializable
    data class VideoEdit(val videoPath: String) : AppRoute

    @Serializable
    data class VideoTrim(val videoPath: String) : AppRoute

    @Serializable
    data class CoverSelect(val videoPath: String) : AppRoute

    // Media tool routes — filter
    @Serializable
    data object Filter : AppRoute

    @Serializable
    data class AudioTrim(val initialUri: String? = null) : AppRoute

    @Serializable
    data class VideoCustomCompress(val initialUri: String? = null) : AppRoute

    @Serializable
    data class AudioConcat(val initialUri: String? = null) : AppRoute

    @Serializable
    data class VideoBgm(val initialVideoUri: String? = null) : AppRoute

    @Serializable
    data class VideoPip(val initialMainUri: String? = null) : AppRoute

    @Serializable
    data class VideoSpeedCustom(val initialUri: String? = null) : AppRoute

    @Serializable
    data class FrameExtract(val initialUri: String? = null) : AppRoute

    @Serializable
    data object AudioRecorder : AppRoute

    // Toolkit routes
    @Serializable
    data class ImageTool(
        val toolKey: String,
        val initialUri: String? = null
    ) : AppRoute

    @Serializable
    data class QrTool(
        val initialText: String? = null,
        val initialUri: String? = null
    ) : AppRoute

    @Serializable
    data class OcrTool(val initialUri: String? = null) : AppRoute

    @Serializable
    data class DocumentScanner(val initialUri: String? = null) : AppRoute

    // App-level routes
    @Serializable
    data object TaskHistory : AppRoute

    @Serializable
    data class ShareEntry(
        val uri: String? = null,
        val mimeType: String? = null,
        val text: String? = null
    ) : AppRoute
}
