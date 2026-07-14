package com.robin.tools.feature.face.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import coil.compose.AsyncImage
import com.robin.tools.core.ui.Dimension
import com.robin.tools.core.ui.ToolsTopAppBar
import com.robin.tools.feature.face.R
import com.robin.tools.feature.face.data.CompareResult
import com.robin.tools.feature.face.data.SimilarityLevel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FaceCompareScreen(
    viewModel: FaceCompareViewModel,
    onBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    val errorMessage = uiState.result?.errorMessage

    LaunchedEffect(errorMessage) {
        errorMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearResult()
        }
    }

    Scaffold(
        topBar = {
            ToolsTopAppBar(
                title = stringResource(R.string.face_compare_title),
                onBack = onBack,
                backContentDescription = stringResource(R.string.face_compare_back)
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = stringResource(
                    if (uiState.deepModelReady) R.string.face_compare_engine_deep
                    else R.string.face_compare_engine_fallback
                ),
                style = MaterialTheme.typography.labelMedium,
                color = if (uiState.deepModelReady) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = Dimension.sm),
                textAlign = TextAlign.Center
            )

            ImageSelectionRow(
                leftUri = uiState.leftImageUri,
                rightUri = uiState.rightImageUri,
                leftFaceCount = uiState.leftFaceCount,
                rightFaceCount = uiState.rightFaceCount,
                enabled = !uiState.isProcessing,
                onPickLeft = { viewModel.setLeftImage(it) },
                onPickRight = { viewModel.setRightImage(it) },
                onSwap = { viewModel.swapImages() }
            )

            Spacer(Modifier.height(Dimension.xl))

            Button(
                onClick = { viewModel.compare() },
                enabled = !uiState.isProcessing
                        && uiState.leftImageUri != null
                        && uiState.rightImageUri != null,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                if (uiState.isProcessing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(Modifier.width(Dimension.md))
                    Text(stringResource(R.string.face_compare_analyzing))
                } else {
                    Text(stringResource(R.string.face_compare_action), style = MaterialTheme.typography.titleMedium)
                }
            }

            Spacer(Modifier.height(Dimension.xl))

            AnimatedVisibility(
                visible = uiState.result != null && uiState.result!!.errorMessage == null,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                uiState.result?.let { result ->
                    ResultCard(result)
                }
            }
        }
    }
}

@Composable
private fun ImageSelectionRow(
    leftUri: android.net.Uri?,
    rightUri: android.net.Uri?,
    leftFaceCount: Int,
    rightFaceCount: Int,
    enabled: Boolean,
    onPickLeft: (android.net.Uri) -> Unit,
    onPickRight: (android.net.Uri) -> Unit,
    onSwap: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        ImageSelectorCard(
            uri = leftUri,
            faceCount = leftFaceCount,
            label = "A",
            enabled = enabled,
            onPick = onPickLeft,
            modifier = Modifier.weight(1f)
        )

        IconButton(
            onClick = onSwap,
            enabled = enabled,
            modifier = Modifier.padding(horizontal = 4.dp)
        ) {
            Icon(
                Icons.Default.SwapHoriz,
                contentDescription = stringResource(R.string.face_compare_swap),
                modifier = Modifier.size(28.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        }

        ImageSelectorCard(
            uri = rightUri,
            faceCount = rightFaceCount,
            label = "B",
            enabled = enabled,
            onPick = onPickRight,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun ImageSelectorCard(
    uri: android.net.Uri?,
    faceCount: Int,
    label: String,
    enabled: Boolean,
    onPick: (android.net.Uri) -> Unit,
    modifier: Modifier = Modifier
) {
    val picker = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri: android.net.Uri? -> uri?.let { onPick(it) } }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp)
                .then(
                    if (enabled) Modifier.clickable { picker.launch("image/*") }
                    else Modifier
                ),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
            )
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                if (uri != null) {
                    AsyncImage(
                        model = uri,
                        contentDescription = stringResource(R.string.face_compare_selected_photo, label),
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.AddAPhoto,
                            contentDescription = null,
                            modifier = Modifier.size(32.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(Dimension.xs))
                        Text(
                            stringResource(R.string.face_compare_select_photo),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(Dimension.xs))

        Text(
            text = stringResource(R.string.face_compare_photo_label, label),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium
        )

        if (faceCount >= 0) {
            Text(
                text = when {
                    faceCount == 0 -> stringResource(R.string.face_compare_no_faces_short)
                    faceCount == 1 -> stringResource(R.string.face_compare_one_face)
                    else -> stringResource(R.string.face_compare_n_faces, faceCount)
                },
                style = MaterialTheme.typography.labelSmall,
                color = if (faceCount > 0) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}

@Composable
private fun ResultCard(result: CompareResult) {
    val (color, labelResId) = when (result.level) {
        SimilarityLevel.HIGH -> MaterialTheme.colorScheme.primary to R.string.face_compare_high
        SimilarityLevel.MEDIUM -> MaterialTheme.colorScheme.tertiary to R.string.face_compare_medium
        SimilarityLevel.LOW -> MaterialTheme.colorScheme.error to R.string.face_compare_low
        SimilarityLevel.NONE -> MaterialTheme.colorScheme.error to R.string.face_compare_none
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = stringResource(R.string.face_compare_result, result.similarityScore * 100f),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )

            Spacer(Modifier.height(Dimension.sm))

            Text(
                text = stringResource(labelResId),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = color
            )

            Spacer(Modifier.height(Dimension.md))

            LinearProgressIndicator(
                progress = { result.similarityScore.coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = color,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                strokeCap = StrokeCap.Round
            )

            Spacer(Modifier.height(Dimension.lg))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        stringResource(R.string.face_compare_left_image),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        stringResource(R.string.face_compare_n_face_s, result.faceCountLeft),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        stringResource(R.string.face_compare_right_image),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        stringResource(R.string.face_compare_n_face_s, result.faceCountRight),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}
