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
import androidx.compose.runtime.remember
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
import com.robin.tools.feature.media.ui.viewmodel.FrameExtractViewModel

private val FRAME_COUNTS = listOf(5, 10, 20)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FrameExtractScreen(
    viewModel: FrameExtractViewModel,
    onBack: () -> Unit,
    initialUri: String? = null
) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? -> uri?.let(viewModel::selectVideo) }

    LaunchedEffect(initialUri) {
        initialUri?.let { viewModel.selectVideo(Uri.parse(it)) }
    }
    LaunchedEffect(state.message) {
        state.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    Scaffold(
        topBar = {
            ToolsTopAppBar(
                title = stringResource(R.string.frame_extract),
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
                text = stringResource(R.string.frame_extract_desc),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            StudioActionButton(
                label = stringResource(R.string.select_file),
                onClick = { picker.launch("video/*") },
                style = StudioActionStyle.SECONDARY,
                enabled = !state.isExtracting
            )
            StudioSurface(tone = StudioSurfaceTone.STANDARD) {
                Text(
                    text = state.sourceUri?.lastPathSegment ?: "—",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            StudioSectionHeader(
                eyebrow = stringResource(R.string.frame_extract_count),
                title = stringResource(R.string.frame_extract_count)
            )
            StudioSelectionRow(
                options = FRAME_COUNTS.map {
                    StudioSelectionOption(it.toString(), it.toString())
                },
                selectedKey = state.frameCount.toString(),
                onOptionSelected = { viewModel.setFrameCount(it.toInt()) },
                enabled = !state.isExtracting
            )
            StudioActionButton(
                label = stringResource(R.string.frame_extract_action),
                onClick = viewModel::extract,
                loading = state.isExtracting,
                enabled = state.sourceUri != null && !state.isExtracting
            )
            if (state.isExtracting) {
                LinearProgressIndicator(
                    progress = { state.progress },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            if (state.saved.isNotEmpty()) {
                StudioSurface(tone = StudioSurfaceTone.EMPHASIZED) {
                    Text(
                        text = stringResource(R.string.frame_extract_saved, state.saved.size),
                        style = MaterialTheme.typography.titleSmall
                    )
                    state.saved.forEach { media ->
                        Text(
                            text = media.displayName,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            Spacer(Modifier.height(Dimension.lg))
        }
    }
}
