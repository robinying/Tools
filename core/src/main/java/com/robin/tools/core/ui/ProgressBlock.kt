package com.robin.tools.core.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

/** Unified progress section for long-running media and conversion tasks. */
@Composable
fun ProgressBlock(
    progress: Float?,
    message: String,
    modifier: Modifier = Modifier,
    onCancel: (() -> Unit)? = null,
    cancelLabel: String = "",
) {
    StudioSurface(
        modifier = modifier,
        tone = StudioSurfaceTone.EMPHASIZED
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (progress != null) {
                LinearProgressIndicator(
                    progress = { progress.coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            Spacer(Modifier.height(Dimension.sm))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (onCancel != null && cancelLabel.isNotEmpty()) {
                Spacer(Modifier.height(Dimension.md))
                StudioActionButton(
                    label = cancelLabel,
                    onClick = onCancel,
                    style = StudioActionStyle.DESTRUCTIVE,
                    icon = Icons.Default.Close,
                    iconContentDescription = cancelLabel
                )
            }
        }
    }
}
