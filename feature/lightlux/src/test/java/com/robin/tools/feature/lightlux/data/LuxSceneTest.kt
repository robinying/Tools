package com.robin.tools.feature.lightlux.data

import org.junit.Assert.assertEquals
import org.junit.Test

class LuxSceneTest {

    @Test
    fun `fromLux maps scene bands`() {
        assertEquals(LuxScene.VERY_DARK, LuxScene.fromLux(0f))
        assertEquals(LuxScene.DIM_INDOOR, LuxScene.fromLux(20f))
        assertEquals(LuxScene.INDOOR, LuxScene.fromLux(100f))
        assertEquals(LuxScene.BRIGHT_INDOOR, LuxScene.fromLux(300f))
        assertEquals(LuxScene.OVERCAST, LuxScene.fromLux(1000f))
        assertEquals(LuxScene.DAYLIGHT, LuxScene.fromLux(5000f))
        assertEquals(LuxScene.FULL_DAYLIGHT, LuxScene.fromLux(15000f))
        assertEquals(LuxScene.DIRECT_SUN, LuxScene.fromLux(50000f))
    }

    @Test
    fun `ChartStats from empty`() {
        val s = ChartStats.from(emptyList())
        assertEquals(0, s.samples)
        assertEquals(0f, s.min, 0f)
        assertEquals(0f, s.max, 0f)
        assertEquals(0f, s.avg, 0f)
    }

    @Test
    fun `ChartStats from points`() {
        val points = listOf(
            ChartDataPoint(1, 10f),
            ChartDataPoint(2, 20f),
            ChartDataPoint(3, 30f)
        )
        val s = ChartStats.from(points)
        assertEquals(3, s.samples)
        assertEquals(10f, s.min, 0.01f)
        assertEquals(30f, s.max, 0.01f)
        assertEquals(20f, s.avg, 0.01f)
    }
}
