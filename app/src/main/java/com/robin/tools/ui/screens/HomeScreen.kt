package com.robin.tools.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.robin.tools.R
import com.robin.tools.core.ui.Dimension
import com.robin.tools.core.ui.FeatureCard
import com.robin.tools.core.ui.FeatureCardEmphasis
import com.robin.tools.core.ui.StudioSectionHeader
import com.robin.tools.core.ui.StudioSurface
import com.robin.tools.core.ui.StudioSurfaceTone

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
            .padding(horizontal = Dimension.pageHorizontal),
        verticalArrangement = Arrangement.spacedBy(Dimension.lg)
    ) {
        Spacer(Modifier.height(Dimension.lg))
        HomeHero()

        StudioSectionHeader(
            eyebrow = stringResource(R.string.home_section_create),
            title = stringResource(R.string.home_create_title),
            description = stringResource(R.string.home_create_desc),
            accent = MaterialTheme.colorScheme.tertiary
        )
        FeatureCard(
            title = stringResource(R.string.camera_title),
            description = stringResource(R.string.camera_desc),
            icon = Icons.Default.Videocam,
            onClick = onCameraClick,
            accent = MaterialTheme.colorScheme.tertiary,
            emphasis = FeatureCardEmphasis.PRIMARY,
            eyebrow = stringResource(R.string.home_primary_tool)
        )
        FeatureCard(
            title = stringResource(R.string.media_editor_title),
            description = stringResource(R.string.media_editor_desc),
            icon = Icons.Default.Image,
            onClick = onMediaClick,
            accent = MaterialTheme.colorScheme.primary
        )

        StudioSectionHeader(
            eyebrow = stringResource(R.string.home_section_utility),
            title = stringResource(R.string.home_utility_title),
            description = stringResource(R.string.home_utility_desc),
            accent = MaterialTheme.colorScheme.secondary
        )
        FeatureCard(
            title = stringResource(R.string.ebook_converter_title),
            description = stringResource(R.string.ebook_converter_desc),
            icon = Icons.AutoMirrored.Filled.MenuBook,
            onClick = onEbookClick,
            accent = MaterialTheme.colorScheme.primary
        )
        FeatureCard(
            title = stringResource(R.string.light_meter_title),
            description = stringResource(R.string.light_meter_desc),
            icon = Icons.Default.LightMode,
            onClick = onLightLuxClick,
            accent = MaterialTheme.colorScheme.secondary
        )
        FeatureCard(
            title = stringResource(R.string.face_compare_title),
            description = stringResource(R.string.face_compare_desc),
            icon = Icons.Default.Face,
            onClick = onFaceCompareClick,
            accent = MaterialTheme.colorScheme.tertiary
        )
        Spacer(Modifier.height(Dimension.xl))
    }
}

@Composable
private fun HomeHero() {
    StudioSurface(tone = StudioSurfaceTone.EMPHASIZED) {
        Text(
            text = stringResource(R.string.home_eyebrow),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(Dimension.sm))
        Text(
            text = stringResource(R.string.home_title),
            style = MaterialTheme.typography.headlineLarge
        )
        Spacer(Modifier.height(Dimension.xs))
        Text(
            text = stringResource(R.string.home_subtitle),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
