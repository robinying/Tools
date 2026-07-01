package com.robin.tools.feature.camera.ui.trim

import android.graphics.SurfaceTexture
import android.view.Surface
import android.view.TextureView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.robin.tools.feature.camera.storage.CameraFileManager
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrimScreen(
    videoPath: String,
    onBack: () -> Unit,
    onComplete: (String) -> Unit = {},
    viewModel: TrimViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val fileManager = remember { CameraFileManager(context) }
    var surfaceReady by remember { mutableStateOf<Surface?>(null) }

    LaunchedEffect(videoPath) {
        viewModel.loadVideo(context, videoPath)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Trim Video") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    if (uiState.durationMs > 0) {
                        TextButton(
                            onClick = {
                                val out = File(fileManager.outputDir, "trimmed_${System.currentTimeMillis()}.mp4")
                                viewModel.export(out, onComplete)
                            },
                            enabled = !uiState.isExporting
                        ) {
                            if (uiState.isExporting) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            } else {
                                Text("Export", color = MaterialTheme.colorScheme.primary)
                            }
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
                // Video preview
                Box(
                    modifier = Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(8.dp))
                        .background(Color.Black)
                ) {
                    AndroidView(
                        factory = { ctx ->
                            TextureView(ctx).apply {
                                surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                                    override fun onSurfaceTextureAvailable(st: SurfaceTexture, w: Int, h: Int) {
                                        val s = Surface(st)
                                        surfaceReady = s
                                        viewModel.setupPlayer(context, s)
                                    }
                                    override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, w: Int, h: Int) {}
                                    override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean {
                                        surfaceReady = null
                                        return true
                                    }
                                    override fun onSurfaceTextureUpdated(st: SurfaceTexture) {}
                                }
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )

                    // Play/Pause overlay
                    IconButton(
                        onClick = { viewModel.togglePlayback() },
                        modifier = Modifier.align(Alignment.Center).size(56.dp)
                    ) {
                        Icon(
                            if (uiState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            "Play",
                            tint = Color.White,
                            modifier = Modifier.size(48.dp)
                        )
                    }

                    // Time display
                    Text(
                        "${formatTime(uiState.currentPositionMs)} / ${formatTime(uiState.durationMs)}",
                        color = Color.White,
                        fontSize = 13.sp,
                        modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp)
                            .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }

                // Rotation controls
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    listOf(0, 90, 180, 270).forEach { deg ->
                        FilterChip(
                            selected = uiState.rotation == deg,
                            onClick = { viewModel.setRotation(deg) },
                            label = { Text("${deg}°", fontSize = 13.sp) }
                        )
                    }
                }

                // Trim range text
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Start: ${formatTime(uiState.startMs)}", fontSize = 12.sp, color = Color.Gray)
                    Text("End: ${formatTime(uiState.endMs)}", fontSize = 12.sp, color = Color.Gray)
                    Text("Duration: ${formatTime(uiState.endMs - uiState.startMs)}", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }

                // Thumbnail strip with trim handles
                Box(modifier = Modifier.fillMaxWidth().height(80.dp)) {
                    // Thumbnails
                    LazyRow(
                        modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(6.dp)),
                        horizontalArrangement = Arrangement.spacedBy(0.dp)
                    ) {
                        items(uiState.thumbnails.size) { i ->
                            val bmp = uiState.thumbnails.getOrNull(i)
                            if (bmp != null) {
                                Box(
                                    modifier = Modifier
                                        .width(((1f / uiState.thumbnails.size) * 360).dp)
                                        .fillMaxHeight()
                                ) {
                                    Image(
                                        bitmap = bmp.asImageBitmap(),
                                        contentDescription = null,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                            }
                        }
                    }

                    // Start handle (left edge)
                    if (uiState.durationMs > 0) {
                        val startFrac = uiState.startMs.toFloat() / uiState.durationMs.toFloat()
                        Box(
                            modifier = Modifier
                                .align(Alignment.CenterStart)
                                .offset(x = (startFrac * 360).dp)
                                .width(4.dp).fillMaxHeight()
                                .background(Color(0xFF00E676))
                                .pointerInput(Unit) {
                                    detectHorizontalDragGestures { _, dragAmount ->
                                        val deltaMs = (dragAmount / 360.dp.toPx() * uiState.durationMs).toLong()
                                        viewModel.setStartTime(uiState.startMs + deltaMs)
                                    }
                                }
                        )

                        // End handle (right edge)
                        val endFrac = uiState.endMs.toFloat() / uiState.durationMs.toFloat()
                        Box(
                            modifier = Modifier
                                .align(Alignment.CenterStart)
                                .offset(x = (endFrac * 360).dp)
                                .width(4.dp).fillMaxHeight()
                                .background(Color.Red)
                                .pointerInput(Unit) {
                                    detectHorizontalDragGestures { _, dragAmount ->
                                        val deltaMs = (dragAmount / 360.dp.toPx() * uiState.durationMs).toLong()
                                        viewModel.setEndTime(uiState.endMs + deltaMs)
                                    }
                                }
                        )

                        // Selected range highlight
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .offset(x = (startFrac * 360).dp)
                                .width(((endFrac - startFrac) * 360).dp).height(2.dp)
                                .background(Color(0xFF00E676))
                        )
                    }
                }

                // Error
                uiState.error?.let { err ->
                    Text(err, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
                }
            }
        }
    }
}

private fun formatTime(ms: Long): String {
    val totalSec = ms / 1000
    val min = totalSec / 60
    val sec = totalSec % 60
    return "%d:%02d".format(min, sec)
}
