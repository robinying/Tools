package com.robin.tools.feature.storage.presentation

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.robin.tools.core.ui.Dimension
import com.robin.tools.core.ui.StudioActionButton
import com.robin.tools.core.ui.StudioActionStyle
import com.robin.tools.core.ui.StudioSectionHeader
import com.robin.tools.core.ui.StudioSelectionOption
import com.robin.tools.core.ui.StudioSelectionRow
import com.robin.tools.core.ui.StudioSurface
import com.robin.tools.core.ui.StudioSurfaceTone
import com.robin.tools.core.ui.ToolsTopAppBar
import com.robin.tools.feature.storage.R
import com.robin.tools.feature.storage.data.DuplicateGroup
import com.robin.tools.feature.storage.data.LargeMediaFile
import com.robin.tools.feature.storage.data.MB
import com.robin.tools.feature.storage.data.StorageCleanupRepository
import com.robin.tools.feature.storage.data.StorageCleanupViewModel

/**
 * Read-only review of large files and suspected duplicates.
 *
 * Deletion is intentionally left to the system UI; this screen only helps the
 * user find candidates and open them.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StorageCleanupScreen(
    viewModel: StorageCleanupViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val report = state.report

    val requiredPermissions = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO)
        } else {
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }
    var permissionGranted by remember {
        mutableStateOf(
            requiredPermissions.all {
                ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
            }
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        // Android 14 partial access grants only a subset; any grant is usable.
        permissionGranted = result.values.any { it }
        if (permissionGranted) viewModel.scan()
    }

    Scaffold(
        topBar = {
            ToolsTopAppBar(
                title = stringResource(R.string.cleanup_title),
                onBack = onBack,
                backContentDescription = stringResource(R.string.back)
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Dimension.pageHorizontal, vertical = Dimension.lg),
            verticalArrangement = Arrangement.spacedBy(Dimension.md)
        ) {
            Text(
                text = stringResource(R.string.cleanup_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            StudioSectionHeader(title = stringResource(R.string.cleanup_threshold))
            StudioSelectionRow(
                options = StorageCleanupRepository.THRESHOLD_OPTIONS.map {
                    StudioSelectionOption(it.toString(), formatSize(it))
                },
                selectedKey = state.thresholdBytes.toString(),
                onOptionSelected = { viewModel.setThreshold(it.toLong()) },
                enabled = !state.isScanning
            )
            if (!permissionGranted) {
                Text(
                    text = stringResource(R.string.cleanup_permission_needed),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            StudioActionButton(
                label = stringResource(R.string.cleanup_scan),
                onClick = {
                    if (permissionGranted) {
                        viewModel.scan()
                    } else {
                        permissionLauncher.launch(requiredPermissions)
                    }
                },
                loading = state.isScanning
            )
            if (state.isScanning) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            report?.let {
                Summary(report.largeFiles.size, report.duplicateGroups.size, report.duplicateBytes)

                StudioSectionHeader(title = stringResource(R.string.cleanup_large_title))
                if (report.largeFiles.isEmpty()) {
                    Text(
                        text = stringResource(R.string.cleanup_large_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    report.largeFiles.forEach { file -> FileCard(file) }
                }

                StudioSectionHeader(title = stringResource(R.string.cleanup_duplicate_title))
                if (report.duplicateGroups.isEmpty()) {
                    Text(
                        text = stringResource(R.string.cleanup_duplicate_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    report.duplicateGroups.forEach { group -> DuplicateCard(group) }
                }
            }
            Spacer(Modifier.height(Dimension.lg))
        }
    }
}

@Composable
private fun Summary(largeCount: Int, groupCount: Int, duplicateBytes: Long) {
    StudioSurface(tone = StudioSurfaceTone.EMPHASIZED) {
        Text(
            text = stringResource(R.string.cleanup_summary, largeCount, groupCount),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = stringResource(R.string.cleanup_wasted, formatSize(duplicateBytes)),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun FileCard(file: LargeMediaFile) {
    val context = LocalContext.current
    StudioSurface(tone = StudioSurfaceTone.STANDARD) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = file.displayName,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = formatSize(file.sizeBytes),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Spacer(Modifier.height(Dimension.sm))
        StudioActionButton(
            label = stringResource(R.string.cleanup_open),
            onClick = { openFile(context, file) },
            style = StudioActionStyle.SECONDARY
        )
    }
}

@Composable
private fun DuplicateCard(group: DuplicateGroup) {
    val context = LocalContext.current
    StudioSurface(tone = StudioSurfaceTone.STANDARD) {
        Text(
            text = stringResource(
                R.string.cleanup_duplicate_group,
                group.files.size,
                formatSize(group.sizeBytes)
            ),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold
        )
        group.files.forEach { file ->
            Spacer(Modifier.height(Dimension.xs))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
            ) {
                Text(
                    text = file.displayName,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                androidx.compose.material3.TextButton(
                    onClick = { openFile(context, file) }
                ) {
                    Text(stringResource(R.string.cleanup_open))
                }
            }
        }
    }
}

private fun openFile(context: Context, file: LargeMediaFile) {
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(file.uri, file.mimeType ?: if (file.isVideo) "video/*" else "image/*")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    runCatching { context.startActivity(intent) }
}

private fun formatSize(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024) return String.format("%.0f KB", kb)
    val mb = kb / 1024.0
    if (mb < 1024) return String.format("%.1f MB", mb)
    return String.format("%.2f GB", mb / 1024.0)
}
