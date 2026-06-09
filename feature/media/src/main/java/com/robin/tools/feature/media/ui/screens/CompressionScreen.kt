package com.robin.tools.feature.media.ui.screens

import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.robin.tools.feature.media.R
import com.robin.tools.feature.media.data.*
import com.robin.tools.feature.media.ui.viewmodel.CompressionViewModel
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
                                        CompressionType.VIDEO -> "video/*"
                                        CompressionType.GIF -> "image/gif"
                                        CompressionType.IMAGE -> "image/*"
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
                                    try { context.startActivity(intent) } catch (e: Exception) {}
                                }
                            } else {
                                val intent = Intent(Intent.ACTION_MAIN).apply {
                                    addCategory(Intent.CATEGORY_APP_GALLERY)
                                }
                                try { context.startActivity(intent) } catch (e: Exception) {}
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
            TopAppBar(
                title = { 
                    Text(when (type) {
                        CompressionType.VIDEO -> context.getString(R.string.video_compress)
                        CompressionType.IMAGE -> context.getString(R.string.image_compress)
                        CompressionType.GIF -> context.getString(R.string.video_to_gif)
                    })
                },
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
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            val isProcessing = taskState is CompressionTaskState.Processing
            
            Button(
                onClick = {
                    if (type == CompressionType.VIDEO || type == CompressionType.GIF) {
                        singleVideoLauncher.launch("video/*")
                    } else {
                        multipleImagesLauncher.launch("image/*")
                    }
                },
                enabled = !isProcessing,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (selectedUris.isEmpty()) context.getString(R.string.select_file) else context.getString(R.string.reselect_file))
            }

            if (selectedUris.isNotEmpty()) {
                Text(context.getString(R.string.files_selected, selectedUris.size))
            }

            Text(if (type == CompressionType.GIF) context.getString(R.string.select_gif_quality) else context.getString(R.string.select_compression_level))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                CompressionLevel.values().forEach { level ->
                    FilterChip(
                        selected = compressionLevel == level,
                        onClick = { if (!isProcessing) compressionLevel = level },
                        label = { Text(context.getString(level.labelResId)) },
                        enabled = !isProcessing
                    )
                }
            }

            if (isProcessing) {
                val state = taskState as CompressionTaskState.Processing
                LinearProgressIndicator(
                    progress = state.progress,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(state.message)
                
                Button(
                    onClick = { viewModel.cancelCompression() },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(context.getString(R.string.close)) // Or add a "Cancel" string
                }
            } else {
                Button(
                    onClick = {
                        if (selectedUris.isNotEmpty()) {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                            } else {
                                viewModel.startCompression(selectedUris, type, compressionLevel)
                            }
                        }
                    },
                    enabled = selectedUris.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (type == CompressionType.GIF) context.getString(R.string.start_conversion) else context.getString(R.string.start_compression))
                }
            }
        }
    }
}
