package com.robin.tools.feature.camera.ui.photo

import android.graphics.Bitmap
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.SwitchCamera
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.robin.tools.feature.camera.R
import com.robin.tools.feature.camera.opengl.CameraGlSurfaceView
import com.robin.tools.feature.camera.storage.GallerySaver
import com.robin.tools.feature.camera.ui.common.CameraGridOverlay
import com.robin.tools.feature.camera.ui.common.CameraGuideChips
import com.robin.tools.feature.camera.ui.common.CameraLevelOverlay
import com.robin.tools.feature.camera.ui.common.rememberDeviceRollDegrees
import com.robin.tools.core.ui.TextOptionChip
import com.robin.tools.feature.camera.ui.record.FilterSwipeSelector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

@Composable
fun PhotoCaptureScreen(
    onBack: () -> Unit,
    viewModel: PhotoCaptureViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var capturing by remember { mutableStateOf(false) }
    var countingDown by remember { mutableStateOf(false) }
    var countdownValue by remember { mutableStateOf(0) }
    var timerSeconds by remember { mutableStateOf(0) }
    var burstCount by remember { mutableStateOf(1) }
    var showGrid by remember { mutableStateOf(true) }
    var showLevel by remember { mutableStateOf(true) }
    val rollDegrees = rememberDeviceRollDegrees()

    DisposableEffect(Unit) {
        onDispose { viewModel.releaseCamera() }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        AndroidView(
            factory = { ctx ->
                CameraGlSurfaceView(ctx).apply {
                    setRenderer(viewModel.renderer)
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        if (showGrid) {
            CameraGridOverlay(modifier = Modifier.fillMaxSize())
        }
        if (showLevel) {
            CameraLevelOverlay(
                rollDegrees = rollDegrees,
                modifier = Modifier.fillMaxSize()
            )
        }

        CameraGuideChips(
            showGrid = showGrid,
            showLevel = showLevel,
            onToggleGrid = { showGrid = !showGrid },
            onToggleLevel = { showLevel = !showLevel },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 100.dp, end = 16.dp)
        )

        if (countingDown) {
            Text(
                text = countdownValue.toString(),
                style = MaterialTheme.typography.displayLarge,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.align(Alignment.Center)
            )
        }

        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 48.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.record_back),
                    tint = MaterialTheme.colorScheme.onBackground
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            IconButton(
                onClick = { viewModel.switchCamera() },
                enabled = !uiState.isSwitching && !capturing
            ) {
                Icon(
                    Icons.Default.SwitchCamera,
                    contentDescription = stringResource(R.string.record_flip_camera),
                    tint = MaterialTheme.colorScheme.onBackground
                )
            }
        }

        FilterSwipeSelector(
            currentFilter = uiState.currentFilter,
            onFilterChanged = { viewModel.setFilter(it) },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 140.dp)
        )

        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 270.dp),
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)
        ) {
            TextOptionChip(
                selected = burstCount == 1,
                onClick = { burstCount = 1 },
                label = stringResource(R.string.camera_burst_off)
            )
            TextOptionChip(
                selected = burstCount == 3,
                onClick = { burstCount = 3 },
                label = stringResource(R.string.camera_burst_3)
            )
            TextOptionChip(
                selected = burstCount == 5,
                onClick = { burstCount = 5 },
                label = stringResource(R.string.camera_burst_5)
            )
        }

        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 210.dp),
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)
        ) {
            TextOptionChip(
                selected = timerSeconds == 0,
                onClick = { timerSeconds = 0 },
                label = stringResource(R.string.camera_timer_off)
            )
            TextOptionChip(
                selected = timerSeconds == 3,
                onClick = { timerSeconds = 3 },
                label = stringResource(R.string.camera_timer_3)
            )
            TextOptionChip(
                selected = timerSeconds == 10,
                onClick = { timerSeconds = 10 },
                label = stringResource(R.string.camera_timer_10)
            )
        }

        suspend fun captureOne(): Boolean {
            val deferred = kotlinx.coroutines.CompletableDeferred<Boolean>()
            viewModel.captureStill { bmp ->
                scope.launch(Dispatchers.IO) {
                    val ok = if (bmp != null) savePhoto(context, bmp) else false
                    deferred.complete(ok)
                }
            }
            return deferred.await()
        }

        val capture: () -> Unit = {
            scope.launch {
                capturing = true
                var saved = 0
                repeat(burstCount) { index ->
                    if (captureOne()) saved++
                    if (index < burstCount - 1) delay(350)
                }
                capturing = false
                Toast.makeText(
                    context,
                    if (burstCount == 1) {
                        context.getString(if (saved == 1) R.string.photo_saved else R.string.photo_failed)
                    } else {
                        context.getString(R.string.camera_burst_saved, saved, burstCount)
                    },
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 48.dp)
                .size(80.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.onBackground.copy(alpha = 0.3f))
                .clickable(enabled = !capturing && !countingDown && uiState.isReady) {
                    if (timerSeconds == 0) {
                        capture()
                    } else {
                        scope.launch {
                            countingDown = true
                            for (remaining in timerSeconds downTo 1) {
                                countdownValue = remaining
                                delay(1_000)
                            }
                            countdownValue = 0
                            countingDown = false
                            capture()
                        }
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            if (capturing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(36.dp),
                    color = MaterialTheme.colorScheme.onBackground
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(60.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.onBackground)
                )
            }
        }
    }
}

private fun ToastMsg(context: android.content.Context, ok: Boolean) {
    Toast.makeText(
        context,
        if (ok) R.string.photo_saved else R.string.photo_failed,
        Toast.LENGTH_SHORT
    ).show()
}

private fun savePhoto(context: android.content.Context, bitmap: Bitmap): Boolean {
    return try {
        val dir = File(context.cacheDir, "camera_photo").apply { mkdirs() }
        val file = File(dir, "photo_${System.currentTimeMillis()}.jpg")
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 92, out)
        }
        if (!bitmap.isRecycled) bitmap.recycle()
        val uri = GallerySaver.saveImage(context, file)
        file.delete()
        uri != null
    } catch (_: Exception) {
        false
    }
}
