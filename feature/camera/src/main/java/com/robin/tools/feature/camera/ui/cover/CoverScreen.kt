package com.robin.tools.feature.camera.ui.cover

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip

import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.robin.tools.core.ui.ToolsTopAppBar
import com.robin.tools.feature.camera.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CoverScreen(
    videoPath: String,
    onBack: () -> Unit,
    onComplete: (String) -> Unit = {},
    viewModel: CoverViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(videoPath) { viewModel.loadVideo(context, videoPath) }

    Scaffold(
        topBar = {
            ToolsTopAppBar(
                title = stringResource(R.string.cover_title),
                onBack = onBack,
                backContentDescription = stringResource(R.string.record_back),
                actions = {
                    if (uiState.selectedBitmap != null) {
                        TextButton(onClick = { viewModel.saveCover(context, onComplete) }) {
                            Icon(Icons.Default.Check, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(stringResource(R.string.cover_save))
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
                modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Large selected frame preview
                Card(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    val bmp = uiState.selectedBitmap
                    if (bmp != null) {
                        Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = "Selected cover",
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(stringResource(R.string.cover_hint), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }

                // Selected time
                if (uiState.selectedTimeMs > 0) {
                    Text(
                        stringResource(R.string.cover_frame, formatTime(uiState.selectedTimeMs)),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                }

                // Thumbnail strip
                Text("Tap to select:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    itemsIndexed(uiState.thumbnails) { i, (time, bmp) ->
                        val isSelected = time == uiState.selectedTimeMs
                        Box(
                            modifier = Modifier
                                .width(120.dp).height(68.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .then(
                                    if (isSelected) Modifier.border(3.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(4.dp))
                                    else Modifier.border(1.dp, MaterialTheme.colorScheme.onSurfaceVariant, RoundedCornerShape(4.dp))
                                )
                                .clickable { viewModel.selectFrame(time) }
                        ) {
                            Image(
                                bitmap = bmp.asImageBitmap(),
                                contentDescription = formatTime(time),
                                modifier = Modifier.fillMaxSize()
                            )
                            Text(
                                formatTime(time),
                                color = MaterialTheme.colorScheme.onBackground,
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.align(Alignment.BottomEnd).padding(2.dp)
                                    .background(MaterialTheme.colorScheme.background.copy(alpha = 0.6f), RoundedCornerShape(2.dp))
                                    .padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                    }
                }
            }
        }

        uiState.error?.let {
            Snackbar(Modifier.padding(16.dp)) { Text(it) }
        }
    }
}

private fun formatTime(ms: Long): String {
    val s = ms / 1000
    return "${s / 60}:%02d".format(s % 60)
}
