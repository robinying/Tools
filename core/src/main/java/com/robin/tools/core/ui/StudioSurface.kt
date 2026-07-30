package com.robin.tools.core.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Surface hierarchy for work panels, image/video previews, and contextual information.
 */
enum class StudioSurfaceTone {
    STANDARD,
    EMPHASIZED,
    OUTLINED
}

@Composable
fun StudioSurface(
    modifier: Modifier = Modifier,
    tone: StudioSurfaceTone = StudioSurfaceTone.STANDARD,
    contentPadding: PaddingValues = PaddingValues(Dimension.lg),
    content: @Composable ColumnScope.() -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val containerColor = when (tone) {
        StudioSurfaceTone.STANDARD -> colorScheme.surface
        StudioSurfaceTone.EMPHASIZED -> colorScheme.surfaceContainerHigh
        StudioSurfaceTone.OUTLINED -> colorScheme.surfaceVariant.copy(alpha = 0.7f)
    }
    val border = if (tone == StudioSurfaceTone.OUTLINED) {
        BorderStroke(1.dp, colorScheme.outlineVariant)
    } else {
        null
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = border,
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (tone == StudioSurfaceTone.EMPHASIZED) 2.dp else 0.dp
        )
    ) {
        androidx.compose.foundation.layout.Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(contentPadding),
            content = content
        )
    }
}
