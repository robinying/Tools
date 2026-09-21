package com.robin.tools.feature.storage.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 真实的 Room 数据库验证。
 *
 * 用 in-memory 库：它仍然是真实的 SQLite 与真实的 DAO 实现，
 * 只是不落盘，测试之间互不干扰、无需清理。
 */
@RunWith(AndroidJUnit4::class)
class StorageBenchDatabaseTest {

    private lateinit var database: StorageBenchDatabase
    private lateinit var dao: BenchmarkResultDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, StorageBenchDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = database.benchmarkResultDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun insert_thenObserveReturnsNewestFirst() = runBlocking {
        dao.insert(testEntity(startedAt = 1_000L, deviceModel = "older"))
        dao.insert(testEntity(startedAt = 2_000L, deviceModel = "newer"))

        val entries = dao.observeRecent(limit = 10).first()

        assertEquals(listOf("newer", "older"), entries.map { it.deviceModel })
    }

    @Test
    fun trimTo_keepsOnlyTheNewestRows() = runBlocking {
        repeat(8) { index -> dao.insert(testEntity(startedAt = 1_000L * index)) }

        dao.trimTo(keep = 3)

        val remaining = dao.getRecent(limit = 100)
        assertEquals(3, remaining.size)
        assertEquals(listOf(7_000L, 6_000L, 5_000L), remaining.map { it.startedAtEpochMillis })
    }

    @Test
    fun trimTo_isANoOpWhenNothingExceedsTheLimit() = runBlocking {
        repeat(3) { index -> dao.insert(testEntity(startedAt = 1_000L * index)) }

        dao.trimTo(keep = 50)

        assertEquals(3, dao.getRecent(limit = 100).size)
    }

    @Test
    fun deleteAll_emptiesTheTable() = runBlocking {
        repeat(4) { dao.insert(testEntity(startedAt = it.toLong())) }

        dao.deleteAll()

        assertEquals(0, dao.getRecent(limit = 100).size)
    }

    @Test
    fun repository_roundTripsARealResult() = runBlocking {
        val repository = BenchmarkRepository(dao)

        repository.save(testResult(coldReadAttempted = true))
        val entry = repository.observeRecent().first().single()

        assertEquals(BenchmarkTarget.INTERNAL_FILES, entry.target)
        assertEquals(BenchmarkPreset.STANDARD, entry.preset)
        assertEquals(true, entry.coldReadAttempted)
        assertEquals(256.0 * (1024.0 * 1024.0), entry.sequentialWriteBytesPerSecond, 0.001)
        assertEquals(75_673.0, entry.randomReadIops, 0.001)
        assertEquals("Test Device", entry.deviceModel)
    }

    @Test
    fun repository_keepsOnlyTheHistoryLimit() = runBlocking {
        val repository = BenchmarkRepository(dao)

        repeat(BenchmarkRepository.MAX_HISTORY_ROWS + 5) { index ->
            repository.save(testResult().copy(startedAtEpochMillis = 1_000L + index))
        }

        assertEquals(BenchmarkRepository.MAX_HISTORY_ROWS, repository.getRecent().size)
    }

    @Test
    fun repository_storesResultsWithoutAPresetOrSqlitePhase() = runBlocking {
        val repository = BenchmarkRepository(dao)

        repository.save(testResult(preset = null).copy(sqliteInsert = null))
        val entry = repository.observeRecent().first().single()

        assertNull(entry.preset)
        assertNull(entry.sqliteRowsPerSecond)
    }

    private fun testEntity(
        startedAt: Long,
        deviceModel: String = "Test Device"
    ) = BenchmarkResultEntity(
        startedAtEpochMillis = startedAt,
        targetName = "INTERNAL_FILES",
        presetName = "QUICK",
        coldReadAttempted = false,
        sequentialWriteBytesPerSecond = 1.0,
        sequentialReadBytesPerSecond = 2.0,
        randomReadIops = 3.0,
        randomWriteIops = 4.0,
        smallFilesPerSecond = 5.0,
        sqliteRowsPerSecond = null,
        totalElapsedMillis = 100L,
        deviceModel = deviceModel
    )

    /**
     * 极小的完整结果，只用于验证映射。
     *
     * 与 JVM 测试里的同名工具分开维护：androidTest 看不到 `src/test` 的源集，
     * 为一个几十行的构造器把测试夹具塞进 main 源集不值得。
     */
    private fun testResult(
        preset: BenchmarkPreset? = BenchmarkPreset.STANDARD,
        coldReadAttempted: Boolean = false
    ): BenchmarkResult {
        val bytesPerSecond = 256.0 * 1024.0 * 1024.0
        val write = ThroughputMetric(
            phase = BenchmarkPhase.SEQUENTIAL_WRITE,
            bytes = 256L,
            elapsedNanos = 1_000_000L,
            averageBytesPerSecond = bytesPerSecond,
            steadyStateBytesPerSecond = null,
            bufferedBytesPerSecond = null,
            timeline = emptyList()
        )
        val read = write.copy(phase = BenchmarkPhase.SEQUENTIAL_READ)
        return BenchmarkResult(
            target = BenchmarkTarget.INTERNAL_FILES,
            preset = preset,
            sequentialWrite = write,
            sequentialReadFirstPass = read,
            sequentialReadRepeat = read,
            randomRead = randomMetric(BenchmarkPhase.RANDOM_READ, 75_673.0),
            randomWrite = randomMetric(BenchmarkPhase.RANDOM_WRITE, 39_002.0),
            smallFile = randomMetric(BenchmarkPhase.SMALL_FILE, 2_080.0),
            sqliteInsert = OperationRateMetric(
                phase = BenchmarkPhase.SQLITE_INSERT,
                operationCount = 10,
                elapsedNanos = 1_000_000L,
                operationsPerSecond = 20_900.0
            ),
            coldReadAttempted = coldReadAttempted,
            device = DeviceSnapshot(
                model = "Test Device",
                apiLevel = 34,
                abi = "arm64-v8a",
                totalRamBytes = 0L,
                volumeTotalBytes = 0L,
                volumeFreeBytesAtStart = 0L,
                isExternalEmulated = false
            ),
            startedAtEpochMillis = 1_700_000_000_000L,
            totalElapsedMillis = 1_964L
        )
    }

    private fun randomMetric(phase: BenchmarkPhase, operationsPerSecond: Double) = RandomIoMetric(
        phase = phase,
        operationCount = 10,
        blockBytes = 4096,
        elapsedNanos = 1_000_000L,
        operationsPerSecond = operationsPerSecond,
        bytesPerSecond = operationsPerSecond * 4096,
        latencyP50Micros = 900L,
        latencyP95Micros = 2_400L,
        latencyP99Micros = 6_100L,
        syncEveryOps = 0
    )
}
