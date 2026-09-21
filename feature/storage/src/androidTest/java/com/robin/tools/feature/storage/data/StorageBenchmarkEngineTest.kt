package com.robin.tools.feature.storage.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * 真机上的引擎端到端验证。
 *
 * 使用极小载荷，只验证流程与清理不变量，不做性能断言——性能数字依赖设备，
 * 断言具体数值会让测试在不同机器上随机失败。
 */
@RunWith(AndroidJUnit4::class)
class StorageBenchmarkEngineTest {

    private lateinit var resolver: StorageTargetResolver
    private lateinit var engine: StorageBenchmarkEngine
    private lateinit var workspace: File

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        resolver = StorageTargetResolver(context)
        engine = StorageBenchmarkEngine(resolver, DeviceSnapshotProvider(context))
        workspace = resolver.requireWorkspace(BenchmarkTarget.INTERNAL_CACHE)
        StorageSafetyGuard.purgeTestArtifacts(workspace)
    }

    @After
    fun tearDown() {
        StorageSafetyGuard.purgeTestArtifacts(workspace)
    }

    @Test
    fun run_producesEveryMetricAndLeavesNoArtifacts() = runBlocking {
        val spec = fullSpec()

        val result = engine.run(spec) { }

        assertEquals(TEST_BYTES, result.sequentialWrite.bytes)
        assertEquals(TEST_BYTES, result.sequentialReadFirstPass.bytes)
        assertEquals(TEST_BYTES, result.sequentialReadRepeat.bytes)
        assertTrue(result.sequentialWrite.averageBytesPerSecond > 0.0)
        assertNotNull(result.sequentialWrite.bufferedBytesPerSecond)
        assertEquals(RANDOM_OPS, result.randomRead.operationCount)
        assertEquals(RANDOM_OPS, result.randomWrite.operationCount)
        assertEquals(SMALL_FILE_COUNT, result.smallFile.operationCount)
        assertEquals(SQLITE_ROWS, result.sqliteInsert?.operationCount)
        assertTrue(result.coldReadAttempted)
        assertEquals(null, result.preset)
        assertWorkspaceClean()
    }

    @Test
    fun run_reportsEveryPhaseOfThePlan() = runBlocking {
        val spec = fullSpec()
        val seenPhases = mutableSetOf<BenchmarkPhase>()

        engine.run(spec) { progress -> seenPhases.add(progress.phase) }

        assertEquals(spec.phasePlan.toSet(), seenPhases)
    }

    @Test
    fun run_reportsLatencyPercentilesInOrder() = runBlocking {
        val result = engine.run(fullSpec()) { }

        listOf(result.randomRead, result.randomWrite, result.smallFile).forEach { metric ->
            assertTrue(
                "p50 ${metric.latencyP50Micros} > p95 ${metric.latencyP95Micros} in ${metric.phase}",
                metric.latencyP50Micros <= metric.latencyP95Micros
            )
            assertTrue(
                "p95 ${metric.latencyP95Micros} > p99 ${metric.latencyP99Micros} in ${metric.phase}",
                metric.latencyP95Micros <= metric.latencyP99Micros
            )
        }
    }

    @Test
    fun run_recordsTheSyncPolicyOfEachPhase() = runBlocking {
        val result = engine.run(fullSpec()) { }

        // 随机读不做同步；随机写批量同步；小文件逐文件同步。
        assertEquals(0, result.randomRead.syncEveryOps)
        assertTrue(result.randomWrite.syncEveryOps > 1)
        assertEquals(1, result.smallFile.syncEveryOps)
        assertEquals(DEFAULT_RANDOM_BLOCK_BYTES, result.randomRead.blockBytes)
        assertEquals(SMALL_FILE_BYTES, result.smallFile.blockBytes)
    }

    @Test
    fun run_skipsTheOptionalPhasesWhenNotRequested() = runBlocking {
        val spec = fullSpec().copy(coldRead = false, sqliteRows = 0, cacheFlushBytes = 0L)

        val result = engine.run(spec) { }

        assertFalse(result.coldReadAttempted)
        assertEquals(null, result.sqliteInsert)
        assertFalse(spec.phasePlan.contains(BenchmarkPhase.CACHE_FLUSH))
        assertWorkspaceClean()
    }

    @Test
    fun run_rejectsOversizedPayloadAndStaysClean() {
        val spec = fullSpec().copy(sequentialBytes = Long.MAX_VALUE / 8)

        assertThrows(InsufficientStorageException::class.java) {
            runBlocking { engine.run(spec) { } }
        }

        assertWorkspaceClean()
    }

    @Test
    fun cancellingRun_stillCleansUpWorkspace() = runBlocking {
        val spec = fullSpec().copy(sequentialBytes = CANCEL_TEST_BYTES)
        val firstProgress = CompletableDeferred<Unit>()

        val job = launch(Dispatchers.IO) {
            engine.run(spec) {
                if (!firstProgress.isCompleted) {
                    firstProgress.complete(Unit)
                }
            }
        }

        val reported = withTimeoutOrNull(START_TIMEOUT_MILLIS) { firstProgress.await() }
        assertNotNull("engine never reported progress", reported)

        job.cancelAndJoin()

        assertWorkspaceClean()
    }

    @Test
    fun run_onExternalStorageAlsoCleansUp() {
        // 应用专属外部目录走的是另一条路径（FUSE 模拟层），清理不变量要在那里也成立。
        val externalRoot = resolver.resolveRootDirectory(BenchmarkTarget.EXTERNAL_APP)
        assumeTrue("no external storage on this device", externalRoot != null)
        val externalWorkspace = File(externalRoot!!, StorageTargetResolver.WORKSPACE_DIR_NAME)
        StorageSafetyGuard.purgeTestArtifacts(externalWorkspace)

        try {
            val spec = fullSpec().copy(target = BenchmarkTarget.EXTERNAL_APP)

            val result = runBlocking { engine.run(spec) { } }

            assertEquals(BenchmarkTarget.EXTERNAL_APP, result.target)
            assertTrue(result.sequentialWrite.averageBytesPerSecond > 0.0)
            val leftovers = externalWorkspace.listFiles()?.map { it.name }.orEmpty()
            assertEquals("external artifacts left behind: $leftovers", 0, leftovers.size)
        } finally {
            StorageSafetyGuard.purgeTestArtifacts(externalWorkspace)
        }
    }

    /** 覆盖全部 7 个阶段的最小载荷。 */
    private fun fullSpec() = BenchmarkSpec(
        target = BenchmarkTarget.INTERNAL_CACHE,
        sequentialBytes = TEST_BYTES,
        randomRegionBytes = RANDOM_REGION_BYTES,
        randomOps = RANDOM_OPS,
        randomBlockBytes = DEFAULT_RANDOM_BLOCK_BYTES,
        smallFileCount = SMALL_FILE_COUNT,
        smallFileBytes = SMALL_FILE_BYTES,
        sqliteRows = SQLITE_ROWS,
        cacheFlushBytes = TEST_BYTES,
        coldRead = true
    )

    private fun assertWorkspaceClean() {
        val leftovers = workspace.listFiles()?.map { it.name }.orEmpty()
        assertEquals("test artifacts left behind: $leftovers", 0, leftovers.size)
    }

    private companion object {
        const val BYTES_PER_MB = 1024L * 1024L

        /** 小载荷：足以触发全部阶段，又不会在测试机上留下明显负担。 */
        const val TEST_BYTES = 2L * BYTES_PER_MB

        const val RANDOM_REGION_BYTES = 1L * BYTES_PER_MB
        const val RANDOM_OPS = 50
        const val SMALL_FILE_COUNT = 5
        const val SMALL_FILE_BYTES = 512
        const val SQLITE_ROWS = 20

        /** 取消测试需要跑得久一点，才能保证取消发生在写入过程中。 */
        const val CANCEL_TEST_BYTES = 64L * BYTES_PER_MB

        const val START_TIMEOUT_MILLIS = 10_000L
    }
}
