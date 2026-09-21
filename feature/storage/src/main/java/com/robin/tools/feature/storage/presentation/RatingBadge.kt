package com.robin.tools.feature.storage.presentation

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import com.robin.tools.core.ui.Dimension
import com.robin.tools.feature.storage.data.BenchmarkRating

/**
 * 指标评级徽标。
 *
 * 文字标签承载语义，颜色只做强化：只靠颜色区分等级对色觉障碍用户不可用，
 * 而且深色主题下几种 container 色的区分度本来就有限。
 */
@Composable
fun RatingBadge(rating: BenchmarkRating, modifier: Modifier = Modifier) {
    val colorScheme = MaterialTheme.colorScheme
    val containerColor = when (rating) {
        BenchmarkRating.POOR -> colorScheme.errorContainer
        BenchmarkRating.FAIR -> colorScheme.tertiaryContainer
        BenchmarkRating.GOOD -> colorScheme.primaryContainer
        BenchmarkRating.EXCELLENT -> colorScheme.secondaryContainer
    }
    val contentColor = when (rating) {
        BenchmarkRating.POOR -> colorScheme.onErrorContainer
        BenchmarkRating.FAIR -> colorScheme.onTertiaryContainer
        BenchmarkRating.GOOD -> colorScheme.onPrimaryContainer
        BenchmarkRating.EXCELLENT -> colorScheme.onSecondaryContainer
    }

    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.small,
        color = containerColor,
        contentColor = contentColor
    ) {
        Text(
            text = stringResource(rating.labelRes),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = Dimension.sm, vertical = Dimension.xxs)
        )
    }
}
