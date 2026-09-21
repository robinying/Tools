package com.robin.tools.feature.storage.presentation

import com.robin.tools.feature.storage.data.KB
import com.robin.tools.feature.storage.data.MB
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

/**
 * 展示层格式化的纯逻辑测试。
 *
 * 只覆盖不需要资源的纯函数（量级选择、数字格式化）；`@Composable` 包装层是
 * 一行 `stringResource(unitRes, number)`，由真机手动验证覆盖。
 * 数字断言一律固定在 [Locale.US]，避免测试依赖开发机的区域设置。
 */
class MetricFormatTest {

    private val us = Locale.US

    @Test
    fun `byteMagnitude picks the largest unit that fits`() {
        assertEquals(MetricFormat.ByteMagnitude.BYTES, MetricFormat.byteMagnitude(0L))
        assertEquals(MetricFormat.ByteMagnitude.BYTES, MetricFormat.byteMagnitude(KB - 1))
        assertEquals(MetricFormat.ByteMagnitude.KILOBYTES, MetricFormat.byteMagnitude(KB))
        assertEquals(MetricFormat.ByteMagnitude.KILOBYTES, MetricFormat.byteMagnitude(MB - 1))
        assertEquals(MetricFormat.ByteMagnitude.MEGABYTES, MetricFormat.byteMagnitude(MB))
        assertEquals(MetricFormat.ByteMagnitude.GIGABYTES, MetricFormat.byteMagnitude(KB * MB))
    }

    @Test
    fun `byteRateMagnitude picks the largest unit that fits`() {
        assertEquals(
            MetricFormat.ByteRateMagnitude.BYTES_PER_SECOND,
            MetricFormat.byteRateMagnitude(512.0)
        )
        assertEquals(
            MetricFormat.ByteRateMagnitude.KILOBYTES_PER_SECOND,
            MetricFormat.byteRateMagnitude(KB.toDouble())
        )
        assertEquals(
            MetricFormat.ByteRateMagnitude.MEGABYTES_PER_SECOND,
            MetricFormat.byteRateMagnitude(2.0 * MB)
        )
        assertEquals(
            MetricFormat.ByteRateMagnitude.GIGABYTES_PER_SECOND,
            MetricFormat.byteRateMagnitude(1.5 * KB * MB)
        )
    }

    @Test
    fun `byteRateMagnitude falls back to bytes for degenerate input`() {
        // 非有限值意味着测量坏了，展示成 0 B/s 而不是一个巨大的量级。
        assertEquals(
            MetricFormat.ByteRateMagnitude.BYTES_PER_SECOND,
            MetricFormat.byteRateMagnitude(0.0)
        )
        assertEquals(
            MetricFormat.ByteRateMagnitude.BYTES_PER_SECOND,
            MetricFormat.byteRateMagnitude(-1.0)
        )
        assertEquals(
            MetricFormat.ByteRateMagnitude.BYTES_PER_SECOND,
            MetricFormat.byteRateMagnitude(Double.NaN)
        )
        assertEquals(
            MetricFormat.ByteRateMagnitude.BYTES_PER_SECOND,
            MetricFormat.byteRateMagnitude(Double.POSITIVE_INFINITY)
        )
    }

    @Test
    fun `latencyMagnitude picks a readable unit`() {
        assertEquals(MetricFormat.LatencyMagnitude.MICROSECONDS, MetricFormat.latencyMagnitude(0L))
        assertEquals(MetricFormat.LatencyMagnitude.MICROSECONDS, MetricFormat.latencyMagnitude(999L))
        assertEquals(MetricFormat.LatencyMagnitude.MILLISECONDS, MetricFormat.latencyMagnitude(1_000L))
        assertEquals(
            MetricFormat.LatencyMagnitude.MILLISECONDS,
            MetricFormat.latencyMagnitude(999_999L)
        )
        assertEquals(MetricFormat.LatencyMagnitude.SECONDS, MetricFormat.latencyMagnitude(1_000_000L))
    }

