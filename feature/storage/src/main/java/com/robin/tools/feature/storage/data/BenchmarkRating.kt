package com.robin.tools.feature.storage.data

import androidx.annotation.StringRes
import com.robin.tools.feature.storage.R

/** 指标评级。展示层按此映射徽标颜色与文案。 */
enum class BenchmarkRating(@StringRes val labelRes: Int) {
    POOR(R.string.storage_rating_poor),
    FAIR(R.string.storage_rating_fair),
    GOOD(R.string.storage_rating_good),
    EXCELLENT(R.string.storage_rating_excellent)
}

/**
 * 指标评级阈值与查表。
 *
 * 阈值按 UFS 2.1 / 3.1 / 4.0 与 eMMC 5.1 的公开性能区间推导，**属于工程经验值而非规范值**，
 * 只用于给用户一个直观印象，不构成硬件鉴定结论。模拟器、虚拟机与老旧 eMMC 设备
 * 全部落在"较差"档是预期行为。
 *
 * 各常量的单位随指标类型变化，与 [rate] 的参数约定一致。
 */
object RatingThresholds {

    // 顺序写入（含 fsync 的平均值），MB/s
    const val SEQUENTIAL_WRITE_FAIR = 80.0
    const val SEQUENTIAL_WRITE_GOOD = 200.0
    const val SEQUENTIAL_WRITE_EXCELLENT = 500.0

    // 顺序读取（首轮），MB/s。口径偏乐观，阈值相应上移。
    const val SEQUENTIAL_READ_FAIR = 200.0
    const val SEQUENTIAL_READ_GOOD = 500.0
    const val SEQUENTIAL_READ_EXCELLENT = 1_200.0

    // 4K 随机读，IOPS
    const val RANDOM_READ_FAIR = 2_000.0
    const val RANDOM_READ_GOOD = 10_000.0
    const val RANDOM_READ_EXCELLENT = 40_000.0

    // 4K 随机写（每 64 次批量同步），IOPS
    const val RANDOM_WRITE_FAIR = 1_000.0
    const val RANDOM_WRITE_GOOD = 5_000.0
    const val RANDOM_WRITE_EXCELLENT = 15_000.0

    // 小文件「创建→写→fdatasync→关闭」，个/秒
    const val SMALL_FILE_FAIR = 300.0
    const val SMALL_FILE_GOOD = 1_000.0
    const val SMALL_FILE_EXCELLENT = 2_500.0

    // SQLite 单事务批量插入，行/秒
    const val SQLITE_INSERT_FAIR = 3_000.0
    const val SQLITE_INSERT_GOOD = 10_000.0
    const val SQLITE_INSERT_EXCELLENT = 25_000.0

    /**
     * 按阶段与数值查评级。
     *
     * **传值约定**（传错单位会得到看似合理但完全错误的评级）：
     *
     * | 阶段 | 传入值 |
     * |------|--------|
     * | `SEQUENTIAL_WRITE` | 含 `fsync` 的平均吞吐，字节/秒 |
     * | `SEQUENTIAL_READ` | **首轮**读取吞吐，字节/秒 |
     * | `RANDOM_READ` / `RANDOM_WRITE` | IOPS |
     * | `SMALL_FILE` | 文件数/秒 |
     * | `SQLITE_INSERT` | 行数/秒 |
     *
     * @return 评级；[BenchmarkPhase.CACHE_FLUSH] 无可评分量，返回 null
     */
    fun rate(phase: BenchmarkPhase, valuePerSecond: Double): BenchmarkRating? = when (phase) {
        BenchmarkPhase.SEQUENTIAL_WRITE -> rateBy(
            valuePerSecond / BYTES_PER_MB,
            SEQUENTIAL_WRITE_FAIR,
            SEQUENTIAL_WRITE_GOOD,
            SEQUENTIAL_WRITE_EXCELLENT
        )

        BenchmarkPhase.SEQUENTIAL_READ -> rateBy(
            valuePerSecond / BYTES_PER_MB,
            SEQUENTIAL_READ_FAIR,
            SEQUENTIAL_READ_GOOD,
            SEQUENTIAL_READ_EXCELLENT
        )

        BenchmarkPhase.RANDOM_READ -> rateBy(
            valuePerSecond,
            RANDOM_READ_FAIR,
            RANDOM_READ_GOOD,
            RANDOM_READ_EXCELLENT
        )

        BenchmarkPhase.RANDOM_WRITE -> rateBy(
            valuePerSecond,
            RANDOM_WRITE_FAIR,
            RANDOM_WRITE_GOOD,
            RANDOM_WRITE_EXCELLENT
        )

        BenchmarkPhase.SMALL_FILE -> rateBy(
            valuePerSecond,
            SMALL_FILE_FAIR,
            SMALL_FILE_GOOD,
            SMALL_FILE_EXCELLENT
        )

        BenchmarkPhase.SQLITE_INSERT -> rateBy(
            valuePerSecond,
            SQLITE_INSERT_FAIR,
            SQLITE_INSERT_GOOD,
            SQLITE_INSERT_EXCELLENT
        )

        BenchmarkPhase.CACHE_FLUSH -> null
    }

    /**
     * 恰好等于阈值时归入较高一档，与"达到该档位"的直觉一致。
     *
     * 非有限值（NaN、±Infinity）与负值一律判为 [BenchmarkRating.POOR]：这类数值意味着
     * 测量本身出了问题（例如耗时算成了 0），绝不能因为"数值看起来很大"就报成优秀。
     */
    private fun rateBy(
        value: Double,
        fair: Double,
        good: Double,
        excellent: Double
    ): BenchmarkRating = when {
        !value.isFinite() || value < fair -> BenchmarkRating.POOR
        value >= excellent -> BenchmarkRating.EXCELLENT
        value >= good -> BenchmarkRating.GOOD
        else -> BenchmarkRating.FAIR
    }

    private const val BYTES_PER_MB = MB.toDouble()
}
