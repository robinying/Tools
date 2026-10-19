package com.robin.tools.feature.toolkit.image

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
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Slider
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
import androidx.compose.ui.text.font.FontWeight
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
import com.robin.tools.core.media.SavedMedia
import kotlin.math.roundToInt

/**
 * One screen for every image operation. Options switch with [tool] while the
 * source picker, preview, and result handling stay shared.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImageToolScreen(
    viewModel: ImageToolViewModel,
    tool: ImageToolType,
    onBack: () -> Unit,
    initialUri: String? = null,
    onSaved: (SavedMedia) -> Unit = {}
) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    var format by remember { mutableStateOf(ImageFormat.JPEG) }
    var quality by remember { mutableIntStateOf(90) }
    var scalePercent by remember { mutableIntStateOf(50) }
    var rotationDegrees by remember { mutableIntStateOf(90) }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let(viewModel::selectImage)
    }

    LaunchedEffect(initialUri) {
        initialUri?.let { viewModel.selectImage(Uri.parse(it)) }
    }
    LaunchedEffect(state.sourceUri, tool) {
        if (state.sourceUri != null && tool == ImageToolType.EXIF) {
            viewModel.loadExif()
        }
    }
    LaunchedEffect(state.message) {
        state.message?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.clearMessage()
        }
    }
    LaunchedEffect(state.output) {
        viewModel.consumeNewOutput()?.let(onSaved)
    }

    Scaffold(
        topBar = {
            ToolsTopAppBar(
                title = stringResource(tool.titleRes),
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
                text = stringResource(tool.descriptionRes),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (state.sourceUri == null) {
                EmptyState(
                    icon = Icons.Default.Image,
                    title = stringResource(R.string.toolkit_no_image_title),
                    description = stringResource(R.string.toolkit_no_image_desc),
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
                            .height(Dimension.xxxl * 5),
                        contentScale = ContentScale.Fit
                    )
                }
                StudioActionButton(
                    label = stringResource(R.string.toolkit_reselect_image),
                    onClick = { picker.launch("image/*") },
                    style = StudioActionStyle.SECONDARY,
                    enabled = !state.isBusy
                )

                StudioSectionHeader(
                    eyebrow = stringResource(R.string.toolkit_section_options),
                    title = stringResource(tool.titleRes),
                    description = null
                )

                when (tool) {
                    ImageToolType.CONVERT -> {
                        FormatSelector(format) { format = it }
                        QualitySlider(quality) { quality = it }
                    }

                    ImageToolType.RESIZE -> {
                        Text(
                            text = stringResource(R.string.image_scale_value, scalePercent),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Slider(
                            value = scalePercent.toFloat(),
                            onValueChange = { scalePercent = it.roundToInt() },
                            valueRange = 5f..100f,
                            enabled = !state.isBusy
                        )
                        FormatSelector(format) { format = it }
                        QualitySlider(quality) { quality = it }
                    }

                    ImageToolType.ROTATE -> {
                        StudioSelectionRow(
                            options = listOf(
                                StudioSelectionOption("90", "90°"),
                                StudioSelectionOption("180", "180°"),
                                StudioSelectionOption("270", "270°")
                            ),
                            selectedKey = rotationDegrees.toString(),
                            onOptionSelected = { rotationDegrees = it.toInt() },
                            enabled = !state.isBusy
                        )
                        FormatSelector(format) { format = it }
                        QualitySlider(quality) { quality = it }
                    }

                    ImageToolType.EXIF -> {
                        ExifFields(state.exifFields)
                        Text(
                            text = stringResource(R.string.image_exif_note),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    ImageToolType.TO_PDF -> {
                        Text(
                            text = stringResource(R.string.image_pdf_note),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                StudioActionButton(
                    label = stringResource(
                        when (tool) {
                            ImageToolType.EXIF -> R.string.image_strip_metadata
                            ImageToolType.TO_PDF -> R.string.image_export_pdf
                            else -> R.string.image_process
                        }
                    ),
                    onClick = {
                        if (tool == ImageToolType.EXIF) {
                            viewModel.stripMetadata(format, quality)
                        } else {
                            viewModel.process(tool, format, quality, scalePercent, rotationDegrees)
                        }
                    },
                    loading = state.isBusy,
                    enabled = !state.isBusy
                )

                if (state.isBusy) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }

                state.output?.let { saved ->
                    ResultCard(saved) {
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = saved.mimeType
                            putExtra(Intent.EXTRA_STREAM, saved.uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(Intent.createChooser(intent, null))
                    }
                }
            }

            Spacer(Modifier.height(Dimension.lg))
        }
    }
}

@Composable
private fun FormatSelector(selected: ImageFormat, onSelect: (ImageFormat) -> Unit) {
    Text(
        text = stringResource(R.string.image_format),
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.SemiBold
    )
    StudioSelectionRow(
        options = ImageFormat.entries.map {
            StudioSelectionOption(it.name, stringResource(it.labelRes))
        },
        selectedKey = selected.name,
        onOptionSelected = { key ->
            ImageFormat.entries.firstOrNull { it.name == key }?.let(onSelect)
        }
    )
}

@Composable
private fun QualitySlider(quality: Int, onQuality: (Int) -> Unit) {
    Text(
        text = stringResource(R.string.image_quality_value, quality),
        style = MaterialTheme.typography.bodyMedium
    )
    Slider(
        value = quality.toFloat(),
        onValueChange = { onQuality(it.roundToInt()) },
        valueRange = 10f..100f
    )
}

@Composable
private fun ExifFields(fields: List<ExifField>) {
    if (fields.isEmpty()) {
        Text(
            text = stringResource(R.string.toolkit_reading_exif),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        return
    }
    StudioSurface(tone = StudioSurfaceTone.STANDARD) {
        fields.forEach { field ->
            Text(
                text = field.label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = field.value,
                style = MaterialTheme.typography.bodyLarge
            )
            Spacer(Modifier.height(Dimension.sm))
        }
    }
}

@Composable
private fun ResultCard(saved: SavedMedia, onShare: () -> Unit) {
    StudioSurface(tone = StudioSurfaceTone.EMPHASIZED) {
        Text(
            text = stringResource(R.string.toolkit_section_result),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(Dimension.xs))
        Text(
            text = saved.displayName,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(Dimension.sm))
        StudioActionButton(
            label = stringResource(R.string.toolkit_share),
            onClick = onShare,
            style = StudioActionStyle.SECONDARY
        )
    }
}
