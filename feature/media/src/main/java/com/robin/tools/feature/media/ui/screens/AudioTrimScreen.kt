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
import androidx.compose.material.icons.filled.AudioFile
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
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

private const val MIN_RANGE_MS = 500L

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioTrimScreen(
    onBack: () -> Unit,
    viewModel: CompressionViewModel,
    initialUri: String? = null
) {
    val context = LocalContext.current
    val taskState by viewModel.taskState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var selectedUri by remember { mutableStateOf<Uri?>(initialUri?.let(Uri::parse)) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var startMs by remember { mutableFloatStateOf(0f) }
    var endMs by remember { mutableFloatStateOf(0f) }
    var loadingDuration by remember { mutableStateOf(false) }
    var started by remember { mutableStateOf(false) }
    var asRingtone by remember { mutableStateOf(false) }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            selectedUri = it
            scope.launch {
                loadingDuration = true
                val duration = withContext(Dispatchers.IO) { readDuration(context, it) }
                loadingDuration = false
                durationMs = duration
                startMs = 0f
                endMs = duration.toFloat()
            }
        }
    }

    LaunchedEffect(initialUri) {
        val parsed = initialUri?.let(Uri::parse) ?: return@LaunchedEffect
        scope.launch {
            loadingDuration = true
            val duration = withContext(Dispatchers.IO) { readDuration(context, parsed) }
            loadingDuration = false
            durationMs = duration
            startMs = 0f
            endMs = duration.toFloat()
        }
    }

    LaunchedEffect(taskState) {
        val state = taskState
        if (state is CompressionTaskState.Finished && started) {
            started = false
            snackbarHostState.showSnackbar(
                if (state.isSuccess) {
                    context.getString(R.string.audio_trim_saved)
                } else {
                    context.getString(R.string.audio_trim_failed, state.message)
                }
            )
            viewModel.resetState()
        }
    }

    Scaffold(
        topBar = {
            ToolsTopAppBar(
                title = stringResource(R.string.audio_trim),
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
                text = stringResource(R.string.audio_trim_desc),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (selectedUri == null) {
                EmptyState(
                    icon = Icons.Default.AudioFile,
                    title = stringResource(R.string.audio_trim_no_file),
                    actionLabel = stringResource(R.string.audio_trim_select),
                    onAction = { picker.launch("audio/*") }
                )
            } else {
                StudioActionButton(
                    label = stringResource(R.string.audio_trim_select),
                    onClick = { picker.launch("audio/*") },
                    style = StudioActionStyle.SECONDARY,
                    enabled = taskState !is CompressionTaskState.Processing
                )

                StudioSectionHeader(
                    eyebrow = stringResource(R.string.audio_trim_range_eyebrow),
                    title = stringResource(R.string.audio_trim_range_title)
                )

                if (loadingDuration) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                } else if (durationMs > 0) {
                    StudioSurface(tone = StudioSurfaceTone.STANDARD) {
                        Text(
                            text = stringResource(
                                R.string.audio_trim_range_value,
                                formatTime(startMs.toLong()),
                                formatTime(endMs.toLong())
                            ),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.height(Dimension.sm))
                        Text(
                            text = stringResource(
                                R.string.audio_trim_start_value,
                                formatTime(startMs.toLong())
                            ),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Slider(
                            value = startMs,
                            onValueChange = {
                                startMs = it.coerceAtMost(endMs - MIN_RANGE_MS).coerceAtLeast(0f)
                            },
                            valueRange = 0f..durationMs.toFloat()
                        )
                        Text(
                            text = stringResource(
                                R.string.audio_trim_end_value,
                                formatTime(endMs.toLong())
                            ),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Slider(
                            value = endMs,
                            onValueChange = {
                                endMs = it.coerceAtLeast(startMs + MIN_RANGE_MS)
                                    .coerceAtMost(durationMs.toFloat())
                            },
                            valueRange = 0f..durationMs.toFloat()
                        )
                    }

                    StudioSectionHeader(
                        eyebrow = stringResource(R.string.audio_trim_destination),
                        title = stringResource(R.string.audio_trim_destination)
                    )
                    StudioSelectionRow(
                        options = listOf(
                            StudioSelectionOption("music", stringResource(R.string.audio_trim_music)),
                            StudioSelectionOption("ringtone", stringResource(R.string.audio_trim_ringtone))
                        ),
                        selectedKey = if (asRingtone) "ringtone" else "music",
                        onOptionSelected = { asRingtone = it == "ringtone" },
                        enabled = taskState !is CompressionTaskState.Processing
                    )
                    StudioActionButton(
                        label = stringResource(R.string.audio_trim_action),
                        onClick = {
                            started = true
                            viewModel.startAudioTrim(
                                selectedUri!!,
                                startMs.toLong(),
                                endMs.toLong(),
                                asRingtone
                            )
                        },
                        loading = taskState is CompressionTaskState.Processing,
                        enabled = endMs - startMs >= MIN_RANGE_MS
                    )
                } else {
                    Text(
                        text = stringResource(R.string.audio_trim_failed, ""),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }

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

private fun readDuration(context: android.content.Context, uri: Uri): Long {
    val retriever = MediaMetadataRetriever()
    return try {
        retriever.setDataSource(context, uri)
        retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            ?.toLongOrNull() ?: 0L
    } catch (e: Exception) {
        0L
    } finally {
        runCatching { retriever.release() }
    }
}

private fun formatTime(ms: Long): String {
    val totalSeconds = ms.coerceAtLeast(0L) / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%d:%02d", minutes, seconds)
}
