package com.robin.tools.feature.camera.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
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
                subtitle = stringResource(R.string.camera_record_desc),
                icon = Icons.Default.Videocam,
                onClick = onRecord
            )
            FeatureCard(
                title = stringResource(R.string.camera_edit_title),
                subtitle = stringResource(R.string.camera_edit_desc),
                icon = Icons.Default.Edit,
                onClick = onEditVideo
            )
            FeatureCard(
                title = stringResource(R.string.camera_trim_title),
                subtitle = stringResource(R.string.camera_trim_desc),
                icon = Icons.Default.ContentCut,
                onClick = onTrimVideo
            )
            FeatureCard(
                title = stringResource(R.string.camera_cover_title),
                subtitle = stringResource(R.string.camera_cover_desc),
                icon = Icons.Default.Collections,
                onClick = onCoverSelect
            )
            if (onTextToVideo != null) {
                FeatureCard(
                    title = "Text to Video",
                    subtitle = "Generate a video from text content",
                    icon = Icons.Default.TextFields,
                    onClick = onTextToVideo
                )
            }
        }
    }
}

@Composable
private fun FeatureCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(40.dp), tint = MaterialTheme.colorScheme.primary)
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text(subtitle, fontSize = 14.sp, color = Color.Gray)
            }
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = Color.Gray)
        }
    }
}
