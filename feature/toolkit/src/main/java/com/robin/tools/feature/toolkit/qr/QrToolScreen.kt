package com.robin.tools.feature.toolkit.qr

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.robin.tools.core.ui.Dimension
import com.robin.tools.core.ui.EmptyState
import com.robin.tools.core.ui.StudioActionButton
import com.robin.tools.core.ui.StudioActionStyle
import com.robin.tools.core.ui.StudioSectionHeader
import com.robin.tools.core.ui.StudioSelectionOption
import com.robin.tools.core.ui.StudioSelectionRow
import com.robin.tools.core.ui.StudioSurface
import com.robin.tools.core.ui.StudioSurfaceTone
import com.robin.tools.core.ui.ToolsTopAppBar
import com.robin.tools.feature.toolkit.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QrToolScreen(
    viewModel: QrToolViewModel,
    onBack: () -> Unit,
    initialText: String? = null,
    initialUri: String? = null,
    onSaved: (com.robin.tools.core.media.SavedMedia) -> Unit = {}
) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let(viewModel::decode)
    }

    LaunchedEffect(initialText) {
        initialText?.takeIf { it.isNotBlank() }?.let(viewModel::setContent)
    }
    LaunchedEffect(initialUri) {
        initialUri?.let { viewModel.decode(Uri.parse(it)) }
    }
    LaunchedEffect(state.message) {
        state.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessage()
        }
    }
    LaunchedEffect(state.saved) {
        viewModel.consumeNewOutput()?.let(onSaved)
    }

    Scaffold(
        topBar = {
            ToolsTopAppBar(
                title = stringResource(R.string.toolkit_qr_title),
                onBack = onBack,
                backContentDescription = stringResource(R.string.toolkit_back)
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Dimension.pageHorizontal, vertical = Dimension.lg),
            verticalArrangement = Arrangement.spacedBy(Dimension.lg)
        ) {
            Text(
                text = stringResource(R.string.toolkit_qr_desc),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            StudioSelectionRow(
                options = listOf(
                    StudioSelectionOption(QrMode.GENERATE.name, stringResource(R.string.qr_mode_generate)),
                    StudioSelectionOption(QrMode.DECODE.name, stringResource(R.string.qr_mode_decode))
                ),
                selectedKey = state.mode.name,
                onOptionSelected = { key ->
                    QrMode.entries.firstOrNull { it.name == key }?.let(viewModel::setMode)
                },
                enabled = !state.isBusy
            )

            when (state.mode) {
                QrMode.GENERATE -> GenerateSection(
                    viewModel = viewModel,
                    state = state,
                    onShare = { saved ->
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = saved.mimeType
                            putExtra(Intent.EXTRA_STREAM, saved.uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(Intent.createChooser(intent, null))
                    }
                )

                QrMode.DECODE -> DecodeSection(
                    state = state,
                    onPick = { picker.launch("image/*") },
                    onCopy = { value -> copyToClipboard(context, value) }
                )
            }

            if (state.isBusy) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            Spacer(Modifier.height(Dimension.lg))
        }
    }
}

@Composable
private fun GenerateSection(
    viewModel: QrToolViewModel,
    state: QrToolViewModel.UiState,
    onShare: (com.robin.tools.core.media.SavedMedia) -> Unit
) {
    OutlinedTextField(
        value = state.content,
        onValueChange = viewModel::setContent,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(stringResource(R.string.qr_content)) },
        placeholder = { Text(stringResource(R.string.qr_content_hint)) },
        minLines = 2
    )
    StudioActionButton(
        label = stringResource(R.string.qr_generate_action),
        onClick = viewModel::generate,
        enabled = state.content.isNotBlank() && !state.isBusy
    )

    state.generated?.takeIf { !it.isRecycled }?.let { bitmap ->
        StudioSurface(tone = StudioSurfaceTone.OUTLINED) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .size(Dimension.xxxl * 4),
                contentScale = ContentScale.Fit
            )
        }
        StudioActionButton(
            label = stringResource(R.string.qr_save_action),
            onClick = viewModel::saveGenerated,
            style = StudioActionStyle.SECONDARY,
            loading = state.isBusy
        )
        state.saved?.let { saved ->
            StudioSurface(tone = StudioSurfaceTone.EMPHASIZED) {
                Text(text = saved.displayName, style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(Dimension.sm))
                StudioActionButton(
                    label = stringResource(R.string.toolkit_share),
                    onClick = { onShare(saved) },
                    style = StudioActionStyle.SECONDARY
                )
            }
        }
    }
}

@Composable
private fun DecodeSection(
    state: QrToolViewModel.UiState,
    onPick: () -> Unit,
    onCopy: (String) -> Unit
) {
    if (state.scannedUri == null) {
        EmptyState(
            icon = Icons.Default.QrCode,
            title = stringResource(R.string.qr_no_result),
            description = stringResource(R.string.qr_decode_hint),
            actionLabel = stringResource(R.string.toolkit_select_image),
            onAction = onPick
        )
        return
    }

    StudioSurface(tone = StudioSurfaceTone.OUTLINED) {
        AsyncImage(
            model = state.scannedUri,
            contentDescription = null,
            modifier = Modifier
                .fillMaxWidth()
                .height(Dimension.xxxl * 4),
            contentScale = ContentScale.Fit
        )
    }
    StudioActionButton(
        label = stringResource(R.string.toolkit_reselect_image),
        onClick = onPick,
        style = StudioActionStyle.SECONDARY,
        enabled = !state.isBusy
    )

    if (state.decodedValues.isNotEmpty()) {
        StudioSectionHeader(
            title = stringResource(R.string.qr_results),
            eyebrow = stringResource(R.string.toolkit_section_result)
        )
        state.decodedValues.forEach { value ->
            StudioSurface(tone = StudioSurfaceTone.STANDARD) {
                Text(text = value, style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(Dimension.sm))
                StudioActionButton(
                    label = stringResource(R.string.toolkit_copy),
                    onClick = { onCopy(value) },
                    style = StudioActionStyle.SECONDARY
                )
            }
        }
    }
}

private fun copyToClipboard(context: Context, value: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("QR", value))
}
