package com.robin.tools.util

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * A container that enables right-swipe-from-left-edge to navigate back.
 *
 * The swipe gesture is only recognized when starting within [edgeWidth] dp from
 * the left edge of the screen. If the drag distance exceeds 35% of the container
 * width, [onBack] is invoked; otherwise, the content snaps back to its original
 * position.
 */
@Composable
fun SwipeBackContainer(
    onBack: () -> Unit,
    edgeWidth: androidx.compose.ui.unit.Dp = 40.dp,
    content: @Composable () -> Unit
) {
    var isDragging by remember { mutableStateOf(false) }
    var dragOffset by remember { mutableStateOf(0f) }
    var containerWidth by remember { mutableIntStateOf(0) }
    var startInEdgeZone by remember { mutableStateOf(false) }
    var navigateBack by remember { mutableStateOf(false) }

    val density = LocalDensity.current
    val edgeWidthPx = with(density) { edgeWidth.toPx() }
    val triggerThreshold = if (containerWidth > 0) containerWidth * 0.35f else Float.MAX_VALUE

    // Animate back to 0 when the user releases without triggering back
    val displayOffset by animateFloatAsState(
        targetValue = if (isDragging) dragOffset else 0f,
        animationSpec = tween(durationMillis = 200),
        label = "swipeBack"
    )

    LaunchedEffect(navigateBack) {
        if (navigateBack) {
            onBack()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { containerWidth = it.width }
            .pointerInput(edgeWidthPx) {
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        startInEdgeZone = offset.x <= edgeWidthPx
                    },
                    onDragEnd = {
                        if (startInEdgeZone && dragOffset > triggerThreshold) {
                            navigateBack = true
                        }
                        isDragging = false
                        startInEdgeZone = false
                    },
                    onDragCancel = {
                        isDragging = false
                        dragOffset = 0f
                        startInEdgeZone = false
                    },
                    onHorizontalDrag = { change, dragAmount ->
                        if (startInEdgeZone) {
                            change.consume()
                            isDragging = true
                            dragOffset = (dragOffset + dragAmount)
                                .coerceIn(0f, containerWidth.toFloat())
                        }
                    }
                )
            }
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .offset { IntOffset(displayOffset.roundToInt(), 0) }
        ) {
            content()
        }
    }
}
