package com.robin.tools.ui.screens

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.robin.tools.R
import com.robin.tools.core.ui.FeatureCard
import com.robin.tools.core.ui.FeatureCardEmphasis
import com.robin.tools.tool.ToolEntry

/** Shared tool row used by search results and category screens. */
@Composable
internal fun ToolCard(
    tool: ToolEntry,
    isFavorite: Boolean,
    onToolClick: (ToolEntry) -> Unit,
    onToggleFavorite: (ToolEntry) -> Unit
) {
    FeatureCard(
        title = stringResource(tool.titleRes),
        description = stringResource(tool.descriptionRes),
        icon = tool.icon,
        onClick = { onToolClick(tool) },
        emphasis = if (tool.id == "camera") {
            FeatureCardEmphasis.PRIMARY
        } else {
            FeatureCardEmphasis.STANDARD
        },
        eyebrow = if (tool.id == "camera") {
            stringResource(R.string.home_primary_tool)
        } else {
            null
        },
        trailing = {
            IconButton(onClick = { onToggleFavorite(tool) }) {
                Icon(
                    imageVector = if (isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                    contentDescription = stringResource(
                        if (isFavorite) {
                            R.string.home_favorite_remove
                        } else {
                            R.string.home_favorite_add
                        }
                    ),
                    tint = if (isFavorite) {
                        MaterialTheme.colorScheme.tertiary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
        }
    )
}
