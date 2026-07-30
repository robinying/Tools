package com.robin.tools.feature.media.ui.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
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
import com.robin.tools.feature.media.data.FilterManager
import com.robin.tools.feature.media.data.FilterState
import com.robin.tools.feature.media.data.FilterType
import com.robin.tools.feature.media.delegate.OpenCVFilterDelegate
import com.robin.tools.feature.media.utils.FileUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.FileOutputStream

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilterScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var selectedUri by remember { mutableStateOf<Uri?>(null) }
    var selectedFilter by remember { mutableStateOf(FilterType.GRAYSCALE) }
    var originalBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var previewBitmap by remember { mutableStateOf<Bitmap?>(null) }

    val filterState by FilterManager.state.collectAsState()
    val isProcessing = filterState is FilterState.Processing
    val delegate = remember { OpenCVFilterDelegate() }

    // Reset manager state on entering the screen
    LaunchedEffect(Unit) {
        FilterManager.reset()
    }

    // Clean up all bitmaps when leaving the screen
    DisposableEffect(Unit) {
        onDispose {
            originalBitmap?.recycle()
            previewBitmap?.recycle()
            originalBitmap = null
            previewBitmap = null
            FilterManager.reset()
        }
    }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            // Recycle old bitmaps before assigning new ones
            originalBitmap?.recycle()
            previewBitmap?.recycle()
            originalBitmap = null
            previewBitmap = null
            selectedUri = uri
            FilterManager.reset()
        }
    }

    // Load original bitmap with downsampling to prevent OOM
    LaunchedEffect(selectedUri) {
        val uri = selectedUri
        if (uri == null) {
            originalBitmap = null
            return@LaunchedEffect
        }
        originalBitmap = withContext(Dispatchers.IO) {
            try {
                // Step 1: decode bounds only to get dimensions
                val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    BitmapFactory.decodeStream(stream, null, boundsOpts)
                }

                val sampleSize = calculateInSampleSize(
                    boundsOpts.outWidth, boundsOpts.outHeight, MAX_DIMENSION, MAX_DIMENSION
                )

                // Step 2: decode with sample size
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    BitmapFactory.decodeStream(stream, null, BitmapFactory.Options().apply {
                        inSampleSize = sampleSize
                    })
                }
            } catch (_: Exception) {
                null
            }
        }
    }

    // Handle filter result — store locally, not in FilterState
    LaunchedEffect(filterState) {
        if (filterState is FilterState.Finished && (filterState as FilterState.Finished).isSuccess) {
            // previewBitmap is set by the coroutine that called applyFilter,
            // so we just flip the flag here
        }
    }

    Scaffold(
        topBar = {
            ToolsTopAppBar(
                title = context.getString(R.string.filter_tool),
                onBack = onBack,
                backContentDescription = context.getString(R.string.back)
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = Dimension.pageHorizontal, vertical = Dimension.lg),
            verticalArrangement = Arrangement.spacedBy(Dimension.md)
        ) {
            StudioSectionHeader(
                eyebrow = context.getString(R.string.media_stage_select_eyebrow),
                title = context.getString(R.string.filter_select_source_title),
                description = context.getString(R.string.filter_select_source_desc)
            )
            StudioActionButton(
                label = if (selectedUri == null) {
                    context.getString(R.string.select_image)
                } else {
                    context.getString(R.string.reselect_file)
                },
                onClick = { imagePickerLauncher.launch("image/*") },
                enabled = !isProcessing,
                style = StudioActionStyle.SECONDARY
            )

            if (selectedUri != null) {
                ImagePreviewPanel(
                    uri = selectedUri!!,
                    resultBitmap = previewBitmap,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                )
            }

            StudioSurface(tone = StudioSurfaceTone.OUTLINED) {
                StudioSectionHeader(
                    eyebrow = context.getString(R.string.media_stage_configure_eyebrow),
                    title = context.getString(R.string.select_filter),
                    description = context.getString(R.string.media_stage_configure_desc)
                )
                Spacer(Modifier.height(Dimension.md))
                StudioSelectionRow(
                    options = FilterType.entries.map { filter ->
                        StudioSelectionOption(filter.name, stringResource(filter.labelRes))
                    },
                    selectedKey = selectedFilter.name,
                    onOptionSelected = { selectedFilter = FilterType.valueOf(it) },
                    enabled = !isProcessing
                )
            }

            if (isProcessing) {
                val state = filterState as FilterState.Processing
                LinearProgressIndicator(
                    progress = { state.progress },
                    modifier = Modifier.fillMaxWidth()
                )
                Text(state.message)

                StudioActionButton(
                    label = context.getString(R.string.cancel),
                    onClick = { FilterManager.cancelTask() },
                    style = StudioActionStyle.DESTRUCTIVE
                )
            } else {
                StudioActionButton(
                    label = context.getString(R.string.start_filter),
                    onClick = {
                        val bitmap = originalBitmap ?: return@StudioActionButton
                        scope.launch {
                            FilterManager.startTask()
                            FilterManager.updateState(FilterState.Processing(0f, context.getString(R.string.filter_processing)))

                            val result = withContext(Dispatchers.Default) {
                                delegate.applyFilter(bitmap, selectedFilter) { progress, msg ->
                                    FilterManager.updateState(FilterState.Processing(progress, msg))
                                }
                            }

                            // Clean up old result before assigning new
                            if (FilterManager.isCancelled()) {
                                previewBitmap = null
                                FilterManager.reset()
                                return@launch
                            }

                            result.fold(
                                onSuccess = { filtered ->
                                    // Recycle old preview
                                    previewBitmap?.recycle()
                                    previewBitmap = filtered
                                    FilterManager.updateState(
                                        FilterState.Finished(true, context.getString(R.string.filter_complete))
                                    )
                                },
                                onFailure = { e ->
                                    previewBitmap = null
                                    FilterManager.updateState(
                                        FilterState.Finished(false, e.message ?: context.getString(R.string.filter_error))
                                    )
                                }
                            )
                        }
                    },
                    enabled = originalBitmap != null
                )

                if (previewBitmap != null) {
                    StudioActionButton(
                        label = context.getString(R.string.save_to_gallery),
                        onClick = {
                            val bitmap = previewBitmap ?: return@StudioActionButton
                            scope.launch {
                                withContext(Dispatchers.IO) {
                                    val ext = if (
                                        selectedFilter == FilterType.EDGE_DETECTION ||
                                            selectedFilter == FilterType.SKETCH
                                    ) {
                                        "png"
                                    } else {
                                        "jpg"
                                    }
                                    val file = FileUtils.createOutputFile(context, ext)
                                    FileOutputStream(file).use { out ->
                                        if (ext == "png") {
                                            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                                        } else {
                                            bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
                                        }
                                        out.flush()
                                    }
                                    FileUtils.saveImageToGallery(context, file)
                                    file.delete()
                                }
                                withContext(Dispatchers.Main) {
                                    Toast.makeText(
                                        context,
                                        context.getString(R.string.saved_to_gallery),
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                        },
                        style = StudioActionStyle.SECONDARY,
                        icon = Icons.Default.Save,
                        iconContentDescription = context.getString(R.string.save_to_gallery)
                    )
                }
            }
        }
    }
}

@Composable
private fun ImagePreviewPanel(
    uri: Uri,
    resultBitmap: Bitmap?,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    Column(modifier = modifier) {
        if (resultBitmap != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = context.getString(R.string.original),
                        style = MaterialTheme.typography.labelMedium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                    AsyncImage(
                        model = uri,
                        contentDescription = context.getString(R.string.original_image),
                        modifier = Modifier.fillMaxWidth(),
                        contentScale = ContentScale.Fit
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = context.getString(R.string.filter_result),
                        style = MaterialTheme.typography.labelMedium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                    AsyncImage(
                        model = resultBitmap,
                        contentDescription = context.getString(R.string.filter_result),
                        modifier = Modifier.fillMaxWidth(),
                        contentScale = ContentScale.Fit
                    )
                }
            }
        } else {
            AsyncImage(
                model = uri,
                contentDescription = context.getString(R.string.preview_image),
                modifier = Modifier.fillMaxWidth(),
                contentScale = ContentScale.Fit
            )
        }
    }
}

private const val MAX_DIMENSION = 1920

private fun calculateInSampleSize(width: Int, height: Int, reqWidth: Int, reqHeight: Int): Int {
    var inSampleSize = 1
    if (height > reqHeight || width > reqWidth) {
        val halfHeight = height / 2
        val halfWidth = width / 2
        while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
            inSampleSize *= 2
        }
    }
    return inSampleSize
}
