package com.robin.tools.feature.storage.data

/**
 * 测试用的小载荷。
 *
 * 每个阶段都有可执行的载荷，但取值都压到最小，避免单元测试碰到真实的磁盘容量与耗时。
 */
internal fun testSpec(
    target: BenchmarkTarget = BenchmarkTarget.INTERNAL_CACHE,
    coldRead: Boolean = false,
    sqliteRows: Int = 0,
    cacheFlushBytes: Long = 1L * MB,
    sequentialBytes: Long = 1L * MB,
    randomRegionBytes: Long = 1L * MB,
    randomOps: Int = 4,
    smallFileCount: Int = 2,
    smallFileBytes: Int = 512
): BenchmarkSpec = BenchmarkSpec(
    target = target,
    sequentialBytes = sequentialBytes,
    randomRegionBytes = randomRegionBytes,
    randomOps = randomOps,
    randomBlockBytes = DEFAULT_RANDOM_BLOCK_BYTES,
    smallFileCount = smallFileCount,
    smallFileBytes = smallFileBytes,
    sqliteRows = sqliteRows,
    cacheFlushBytes = cacheFlushBytes,
    coldRead = coldRead
)

/** 各字段取值互不相同，便于断言映射时发现字段串位。 */
internal val TEST_AVAILABLE_BYTES = 32L * KB * MB
internal val TEST_TOTAL_BYTES = 128L * KB * MB

/** 构造一份完整的测试结果，供映射与状态机测试使用。 */
internal fun testResult(
    target: BenchmarkTarget = BenchmarkTarget.INTERNAL_FILES,
    preset: BenchmarkPreset? = BenchmarkPreset.STANDARD,
    coldReadAttempted: Boolean = false
): BenchmarkResult {
    val write = ThroughputMetric(
        phase = BenchmarkPhase.SEQUENTIAL_WRITE,
        bytes = 256L * MB,
        elapsedNanos = 1_000_000_000L,
        averageBytesPerSecond = 256.0 * MB,
        steadyStateBytesPerSecond = 200.0 * MB,
        bufferedBytesPerSecond = 1_000.0 * MB,
        timeline = emptyList()
    )
    val read = write.copy(phase = BenchmarkPhase.SEQUENTIAL_READ)
    return BenchmarkResult(
        target = target,
        preset = preset,
        sequentialWrite = write,
        sequentialReadFirstPass = read,
        sequentialReadRepeat = read,
        randomRead = testRandomMetric(BenchmarkPhase.RANDOM_READ, operationsPerSecond = 75_673.0, syncEveryOps = 0),
        randomWrite = testRandomMetric(BenchmarkPhase.RANDOM_WRITE, operationsPerSecond = 39_002.0, syncEveryOps = 64),
        smallFile = testRandomMetric(BenchmarkPhase.SMALL_FILE, operationsPerSecond = 2_080.0, syncEveryOps = 1),
        sqliteInsert = OperationRateMetric(
            phase = BenchmarkPhase.SQLITE_INSERT,
            operationCount = 2_000,
            elapsedNanos = 1_500_000_000L,
            operationsPerSecond = 20_900.0
        ),
        coldReadAttempted = coldReadAttempted,
        device = DeviceSnapshot(
            model = "Test Device",
            apiLevel = 34,
            abi = "arm64-v8a",
            totalRamBytes = 8L * KB * MB,
            volumeTotalBytes = TEST_TOTAL_BYTES,
            volumeFreeBytesAtStart = TEST_AVAILABLE_BYTES,
            isExternalEmulated = false
        ),
        startedAtEpochMillis = 1_700_000_000_000L,
        totalElapsedMillis = 1_964L
    )
}

internal fun testRandomMetric(
    phase: BenchmarkPhase,
    operationsPerSecond: Double,
    syncEveryOps: Int
): RandomIoMetric = RandomIoMetric(
    phase = phase,
    operationCount = 2_000,
    blockBytes = DEFAULT_RANDOM_BLOCK_BYTES,
    elapsedNanos = 2_000_000_000L,
    operationsPerSecond = operationsPerSecond,
    bytesPerSecond = operationsPerSecond * DEFAULT_RANDOM_BLOCK_BYTES,
    latencyP50Micros = 900L,
    latencyP95Micros = 2_400L,
    latencyP99Micros = 6_100L,
    syncEveryOps = syncEveryOps
)
