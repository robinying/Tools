package com.robin.tools.feature.media.ui.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.robin.tools.feature.media.R
import com.robin.tools.feature.media.data.FilterManager
import com.robin.tools.feature.media.data.FilterState
import com.robin.tools.feature.media.data.FilterType
import com.robin.tools.feature.media.delegate.OpenCVFilterDelegate
import com.robin.tools.feature.media.utils.FileUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.ui.res.stringResource
import java.io.File
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
    var hasResult by remember { mutableStateOf(false) }

    val filterState by FilterManager.state.collectAsState()
    val isProcessing = filterState is FilterState.Processing
    val delegate = remember { OpenCVFilterDelegate() }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            selectedUri = uri
            previewBitmap = null
            hasResult = false
            FilterManager.reset()
        }
    }

    // Load original bitmap when URI changes
    LaunchedEffect(selectedUri) {
        if (selectedUri == null) {
            originalBitmap = null
            return@LaunchedEffect
        }
        originalBitmap = withContext(Dispatchers.IO) {
            try {
                context.contentResolver.openInputStream(selectedUri!!)?.use { stream ->
                    BitmapFactory.decodeStream(stream)
                }
            } catch (_: Exception) {
                null
            }
        }
    }

    // Reset UI when finished
    LaunchedEffect(filterState) {
        if (filterState is FilterState.Finished && (filterState as FilterState.Finished).isSuccess) {
            previewBitmap = (filterState as FilterState.Finished).result
            hasResult = true
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(context.getString(R.string.filter_tool)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = context.getString(R.string.back))
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Image picker button
            Button(
                onClick = { imagePickerLauncher.launch("image/*") },
                enabled = !isProcessing,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (selectedUri == null) context.getString(R.string.select_image) else context.getString(R.string.reselect_file))
            }

            // Preview area
            if (selectedUri != null) {
                ImagePreviewPanel(
                    uri = selectedUri!!,
                    resultBitmap = previewBitmap,
                    hasResult = hasResult,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                )
            }

            // Filter type chips
            Text(
                text = context.getString(R.string.select_filter),
                style = MaterialTheme.typography.labelLarge
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterType.entries.forEach { filter ->
                    FilterChip(
                        selected = selectedFilter == filter,
                        onClick = { if (!isProcessing) selectedFilter = filter },
                        label = { Text(stringResource(filter.labelRes)) },
                        enabled = !isProcessing
                    )
                }
            }

            // Progress or action button
            if (isProcessing) {
                val state = filterState as FilterState.Processing
                LinearProgressIndicator(
                    progress = state.progress,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(state.message)

                Button(
                    onClick = { FilterManager.cancelTask() },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(context.getString(R.string.cancel))
                }
            } else {
                // Apply filter button
                Button(
                    onClick = {
                        val bitmap = originalBitmap ?: return@Button
                        scope.launch {
                            FilterManager.startTask()
                            FilterManager.updateState(FilterState.Processing(0f, context.getString(R.string.filter_processing)))
                            val result = delegate.applyFilter(bitmap, selectedFilter) { progress, msg ->
                                FilterManager.updateState(FilterState.Processing(progress, msg))
                            }
                            if (FilterManager.isCancelled()) {
                                FilterManager.reset()
                                return@launch
                            }
                            result.fold(
                                onSuccess = { filtered ->
                                    FilterManager.updateState(
                                        FilterState.Finished(true, context.getString(R.string.filter_complete), filtered)
                                    )
                                },
                                onFailure = { e ->
                                    FilterManager.updateState(
                                        FilterState.Finished(false, e.message ?: context.getString(R.string.filter_error))
                                    )
                                }
                            )
                        }
                    },
                    enabled = originalBitmap != null,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(context.getString(R.string.start_filter))
                }

                // Save button (visible after processing)
                if (hasResult && previewBitmap != null) {
                    Button(
                        onClick = {
                            scope.launch {
                                val bitmap = previewBitmap ?: return@launch
                                withContext(Dispatchers.IO) {
                                    val file = FileUtils.createOutputFile(context, "jpg")
                                    FileOutputStream(file).use { out ->
                                        bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
                                        out.flush()
                                    }
                                    FileUtils.saveImageToGallery(context, file)
                                    file.delete()
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Save, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(context.getString(R.string.save_to_gallery))
                    }
                }
            }
        }
    }
}

/**
 * Side-by-side or overlaid preview of the original and filtered images.
 */
@Composable
private fun ImagePreviewPanel(
    uri: Uri,
    resultBitmap: Bitmap?,
    hasResult: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    Column(modifier = modifier) {
        if (hasResult && resultBitmap != null) {
            // Show original + result side by side
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
            // Only show original before processing
            AsyncImage(
                model = uri,
                contentDescription = context.getString(R.string.preview_image),
                modifier = Modifier.fillMaxWidth(),
                contentScale = ContentScale.Fit
            )
        }
    }
}
