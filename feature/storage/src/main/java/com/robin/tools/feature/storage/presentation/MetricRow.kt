package com.robin.tools.feature.storage.presentation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.robin.tools.core.ui.Dimension
import com.robin.tools.feature.storage.data.BenchmarkRating

/**
 * 指标行：左侧名称与说明，右侧数值与评级徽标。
 *
 * 自带 [Dimension.xs] 的纵向留白，使相邻指标行之间形成统一的节奏，
 * 调用方不必逐个补 Spacer。
 *
 * @param label 指标名称
 * @param value 已格式化的数值（含单位）
 * @param rating 评级；无可评分量的指标传 null，此时不显示徽标
 * @param caption 数值下方的补充说明，例如延迟尾部或口径注释
 */
@Composable
fun MetricRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    rating: BenchmarkRating? = null,
    caption: String? = null
) {
    Row(
        // 合并成一个无障碍节点：读屏会把"平均，1.1 GB/s，优秀"作为一整条念出来，
        // 而不是拆成三个互不相关的片段。
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = Dimension.xs)
            .semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.Top
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (caption != null) {
                Text(
                    text = caption,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(Modifier.width(Dimension.sm))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.End,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (rating != null) {
            Spacer(Modifier.width(Dimension.sm))
            RatingBadge(rating)
        }
    }
}
