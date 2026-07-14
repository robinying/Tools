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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.robin.tools.feature.camera.R
import com.robin.tools.feature.camera.opengl.CameraGlSurfaceView
import com.robin.tools.feature.camera.storage.GallerySaver
import com.robin.tools.feature.camera.ui.record.FilterSwipeSelector
import kotlinx.coroutines.Dispatchers
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

        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 48.dp)
                .size(80.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.onBackground.copy(alpha = 0.3f))
                .clickable(enabled = !capturing && uiState.isReady) {
                    capturing = true
                    viewModel.captureStill { bmp ->
                        scope.launch {
                            val ok = if (bmp != null) {
                                withContext(Dispatchers.IO) { savePhoto(context, bmp) }
                            } else {
                                false
                            }
                            capturing = false
                            ToastMsg(context, ok)
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
