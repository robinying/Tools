package com.robin.tools.feature.media.ui.screens

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.robin.tools.core.ui.Dimension
import com.robin.tools.core.ui.StudioActionButton
import com.robin.tools.core.ui.StudioActionStyle
import com.robin.tools.core.ui.StudioSectionHeader
import com.robin.tools.core.ui.StudioSurface
import com.robin.tools.core.ui.StudioSurfaceTone
import com.robin.tools.core.ui.ToolsTopAppBar
import com.robin.tools.feature.media.R
import com.robin.tools.feature.media.ui.viewmodel.DocumentScanViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentScanScreen(
    viewModel: DocumentScanViewModel,
    onBack: () -> Unit,
    initialUri: String? = null,
    onSaved: (com.robin.tools.core.media.SavedMedia) -> Unit = {}
) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? -> uri?.let(viewModel::selectImage) }

    LaunchedEffect(initialUri) {
        initialUri?.let { viewModel.selectImage(Uri.parse(it)) }
    }
    LaunchedEffect(state.message) {
        state.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessage()
        }
    }
    LaunchedEffect(state.saved) {
        state.saved?.let(onSaved)
    }

    Scaffold(
        topBar = {
            ToolsTopAppBar(
                title = stringResource(R.string.doc_scan_title),
                onBack = onBack,
                backContentDescription = stringResource(R.string.back)
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
                text = stringResource(R.string.doc_scan_desc),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            StudioActionButton(
                label = stringResource(R.string.doc_scan_select),
                onClick = { picker.launch("image/*") },
                style = StudioActionStyle.SECONDARY,
                enabled = !state.isBusy
            )
            state.sourceUri?.let { uri ->
                StudioSurface(tone = StudioSurfaceTone.OUTLINED) {
                    AsyncImage(
                        model = uri,
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(Dimension.xxxl * 5),
                        contentScale = ContentScale.Fit
                    )
                }
            }
            StudioActionButton(
                label = stringResource(R.string.doc_scan_action),
                onClick = viewModel::scan,
                loading = state.isBusy,
                enabled = state.sourceUri != null && !state.isBusy
            )
            if (state.isBusy) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            val result = state.result
            if (result != null && !result.isRecycled) {
                StudioSectionHeader(
                    eyebrow = stringResource(R.string.doc_scan_result),
                    title = stringResource(
                        if (state.pageDetected) {
                            R.string.doc_scan_detected
                        } else {
                            R.string.doc_scan_full_image
                        }
                    )
                )
                StudioSurface(tone = StudioSurfaceTone.STANDARD) {
                    Image(
                        bitmap = result.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(Dimension.xxxl * 6),
                        contentScale = ContentScale.Fit
                    )
                }
                StudioActionButton(
                    label = stringResource(R.string.doc_scan_save),
                    onClick = viewModel::save,
                    loading = state.isBusy,
                    enabled = !state.isBusy
                )
                state.saved?.let { saved ->
                    StudioActionButton(
                        label = stringResource(R.string.toolkit_share_saved),
                        onClick = {
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = saved.mimeType
                                putExtra(Intent.EXTRA_STREAM, saved.uri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            runCatching {
                                context.startActivity(Intent.createChooser(intent, null))
                            }
                        },
                        style = StudioActionStyle.SECONDARY
                    )
                }
            }
            Spacer(Modifier.height(Dimension.lg))
        }
    }
}
