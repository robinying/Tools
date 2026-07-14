package com.robin.tools.feature.media.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.CallMerge
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Slideshow
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Transform
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.robin.tools.core.ui.Dimension
import com.robin.tools.core.ui.FeatureCard
import com.robin.tools.core.ui.ToolsTopAppBar
import com.robin.tools.feature.media.R
import com.robin.tools.feature.media.utils.FileUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    onBack: () -> Unit = {},
    onVideoCompressClick: () -> Unit,
    onImageCompressClick: () -> Unit,
    onGifConvertClick: () -> Unit,
    onFilterClick: () -> Unit,
    onExtractAudioClick: () -> Unit = {},
    onStripAudioClick: () -> Unit = {},
    onTranscodeClick: () -> Unit = {},
    onSpeedClick: () -> Unit = {},
    onReverseClick: () -> Unit = {},
    onConcatClick: () -> Unit = {},
    onCropClick: () -> Unit = {},
    onVolumeFadeClick: () -> Unit = {},
) {
    val context = LocalContext.current
    Scaffold(
        topBar = {
            ToolsTopAppBar(
                title = context.getString(R.string.media_compression_tool),
                onBack = onBack,
                backContentDescription = context.getString(R.string.back)
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(Dimension.lg),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            FeatureCard(
                title = context.getString(R.string.video_compress),
                description = context.getString(R.string.video_compress_desc),
                icon = Icons.Default.VideoLibrary,
                onClick = onVideoCompressClick
            )
            Spacer(Modifier.height(Dimension.md))

            FeatureCard(
                title = context.getString(R.string.image_compress),
                description = context.getString(R.string.image_compress_desc),
                icon = Icons.Default.Image,
                onClick = onImageCompressClick
            )
            Spacer(Modifier.height(Dimension.md))

            FeatureCard(
                title = context.getString(R.string.video_to_gif),
                description = context.getString(R.string.video_to_gif_desc),
                icon = Icons.Default.Slideshow,
                onClick = onGifConvertClick
            )
            Spacer(Modifier.height(Dimension.md))

            FeatureCard(
                title = context.getString(R.string.extract_audio),
                description = context.getString(R.string.extract_audio_desc),
                icon = Icons.Default.AudioFile,
                onClick = onExtractAudioClick
            )
            Spacer(Modifier.height(Dimension.md))

            FeatureCard(
                title = context.getString(R.string.strip_audio),
                description = context.getString(R.string.strip_audio_desc),
                icon = Icons.Default.VolumeOff,
                onClick = onStripAudioClick
            )
            Spacer(Modifier.height(Dimension.md))

            FeatureCard(
                title = context.getString(R.string.transcode_mp4),
                description = context.getString(R.string.transcode_mp4_desc),
                icon = Icons.Default.Transform,
                onClick = onTranscodeClick
            )
            Spacer(Modifier.height(Dimension.md))

            FeatureCard(
                title = context.getString(R.string.speed_change),
                description = context.getString(R.string.speed_change_desc),
                icon = Icons.Default.Speed,
                onClick = onSpeedClick
            )
            Spacer(Modifier.height(Dimension.md))

            FeatureCard(
                title = context.getString(R.string.reverse_video),
                description = context.getString(R.string.reverse_video_desc),
                icon = Icons.Default.Replay,
                onClick = onReverseClick
            )
            Spacer(Modifier.height(Dimension.md))

            FeatureCard(
                title = context.getString(R.string.concat_video),
                description = context.getString(R.string.concat_video_desc),
                icon = Icons.Default.CallMerge,
                onClick = onConcatClick
            )
            Spacer(Modifier.height(Dimension.md))

            FeatureCard(
                title = context.getString(R.string.crop_aspect),
                description = context.getString(R.string.crop_aspect_desc),
                icon = Icons.Default.Crop,
                onClick = onCropClick
            )
            Spacer(Modifier.height(Dimension.md))

            FeatureCard(
                title = context.getString(R.string.volume_fade),
                description = context.getString(R.string.volume_fade_desc),
                icon = Icons.Default.VolumeUp,
                onClick = onVolumeFadeClick
            )
            Spacer(Modifier.height(Dimension.md))

            FeatureCard(
                title = context.getString(R.string.image_filter),
                description = context.getString(R.string.image_filter_desc),
                icon = Icons.Default.FilterAlt,
                onClick = onFilterClick
            )

            Spacer(Modifier.height(Dimension.xl))

            TextButton(
                onClick = {
                    FileUtils.clearCache(context)
                    android.widget.Toast.makeText(
                        context,
                        context.getString(R.string.cache_cleared),
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                }
            ) {
                Text(
                    context.getString(R.string.clear_cache),
                    color = MaterialTheme.colorScheme.secondary
                )
            }
        }
    }
}
