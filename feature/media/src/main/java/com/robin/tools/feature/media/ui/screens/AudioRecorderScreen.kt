package com.robin.tools.feature.media.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.robin.tools.core.ui.Dimension
import com.robin.tools.core.ui.StudioActionButton
import com.robin.tools.core.ui.StudioActionStyle
import com.robin.tools.core.ui.StudioSurface
import com.robin.tools.core.ui.StudioSurfaceTone
import com.robin.tools.core.ui.ToolsTopAppBar
import com.robin.tools.feature.media.R
import com.robin.tools.feature.media.ui.viewmodel.AudioRecorderViewModel
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioRecorderScreen(
    viewModel: AudioRecorderViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasPermission = granted
        if (granted) viewModel.start()
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
                title = stringResource(R.string.audio_recorder),
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
                text = stringResource(R.string.audio_recorder_desc),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (!hasPermission) {
                Text(
                    text = stringResource(R.string.audio_recorder_permission),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
            }
            StudioSurface(tone = StudioSurfaceTone.OUTLINED) {
                Text(
                    text = formatElapsed(state.elapsedMs),
                    style = MaterialTheme.typography.headlineMedium
                )
                Spacer(Modifier.height(Dimension.sm))
                val accent = MaterialTheme.colorScheme.tertiary
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(Dimension.xxxl * 2)
                ) {
                    val samples = state.amplitudes
                    if (samples.isEmpty()) return@Canvas
                    val mid = size.height / 2f
                    val barWidth = size.width / samples.size.coerceAtLeast(1)
                    samples.forEachIndexed { index, amplitude ->
                        // Square-root scaling keeps quiet speech visible without
                        // clipping loud peaks.
                        val normalized = kotlin.math.sqrt(
                            (amplitude / 32767f).coerceIn(0f, 1f)
                        )
                        val barHeight = (normalized * size.height * 0.95f)
                            .coerceAtLeast(2f)
                        val x = index * barWidth + barWidth / 2f
                        drawLine(
                            color = accent,
                            start = Offset(x, mid - barHeight / 2f),
                            end = Offset(x, mid + barHeight / 2f),
                            strokeWidth = (barWidth * 0.55f).coerceAtLeast(1.5f),
                            cap = StrokeCap.Round
                        )
                    }
                }
            }
            Text(
                text = stringResource(R.string.audio_recorder_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (state.isRecording) {
                StudioActionButton(
                    label = stringResource(R.string.audio_recorder_stop),
                    onClick = viewModel::stopAndSave,
                    style = StudioActionStyle.DESTRUCTIVE
                )
            } else {
                StudioActionButton(
                    label = stringResource(R.string.audio_recorder_start),
                    onClick = {
                        if (hasPermission) {
                            viewModel.start()
                        } else {
                            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    },
                    loading = state.isSaving,
                    enabled = !state.isSaving
                )
            }
            state.saved?.let { saved ->
                StudioSurface(tone = StudioSurfaceTone.EMPHASIZED) {
                    Text(
                        text = stringResource(R.string.audio_recorder_saved),
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text(
                        text = saved.displayName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.height(Dimension.lg))
        }
    }
}

private fun formatElapsed(ms: Long): String {
    val totalSeconds = ms / 1000
    return String.format(
        Locale.US,
        "%02d:%02d",
        totalSeconds / 60,
        totalSeconds % 60
    )
}