    @Test
    fun `scaledNumber keeps one decimal below the threshold`() {
        assertEquals("0.0", MetricFormat.scaledNumber(0.0, us))
        assertEquals("1.5", MetricFormat.scaledNumber(1.5, us))
        assertEquals("99.9", MetricFormat.scaledNumber(99.9, us))
    }

    @Test
    fun `scaledNumber drops the decimal at and above the threshold`() {
        // 412 比 412.0 好读，而这个量级上小数位本来也没意义。
        assertEquals("100", MetricFormat.scaledNumber(100.0, us))
        assertEquals("412", MetricFormat.scaledNumber(412.3, us))
        assertEquals("2000", MetricFormat.scaledNumber(2_000.4, us))
    }

    @Test
    fun `scaledNumber clamps degenerate input`() {
        assertEquals("0.0", MetricFormat.scaledNumber(-1.0, us))
        assertEquals("0", MetricFormat.scaledNumber(Double.NaN, us))
        assertEquals("0", MetricFormat.scaledNumber(Double.POSITIVE_INFINITY, us))
    }

    @Test
    fun `scaledNumber uses the locale decimal separator`() {
        // 法语等语言用逗号做小数分隔符，这是把数字与单位分开处理的直接收益。
        assertEquals("1,5", MetricFormat.scaledNumber(1.5, Locale.FRANCE))
    }

    @Test
    fun `scaledRate adds an SI magnitude prefix`() {
        assertEquals("0", MetricFormat.scaledRate(0.0, us))
        assertEquals("320", MetricFormat.scaledRate(320.0, us))
        assertEquals("1.0 k", MetricFormat.scaledRate(1_000.0, us))
        assertEquals("12.4 k", MetricFormat.scaledRate(12_400.0, us))
        assertEquals("999 k", MetricFormat.scaledRate(999_000.0, us))
        assertEquals("1.2 M", MetricFormat.scaledRate(1_200_000.0, us))
    }

    @Test
    fun `scaledRate clamps degenerate input`() {
        assertEquals("0", MetricFormat.scaledRate(-1.0, us))
        assertEquals("0", MetricFormat.scaledRate(Double.NaN, us))
        assertEquals("0", MetricFormat.scaledRate(Double.POSITIVE_INFINITY, us))
    }

    @Test
    fun `each byte magnitude scales its band into a readable range`() {
        // 量级与除数的搭配必须自洽：落在某个量级区间里的值，缩放后应当落在
        // [1, 1024) —— 否则就该选更高一档的量级。
        val samples = listOf(
            KB to MetricFormat.ByteMagnitude.KILOBYTES,
            MB to MetricFormat.ByteMagnitude.MEGABYTES,
            (KB * MB) to MetricFormat.ByteMagnitude.GIGABYTES,
            (2L * MB) to MetricFormat.ByteMagnitude.MEGABYTES
        )

        samples.forEach { (bytes, expected) ->
            assertEquals("magnitude for $bytes bytes", expected, MetricFormat.byteMagnitude(bytes))
            val scaled = bytes.toDouble() / expected.divisor
            assertTrue("scaled value $scaled out of range for $expected", scaled in 1.0..1023.999)
        }
    }

    @Test
    fun `each byte rate magnitude scales its band into a readable range`() {
        val samples = listOf(
            KB.toDouble() to MetricFormat.ByteRateMagnitude.KILOBYTES_PER_SECOND,
            (2.0 * MB) to MetricFormat.ByteRateMagnitude.MEGABYTES_PER_SECOND,
            (1.5 * KB * MB) to MetricFormat.ByteRateMagnitude.GIGABYTES_PER_SECOND
        )

        samples.forEach { (rate, expected) ->
            assertEquals("magnitude for $rate B/s", expected, MetricFormat.byteRateMagnitude(rate))
            val scaled = rate / expected.divisor
            assertTrue("scaled value $scaled out of range for $expected", scaled in 1.0..1023.999)
        }
    }
}
