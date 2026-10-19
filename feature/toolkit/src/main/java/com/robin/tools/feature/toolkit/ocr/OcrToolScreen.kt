package com.robin.tools.feature.toolkit.ocr

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
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
import com.robin.tools.core.ui.StudioSurface
import com.robin.tools.core.ui.StudioSurfaceTone
import com.robin.tools.core.ui.ToolsTopAppBar
import com.robin.tools.feature.toolkit.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OcrToolScreen(
    viewModel: OcrToolViewModel,
    onBack: () -> Unit,
    initialUri: String? = null
) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let(viewModel::recognize)
    }

    LaunchedEffect(initialUri) {
        initialUri?.let { viewModel.recognize(Uri.parse(it)) }
    }
    LaunchedEffect(state.message) {
        state.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    Scaffold(
        topBar = {
            ToolsTopAppBar(
                title = stringResource(R.string.toolkit_ocr_title),
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
                text = stringResource(R.string.toolkit_ocr_desc),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (state.sourceUri == null) {
                EmptyState(
                    icon = Icons.Default.DocumentScanner,
                    title = stringResource(R.string.ocr_pick_title),
                    description = stringResource(R.string.ocr_pick_desc),
                    actionLabel = stringResource(R.string.toolkit_select_image),
                    onAction = { picker.launch("image/*") }
                )
            } else {
                StudioSurface(tone = StudioSurfaceTone.OUTLINED) {
                    AsyncImage(
                        model = state.sourceUri,
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(Dimension.xxxl * 4),
                        contentScale = ContentScale.Fit
                    )
                }
                StudioActionButton(
                    label = stringResource(R.string.ocr_recognize),
                    onClick = { state.sourceUri?.let(viewModel::recognize) },
                    style = StudioActionStyle.SECONDARY,
                    enabled = !state.isBusy
                )

                if (state.isBusy) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }

                if (state.text.isNotBlank()) {
                    StudioSectionHeader(
                        title = stringResource(R.string.ocr_result_title),
                        eyebrow = stringResource(R.string.toolkit_section_result)
                    )
                    StudioSurface(tone = StudioSurfaceTone.STANDARD) {
                        SelectionContainer {
                            Text(
                                text = state.text,
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }
                    StudioActionButton(
                        label = stringResource(R.string.toolkit_copy),
                        onClick = { copyText(context, state.text) },
                        style = StudioActionStyle.SECONDARY
                    )
                    StudioActionButton(
                        label = stringResource(R.string.toolkit_share),
                        onClick = { shareText(context, state.text) },
                        style = StudioActionStyle.SECONDARY
                    )
                }
            }

            Spacer(Modifier.height(Dimension.lg))
        }
    }
}

private fun copyText(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("OCR", text))
}

private fun shareText(context: Context, text: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(intent, null))
}
