package com.robin.tools.feature.media.ui.screens

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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.robin.tools.core.ui.Dimension
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoBgmScreen(
    viewModel: CompressionViewModel,
    onBack: () -> Unit,
    initialVideoUri: String? = null
) {
    val context = LocalContext.current
    val taskState by viewModel.taskState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var videoUri by remember { mutableStateOf<Uri?>(null) }
    var audioUri by remember { mutableStateOf<Uri?>(null) }
    var mix by remember { mutableStateOf(true) }
    var started by remember { mutableStateOf(false) }

    val videoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { videoUri = it }
    val audioPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { audioUri = it }

    LaunchedEffect(initialVideoUri) {
        initialVideoUri?.let { videoUri = Uri.parse(it) }
    }
    LaunchedEffect(taskState) {
        val state = taskState
        if (state is CompressionTaskState.Finished && started) {
            started = false
            snackbarHostState.showSnackbar(
                if (state.isSuccess) {
                    context.getString(R.string.video_bgm_saved)
                } else {
                    context.getString(R.string.video_bgm_failed, state.message)
                }
            )
            viewModel.resetState()
        }
    }

    Scaffold(
        topBar = {
            ToolsTopAppBar(
                title = stringResource(R.string.video_bgm),
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
                text = stringResource(R.string.video_bgm_desc),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            StudioActionButton(
                label = stringResource(R.string.video_bgm_select_video),
                onClick = { videoPicker.launch("video/*") },
                style = StudioActionStyle.SECONDARY,
                enabled = taskState !is CompressionTaskState.Processing
            )
            StudioActionButton(
                label = stringResource(R.string.video_bgm_select_audio),
                onClick = { audioPicker.launch("audio/*") },
                style = StudioActionStyle.SECONDARY,
                enabled = taskState !is CompressionTaskState.Processing
            )
            StudioSurface(tone = StudioSurfaceTone.STANDARD) {
                Text(
                    text = buildString {
                        append(videoUri?.lastPathSegment ?: "—")
                        append("  +  ")
                        append(audioUri?.lastPathSegment ?: "—")
                    },
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            StudioSectionHeader(
                eyebrow = stringResource(R.string.video_bgm_mode),
                title = stringResource(R.string.video_bgm_mode)
            )
            StudioSelectionRow(
                options = listOf(
                    StudioSelectionOption("mix", stringResource(R.string.video_bgm_mix)),
                    StudioSelectionOption("replace", stringResource(R.string.video_bgm_replace))
                ),
                selectedKey = if (mix) "mix" else "replace",
                onOptionSelected = { mix = it == "mix" },
                enabled = taskState !is CompressionTaskState.Processing
            )
            StudioActionButton(
                label = stringResource(R.string.video_bgm_action),
                onClick = {
                    val video = videoUri ?: return@StudioActionButton
                    val audio = audioUri ?: return@StudioActionButton
                    started = true
                    viewModel.startVideoBgm(video, audio, mix)
                },
                loading = taskState is CompressionTaskState.Processing,
                enabled = videoUri != null && audioUri != null &&
                    taskState !is CompressionTaskState.Processing
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
            Spacer(Modifier.height(Dimension.lg))
        }
    }
}
