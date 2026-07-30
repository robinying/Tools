package com.robin.tools.feature.media.ui.screens

import android.content.Intent
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import android.util.Log
import android.util.Size
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.robin.tools.core.ui.Dimension
import com.robin.tools.core.ui.ProgressBlock
import com.robin.tools.core.ui.StudioActionButton
import com.robin.tools.core.ui.StudioActionStyle
import com.robin.tools.core.ui.StudioSectionHeader
import com.robin.tools.core.ui.StudioSelectionOption
import com.robin.tools.core.ui.StudioSelectionRow
import com.robin.tools.core.ui.StudioSurface
import com.robin.tools.core.ui.StudioSurfaceTone
import com.robin.tools.core.ui.ToolsTopAppBar
import com.robin.tools.feature.media.R
import com.robin.tools.feature.media.data.*
import com.robin.tools.feature.media.ui.viewmodel.CompressionViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.res.stringResource
import java.util.ArrayList

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompressionScreen(
    type: CompressionType,
    onBack: () -> Unit,
    viewModel: CompressionViewModel = viewModel()
) {
    val context = LocalContext.current
    var selectedUris by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var compressionLevel by remember { mutableStateOf(CompressionLevel.MEDIUM) }
    
    val taskState by viewModel.taskState.collectAsStateWithLifecycle()
    
    var showCompletionDialog by remember { mutableStateOf(false) }

    // Launcher for multiple images
    val multipleImagesLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->
        selectedUris = uris
    }

    // Launcher for single video
    val singleVideoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        selectedUris = if (uri != null) listOf(uri) else emptyList()
    }
    
    // Permission launcher for notifications
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        viewModel.startCompression(selectedUris, type, compressionLevel)
    }

    LaunchedEffect(taskState) {
        if (taskState is CompressionTaskState.Finished) {
            showCompletionDialog = true
        }
    }
    
    if (showCompletionDialog) {
        val state = taskState as? CompressionTaskState.Finished
        AlertDialog(
            onDismissRequest = { 
                showCompletionDialog = false
                viewModel.resetState()
            },
            title = { Text(if (state?.isSuccess == true) context.getString(R.string.processing_complete) else context.getString(R.string.processing_failed)) },
            text = { 
                Text(
                    if (state?.isSuccess == true) context.getString(R.string.all_saved_to_gallery)
                    else context.getString(R.string.processing_error)
                ) 
            },
            confirmButton = {
                if (state?.isSuccess == true) {
                    TextButton(
                        onClick = {
                            showCompletionDialog = false
                            val outputUri = state.outputUri
                            if (selectedUris.size == 1 && outputUri != null) {
                                try {
                                    val mimeType = when (type) {
                                        CompressionType.VIDEO,
                                        CompressionType.STRIP_AUDIO,
                                        CompressionType.TRANSCODE,
                                        CompressionType.SPEED,
                                        CompressionType.REVERSE,
                                        CompressionType.CONCAT,
                                        CompressionType.CROP,
                                        CompressionType.VOLUME_FADE -> "video/*"
                                        CompressionType.GIF -> "image/gif"
                                        CompressionType.IMAGE -> "image/*"
                                        CompressionType.EXTRACT_AUDIO -> "audio/*"
                                    }
                                    val intent = Intent(Intent.ACTION_VIEW).apply {
                                        setDataAndType(Uri.parse(outputUri), mimeType)
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    val intent = Intent(Intent.ACTION_MAIN).apply {
                                        addCategory(Intent.CATEGORY_APP_GALLERY)
                                    }
                                    try { context.startActivity(intent) } catch (e: Exception) { Log.w("CompressionScreen", "Failed to open file viewer", e) }
                                }
                            } else {
                                val intent = Intent(Intent.ACTION_MAIN).apply {
                                    addCategory(Intent.CATEGORY_APP_GALLERY)
                                }
                                try { context.startActivity(intent) } catch (e: Exception) { Log.w("CompressionScreen", "Failed to open gallery", e) }
                            }
                            viewModel.resetState()
                        }
                    ) {
                        Text(if (selectedUris.size > 1) context.getString(R.string.open_gallery) else context.getString(R.string.view_file))
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { 
                    showCompletionDialog = false
                    viewModel.resetState()
                }) {
                    Text(context.getString(R.string.close))
                }
            }
        )
    }

    Scaffold(
        topBar = {
            ToolsTopAppBar(
                title = when (type) {
                    CompressionType.VIDEO -> context.getString(R.string.video_compress)
                    CompressionType.IMAGE -> context.getString(R.string.image_compress)
                    CompressionType.GIF -> context.getString(R.string.video_to_gif)
                    CompressionType.EXTRACT_AUDIO -> context.getString(R.string.extract_audio)
                    CompressionType.STRIP_AUDIO -> context.getString(R.string.strip_audio)
                    CompressionType.TRANSCODE -> context.getString(R.string.transcode_mp4)
                    CompressionType.SPEED -> context.getString(R.string.speed_change)
                    CompressionType.REVERSE -> context.getString(R.string.reverse_video)
                    CompressionType.CONCAT -> context.getString(R.string.concat_video)
                    CompressionType.CROP -> context.getString(R.string.crop_aspect)
                    CompressionType.VOLUME_FADE -> context.getString(R.string.volume_fade)
                },
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
            verticalArrangement = Arrangement.spacedBy(Dimension.lg)
        ) {
            val isProcessing = taskState is CompressionTaskState.Processing

            StudioSectionHeader(
                eyebrow = context.getString(R.string.media_stage_select_eyebrow),
                title = context.getString(R.string.media_stage_select_title),
                description = context.getString(R.string.media_stage_select_desc)
            )
            StudioActionButton(
                label = if (selectedUris.isEmpty()) {
                    context.getString(R.string.select_file)
                } else {
                    context.getString(R.string.reselect_file)
                },
                onClick = {
                    when (type) {
                        CompressionType.IMAGE -> multipleImagesLauncher.launch("image/*")
                        CompressionType.CONCAT -> multipleImagesLauncher.launch("video/*")
                        else -> singleVideoLauncher.launch("video/*")
                    }
                },
                enabled = !isProcessing,
                style = StudioActionStyle.SECONDARY
            )

            if (selectedUris.isNotEmpty()) {
                MediaPreviewSection(
                    uris = selectedUris,
                    type = type,
                    onRemove = { uri -> selectedUris = selectedUris - uri }
                )
            }

            StudioSurface(tone = StudioSurfaceTone.OUTLINED) {
                StudioSectionHeader(
                    eyebrow = context.getString(R.string.media_stage_configure_eyebrow),
                    title = optionSectionTitle(type, context),
                    description = context.getString(R.string.media_stage_configure_desc)
                )
                Spacer(Modifier.height(Dimension.md))
                StudioSelectionRow(
                    options = CompressionLevel.entries.map { level ->
                        StudioSelectionOption(
                            key = level.name,
                            label = optionLabel(type, level, context)
                        )
                    },
                    selectedKey = compressionLevel.name,
                    onOptionSelected = { selectedKey ->
                        if (!isProcessing) {
                            compressionLevel = CompressionLevel.valueOf(selectedKey)
                        }
                    },
                    enabled = !isProcessing
                )
            }

            if (isProcessing) {
                val state = taskState as CompressionTaskState.Processing
                ProgressBlock(
                    progress = state.progress,
                    message = state.message,
                    onCancel = { viewModel.cancelCompression() },
                    cancelLabel = context.getString(R.string.cancel)
                )
            } else {
                StudioActionButton(
                    label = when (type) {
                        CompressionType.GIF -> context.getString(R.string.start_conversion)
                        CompressionType.VIDEO, CompressionType.IMAGE ->
                            context.getString(R.string.start_compression)
                        else -> context.getString(R.string.start_process)
                    },
                    onClick = {
                        if (selectedUris.isNotEmpty()) {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                notificationPermissionLauncher.launch(
                                    android.Manifest.permission.POST_NOTIFICATIONS
                                )
                            } else {
                                viewModel.startCompression(selectedUris, type, compressionLevel)
                            }
                        }
                    },
                    enabled = selectedUris.isNotEmpty() &&
                        (type != CompressionType.CONCAT || selectedUris.size >= 2)
                )
            }
        }
    }
}

private fun optionSectionTitle(type: CompressionType, context: android.content.Context): String {
    return when (type) {
        CompressionType.GIF -> context.getString(R.string.select_gif_quality)
        CompressionType.SPEED -> context.getString(R.string.select_speed)
        CompressionType.REVERSE -> context.getString(R.string.select_reverse_mode)
        CompressionType.CROP -> context.getString(R.string.select_aspect)
        CompressionType.VOLUME_FADE -> context.getString(R.string.select_volume)
        CompressionType.STRIP_AUDIO -> context.getString(R.string.select_quality)
        else -> context.getString(R.string.select_compression_level)
    }
}

private fun optionLabel(
    type: CompressionType,
    level: CompressionLevel,
    context: android.content.Context
): String {
    return when (type) {
        CompressionType.SPEED -> when (level) {
            CompressionLevel.LOW -> context.getString(R.string.option_speed_05)
            CompressionLevel.MEDIUM -> context.getString(R.string.option_speed_15)
            CompressionLevel.HIGH -> context.getString(R.string.option_speed_20)
        }
        CompressionType.REVERSE -> when (level) {
            CompressionLevel.LOW, CompressionLevel.HIGH ->
                context.getString(R.string.option_reverse_mute)
            CompressionLevel.MEDIUM -> context.getString(R.string.option_reverse_audio)
        }
        CompressionType.CROP -> when (level) {
            CompressionLevel.LOW -> context.getString(R.string.option_aspect_11)
            CompressionLevel.MEDIUM -> context.getString(R.string.option_aspect_916)
            CompressionLevel.HIGH -> context.getString(R.string.option_aspect_169)
        }
        CompressionType.VOLUME_FADE -> when (level) {
            CompressionLevel.LOW -> context.getString(R.string.option_vol_soft)
            CompressionLevel.MEDIUM -> context.getString(R.string.option_vol_normal)
            CompressionLevel.HIGH -> context.getString(R.string.option_vol_loud)
        }
        else -> context.getString(level.labelRes)
    }
}

/**
 * Displays preview thumbnails for selected media files.
 * - For images: a horizontal scrollable row of square thumbnails.
 * - For video/GIF (single file): a wider preview card with file metadata.
 */
@Composable
private fun MediaPreviewSection(
    uris: List<Uri>,
    type: CompressionType,
    onRemove: (Uri) -> Unit
) {
    val context = LocalContext.current

    Column(modifier = Modifier.fillMaxWidth()) {
        // Section header
        Text(
            text = context.getString(R.string.files_selected, uris.size),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        if (type == CompressionType.IMAGE || type == CompressionType.CONCAT) {
            // Multiple files: horizontal scrollable row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                uris.forEach { uri ->
                    if (type == CompressionType.IMAGE) {
                        ImageThumbnail(uri = uri, onRemove = { onRemove(uri) })
                    } else {
                        VideoPreviewCard(uri = uri, onRemove = { onRemove(uri) })
                    }
                }
            }
        } else {
            // Single video/GIF: prominent preview card
            val uri = uris.first()
            VideoPreviewCard(uri = uri, onRemove = { onRemove(uri) })
        }
    }
}

