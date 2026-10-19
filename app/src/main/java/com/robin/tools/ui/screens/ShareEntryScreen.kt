package com.robin.tools.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.robin.tools.R
import com.robin.tools.core.ui.Dimension
import com.robin.tools.core.ui.EmptyState
import com.robin.tools.core.ui.FeatureCard
import com.robin.tools.core.ui.StudioSectionHeader
import com.robin.tools.core.ui.ToolsTopAppBar
import com.robin.tools.feature.media.data.CompressionType
import com.robin.tools.navigation.AppRoute
import com.robin.tools.tool.ToolEntry
import com.robin.tools.tool.ToolRegistry

private enum class SharedKind { IMAGE, VIDEO, AUDIO, TEXT, OTHER }

/**
 * Entry point for content shared into the app from other applications.
 *
 * The list is derived from [ToolRegistry], so any new image/video tool appears
 * here automatically once its route is handled below.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareEntryScreen(
    uri: String?,
    mimeType: String?,
    text: String?,
    onBack: () -> Unit,
    onCompression: (type: CompressionType, initialUri: String?) -> Unit,
    onImageTool: (toolKey: String, initialUri: String?) -> Unit,
    onQr: (initialText: String?, initialUri: String?) -> Unit,
    onOcr: (initialUri: String?) -> Unit,
    onAudioTrim: (initialUri: String?) -> Unit,
    onFaceBlur: (initialUri: String?) -> Unit,
    onFaceIdPhoto: (initialUri: String?) -> Unit,
    onDocumentScanner: (initialUri: String?) -> Unit,
    onVideoCustomCompress: (initialUri: String?) -> Unit,
    onAudioConcat: (initialUri: String?) -> Unit,
    onVideoBgm: (initialVideoUri: String?) -> Unit,
    onVideoPip: (initialMainUri: String?) -> Unit,
    onVideoSpeedCustom: (initialUri: String?) -> Unit,
    onFrameExtract: (initialUri: String?) -> Unit
) {
    val kind = when {
        mimeType?.startsWith("image/") == true -> SharedKind.IMAGE
        mimeType?.startsWith("video/") == true -> SharedKind.VIDEO
        mimeType?.startsWith("audio/") == true -> SharedKind.AUDIO
        !text.isNullOrBlank() -> SharedKind.TEXT
        else -> SharedKind.OTHER
    }
    val tools = toolsFor(kind)

    Scaffold(
        topBar = {
            ToolsTopAppBar(
                title = stringResource(R.string.share_title),
                onBack = onBack,
                backContentDescription = stringResource(R.string.app_back)
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Dimension.pageHorizontal, vertical = Dimension.lg),
            verticalArrangement = Arrangement.spacedBy(Dimension.md)
        ) {
            StudioSectionHeader(
                title = stringResource(R.string.share_title),
                description = stringResource(subtitleRes(kind))
            )
            if (tools.isEmpty()) {
                EmptyState(
                    icon = Icons.Default.Share,
                    title = stringResource(R.string.share_no_tools),
                    description = stringResource(R.string.share_subtitle_other)
                )
            } else {
                tools.forEach { tool ->
                    FeatureCard(
                        title = stringResource(tool.titleRes),
                        description = stringResource(tool.descriptionRes),
                        icon = tool.icon,
                        onClick = {
                            when (val route = tool.route) {
                                is AppRoute.Compression ->
                                    onCompression(route.type, uri)
                                is AppRoute.ImageTool ->
                                    onImageTool(route.toolKey, uri)
                                is AppRoute.QrTool ->
                                    onQr(text, uri)
                                is AppRoute.OcrTool ->
                                    onOcr(uri)
                                is AppRoute.AudioTrim ->
                                    onAudioTrim(uri)
                                is AppRoute.FaceBlur ->
                                    onFaceBlur(uri)
                                is AppRoute.FaceIdPhoto ->
                                    onFaceIdPhoto(uri)
                                is AppRoute.DocumentScanner ->
                                    onDocumentScanner(uri)
                                is AppRoute.VideoCustomCompress ->
                                    onVideoCustomCompress(uri)
                                is AppRoute.AudioConcat ->
                                    onAudioConcat(uri)
                                is AppRoute.VideoBgm ->
                                    onVideoBgm(uri)
                                is AppRoute.VideoPip ->
                                    onVideoPip(uri)
                                is AppRoute.VideoSpeedCustom ->
                                    onVideoSpeedCustom(uri)
                                is AppRoute.FrameExtract ->
                                    onFrameExtract(uri)
                                else -> Unit
                            }
                        }
                    )
                }
            }
            Spacer(Modifier.height(Dimension.lg))
        }
    }
}

private fun subtitleRes(kind: SharedKind): Int = when (kind) {
    SharedKind.IMAGE -> R.string.share_subtitle_image
    SharedKind.VIDEO -> R.string.share_subtitle_video
    SharedKind.AUDIO -> R.string.share_subtitle_audio
    SharedKind.TEXT -> R.string.share_subtitle_text
    SharedKind.OTHER -> R.string.share_subtitle_other
}

private fun toolsFor(kind: SharedKind): List<ToolEntry> {
    val ids = when (kind) {
        SharedKind.IMAGE -> listOf(
            "image_convert", "image_resize", "image_rotate", "image_exif",
            "image_pdf", "image_compress", "face_blur", "face_id_photo", "document_scanner", "qr", "ocr"
        )
        SharedKind.VIDEO -> listOf(
            "video_compress", "video_custom", "video_bgm", "video_pip", "video_speed_custom",
            "frame_extract", "gif", "transcode", "speed", "reverse",
            "crop", "volume", "strip_audio", "extract_audio"
        )
        SharedKind.AUDIO -> listOf("audio_trim", "audio_concat")
        SharedKind.TEXT -> listOf("qr")
        SharedKind.OTHER -> emptyList()
    }
    return ids.mapNotNull(ToolRegistry::byId)
}
