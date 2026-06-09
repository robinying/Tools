package com.robin.tools.util

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp

/**
 * Detects right-swipe gestures starting from the left edge (within [edgeWidth])
 * and calls [onBack] when the drag exceeds [threshold].
 * Content slides right during drag with a shadow + arrow indicator.
 */
@Composable
fun SwipeBackContainer(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    edgeWidth: Float = with(LocalDensity.current) { 40.dp.toPx() },
    threshold: Float = with(LocalDensity.current) { 120.dp.toPx() },
    content: @Composable () -> Unit
) {
    var offsetX by remember { mutableFloatStateOf(0f) }

    Box(modifier = modifier.fillMaxSize()) {
        // Shadow + arrow indicator visible during drag
        if (offsetX > 0f) {
            val progress = (offsetX / threshold).coerceIn(0f, 1f)

            // Dark overlay behind content
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = progress * 0.25f))
            )

            // Arrow indicator strip on left edge
            Column(
                modifier = Modifier
                    .width(48.dp)
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = progress * 0.4f)),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(Modifier.height(250.dp))
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                    contentDescription = "Back",
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = progress),
                    modifier = Modifier.width(28.dp).height(28.dp)
                )
            }
        }

        // Content shifted right by drag offset
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clipToBounds()
                .graphicsLayer { translationX = offsetX }
                .pointerInput(edgeWidth) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        // Only respond to touches starting from left edge
                        if (down.position.x > edgeWidth) return@awaitEachGesture

                        var totalDrag = 0f
                        do {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull() ?: break
                            change.consume()
                            val dx = change.position.x - change.previousPosition.x
                            totalDrag += dx
                            offsetX = totalDrag.coerceAtLeast(0f)
                        } while (change.pressed)

                        if (totalDrag >= threshold) {
                            onBack()
                        }
                        // Snap back
                        offsetX = 0f
                    }
                }
        ) {
            content()
        }
    }
}