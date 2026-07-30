package com.robin.tools.feature.camera.ui.slideshow

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.robin.tools.core.ui.Dimension
import com.robin.tools.core.ui.StudioActionButton
import com.robin.tools.core.ui.StudioActionStyle
import com.robin.tools.core.ui.StudioSectionHeader
import com.robin.tools.core.ui.StudioSurface
import com.robin.tools.core.ui.StudioSurfaceTone
import com.robin.tools.core.ui.ToolsTopAppBar
import com.robin.tools.feature.camera.R
import com.robin.tools.feature.camera.editor.SlideshowGenerator
import com.robin.tools.feature.camera.storage.CameraFileManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SlideshowScreen(
    onBack: () -> Unit,
    onGenerated: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val fileManager = remember { CameraFileManager(context) }
    var imageCount by remember { mutableStateOf(0) }
    var secondsPerImage by remember { mutableFloatStateOf(2f) }
    var busy by remember { mutableStateOf(false) }
    var pendingUris by remember { mutableStateOf<List<android.net.Uri>>(emptyList()) }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        pendingUris = uris
        imageCount = uris.size
    }

    Scaffold(
        topBar = {
            ToolsTopAppBar(
                title = stringResource(R.string.camera_slideshow_title),
                onBack = onBack,
                backContentDescription = stringResource(R.string.record_back)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = Dimension.pageHorizontal, vertical = Dimension.lg),
            verticalArrangement = Arrangement.spacedBy(Dimension.lg)
        ) {
            StudioSectionHeader(
                eyebrow = stringResource(R.string.camera_section_generate_eyebrow),
                title = stringResource(R.string.slideshow_workflow_title),
                description = stringResource(R.string.slideshow_workflow_desc),
                accent = MaterialTheme.colorScheme.secondary
            )
            StudioActionButton(
                label = if (imageCount == 0) {
                    stringResource(R.string.slideshow_select_images)
                } else {
                    stringResource(R.string.slideshow_images_selected, imageCount)
                },
                onClick = { picker.launch("image/*") },
                enabled = !busy,
                style = StudioActionStyle.SECONDARY
            )
            StudioSurface(tone = StudioSurfaceTone.OUTLINED) {
                Text(
                    stringResource(R.string.slideshow_duration, secondsPerImage.toInt()),
                    style = MaterialTheme.typography.labelLarge
                )
                Slider(
                    value = secondsPerImage,
                    onValueChange = { secondsPerImage = it },
                    valueRange = 1f..5f,
                    steps = 3,
                    enabled = !busy
                )
                Text(
                    stringResource(R.string.slideshow_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.weight(1f))

            StudioActionButton(
                label = stringResource(R.string.slideshow_generate),
                onClick = {
                    if (pendingUris.size < 2) {
                        Toast.makeText(context, R.string.slideshow_need_two, Toast.LENGTH_SHORT).show()
                    } else {
                        busy = true
                        scope.launch {
                            val out = fileManager.createOutputFile("slideshow")
                            val ok = withContext(Dispatchers.Default) {
                                val bitmaps = mutableListOf<Bitmap>()
                                try {
                                    for (uri in pendingUris) {
                                        try {
                                            context.contentResolver.openInputStream(uri)?.use { stream ->
                                                val bmp = BitmapFactory.decodeStream(stream) ?: return@use
                                                val max = 1280
                                                val scale = maxOf(bmp.width, bmp.height).toFloat() / max
                                                val finalBmp = if (scale > 1f) {
                                                    Bitmap.createScaledBitmap(
                                                        bmp,
                                                        (bmp.width / scale).toInt().coerceAtLeast(1),
                                                        (bmp.height / scale).toInt().coerceAtLeast(1),
                                                        true
                                                    ).also { if (it !== bmp) bmp.recycle() }
                                                } else {
                                                    bmp
                                                }
                                                bitmaps.add(finalBmp)
                                            }
                                        } catch (e: Exception) {
                                            Log.w("Slideshow", "decode failed", e)
                                        }
                                    }
                                    SlideshowGenerator().generate(
                                        bitmaps = bitmaps,
                                        outputFile = out,
                                        secondsPerImage = secondsPerImage.toInt()
                                    )
                                } finally {
                                    bitmaps.forEach { if (!it.isRecycled) it.recycle() }
                                }
                            }
                            busy = false
                            if (ok) {
                                Toast.makeText(context, R.string.slideshow_done, Toast.LENGTH_SHORT).show()
                                onGenerated(out.absolutePath)
                            } else {
                                Toast.makeText(context, R.string.slideshow_failed, Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                },
                enabled = !busy && imageCount >= 2,
                loading = busy
            )
        }
    }
}
