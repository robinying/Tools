package com.robin.tools.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.robin.tools.R
import com.robin.tools.core.ui.Dimension
import com.robin.tools.core.ui.FeatureCard
import com.robin.tools.core.ui.StudioSectionHeader
import com.robin.tools.core.ui.ToolsTopAppBar
import com.robin.tools.tool.ToolCategory
import com.robin.tools.tool.ToolGroup
import com.robin.tools.tool.ToolEntry
import com.robin.tools.tool.ToolRegistry

/** Tools grouped under one home category. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolCategoryScreen(
    category: ToolCategory,
    group: ToolGroup? = null,
    favorites: Set<String>,
    onGroupClick: (ToolGroup) -> Unit,
    onToolClick: (ToolEntry) -> Unit,
    onToggleFavorite: (ToolEntry) -> Unit,
    onBack: () -> Unit
) {
    val categoryTools = ToolRegistry.tools.filter { it.category == category }
    val groups = ToolRegistry.groupsFor(category)
    val showGroups = group == null && groups.isNotEmpty()
    val tools = if (group != null) {
        categoryTools.filter { it.group == group }
    } else {
        categoryTools
    }
    val headerTitle = group?.labelRes ?: category.labelRes
    val headerDescription = group?.descriptionRes ?: category.descriptionRes

    Scaffold(
        topBar = {
            ToolsTopAppBar(
                title = stringResource(headerTitle),
                onBack = onBack,
                backContentDescription = stringResource(R.string.app_back)
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Dimension.pageHorizontal, vertical = Dimension.lg),
            verticalArrangement = Arrangement.spacedBy(Dimension.md)
        ) {
            StudioSectionHeader(
                eyebrow = stringResource(R.string.home_category_tool_count, tools.size),
                title = stringResource(headerTitle),
                description = stringResource(headerDescription)
            )
            if (showGroups) {
                groups.forEach { toolGroup ->
                    val count = categoryTools.count { it.group == toolGroup }
                    FeatureCard(
                        title = stringResource(toolGroup.labelRes),
                        description = stringResource(toolGroup.descriptionRes),
                        icon = toolGroup.icon,
                        eyebrow = stringResource(R.string.home_category_tool_count, count),
                        onClick = { onGroupClick(toolGroup) }
                    )
                }
            } else {
                tools.forEach { tool ->
                    ToolCard(
                        tool = tool,
                        isFavorite = tool.id in favorites,
                        onToolClick = onToolClick,
                        onToggleFavorite = onToggleFavorite
                    )
                }
            }
            Spacer(Modifier.height(Dimension.lg))
        }
    }
}
