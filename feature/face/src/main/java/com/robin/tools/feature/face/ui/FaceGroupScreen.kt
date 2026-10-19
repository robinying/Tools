package com.robin.tools.feature.face.ui

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Groups
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
import com.robin.tools.feature.face.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FaceGroupScreen(
    viewModel: FaceGroupViewModel,
    onBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetMultipleContents()
    ) { uris -> if (uris.isNotEmpty()) viewModel.group(uris) }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    Scaffold(
        topBar = {
            ToolsTopAppBar(
                title = stringResource(R.string.face_group_title),
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
                text = stringResource(R.string.face_group_desc),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            StudioActionButton(
                label = stringResource(R.string.face_group_select),
                onClick = { picker.launch("image/*") },
                style = StudioActionStyle.SECONDARY,
                enabled = !state.isBusy
            )
            if (state.isBusy) {
                LinearProgressIndicator(
                    progress = { state.progress },
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = stringResource(R.string.face_group_progress, state.processed),
                    style = MaterialTheme.typography.bodySmall
                )
            }
            if (!state.isBusy && state.groups.isEmpty()) {
                EmptyState(
                    icon = Icons.Default.Groups,
                    title = stringResource(R.string.face_group_empty_title),
                    description = stringResource(R.string.face_group_empty_desc)
                )
            }
            state.groups.forEachIndexed { index, group ->
                StudioSectionHeader(
                    eyebrow = stringResource(R.string.face_group_group),
                    title = stringResource(R.string.face_group_group_title, index + 1, group.uris.size)
                )
                StudioSurface(tone = StudioSurfaceTone.STANDARD) {
                    group.uris.forEach { uri ->
                        Row(modifier = Modifier.fillMaxWidth()) {
                            AsyncImage(
                                model = uri,
                                contentDescription = null,
                                modifier = Modifier.size(Dimension.xxxl),
                                contentScale = ContentScale.Crop
                            )
                            Spacer(Modifier.size(Dimension.sm))
                            Text(
                                text = uri.lastPathSegment ?: uri.toString(),
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        Spacer(Modifier.height(Dimension.sm))
                    }
                }
            }
            Spacer(Modifier.height(Dimension.lg))
        }
    }
}
