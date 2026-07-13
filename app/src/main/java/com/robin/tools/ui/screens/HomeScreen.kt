package com.robin.tools.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.robin.tools.R
import com.robin.tools.core.ui.Dimension
import com.robin.tools.core.ui.FeatureCard

@Composable
fun HomeScreen(
    onMediaClick: () -> Unit,
    onEbookClick: () -> Unit,
    onLightLuxClick: () -> Unit,
    onFaceCompareClick: () -> Unit,
    onCameraClick: () -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(Dimension.xl),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = stringResource(R.string.home_title),
            style = MaterialTheme.typography.headlineLarge,
            modifier = Modifier.padding(bottom = Dimension.sm, top = Dimension.xxxl)
        )
        Text(
            text = stringResource(R.string.home_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = Dimension.xxxl)
        )

        FeatureCard(
            title = stringResource(R.string.media_editor_title),
            description = stringResource(R.string.media_editor_desc),
            icon = Icons.Default.Image,
            onClick = onMediaClick
        )
        Spacer(Modifier.height(Dimension.lg))
        FeatureCard(
            title = stringResource(R.string.ebook_converter_title),
            description = stringResource(R.string.ebook_converter_desc),
            icon = Icons.AutoMirrored.Filled.MenuBook,
            onClick = onEbookClick
        )
        Spacer(Modifier.height(Dimension.lg))
        FeatureCard(
            title = stringResource(R.string.light_meter_title),
            description = stringResource(R.string.light_meter_desc),
            icon = Icons.Default.LightMode,
            onClick = onLightLuxClick
        )
        Spacer(Modifier.height(Dimension.lg))
        FeatureCard(
            title = stringResource(R.string.face_compare_title),
            description = stringResource(R.string.face_compare_desc),
            icon = Icons.Default.Face,
            onClick = onFaceCompareClick
        )
        Spacer(Modifier.height(Dimension.lg))
        FeatureCard(
            title = stringResource(R.string.camera_title),
            description = stringResource(R.string.camera_desc),
            icon = Icons.Default.Videocam,
            onClick = onCameraClick
        )

        Spacer(Modifier.height(Dimension.xxl))
    }
}
