package com.robin.tools.feature.camera.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import com.robin.tools.core.ui.Dimension
import com.robin.tools.core.ui.EmptyState
import com.robin.tools.core.ui.FeatureCard
import com.robin.tools.core.ui.ToolsTopAppBar
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
    var permissionsGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        permissionsGranted = results.values.all { it }
    }

    val requestPermissions = {
        permissionLauncher.launch(
            arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
        )
    }

    // First entry: system dialog once; if denied, EmptyState offers retry.
    LaunchedEffect(Unit) {
        if (!permissionsGranted) {
            requestPermissions()
        }
    }

    Scaffold(
        topBar = {
            ToolsTopAppBar(
                title = stringResource(R.string.camera_feature_title),
                onBack = onBack,
                backContentDescription = stringResource(R.string.record_back)
            )
        }
    ) { padding ->
        if (!permissionsGranted) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                EmptyState(
                    icon = Icons.Default.Videocam,
                    title = stringResource(R.string.camera_permission_title),
                    description = stringResource(R.string.camera_permission_desc),
                    actionLabel = stringResource(R.string.grant_permission),
                    onAction = { requestPermissions() }
                )
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(Dimension.lg),
                verticalArrangement = Arrangement.spacedBy(Dimension.md)
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
                        title = stringResource(R.string.camera_text_to_video_title),
                        description = stringResource(R.string.camera_text_to_video_desc),
                        icon = Icons.Default.TextFields,
                        onClick = onTextToVideo
                    )
                }
            }
        }
    }
}
