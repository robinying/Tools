package com.robin.tools.feature.storage.presentation

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.robin.tools.feature.storage.R
import com.robin.tools.feature.storage.data.ThroughputSample

/** 少于两个采样点画不出曲线，此时调用方应改为展示文字说明。 */
const val MIN_CHART_SAMPLES = 2

/**
 * 吞吐时间线图。
 *
 * 纵向按序列自身的峰值归一化而不是固定量程：不同设备的吞吐相差几个数量级，
 * 固定量程会让慢设备压成一条直线。代价是**纵轴没有绝对刻度**，因此峰值与末尾值
 * 通过无障碍描述给出，避免图形成为唯一的信息载体。
 *
 * @param accent 曲线颜色；半透明填充使用同一颜色的低透明度版本
 */
@Composable
fun ThroughputChart(
    timeline: List<ThroughputSample>,
    accent: Color,
    modifier: Modifier = Modifier
) {
    if (timeline.size < MIN_CHART_SAMPLES) {
        return
    }

    val peakBytesPerSecond = timeline.maxOf { it.bytesPerSecond }
    if (!peakBytesPerSecond.isFinite() || peakBytesPerSecond <= 0.0) {
        return
    }

    val description = stringResource(
        R.string.storage_chart_description,
        byteRateText(peakBytesPerSecond),
        byteRateText(timeline.last().bytesPerSecond)
    )
    val baselineColor = MaterialTheme.colorScheme.outlineVariant

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(CHART_HEIGHT)
            .semantics { contentDescription = description }
    ) {
        val dx = size.width / (timeline.size - 1)
        val points = timeline.mapIndexed { index, sample ->
            Offset(
                x = index * dx,
                y = size.height * (1f - (sample.bytesPerSecond / peakBytesPerSecond).toFloat())
            )
        }

        val area = Path().apply {
            moveTo(points.first().x, size.height)
            points.forEach { lineTo(it.x, it.y) }
            lineTo(points.last().x, size.height)
            close()
        }
        drawPath(area, color = accent.copy(alpha = AREA_ALPHA))

        val curve = Path().apply {
            moveTo(points.first().x, points.first().y)
            points.drop(1).forEach { lineTo(it.x, it.y) }
        }
        drawPath(curve, color = accent, style = Stroke(width = CURVE_WIDTH.toPx()))

        drawLine(
            color = baselineColor,
            start = Offset(0f, size.height),
            end = Offset(size.width, size.height),
            strokeWidth = BASELINE_WIDTH.toPx()
        )
    }
}

private val CHART_HEIGHT = 72.dp
private val CURVE_WIDTH = 2.dp
private val BASELINE_WIDTH = 1.dp
private const val AREA_ALPHA = 0.22f
