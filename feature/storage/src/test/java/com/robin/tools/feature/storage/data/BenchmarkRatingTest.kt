package com.robin.tools.feature.storage.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BenchmarkRatingTest {

    @Test
    fun `every phase except the cache flush is rateable`() {
        // 新增阶段时若忘了补阈值，这里会失败。
        rateablePhases().forEach { phase ->
            assertEquals(
                "$phase should be rateable",
                BenchmarkRating.POOR,
                RatingThresholds.rate(phase, 0.0)
            )
        }

        assertNull(RatingThresholds.rate(BenchmarkPhase.CACHE_FLUSH, 1_000.0))
    }

    @Test
    fun `sequential write thresholds take megabytes per second`() {
        val phase = BenchmarkPhase.SEQUENTIAL_WRITE

        assertEquals(BenchmarkRating.POOR, RatingThresholds.rate(phase, 79.0 * MB))
        assertEquals(BenchmarkRating.FAIR, RatingThresholds.rate(phase, 80.0 * MB))
        assertEquals(BenchmarkRating.GOOD, RatingThresholds.rate(phase, 200.0 * MB))
        assertEquals(BenchmarkRating.EXCELLENT, RatingThresholds.rate(phase, 500.0 * MB))
        assertEquals(BenchmarkRating.EXCELLENT, RatingThresholds.rate(phase, 2_000.0 * MB))
    }

    @Test
    fun `sequential read thresholds take megabytes per second`() {
        val phase = BenchmarkPhase.SEQUENTIAL_READ

        assertEquals(BenchmarkRating.POOR, RatingThresholds.rate(phase, 199.0 * MB))
        assertEquals(BenchmarkRating.FAIR, RatingThresholds.rate(phase, 200.0 * MB))
        assertEquals(BenchmarkRating.GOOD, RatingThresholds.rate(phase, 500.0 * MB))
        assertEquals(BenchmarkRating.EXCELLENT, RatingThresholds.rate(phase, 1_200.0 * MB))
    }

    @Test
    fun `sequential write expects bytes per second not megabytes`() {
        // 锁定"传值约定"：SEQUENTIAL_WRITE 收的是字节/秒，内部再换算成 MB/s。
        // 若直接把 MB/s 数值传进来，会得到严重偏低的评级。
        assertEquals(
            BenchmarkRating.GOOD,
            RatingThresholds.rate(BenchmarkPhase.SEQUENTIAL_WRITE, 200.0 * MB)
        )
        assertEquals(
            BenchmarkRating.POOR,
            RatingThresholds.rate(BenchmarkPhase.SEQUENTIAL_WRITE, 200.0)
        )
    }

    @Test
    fun `random io thresholds take iops without unit conversion`() {
        assertEquals(BenchmarkRating.POOR, RatingThresholds.rate(BenchmarkPhase.RANDOM_READ, 1_999.0))
        assertEquals(BenchmarkRating.FAIR, RatingThresholds.rate(BenchmarkPhase.RANDOM_READ, 2_000.0))
        assertEquals(BenchmarkRating.GOOD, RatingThresholds.rate(BenchmarkPhase.RANDOM_READ, 10_000.0))
        assertEquals(BenchmarkRating.EXCELLENT, RatingThresholds.rate(BenchmarkPhase.RANDOM_READ, 40_000.0))

        assertEquals(BenchmarkRating.POOR, RatingThresholds.rate(BenchmarkPhase.RANDOM_WRITE, 999.0))
        assertEquals(BenchmarkRating.FAIR, RatingThresholds.rate(BenchmarkPhase.RANDOM_WRITE, 1_000.0))
        assertEquals(BenchmarkRating.GOOD, RatingThresholds.rate(BenchmarkPhase.RANDOM_WRITE, 5_000.0))
        assertEquals(BenchmarkRating.EXCELLENT, RatingThresholds.rate(BenchmarkPhase.RANDOM_WRITE, 15_000.0))
    }

    @Test
    fun `real world phase thresholds take operations per second`() {
        assertEquals(BenchmarkRating.POOR, RatingThresholds.rate(BenchmarkPhase.SMALL_FILE, 299.0))
        assertEquals(BenchmarkRating.FAIR, RatingThresholds.rate(BenchmarkPhase.SMALL_FILE, 300.0))
        assertEquals(BenchmarkRating.EXCELLENT, RatingThresholds.rate(BenchmarkPhase.SMALL_FILE, 2_500.0))

        assertEquals(BenchmarkRating.POOR, RatingThresholds.rate(BenchmarkPhase.SQLITE_INSERT, 2_999.0))
        assertEquals(BenchmarkRating.FAIR, RatingThresholds.rate(BenchmarkPhase.SQLITE_INSERT, 3_000.0))
        assertEquals(BenchmarkRating.EXCELLENT, RatingThresholds.rate(BenchmarkPhase.SQLITE_INSERT, 25_000.0))
    }

    @Test
    fun `degenerate values rate predictably instead of crashing`() {
        // CACHE_FLUSH 本就没有可评分量，对任何输入都返回 null，不在本测试范围内。
        rateablePhases().forEach { phase ->
            assertEquals("$phase NaN", BenchmarkRating.POOR, RatingThresholds.rate(phase, Double.NaN))
            assertEquals("$phase negative", BenchmarkRating.POOR, RatingThresholds.rate(phase, -1.0))
            // 非有限吞吐意味着测量本身坏了（例如耗时算成了 0），
            // 绝不能因为"数值很大"就报成优秀。
            assertEquals(
                "$phase infinite",
                BenchmarkRating.POOR,
                RatingThresholds.rate(phase, Double.POSITIVE_INFINITY)
            )
        }
    }

    private fun rateablePhases() = BenchmarkPhase.entries.filter { it != BenchmarkPhase.CACHE_FLUSH }

    @Test
    fun `thresholds are ordered within each phase`() {
        // FAIR < GOOD < EXCELLENT 是 rateBy 的前提，写反了会静默错判。
        val ordered = listOf(
            Triple(
                RatingThresholds.SEQUENTIAL_WRITE_FAIR,
                RatingThresholds.SEQUENTIAL_WRITE_GOOD,
                RatingThresholds.SEQUENTIAL_WRITE_EXCELLENT
            ),
            Triple(
                RatingThresholds.SEQUENTIAL_READ_FAIR,
                RatingThresholds.SEQUENTIAL_READ_GOOD,
                RatingThresholds.SEQUENTIAL_READ_EXCELLENT
            ),
            Triple(
                RatingThresholds.RANDOM_READ_FAIR,
                RatingThresholds.RANDOM_READ_GOOD,
                RatingThresholds.RANDOM_READ_EXCELLENT
            ),
            Triple(
                RatingThresholds.RANDOM_WRITE_FAIR,
                RatingThresholds.RANDOM_WRITE_GOOD,
                RatingThresholds.RANDOM_WRITE_EXCELLENT
            ),
            Triple(
                RatingThresholds.SMALL_FILE_FAIR,
                RatingThresholds.SMALL_FILE_GOOD,
                RatingThresholds.SMALL_FILE_EXCELLENT
            ),
            Triple(
                RatingThresholds.SQLITE_INSERT_FAIR,
                RatingThresholds.SQLITE_INSERT_GOOD,
                RatingThresholds.SQLITE_INSERT_EXCELLENT
            )
        )

        ordered.forEach { (fair, good, excellent) ->
            assertTrue("$fair < $good", fair < good)
            assertTrue("$good < $excellent", good < excellent)
        }
    }
}
