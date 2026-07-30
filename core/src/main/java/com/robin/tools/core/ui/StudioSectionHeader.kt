package com.robin.tools.core.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Structural heading for a workbench category or a real workflow stage.
 * The calibration rail intentionally appears only at meaningful section boundaries.
 */
@Composable
fun StudioSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    eyebrow: String? = null,
    description: String? = null,
    accent: Color = MaterialTheme.colorScheme.primary
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Canvas(
            modifier = Modifier
                .width(Dimension.sm)
                .height(if (description == null) 34.dp else 52.dp)
                .padding(vertical = Dimension.xs)
        ) {
            val lineX = size.width / 2f
            drawLine(
                color = accent,
                start = androidx.compose.ui.geometry.Offset(lineX, 0f),
                end = androidx.compose.ui.geometry.Offset(lineX, size.height),
                strokeWidth = 2.dp.toPx()
            )
            repeat(4) { index ->
                val y = size.height * index / 3f
                drawLine(
                    color = accent.copy(alpha = 0.8f),
                    start = androidx.compose.ui.geometry.Offset(0f, y),
                    end = androidx.compose.ui.geometry.Offset(size.width, y),
                    strokeWidth = 1.dp.toPx()
                )
            }
        }
        Spacer(Modifier.width(Dimension.md))
        Column(verticalArrangement = Arrangement.spacedBy(Dimension.xxs)) {
            if (eyebrow != null) {
                Text(
                    text = eyebrow,
                    style = MaterialTheme.typography.labelSmall,
                    color = accent
                )
            }
            Text(text = title, style = MaterialTheme.typography.titleLarge)
            if (description != null) {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
