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
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.robin.tools.R
import com.robin.tools.core.ui.Dimension
import com.robin.tools.core.ui.FeatureCard
import com.robin.tools.core.ui.FeatureCardEmphasis
import com.robin.tools.core.ui.StudioSectionHeader
import com.robin.tools.core.ui.StudioSurface
import com.robin.tools.core.ui.StudioSurfaceTone
import com.robin.tools.tool.ToolCategory
import com.robin.tools.tool.ToolEntry
import com.robin.tools.tool.ToolRegistry

/**
 * Searchable home workbench.
 *
 * Tools come from [ToolRegistry]; favorites and recents are persisted by
 * `ToolPreferences` in the activity and passed in as state.
 */
@Composable
fun HomeScreen(
    favorites: Set<String>,
    recents: List<String>,
    onToolClick: (ToolEntry) -> Unit,
    onToggleFavorite: (ToolEntry) -> Unit
) {
    val context = LocalContext.current
    var query by rememberSaveable { mutableStateOf("") }

    val allTools = ToolRegistry.tools
    val filtered = remember(query, allTools) {
        if (query.isBlank()) {
            allTools
        } else {
            allTools.filter { tool ->
                val haystack = buildString {
                    append(context.getString(tool.titleRes))
                    append(' ')
                    append(context.getString(tool.descriptionRes))
                    append(' ')
                    append(tool.keywords.joinToString(" "))
                }
                haystack.contains(query.trim(), ignoreCase = true)
            }
        }
    }
    val favoriteTools = remember(favorites) {
        allTools.filter { it.id in favorites }
    }
    val recentTools = remember(recents) {
        recents.mapNotNull(ToolRegistry::byId)
    }

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

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { query = "" }) {
                        Icon(
                            Icons.Default.Clear,
                            contentDescription = stringResource(R.string.home_search_hint)
                        )
                    }
                }
            },
            placeholder = { Text(stringResource(R.string.home_search_hint)) }
        )

        if (query.isNotBlank()) {
            StudioSectionHeader(
                eyebrow = stringResource(R.string.home_search_results),
                title = stringResource(R.string.home_search_results)
            )
            if (filtered.isEmpty()) {
                StudioSurface(tone = StudioSurfaceTone.OUTLINED) {
                    Text(
                        text = stringResource(R.string.home_search_empty, query),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                filtered.forEach { tool ->
                    ToolCard(tool, isFavorite = tool.id in favorites, onToolClick, onToggleFavorite)
                }
            }
        } else {
            if (favoriteTools.isNotEmpty()) {
                StudioSectionHeader(
                    eyebrow = stringResource(R.string.home_section_favorites),
                    title = stringResource(R.string.home_section_favorites)
                )
                favoriteTools.forEach { tool ->
                    ToolCard(tool, isFavorite = true, onToolClick, onToggleFavorite)
                }
            }

            if (recentTools.isNotEmpty()) {
                StudioSectionHeader(
                    eyebrow = stringResource(R.string.home_section_recent),
                    title = stringResource(R.string.home_section_recent)
                )
                recentTools.forEach { tool ->
                    ToolCard(tool, isFavorite = tool.id in favorites, onToolClick, onToggleFavorite)
                }
            }

            ToolCategory.entries.forEach { category ->
                val categoryTools = allTools.filter { it.category == category }
                if (categoryTools.isNotEmpty()) {
                    StudioSectionHeader(title = stringResource(category.labelRes))
                    categoryTools.forEach { tool ->
                        ToolCard(
                            tool = tool,
                            isFavorite = tool.id in favorites,
                            onToolClick = onToolClick,
                            onToggleFavorite = onToggleFavorite
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(Dimension.xl))
    }
}

@Composable
private fun ToolCard(
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
        emphasis = if (tool.id == "camera") FeatureCardEmphasis.PRIMARY else FeatureCardEmphasis.STANDARD,
        eyebrow = if (tool.id == "camera") stringResource(R.string.home_primary_tool) else null,
        trailing = {
            IconButton(onClick = { onToggleFavorite(tool) }) {
                Icon(
                    imageVector = if (isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                    contentDescription = stringResource(
                        if (isFavorite) R.string.home_favorite_remove else R.string.home_favorite_add
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
