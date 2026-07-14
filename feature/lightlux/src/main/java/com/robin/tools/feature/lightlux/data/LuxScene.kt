package com.robin.tools.feature.lightlux.data

import androidx.annotation.StringRes
import com.robin.tools.feature.lightlux.R

/**
 * Rough ambient-light scene labels for TYPE_LIGHT readings (illustrative only).
 */
enum class LuxScene(@StringRes val labelRes: Int) {
    VERY_DARK(R.string.scene_very_dark),
    DIM_INDOOR(R.string.scene_dim_indoor),
    INDOOR(R.string.scene_indoor),
    BRIGHT_INDOOR(R.string.scene_bright_indoor),
    OVERCAST(R.string.scene_overcast),
    DAYLIGHT(R.string.scene_daylight),
    FULL_DAYLIGHT(R.string.scene_full_daylight),
    DIRECT_SUN(R.string.scene_direct_sun);

    companion object {
        fun fromLux(lux: Float): LuxScene {
            val v = lux.coerceAtLeast(0f)
            return when {
                v < 1f -> VERY_DARK
                v < 50f -> DIM_INDOOR
                v < 200f -> INDOOR
                v < 500f -> BRIGHT_INDOOR
                v < 2_000f -> OVERCAST
                v < 10_000f -> DAYLIGHT
                v < 25_000f -> FULL_DAYLIGHT
                else -> DIRECT_SUN
            }
        }
    }
}

enum class ChartWindow(val durationMs: Long, @StringRes val labelRes: Int) {
    SEC_15(15_000L, R.string.chart_window_15s),
    SEC_60(60_000L, R.string.chart_window_60s),
    MIN_5(300_000L, R.string.chart_window_5m)
}

data class ChartStats(
    val min: Float = 0f,
    val max: Float = 0f,
    val avg: Float = 0f,
    val samples: Int = 0
) {
    companion object {
        fun from(points: List<ChartDataPoint>): ChartStats {
            if (points.isEmpty()) return ChartStats()
            val values = points.map { it.luxValue }
            return ChartStats(
                min = values.min(),
                max = values.max(),
                avg = values.average().toFloat(),
                samples = values.size
            )
        }
    }
}
