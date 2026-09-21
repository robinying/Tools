package com.robin.tools.feature.storage.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BenchmarkMathTest {

    @Test
    fun `bytesPerSecond converts bytes and nanos to a rate`() {
        // 10 MB in exactly one second.
        assertEquals(10.0 * MB, BenchmarkMath.bytesPerSecond(10L * MB, 1_000_000_000L), 0.001)
    }

    @Test
    fun `bytesPerSecond halves when the duration doubles`() {
        val single = BenchmarkMath.bytesPerSecond(100L * MB, 1_000_000_000L)
        val doubled = BenchmarkMath.bytesPerSecond(100L * MB, 2_000_000_000L)
        assertEquals(single / 2.0, doubled, 0.001)
    }

    @Test
    fun `bytesPerSecond returns zero instead of infinity for degenerate input`() {
        assertEquals(0.0, BenchmarkMath.bytesPerSecond(10L * MB, 0L), 0.0)
        assertEquals(0.0, BenchmarkMath.bytesPerSecond(10L * MB, -1L), 0.0)
        assertEquals(0.0, BenchmarkMath.bytesPerSecond(0L, 1_000_000_000L), 0.0)
        assertEquals(0.0, BenchmarkMath.bytesPerSecond(-1L, 1_000_000_000L), 0.0)
    }

    @Test
    fun `steadyStateBytesPerSecond returns null when samples are too few`() {
        assertNull(BenchmarkMath.steadyStateBytesPerSecond(emptyList()))
        assertNull(BenchmarkMath.steadyStateBytesPerSecond(listOf(sample(100.0))))
        assertNull(BenchmarkMath.steadyStateBytesPerSecond(listOf(sample(100.0), sample(100.0))))
    }

    @Test
    fun `steadyStateBytesPerSecond ignores the head and tail of the timeline`() {
        // 10 fast samples, 85 steady samples, 5 slow samples.
        val timeline = buildList {
            repeat(10) { add(sample(1_000.0)) }
            repeat(85) { add(sample(500.0)) }
            repeat(5) { add(sample(10.0)) }
        }

        assertEquals(500.0, BenchmarkMath.steadyStateBytesPerSecond(timeline)!!, 0.001)
    }

    @Test
    fun `steadyStateBytesPerSecond averages the remaining samples`() {
        val timeline = buildList {
            repeat(20) { add(sample(100.0)) }
            repeat(60) { add(sample(200.0)) }
            repeat(20) { add(sample(300.0)) }
        }

        // 头尾各裁掉一段后剩下 85 个样本：10 个 100、60 个 200、15 个 300。
        val expected = (10 * 100.0 + 60 * 200.0 + 15 * 300.0) / 85.0
        assertEquals(expected, BenchmarkMath.steadyStateBytesPerSecond(timeline)!!, 0.001)
    }

    @Test
    fun `percentileNanos returns zero for an empty sample set`() {
        assertEquals(0L, BenchmarkMath.percentileNanos(LongArray(0), 0.5))
    }

    @Test
    fun `percentileNanos returns the only sample for any percentile`() {
        val single = longArrayOf(4_200L)
        assertEquals(4_200L, BenchmarkMath.percentileNanos(single, 0.0))
        assertEquals(4_200L, BenchmarkMath.percentileNanos(single, 0.5))
        assertEquals(4_200L, BenchmarkMath.percentileNanos(single, 1.0))
    }

    @Test
    fun `percentileNanos uses the nearest rank`() {
        val samples = LongArray(10) { it + 1L } // 1..10

        // p50 是真正的中位数：ceil(0.50 * 10) = 5，取第 5 小。
        assertEquals(5L, BenchmarkMath.percentileNanos(samples, 0.50))
        assertEquals(10L, BenchmarkMath.percentileNanos(samples, 0.95))
        assertEquals(10L, BenchmarkMath.percentileNanos(samples, 0.99))
        assertEquals(10L, BenchmarkMath.percentileNanos(samples, 1.0))
    }

    @Test
    fun `percentileNanos clamps out of range percentiles`() {
        val samples = LongArray(10) { it + 1L }

        assertEquals(1L, BenchmarkMath.percentileNanos(samples, 0.0))
        assertEquals(1L, BenchmarkMath.percentileNanos(samples, -1.0))
        assertEquals(10L, BenchmarkMath.percentileNanos(samples, 2.0))
    }

    @Test
    fun `percentileNanos never extrapolates beyond the samples`() {
        val samples = LongArray(100) { it + 1L } // 1..100

        assertEquals(99L, BenchmarkMath.percentileNanos(samples, 0.99))
        assertEquals(100L, BenchmarkMath.percentileNanos(samples, 1.0))
    }

    @Test
    fun `nanosToMicros truncates`() {
        assertEquals(0L, BenchmarkMath.nanosToMicros(0L))
        assertEquals(0L, BenchmarkMath.nanosToMicros(999L))
        assertEquals(1L, BenchmarkMath.nanosToMicros(1_000L))
        assertEquals(128L, BenchmarkMath.nanosToMicros(128_400L))
    }

    private fun sample(bytesPerSecond: Double) = ThroughputSample(
        elapsedMillis = 500L,
        bytesPerSecond = bytesPerSecond
    )
}
