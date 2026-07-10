package com.robin.tools.feature.camera.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.robin.tools.core.ui.FeatureCard
import com.robin.tools.feature.camera.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CameraMainScreen(
    onBack: () -> Unit,
    onRecord: () -> Unit,
    onEditVideo: () -> Unit,
    onTrimVideo: () -> Unit,
    onCoverSelect: () -> Unit,
    onTextToVideo: (() -> Unit)? = null
) {
    val context = LocalContext.current
    var permissionsGranted by remember { mutableStateOf(
        ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    )}

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        permissionsGranted = results.values.all { it }
    }

    LaunchedEffect(Unit) {
        if (!permissionsGranted) {
            permissionLauncher.launch(
                arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
            )
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.camera_feature_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            FeatureCard(
                title = stringResource(R.string.camera_record_title),
                description = stringResource(R.string.camera_record_desc),
                icon = Icons.Default.Videocam,
                onClick = onRecord
            )
            FeatureCard(
                title = stringResource(R.string.camera_edit_title),
                description = stringResource(R.string.camera_edit_desc),
                icon = Icons.Default.Edit,
                onClick = onEditVideo
            )
            FeatureCard(
                title = stringResource(R.string.camera_trim_title),
                description = stringResource(R.string.camera_trim_desc),
                icon = Icons.Default.ContentCut,
                onClick = onTrimVideo
            )
            FeatureCard(
                title = stringResource(R.string.camera_cover_title),
                description = stringResource(R.string.camera_cover_desc),
                icon = Icons.Default.Collections,
                onClick = onCoverSelect
            )
            if (onTextToVideo != null) {
                FeatureCard(
                    title = "Text to Video",
                    description = "Generate a video from text content",
                    icon = Icons.Default.TextFields,
                    onClick = onTextToVideo
                )
            }
        }
    }
}
