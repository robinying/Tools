package com.robin.tools.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.core.content.FileProvider
import com.robin.tools.R
import com.robin.tools.core.ui.Dimension
import com.robin.tools.core.ui.EmptyState
import com.robin.tools.core.ui.StudioActionButton
import com.robin.tools.core.ui.StudioActionStyle
import com.robin.tools.core.ui.StudioSectionHeader
import com.robin.tools.core.ui.StudioSurface
import com.robin.tools.core.ui.StudioSurfaceTone
import com.robin.tools.core.ui.ToolsTopAppBar
import com.robin.tools.history.TaskHistoryEntity
import com.robin.tools.history.TaskHistoryViewModel
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskHistoryScreen(
    viewModel: TaskHistoryViewModel,
    onBack: () -> Unit
) {
    val entries by viewModel.entries.collectAsStateWithLifecycle()
    val context = LocalContext.current

    Scaffold(
        topBar = {
            ToolsTopAppBar(
                title = stringResource(R.string.history_title),
                onBack = onBack,
                backContentDescription = stringResource(R.string.app_back)
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
            if (entries.isEmpty()) {
                EmptyState(
                    icon = Icons.Default.History,
                    title = stringResource(R.string.history_empty_title),
                    description = stringResource(R.string.history_empty_desc)
                )
            } else {
                StudioSectionHeader(title = stringResource(R.string.history_title))
                entries.forEach { entry ->
                    HistoryCard(
                        entry = entry,
                        onOpen = { openOutput(context, entry.outputUri) },
                        onShare = { shareOutput(context, entry.outputUri) }
                    )
                }
                Spacer(Modifier.height(Dimension.sm))
                StudioActionButton(
                    label = stringResource(R.string.history_clear),
                    onClick = viewModel::clear,
                    style = StudioActionStyle.DESTRUCTIVE
                )
            }
            Spacer(Modifier.height(Dimension.lg))
        }
    }
}

@Composable
private fun HistoryCard(
    entry: TaskHistoryEntity,
    onOpen: () -> Unit,
    onShare: () -> Unit
) {
    StudioSurface(tone = StudioSurfaceTone.STANDARD) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = entry.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                entry.detail?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = formatTime(entry.createdAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = stringResource(
                    if (entry.isSuccess) R.string.history_success else R.string.history_failure
                ),
                style = MaterialTheme.typography.labelMedium,
                color = if (entry.isSuccess) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.error
                }
            )
        }
        if (!entry.outputUri.isNullOrBlank()) {
            Spacer(Modifier.height(Dimension.sm))
            StudioActionButton(
                label = stringResource(R.string.history_open),
                onClick = onOpen,
                style = StudioActionStyle.SECONDARY
            )
            Spacer(Modifier.height(Dimension.sm))
            StudioActionButton(
                label = stringResource(R.string.history_share),
                onClick = onShare,
                style = StudioActionStyle.SECONDARY
            )
        }
    }
}

private fun formatTime(epochMillis: Long): String =
    SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(epochMillis))

private fun resolveUri(context: Context, raw: String?): Uri? {
    if (raw.isNullOrBlank()) return null
    return if (raw.startsWith("content://") || raw.startsWith("file://")) {
        Uri.parse(raw)
    } else {
        val file = File(raw)
        if (!file.exists()) return null
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }
}

private fun openOutput(context: Context, raw: String?) {
    val uri = resolveUri(context, raw) ?: return
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, context.contentResolver.getType(uri) ?: "*/*")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    runCatching { context.startActivity(intent) }
}

private fun shareOutput(context: Context, raw: String?) {
    val uri = resolveUri(context, raw) ?: return
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = context.contentResolver.getType(uri) ?: "*/*"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    runCatching { context.startActivity(Intent.createChooser(intent, null)) }
}
