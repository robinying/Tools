package com.robin.tools.feature.storage.data

/**
 * 逐次操作延迟采样器。
 *
 * 容量在构造时一次性确定并按操作次数精确预分配：测量延迟的代码路径里不能出现扩容分配，
 * 否则分配开销本身会进入被测量的区间。调用方负责保证 `record` 的调用次数不超过容量，
 * 超出部分会被静默丢弃——引擎按操作次数构造，因此正常路径不会发生。
 *
 * 本类不持有 Android 依赖，可在 JVM 单元测试中直接使用。
 */
class LatencyRecorder(private val capacity: Int) {

    init {
        require(capacity > 0) { "capacity must be positive, was $capacity" }
    }

    private val samples = LongArray(capacity)
    private var count = 0

    /** 记录一次操作的耗时。 */
    fun record(elapsedNanos: Long) {
        if (count >= capacity) {
            return
        }
        samples[count] = elapsedNanos
        count++
    }

    /** 已记录的样本数。 */
    fun sampleCount(): Int = count

    /** 升序排序后的样本副本，供 [BenchmarkMath.percentileNanos] 使用。 */
    fun sortedSamples(): LongArray = samples.copyOf(count).apply { sort() }
}
