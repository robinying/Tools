package com.robin.tools.feature.storage.data

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class LatencyRecorderTest {

    @Test
    fun `records samples and returns them sorted`() {
        val recorder = LatencyRecorder(4)

        recorder.record(500L)
        recorder.record(100L)
        recorder.record(300L)
        recorder.record(200L)

        assertEquals(4, recorder.sampleCount())
        assertArrayEquals(longArrayOf(100L, 200L, 300L, 500L), recorder.sortedSamples())
    }

    @Test
    fun `sortedSamples returns an independent copy`() {
        val recorder = LatencyRecorder(2)
        recorder.record(20L)
        recorder.record(10L)

        val first = recorder.sortedSamples()
        first[0] = 999L

        assertArrayEquals(longArrayOf(10L, 20L), recorder.sortedSamples())
    }

    @Test
    fun `empty recorder reports no samples`() {
        val recorder = LatencyRecorder(8)

        assertEquals(0, recorder.sampleCount())
        assertArrayEquals(LongArray(0), recorder.sortedSamples())
    }

    @Test
    fun `samples beyond the capacity are dropped instead of resizing`() {
        // 容量按操作次数精确预分配，测量路径里不能出现扩容分配。
        val recorder = LatencyRecorder(2)

        recorder.record(1L)
        recorder.record(2L)
        recorder.record(3L)

        assertEquals(2, recorder.sampleCount())
        assertArrayEquals(longArrayOf(1L, 2L), recorder.sortedSamples())
    }

    @Test
    fun `a non positive capacity is rejected`() {
        assertThrows(IllegalArgumentException::class.java) { LatencyRecorder(0) }
        assertThrows(IllegalArgumentException::class.java) { LatencyRecorder(-1) }
    }
}
