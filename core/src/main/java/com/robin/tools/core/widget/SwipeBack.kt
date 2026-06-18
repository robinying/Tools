package com.robin.tools.core.widget

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * 一个支持「从屏幕左边缘向右拖动返回上一级」的容器。
 *
 * 实现要点（解决与内层可滚动组件的手势竞争）：
 * - 内容 [content] 铺满容器；在其之上叠加一条覆盖左边缘 [edgeWidth] 宽度的透明手势条。
 *   该手势条位于最顶层（z-index 最高），从左边缘起手必然先命中它，从而避免被内容区的
 *   verticalScroll / LazyColumn / horizontalScroll 等抢先消费。
 * - 一旦手势条在 down 时消费该指针，后续 move 事件会持续路由给手势条直到 up（Compose
 *   pointer input 的指针捕获特性），因此手指拖出边缘区域后仍能继续跟踪。
 * - 松手时若拖拽距离超过容器宽度 [triggerFraction]（默认 25%）即触发 [onBack]，
 *   否则带动画回弹原位。拖动过程中内容随手指水平平移，营造「滑出」视觉反馈。
 *
 * 注意：手势仅在起手点落于左边缘 [edgeWidth] 内时生效；内容区主体的正常滚动/点击不受影响。
 * 当 [enabled] 为 false 时不响应手势（可用于在嵌套场景中让位于内层 SwipeBack）。
 */
@Composable
fun SwipeBackContainer(
    onBack: () -> Unit,
    edgeWidth: androidx.compose.ui.unit.Dp = 24.dp,
    triggerFraction: Float = 0.25f,
    enabled: Boolean = true,
    content: @Composable () -> Unit
) {
    var isDragging by remember { mutableStateOf(false) }
    var dragOffset by remember { mutableStateOf(0f) }
    var containerWidth by remember { mutableIntStateOf(0) }
    var navigateBack by remember { mutableStateOf(false) }

    val density = LocalDensity.current
    val edgeWidthPx = with(density) { edgeWidth.toPx() }
    val triggerThreshold = if (containerWidth > 0) containerWidth * triggerFraction else Float.MAX_VALUE

    // 松手未触发返回时，带动画回弹到原位
    val displayOffset by animateFloatAsState(
        targetValue = if (isDragging) dragOffset else 0f,
        animationSpec = tween(durationMillis = 200),
        label = "swipeBack"
    )

    LaunchedEffect(navigateBack) {
        if (navigateBack) {
            onBack()
            // 重置标志，便于再次进入该组合时状态干净
            navigateBack = false
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { containerWidth = it.width }
    ) {
        // 内容主体，随拖拽水平平移
        Box(
            modifier = Modifier
                .fillMaxSize()
                .offset { IntOffset(displayOffset.roundToInt(), 0) }
        ) {
            content()
        }

        // 左边缘透明手势条：在最顶层捕获边缘起手的水平拖拽
        val gestureModifier = if (enabled) {
            Modifier.pointerInput(edgeWidthPx, triggerThreshold) {
                detectHorizontalDragGestures(
                    onDragStart = { _ ->
                        isDragging = true
                        dragOffset = 0f
                    },
                    onDragEnd = {
                        if (dragOffset > triggerThreshold) {
                            navigateBack = true
                        }
                        isDragging = false
                        dragOffset = 0f
                    },
                    onDragCancel = {
                        isDragging = false
                        dragOffset = 0f
                    },
                    onHorizontalDrag = { change, dragAmount ->
                        change.consume()
                        // 只允许向右拖（正值），向左不超过 0
                        dragOffset = (dragOffset + dragAmount)
                            .coerceIn(0f, containerWidth.toFloat())
                    }
                )
            }
        } else {
            Modifier
        }
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(edgeWidth)
                .then(gestureModifier)
        )
    }
}
