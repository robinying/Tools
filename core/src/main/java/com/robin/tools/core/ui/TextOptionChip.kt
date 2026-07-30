package com.robin.tools.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** Compact selectable option with a visible marker in addition to its color state. */
@Composable
fun TextOptionChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colorScheme = MaterialTheme.colorScheme
    val textColor = when {
        !enabled -> colorScheme.onSurface.copy(alpha = 0.38f)
        selected -> colorScheme.primary
        else -> colorScheme.onSurfaceVariant
    }
    val containerColor = if (selected) {
        colorScheme.primaryContainer.copy(alpha = 0.68f)
    } else {
        colorScheme.surfaceVariant.copy(alpha = 0.38f)
    }
    Row(
        modifier = modifier
            .defaultMinSize(minHeight = Dimension.compactControl)
            .clip(MaterialTheme.shapes.medium)
            .background(containerColor)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = Dimension.md, vertical = Dimension.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (selected) {
            androidx.compose.foundation.layout.Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(androidx.compose.foundation.shape.CircleShape)
                    .background(textColor)
            )
            Spacer(Modifier.width(Dimension.sm))
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = textColor,
            maxLines = 1
        )
    }
}
