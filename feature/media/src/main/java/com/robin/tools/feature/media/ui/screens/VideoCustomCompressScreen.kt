package com.robin.tools.feature.media.ui.screens

import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.robin.tools.core.ui.Dimension
import com.robin.tools.core.ui.EmptyState
import com.robin.tools.core.ui.StudioActionButton
import com.robin.tools.core.ui.StudioActionStyle
import com.robin.tools.core.ui.StudioSectionHeader
import com.robin.tools.core.ui.StudioSelectionOption
import com.robin.tools.core.ui.StudioSelectionRow
import com.robin.tools.core.ui.StudioSurface
import com.robin.tools.core.ui.StudioSurfaceTone
import com.robin.tools.core.ui.ToolsTopAppBar
import com.robin.tools.feature.media.R
import com.robin.tools.feature.media.data.CompressionTaskState
import com.robin.tools.feature.media.ui.viewmodel.CompressionViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class VideoInfo(val durationMs: Long, val width: Int, val height: Int)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoCustomCompressScreen(
    onBack: () -> Unit,
    viewModel: CompressionViewModel,
    initialUri: String? = null
) {
    val context = LocalContext.current
    val taskState by viewModel.taskState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var selectedUri by remember { mutableStateOf<Uri?>(initialUri?.let(Uri::parse)) }
    var videoInfo by remember { mutableStateOf<VideoInfo?>(null) }
    var loadingInfo by remember { mutableStateOf(false) }
    var minDimension by remember { mutableIntStateOf(720) }
    var bitrateKbps by remember { mutableIntStateOf(2000) }
    var started by remember { mutableStateOf(false) }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            selectedUri = it
            scope.launch {
                loadingInfo = true
                videoInfo = withContext(Dispatchers.IO) { readVideoInfo(context, it) }
                loadingInfo = false
            }
        }
    }

    LaunchedEffect(initialUri) {
        val parsed = initialUri?.let(Uri::parse) ?: return@LaunchedEffect
        scope.launch {
            loadingInfo = true
            videoInfo = withContext(Dispatchers.IO) { readVideoInfo(context, parsed) }
            loadingInfo = false
        }
    }

    LaunchedEffect(taskState) {
        val state = taskState
        if (state is CompressionTaskState.Finished && started) {
            started = false
            snackbarHostState.showSnackbar(
                if (state.isSuccess) {
                    context.getString(R.string.video_custom_saved)
                } else {
                    context.getString(R.string.video_custom_failed, state.message)
                }
            )
            viewModel.resetState()
        }
    }

    Scaffold(
        topBar = {
            ToolsTopAppBar(
                title = stringResource(R.string.video_custom),
                onBack = onBack,
                backContentDescription = stringResource(R.string.back)
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Dimension.pageHorizontal, vertical = Dimension.lg),
            verticalArrangement = Arrangement.spacedBy(Dimension.lg)
        ) {
            Text(
                text = stringResource(R.string.video_custom_desc),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (selectedUri == null) {
                EmptyState(
                    icon = Icons.Default.VideoLibrary,
                    title = stringResource(R.string.video_custom_no_file),
                    actionLabel = stringResource(R.string.video_custom_select),
                    onAction = { picker.launch("video/*") }
                )
            } else {
                StudioActionButton(
                    label = stringResource(R.string.video_custom_select),
                    onClick = { picker.launch("video/*") },
                    style = StudioActionStyle.SECONDARY,
                    enabled = taskState !is CompressionTaskState.Processing
                )

                if (loadingInfo) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                } else {
                    videoInfo?.let { info ->
                        StudioSurface(tone = StudioSurfaceTone.STANDARD) {
                            Text(
                                text = stringResource(
                                    R.string.video_custom_info,
                                    info.width,
                                    info.height,
                                    info.durationMs / 1000
                                ),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }

                StudioSectionHeader(
                    eyebrow = stringResource(R.string.video_custom_settings_eyebrow),
                    title = stringResource(R.string.video_custom_settings_title)
                )
                Text(
                    text = stringResource(R.string.video_custom_resolution),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                StudioSelectionRow(
                    options = listOf(480, 720, 1080).map {
                        StudioSelectionOption(it.toString(), "${it}p")
                    },
                    selectedKey = minDimension.toString(),
                    onOptionSelected = { minDimension = it.toInt() },
                    enabled = taskState !is CompressionTaskState.Processing
                )
                Text(
                    text = stringResource(R.string.video_custom_bitrate, bitrateKbps),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Slider(
                    value = bitrateKbps.toFloat(),
                    onValueChange = { bitrateKbps = (it / 250f).toInt() * 250 },
                    valueRange = 500f..8000f,
                    steps = 29,
                    enabled = taskState !is CompressionTaskState.Processing
                )

                StudioActionButton(
                    label = stringResource(R.string.video_custom_action),
                    onClick = {
                        started = true
                        viewModel.startCustomVideoCompression(
                            selectedUri!!,
                            minDimension,
                            bitrateKbps
                        )
                    },
                    loading = taskState is CompressionTaskState.Processing,
                    enabled = taskState !is CompressionTaskState.Processing
                )

                (taskState as? CompressionTaskState.Processing)?.let { processing ->
                    LinearProgressIndicator(
                        progress = { processing.progress },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        text = processing.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.height(Dimension.lg))
        }
    }
}

private fun readVideoInfo(context: android.content.Context, uri: Uri): VideoInfo? {
    val retriever = MediaMetadataRetriever()
    return try {
        retriever.setDataSource(context, uri)
        val duration = retriever
            .extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            ?.toLongOrNull() ?: 0L
        var width = retriever
            .extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
            ?.toIntOrNull() ?: 0
        var height = retriever
            .extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
            ?.toIntOrNull() ?: 0
        val rotation = retriever
            .extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)
            ?.toIntOrNull() ?: 0
        if (rotation == 90 || rotation == 270) {
            val swap = width
            width = height
            height = swap
        }
        VideoInfo(duration, width, height)
    } catch (e: Exception) {
        null
    } finally {
        runCatching { retriever.release() }
    }
}