/**
 * Square thumbnail for image preview with a remove button.
 * Uses Coil [AsyncImage] which handles content URIs efficiently.
 */
@Composable
private fun ImageThumbnail(
    uri: Uri,
    onRemove: () -> Unit
) {
    val context = LocalContext.current
    val fileName = remember(uri) { resolveFileName(context, uri) }
    val fileSize = remember(uri) { resolveFileSize(context, uri) }

    Card(
        modifier = Modifier.width(120.dp),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Box {
            // Image preview
            AsyncImage(
                model = uri,
                contentDescription = context.getString(R.string.preview_image),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
                    .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)),
                contentScale = ContentScale.Crop
            )

            // Remove button
            IconButton(
                onClick = onRemove,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(28.dp)
                    .padding(2.dp)
                    .background(
                        MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
                        RoundedCornerShape(bottomStart = 8.dp)
                    )
            ) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = context.getString(R.string.remove),
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        // File info below the image
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
        ) {
            if (fileName != null) {
                Text(
                    text = fileName,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (fileSize != null) {
                Text(
                    text = fileSize,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * Wider preview card for video or GIF with thumbnail, file name, size, and remove button.
 */
@Composable
private fun VideoPreviewCard(
    uri: Uri,
    onRemove: () -> Unit
) {
    val context = LocalContext.current
    val fileName = remember(uri) { resolveFileName(context, uri) }
    val fileSize = remember(uri) { resolveFileSize(context, uri) }

    // Load video thumbnail asynchronously; recycle on leave / URI change
    var thumbnail by remember { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(uri) {
        val previous = thumbnail
        val next = withContext(Dispatchers.IO) {
            loadVideoThumbnail(context, uri)
        }
        thumbnail = next
        if (previous != null && previous !== next && !previous.isRecycled) {
            previous.recycle()
        }
    }
    DisposableEffect(uri) {
        onDispose {
            thumbnail?.let { bmp ->
                if (!bmp.isRecycled) bmp.recycle()
            }
            thumbnail = null
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Thumbnail or placeholder
            Box(
                modifier = Modifier
                    .size(width = 120.dp, height = 80.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                if (thumbnail != null) {
                    androidx.compose.foundation.Image(
                        bitmap = thumbnail!!.asImageBitmap(),
                        contentDescription = context.getString(R.string.preview_image),
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(
                        Icons.Default.Videocam,
                        contentDescription = null,
                        modifier = Modifier.size(32.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // File info
            Column(modifier = Modifier.weight(1f)) {
                if (fileName != null) {
                    Text(
                        text = fileName,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (fileSize != null) {
                    Text(
                        text = fileSize,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Remove button
            IconButton(onClick = onRemove) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = context.getString(R.string.remove),
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

/**
 * Loads a video thumbnail from the given URI.
 * Uses [ContentResolver.loadThumbnail] on API 29+, falls back to [MediaMetadataRetriever] on API 28.
 */
private fun loadVideoThumbnail(context: android.content.Context, uri: Uri): Bitmap? {
    return try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            context.contentResolver.loadThumbnail(uri, Size(512, 512), null)
        } else {
            @Suppress("DEPRECATION")
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(context, uri)
                retriever.frameAtTime
            } finally {
                retriever.release()
            }
        }
    } catch (_: Exception) {
        null
    }
}

/**
 * Resolves a human-readable file name from a content URI.
 */
private fun resolveFileName(context: android.content.Context, uri: Uri): String? {
    return try {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0) cursor.getString(index) else null
            } else null
        }
    } catch (_: Exception) {
        null
    }
}

/**
 * Resolves a human-readable file size from a content URI.
 */
private fun resolveFileSize(context: android.content.Context, uri: Uri): String? {
    return try {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (index >= 0) {
                    val bytes = cursor.getLong(index)
                    if (bytes > 0) formatFileSize(bytes) else null
                } else null
            } else null
        }
    } catch (_: Exception) {
        null
    }
}

/**
 * Formats a file size in bytes to a human-readable string (e.g., "1.5 MB").
 */
private fun formatFileSize(bytes: Long): String {
    return when {
        bytes >= 1_073_741_824 -> "%.1f GB".format(bytes / 1_073_741_824.0)
        bytes >= 1_048_576 -> "%.1f MB".format(bytes / 1_048_576.0)
        bytes >= 1_024 -> "%.1f KB".format(bytes / 1_024.0)
        else -> "$bytes B"
    }
}
