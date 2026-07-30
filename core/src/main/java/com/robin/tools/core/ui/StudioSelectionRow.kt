package com.robin.tools.core.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** Horizontally scrolling option row for compact, mutually exclusive workbench controls. */
@Composable
fun StudioSelectionRow(
    options: List<StudioSelectionOption>,
    selectedKey: String,
    onOptionSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(Dimension.sm)
    ) {
        options.forEach { option ->
            TextOptionChip(
                selected = option.key == selectedKey,
                onClick = { onOptionSelected(option.key) },
                label = option.label,
                enabled = enabled
            )
        }
    }
}

data class StudioSelectionOption(
    val key: String,
    val label: String
)
