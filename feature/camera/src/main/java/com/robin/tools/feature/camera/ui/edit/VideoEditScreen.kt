package com.robin.tools.feature.camera.ui.edit

import android.graphics.SurfaceTexture
import android.view.Surface
import android.view.TextureView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip

import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.robin.tools.feature.camera.R
import com.robin.tools.feature.camera.filter.FilterType
import com.robin.tools.feature.camera.filter.stringRes
import com.robin.tools.feature.camera.storage.CameraFileManager
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoEditScreen(
    videoPath: String,
    onBack: () -> Unit,
    onComplete: (String) -> Unit = {},
    viewModel: VideoEditViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val fileManager = remember { CameraFileManager(context) }
    var showWatermarkDialog by remember { mutableStateOf(false) }
    var watermarkInput by remember { mutableStateOf("") }

    LaunchedEffect(videoPath) { viewModel.loadVideo(context, videoPath) }

    // Watermark input dialog
    if (showWatermarkDialog) {
        AlertDialog(
            onDismissRequest = { showWatermarkDialog = false },
            title = { Text(stringResource(R.string.edit_watermark_title)) },
            text = {
                OutlinedTextField(
                    value = watermarkInput,
                    onValueChange = { watermarkInput = it },
                    label = { Text(stringResource(R.string.edit_watermark_hint)) },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (watermarkInput.isNotBlank()) {
                        viewModel.setWatermark(watermarkInput)
                        watermarkInput = ""
                    }
                    showWatermarkDialog = false
                }) { Text(stringResource(R.string.edit_add)) }
            },
            dismissButton = {
                TextButton(onClick = { showWatermarkDialog = false }) { Text(stringResource(R.string.edit_cancel)) }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.edit_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                },
                actions = {
                    TextButton(
                        onClick = {
                            val out = File(fileManager.outputDir, "edited_${System.currentTimeMillis()}.mp4")
                            viewModel.export(out, onComplete)
                        },
                        enabled = !uiState.isExporting && uiState.durationMs > 0
                    ) {
                        if (uiState.isExporting) {
                            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else {
                            Text(stringResource(R.string.edit_export), color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            )
        }
    ) { padding ->
        if (uiState.durationMs == 0L) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            Column(
                modifier = Modifier.fillMaxSize().padding(padding),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Video preview
                Box(
                    modifier = Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.background)
                ) {
                    AndroidView(
                        factory = { ctx ->
                            TextureView(ctx).apply {
                                surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                                    override fun onSurfaceTextureAvailable(st: SurfaceTexture, w: Int, h: Int) {
                                        viewModel.setupPlayer(context, Surface(st))
                                    }
                                    override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, w: Int, h: Int) {}
                                    override fun onSurfaceTextureDestroyed(st: SurfaceTexture) = true
                                    override fun onSurfaceTextureUpdated(st: SurfaceTexture) {}
                                }
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )

                    // Watermark overlay
                    if (uiState.watermarkText.isNotEmpty()) {
                        Text(
                            uiState.watermarkText,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }

                    // Play/Pause
                    IconButton(
                        onClick = { viewModel.togglePlayback() },
                        modifier = Modifier.align(Alignment.Center).size(56.dp)
                    ) {
                        Icon(
                            if (uiState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            "Play", tint = MaterialTheme.colorScheme.onBackground, modifier = Modifier.size(48.dp)
                        )
                    }
                }

                // Filter selector
                LazyRow(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(FilterType.entries) { filter ->
                        val sel = filter == uiState.currentFilter
                        FilterChip(
                            selected = sel,
                            onClick = { viewModel.setFilter(filter) },
                            label = { Text(stringResource(filter.stringRes()), style = MaterialTheme.typography.labelSmall) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer
                            )
                        )
                    }
                }

                // Toolbar
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    IconButton(onClick = { showWatermarkDialog = true }) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.TextFields, "Watermark")
                            Text(stringResource(R.string.edit_watermark), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    IconButton(onClick = {}) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.EmojiEmotions, "Sticker")
                            Text(stringResource(R.string.edit_sticker), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))
            }
        }

        uiState.error?.let {
            Snackbar(modifier = Modifier.padding(16.dp)) { Text(it) }
        }
    }
}
