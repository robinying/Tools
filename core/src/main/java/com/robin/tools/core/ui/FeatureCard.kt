package com.robin.tools.core.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** A feature entry with controlled workbench emphasis and category accent. */
enum class FeatureCardEmphasis {
    STANDARD,
    PRIMARY
}

@Composable
fun FeatureCard(
    title: String,
    description: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    iconContentDescription: String? = title,
    enabled: Boolean = true,
    accent: Color = MaterialTheme.colorScheme.primary,
    emphasis: FeatureCardEmphasis = FeatureCardEmphasis.STANDARD,
    eyebrow: String? = null
) {
    val colorScheme = MaterialTheme.colorScheme
    val isPrimary = emphasis == FeatureCardEmphasis.PRIMARY
    val containerColor = if (isPrimary) {
        colorScheme.primaryContainer
    } else {
        colorScheme.surfaceContainerHigh
    }
    val contentColor = if (isPrimary) {
        colorScheme.onPrimaryContainer
    } else {
        colorScheme.onSurface
    }

    Card(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = if (isPrimary) 112.dp else 88.dp),
        shape = MaterialTheme.shapes.large,
        border = if (isPrimary) BorderStroke(1.dp, accent.copy(alpha = 0.32f)) else null,
        elevation = CardDefaults.cardElevation(defaultElevation = if (isPrimary) 2.dp else 0.dp),
        colors = CardDefaults.cardColors(
            containerColor = containerColor,
            contentColor = contentColor,
            disabledContainerColor = containerColor.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Dimension.lg),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier
                    .width(4.dp)
                    .heightIn(min = if (isPrimary) 62.dp else 50.dp)
                    .clip(MaterialTheme.shapes.small)
                    .background(accent)
            ) {}
            Spacer(Modifier.width(Dimension.md))
            Icon(
                imageVector = icon,
                contentDescription = iconContentDescription,
                modifier = Modifier.size(if (isPrimary) 30.dp else 26.dp),
                tint = accent
            )
            Spacer(Modifier.width(Dimension.md))
            Column(modifier = Modifier.weight(1f)) {
                if (eyebrow != null) {
                    Text(
                        text = eyebrow,
                        style = MaterialTheme.typography.labelSmall,
                        color = accent
                    )
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = if (isPrimary) FontWeight.Bold else FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = contentColor.copy(alpha = 0.72f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.width(Dimension.xs))
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = contentColor.copy(alpha = 0.7f)
            )
        }
    }
}
