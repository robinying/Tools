package com.robin.tools.feature.face.ui

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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Face
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.robin.tools.core.ui.StudioSelectionOption
import com.robin.tools.core.ui.StudioSelectionRow
import com.robin.tools.core.ui.StudioSurface
import com.robin.tools.core.ui.StudioSurfaceTone
import com.robin.tools.core.ui.ToolsTopAppBar
import com.robin.tools.feature.face.R
import com.robin.tools.feature.face.data.FaceBlurMode
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FaceBlurScreen(
    viewModel: FaceBlurViewModel,
    onBack: () -> Unit,
    initialUri: String? = null,
    onSaved: (com.robin.tools.core.media.SavedMedia) -> Unit = {}
) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var mode by remember { mutableStateOf(FaceBlurMode.MOSAIC) }
    var strength by remember { mutableIntStateOf(3) }

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
    LaunchedEffect(state.output) {
        viewModel.consumeNewOutput()?.let(onSaved)
    }

    Scaffold(
        topBar = {
            ToolsTopAppBar(
                title = stringResource(R.string.face_blur_title),
                onBack = onBack,
                backContentDescription = stringResource(R.string.face_compare_back)
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
                text = stringResource(R.string.face_blur_desc),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (state.sourceUri == null) {
                EmptyState(
                    icon = Icons.Default.Face,
                    title = stringResource(R.string.face_blur_empty_title),
                    description = stringResource(R.string.face_blur_empty_desc),
                    actionLabel = stringResource(R.string.face_compare_select_photo),
                    onAction = { picker.launch("image/*") }
                )
            } else {
                StudioSurface(tone = StudioSurfaceTone.OUTLINED) {
                    AsyncImage(
                        model = state.sourceUri,
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(Dimension.xxxl * 5),
                        contentScale = ContentScale.Fit
                    )
                }
                StudioActionButton(
                    label = stringResource(R.string.face_compare_select_photo),
                    onClick = { picker.launch("image/*") },
                    style = StudioActionStyle.SECONDARY,
                    enabled = !state.isBusy
                )

                StudioSectionHeader(
                    eyebrow = stringResource(R.string.face_blur_mode),
                    title = stringResource(R.string.face_blur_title)
                )
                StudioSelectionRow(
                    options = listOf(
                        StudioSelectionOption(
                            FaceBlurMode.MOSAIC.name,
                            stringResource(R.string.face_blur_mosaic)
                        ),
                        StudioSelectionOption(
                            FaceBlurMode.SOFT.name,
                            stringResource(R.string.face_blur_soft)
                        )
                    ),
                    selectedKey = mode.name,
                    onOptionSelected = { key ->
                        FaceBlurMode.entries.firstOrNull { it.name == key }?.let { mode = it }
                    },
                    enabled = !state.isBusy
                )
                Text(
                    text = stringResource(R.string.face_blur_strength, strength),
                    style = MaterialTheme.typography.bodyMedium
                )
                Slider(
                    value = strength.toFloat(),
                    onValueChange = { strength = it.roundToInt().coerceIn(1, 5) },
                    valueRange = 1f..5f,
                    steps = 3,
                    enabled = !state.isBusy
                )

                StudioActionButton(
                    label = stringResource(R.string.face_blur_action),
                    onClick = { viewModel.process(mode, strength) },
                    loading = state.isBusy,
                    enabled = !state.isBusy
                )

                if (state.isBusy) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }

                if (state.faceCount > 0) {
                    Text(
                        text = stringResource(R.string.face_compare_face_count, state.faceCount),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                state.output?.let { saved ->
                    StudioSurface(tone = StudioSurfaceTone.EMPHASIZED) {
                        Text(
                            text = saved.displayName,
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Spacer(Modifier.height(Dimension.sm))
                        StudioActionButton(
                            label = stringResource(R.string.face_blur_share),
                            onClick = {
                                val intent = Intent(Intent.ACTION_SEND).apply {
                                    type = saved.mimeType
                                    putExtra(Intent.EXTRA_STREAM, saved.uri)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(Intent.createChooser(intent, null))
                            },
                            style = StudioActionStyle.SECONDARY
                        )
                    }
                }
            }
            Spacer(Modifier.height(Dimension.lg))
        }
    }
}
