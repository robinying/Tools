package com.robin.tools.feature.storage.data

import kotlin.math.ceil

/**
 * 基准测试的纯计算。
 *
 * 这里刻意不含任何 Android 依赖，也**不含任何面向展示的格式化**：
 * 单位选择与数值呈现属于展示层，见 `presentation/MetricFormat.kt`。
 * 这样这个对象可以在 JVM 单元测试里被完整覆盖，而格式化也能独立演进。
 */
object BenchmarkMath {

    /** 稳态区间头部裁剪比例：剔除写入初期页缓存建立阶段的快速样本。 */
    const val STEADY_STATE_HEAD_TRIM = 0.10

    /** 稳态区间尾部裁剪比例：剔除末尾落盘同步造成的低速样本。 */
    const val STEADY_STATE_TAIL_TRIM = 0.05

    /** 低于该采样点数不足以计算稳态值。 */
    const val MIN_STEADY_STATE_SAMPLES = 3

    /**
     * 计算平均吞吐量。
     *
     * @param bytes 传输字节数，非正数按 0 处理
     * @param elapsedNanos 耗时纳秒数，非正数时返回 0 而不是无穷大
     * @return 字节每秒
     */
    fun bytesPerSecond(bytes: Long, elapsedNanos: Long): Double {
        if (bytes <= 0L || elapsedNanos <= 0L) {
            return 0.0
        }
        return bytes.toDouble() * NANOS_PER_SECOND / elapsedNanos.toDouble()
    }

    /**
     * 由时间线采样计算稳态吞吐。
     *
     * 取中间区间（剔除首 [STEADY_STATE_HEAD_TRIM] 与末 [STEADY_STATE_TAIL_TRIM]）
     * 的瞬时速率均值。采样点按等字节间隔产生，因此该均值是对稳态段的均匀加权估计。
     *
     * @return 稳态字节每秒；剩余采样点少于 [MIN_STEADY_STATE_SAMPLES] 时返回 null
     */
    fun steadyStateBytesPerSecond(timeline: List<ThroughputSample>): Double? {
        val headIndex = (timeline.size * STEADY_STATE_HEAD_TRIM).toInt()
        val tailIndex = timeline.size - (timeline.size * STEADY_STATE_TAIL_TRIM).toInt()
        if (tailIndex <= headIndex) {
            return null
        }

        val window = timeline.subList(headIndex, tailIndex)
        if (window.size < MIN_STEADY_STATE_SAMPLES) {
            return null
        }
        return window.sumOf { it.bytesPerSecond } / window.size
    }

    /**
     * 最近秩法（nearest-rank）计算百分位。
     *
     * 取第 `ceil(p * n)` 小的样本，因此 p50 是真正的中位数，p99 不会外插出样本之外的值。
     *
     * @param sortedNanos 已升序排序的样本
     * @param percentile 0.0 < p <= 1.0；越界会被夹到 [0, 1]
     * @return 对应百分位的样本值；样本为空时返回 0
     */
    fun percentileNanos(sortedNanos: LongArray, percentile: Double): Long {
        if (sortedNanos.isEmpty()) {
            return 0L
        }
        val rank = ceil(percentile * sortedNanos.size).toInt().coerceIn(1, sortedNanos.size)
        return sortedNanos[rank - 1]
    }

    /** 纳秒转微秒。延迟按微秒展示，纳秒级的抖动没有必要呈现。 */
    fun nanosToMicros(nanos: Long): Long = nanos / NANOS_PER_MICRO

    private const val NANOS_PER_SECOND = 1_000_000_000.0
    private const val NANOS_PER_MICRO = 1_000L
}
