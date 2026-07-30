package com.robin.tools.feature.camera.ui.record

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.SwitchCamera
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.robin.tools.core.ui.TextOptionChip
import com.robin.tools.feature.camera.R
import com.robin.tools.feature.camera.filter.FilterType
import com.robin.tools.feature.camera.filter.stringRes
import com.robin.tools.feature.camera.opengl.CameraGlSurfaceView

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordScreen(
    onBack: () -> Unit,
    onRecordingComplete: (String) -> Unit,
    viewModel: RecordViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val configuration = LocalConfiguration.current

    // Keep preview upright if display rotation changes while recording UI is open.
    LaunchedEffect(configuration.orientation) {
        viewModel.refreshDisplayOrientation()
    }

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {

        // Camera preview
        AndroidView(
            factory = { ctx ->
                CameraGlSurfaceView(ctx).apply {
                    setRenderer(viewModel.renderer)
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // Top bar
        Row(
            modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 48.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.record_back),
                    tint = MaterialTheme.colorScheme.onBackground
                )
            }
            IconButton(
                onClick = { viewModel.switchCamera() },
                enabled = !uiState.isSwitchingCamera && !uiState.isRecording && !uiState.isCountingDown
            ) {
                if (uiState.isSwitchingCamera) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                } else {
                    Icon(
                        Icons.Default.SwitchCamera,
                        contentDescription = stringResource(R.string.record_flip_camera),
                        tint = MaterialTheme.colorScheme.onBackground
                    )
                }
            }
        }

        // Segment progress
        if (uiState.segmentCount > 0) {
            Row(
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 100.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                repeat(uiState.segmentCount) {
                    Box(
                        modifier = Modifier.width(20.dp).height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(MaterialTheme.colorScheme.tertiary)
                    )
                }
            }
        }

        // Countdown overlay
        AnimatedVisibility(
            visible = uiState.isCountingDown,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.Center)
        ) {
            Text(
                text = if (uiState.countdownValue > 0) {
                    uiState.countdownValue.toString()
                } else {
                    stringResource(R.string.record_countdown_go)
                },
                style = MaterialTheme.typography.displayLarge,
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        // Processing overlay
        if (uiState.isProcessing) {
            Box(
                modifier = Modifier.align(Alignment.Center).fillMaxSize()
                    .background(MaterialTheme.colorScheme.background.copy(alpha = 0.7f)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.onBackground)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        stringResource(R.string.record_processing),
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }
            }
        }

        // Bottom controls
        Row(
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .padding(bottom = 48.dp, start = 24.dp, end = 24.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Delete segment
            IconButton(
                onClick = { viewModel.deleteLastSegment() },
                enabled = uiState.segmentCount > 0 && !uiState.isRecording
            ) {
                Icon(
                    Icons.Default.Undo,
                    stringResource(R.string.record_delete_segment),
                    tint = if (uiState.segmentCount > 0) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.3f),
                    modifier = Modifier.size(32.dp)
                )
            }

            // Record button
            Box(
                modifier = Modifier.size(80.dp).clip(CircleShape)
                    .background(MaterialTheme.colorScheme.onBackground.copy(alpha = 0.3f))
                    .clickable {
                        if (uiState.isRecording) viewModel.stopRecording()
                        else viewModel.startRecording()
                    },
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(if (uiState.isRecording) 32.dp else 60.dp)
                        .clip(if (uiState.isRecording) RoundedCornerShape(6.dp) else CircleShape)
                        .background(MaterialTheme.colorScheme.error)
                )
            }

            // Done
            IconButton(
                onClick = { viewModel.finishRecording { path -> onRecordingComplete(path) } },
                enabled = uiState.segmentCount > 0 && !uiState.isRecording
            ) {
                Icon(
                    Icons.Default.Check,
                    stringResource(R.string.record_done),
                    tint = if (uiState.segmentCount > 0) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.3f),
                    modifier = Modifier.size(32.dp)
                )
            }
        }

        // Filter selector - horizontal row at bottom
        FilterSwipeSelector(
            currentFilter = uiState.currentFilter,
            onFilterChanged = { viewModel.setFilter(it) },
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 130.dp)
        )

        // Error
        uiState.error?.let { error ->
            Snackbar(
                modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp)
            ) {
                Text(error)
            }
        }
    }
}

@Composable
fun FilterSwipeSelector(
    currentFilter: FilterType,
    onFilterChanged: (FilterType) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyRow(
        modifier = modifier.fillMaxWidth().padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        items(FilterType.entries) { filter ->
            TextOptionChip(
                selected = filter == currentFilter,
                onClick = { onFilterChanged(filter) },
                label = stringResource(filter.stringRes())
            )
        }
    }
}
