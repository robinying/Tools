package com.robin.tools.core.ui

import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

/** Action hierarchy used for selecting material, starting work, and destructive cancellation. */
enum class StudioActionStyle {
    PRIMARY,
    SECONDARY,
    DESTRUCTIVE
}

@Composable
fun StudioActionButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: StudioActionStyle = StudioActionStyle.PRIMARY,
    enabled: Boolean = true,
    loading: Boolean = false,
    icon: ImageVector? = null,
    iconContentDescription: String? = null
) {
    val buttonModifier = modifier
        .fillMaxWidth()
        .defaultMinSize(minHeight = Dimension.touchTarget)
    val colors = actionColors(style)

    val content: @Composable RowScope.() -> Unit = {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp,
                color = when (style) {
                    StudioActionStyle.PRIMARY, StudioActionStyle.DESTRUCTIVE ->
                        MaterialTheme.colorScheme.onPrimary
                    StudioActionStyle.SECONDARY -> MaterialTheme.colorScheme.primary
                }
            )
        } else if (icon != null) {
            Icon(imageVector = icon, contentDescription = iconContentDescription)
        }
        if (loading || icon != null) {
            Spacer(Modifier.width(Dimension.sm))
        }
        Text(text = label)
    }

    if (style == StudioActionStyle.SECONDARY) {
        OutlinedButton(
            onClick = onClick,
            modifier = buttonModifier,
            enabled = enabled && !loading,
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary)
        ) {
            content()
        }
    } else {
        Button(
            onClick = onClick,
            modifier = buttonModifier,
            enabled = enabled && !loading,
            colors = colors
        ) {
            content()
        }
    }
}

@Composable
private fun actionColors(style: StudioActionStyle): ButtonColors {
    return when (style) {
        StudioActionStyle.PRIMARY -> ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary
        )
        StudioActionStyle.SECONDARY -> ButtonDefaults.buttonColors()
        StudioActionStyle.DESTRUCTIVE -> ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.error,
            contentColor = MaterialTheme.colorScheme.onError
        )
    }
}
