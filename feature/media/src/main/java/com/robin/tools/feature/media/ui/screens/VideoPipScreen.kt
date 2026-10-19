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
import com.robin.tools.feature.media.delegate.PipPosition
import com.robin.tools.feature.media.ui.viewmodel.CompressionViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoPipScreen(
    viewModel: CompressionViewModel,
    onBack: () -> Unit,
    initialMainUri: String? = null
) {
    val context = LocalContext.current
    val taskState by viewModel.taskState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var mainUri by remember { mutableStateOf<Uri?>(null) }
    var overlayUri by remember { mutableStateOf<Uri?>(null) }
    var position by remember { mutableStateOf(PipPosition.TOP_RIGHT) }
    var started by remember { mutableStateOf(false) }

    val mainPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { mainUri = it }
    val overlayPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { overlayUri = it }

    LaunchedEffect(initialMainUri) {
        initialMainUri?.let { mainUri = Uri.parse(it) }
    }
    LaunchedEffect(taskState) {
        val state = taskState
        if (state is CompressionTaskState.Finished && started) {
            started = false
            snackbarHostState.showSnackbar(
                if (state.isSuccess) {
                    context.getString(R.string.video_pip_saved)
                } else {
                    context.getString(R.string.video_pip_failed, state.message)
                }
            )
            viewModel.resetState()
        }
    }

    Scaffold(
        topBar = {
            ToolsTopAppBar(
                title = stringResource(R.string.video_pip),
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
                text = stringResource(R.string.video_pip_desc),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            StudioActionButton(
                label = stringResource(R.string.video_pip_select_main),
                onClick = { mainPicker.launch("video/*") },
                style = StudioActionStyle.SECONDARY,
                enabled = taskState !is CompressionTaskState.Processing
            )
            StudioActionButton(
                label = stringResource(R.string.video_pip_select_overlay),
                onClick = { overlayPicker.launch("video/*") },
                style = StudioActionStyle.SECONDARY,
                enabled = taskState !is CompressionTaskState.Processing
            )
            StudioSurface(tone = StudioSurfaceTone.STANDARD) {
                Text(
                    text = buildString {
                        append(mainUri?.lastPathSegment ?: "—")
                        append("  +  ")
                        append(overlayUri?.lastPathSegment ?: "—")
                    },
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            StudioSectionHeader(
                eyebrow = stringResource(R.string.video_pip_position),
                title = stringResource(R.string.video_pip_position)
            )
            StudioSelectionRow(
                options = listOf(
                    StudioSelectionOption(PipPosition.TOP_LEFT.name, stringResource(R.string.video_pip_tl)),
                    StudioSelectionOption(PipPosition.TOP_RIGHT.name, stringResource(R.string.video_pip_tr)),
                    StudioSelectionOption(PipPosition.BOTTOM_LEFT.name, stringResource(R.string.video_pip_bl)),
                    StudioSelectionOption(PipPosition.BOTTOM_RIGHT.name, stringResource(R.string.video_pip_br))
                ),
                selectedKey = position.name,
                onOptionSelected = { key ->
                    PipPosition.entries.firstOrNull { it.name == key }?.let { position = it }
                },
                enabled = taskState !is CompressionTaskState.Processing
            )
            StudioActionButton(
                label = stringResource(R.string.video_pip_action),
                onClick = {
                    val main = mainUri ?: return@StudioActionButton
                    val overlay = overlayUri ?: return@StudioActionButton
                    started = true
                    viewModel.startVideoPip(main, overlay, position.name)
                },
                loading = taskState is CompressionTaskState.Processing,
                enabled = mainUri != null && overlayUri != null &&
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
