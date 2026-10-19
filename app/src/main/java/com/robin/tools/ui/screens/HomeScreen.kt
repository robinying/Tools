package com.robin.tools.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material3.AssistChip
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
import androidx.compose.ui.unit.dp
import com.robin.tools.R
import com.robin.tools.core.ui.Dimension
import com.robin.tools.core.ui.FeatureCard
import com.robin.tools.core.ui.StudioSectionHeader
import com.robin.tools.core.ui.StudioSurface
import com.robin.tools.core.ui.StudioSurfaceTone
import com.robin.tools.tool.ToolCategory
import com.robin.tools.tool.ToolEntry
import com.robin.tools.tool.ToolRegistry

/**
 * Category-first home workbench.
 *
 * The home page keeps search, favorites, recents, and four category cards;
 * individual tools live in [ToolCategoryScreen] so the first screen stays short.
 */
@Composable
fun HomeScreen(
    favorites: Set<String>,
    recents: List<String>,
    onCategoryClick: (ToolCategory) -> Unit,
    onToolClick: (ToolEntry) -> Unit,
    onToggleFavorite: (ToolEntry) -> Unit
) {
    val context = LocalContext.current
    var query by rememberSaveable { mutableStateOf("") }

    val allTools = ToolRegistry.tools
    val filtered = remember(query, allTools) {
        if (query.isBlank()) {
            emptyList()
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
                    ToolCard(
                        tool = tool,
                        isFavorite = tool.id in favorites,
                        onToolClick = onToolClick,
                        onToggleFavorite = onToggleFavorite
                    )
                }
            }
        } else {
            if (favoriteTools.isNotEmpty()) {
                StudioSectionHeader(
                    title = stringResource(R.string.home_section_favorites)
                )
                QuickToolRow(
                    tools = favoriteTools,
                    onToolClick = onToolClick
                )
            }

            if (recentTools.isNotEmpty()) {
                StudioSectionHeader(
                    title = stringResource(R.string.home_section_recent)
                )
                QuickToolRow(
                    tools = recentTools,
                    onToolClick = onToolClick
                )
            }

            StudioSectionHeader(
                eyebrow = stringResource(R.string.home_browse_eyebrow),
                title = stringResource(R.string.home_browse_title),
                description = stringResource(R.string.home_browse_desc)
            )
            ToolCategory.entries.forEach { category ->
                val count = allTools.count { it.category == category }
                FeatureCard(
                    title = stringResource(category.labelRes),
                    description = stringResource(category.descriptionRes),
                    icon = category.icon,
                    eyebrow = stringResource(R.string.home_category_tool_count, count),
                    onClick = { onCategoryClick(category) }
                )
            }
        }

        Spacer(Modifier.height(Dimension.xl))
    }
}

/** Compact favorites/recent strip that keeps the home page short. */
@Composable
private fun QuickToolRow(
    tools: List<ToolEntry>,
    onToolClick: (ToolEntry) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(Dimension.sm)
    ) {
        tools.forEach { tool ->
            AssistChip(
                onClick = { onToolClick(tool) },
                label = { Text(stringResource(tool.titleRes)) },
                leadingIcon = {
                    Icon(
                        imageVector = tool.icon,
                        contentDescription = null,
                        modifier = Modifier.padding(Dimension.xxs)
                    )
                }
            )
        }
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
