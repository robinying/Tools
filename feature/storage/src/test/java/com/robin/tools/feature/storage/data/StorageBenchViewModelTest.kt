package com.robin.tools.feature.storage.data

import android.database.sqlite.SQLiteException
import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class StorageBenchViewModelTest {

    private val engine = mockk<BenchmarkEngine>()
    private val resolver = mockk<StorageTargetResolver>()
    private val repository = mockk<BenchmarkRepository>()
    private val dispatcher = UnconfinedTestDispatcher()
    private lateinit var viewModel: StorageBenchViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        every { resolver.resolveRootDirectory(any()) } returns File("/storage-bench-test")
        every { resolver.availableBytes(any()) } returns TEST_AVAILABLE_BYTES
        every { resolver.totalBytes(any()) } returns TEST_TOTAL_BYTES
        every { repository.observeRecent(any()) } returns flowOf(emptyList())
        coEvery { repository.save(any()) } returns 1L
        viewModel = StorageBenchViewModel(engine, resolver, repository, dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `starts idle with the default selection and loaded capacity`() {
        assertEquals(BenchmarkProgress.Idle, viewModel.progress.value)
        assertEquals(BenchmarkTarget.INTERNAL_FILES, viewModel.selectedTarget.value)
        assertEquals(BenchmarkPreset.STANDARD, viewModel.selectedPreset.value)
        assertFalse(viewModel.coldRead.value)
        assertEquals(TEST_AVAILABLE_BYTES, viewModel.targetInfo.value?.availableBytes)
        assertEquals(TEST_TOTAL_BYTES, viewModel.targetInfo.value?.totalBytes)
    }

    @Test
    fun `start publishes running and then the finished result`() = runTest(dispatcher) {
        coEvery { engine.run(any(), any()) } returns testResult()

        viewModel.progress.test {
            assertEquals(BenchmarkProgress.Idle, awaitItem())
            viewModel.start()
            assertTrue(awaitItem() is BenchmarkProgress.Running)

            val finished = awaitTerminal()
            assertTrue(finished is BenchmarkProgress.Finished)
            assertEquals(BenchmarkPreset.STANDARD, (finished as BenchmarkProgress.Finished).result.preset)
        }
    }

    @Test
    fun `a finished run is persisted to the history`() = runTest(dispatcher) {
        val result = testResult()
        coEvery { engine.run(any(), any()) } returns result

        viewModel.start()

        coVerify(exactly = 1) { repository.save(result) }
    }

    @Test
    fun `a cancelled run is not persisted`() = runTest(dispatcher) {
        val gate = CompletableDeferred<BenchmarkResult>()
        coEvery { engine.run(any(), any()) } coAnswers { gate.await() }

        viewModel.progress.test {
            viewModel.start()
            viewModel.cancel()
            assertTrue(awaitTerminal() is BenchmarkProgress.Cancelled)
        }

        coVerify(exactly = 0) { repository.save(any()) }
    }

    @Test
    fun `a failing history write does not turn a good run into a failure`() = runTest(dispatcher) {
        // mockk 实例化不调用构造函数，避免 JVM 单测里碰到 android.jar 的桩实现。
        coEvery { engine.run(any(), any()) } returns testResult()
        coEvery { repository.save(any()) } throws mockk<SQLiteException>()

        viewModel.progress.test {
            viewModel.start()
            assertTrue(awaitTerminal() is BenchmarkProgress.Finished)
        }
    }

    @Test
    fun `history is exposed from the repository`() = runTest(dispatcher) {
        val entry = testResult().toEntity().toHistoryEntry()
        every { repository.observeRecent(any()) } returns flowOf(listOf(entry))
        val localViewModel = StorageBenchViewModel(engine, resolver, repository, dispatcher)

        assertEquals(listOf(entry), localViewModel.history.value)
    }

    @Test
    fun `clearHistory delegates to the repository`() = runTest(dispatcher) {
        coEvery { repository.deleteAll() } returns Unit

        viewModel.clearHistory()

        coVerify(exactly = 1) { repository.deleteAll() }
    }

    @Test
    fun `the running state sizes its progress bar from the actual phase plan`() = runTest(dispatcher) {
        val gate = CompletableDeferred<BenchmarkResult>()
        coEvery { engine.run(any(), any()) } coAnswers { gate.await() }

        viewModel.progress.test {
            assertEquals(BenchmarkProgress.Idle, awaitItem())
            viewModel.start()

            // 默认档位带 SQLite、不带冷读，因此分母是 6 而不是枚举总数 7。
            val running = awaitItem() as BenchmarkProgress.Running
            assertEquals(
                BenchmarkPreset.STANDARD.toSpec(BenchmarkTarget.INTERNAL_FILES).phasePlan.size,
                running.phaseCount
            )
        }

        gate.complete(testResult())
    }

    @Test
    fun `enabling cold read adds the cache flush phase to the plan`() = runTest(dispatcher) {
        val gate = CompletableDeferred<BenchmarkResult>()
        coEvery { engine.run(any(), any()) } coAnswers { gate.await() }

        viewModel.setColdRead(true)

        viewModel.progress.test {
            assertEquals(BenchmarkProgress.Idle, awaitItem())
            viewModel.start()

            val running = awaitItem() as BenchmarkProgress.Running
            assertEquals(7, running.phaseCount)
        }

        gate.complete(testResult())
    }

    @Test
    fun `start is ignored while a run is in flight`() = runTest(dispatcher) {
        val gate = CompletableDeferred<BenchmarkResult>()
        coEvery { engine.run(any(), any()) } coAnswers { gate.await() }

        viewModel.start()
        viewModel.start()

        coVerify(exactly = 1) { engine.run(any(), any()) }

        gate.complete(testResult())
    }

    @Test
    fun `insufficient space maps to the space failure with byte counts`() = runTest(dispatcher) {
        coEvery { engine.run(any(), any()) } throws InsufficientStorageException(
            requiredAvailableBytes = 4L * KB * MB,
            availableBytes = KB * MB,
            message = "not enough space"
        )

        viewModel.progress.test {
            viewModel.start()
            val failed = awaitTerminal() as BenchmarkProgress.Failed

            assertEquals(BenchmarkFailure.NOT_ENOUGH_SPACE, failed.reason)
            assertEquals(4L * KB * MB, failed.requiredBytes)
            assertEquals(KB * MB, failed.availableBytes)
        }
    }

    @Test
    fun `unavailable target maps to the target failure`() = runTest(dispatcher) {
        coEvery { engine.run(any(), any()) } throws TargetUnavailableException("no external storage")

        viewModel.progress.test {
            viewModel.start()
            val failed = awaitTerminal() as BenchmarkProgress.Failed

            assertEquals(BenchmarkFailure.TARGET_UNAVAILABLE, failed.reason)
            assertEquals(null, failed.requiredBytes)
        }
    }

    @Test
    fun `a database failure is not reported as a plain io error`() = runTest(dispatcher) {
        // BenchmarkDatabaseException 是 IOException 的子类，catch 顺序错了就会被吞成 IO_ERROR。
        coEvery { engine.run(any(), any()) } throws BenchmarkDatabaseException(
            "sqlite insert failed",
            IllegalStateException("disk full")
        )

        viewModel.progress.test {
            viewModel.start()
            val failed = awaitTerminal() as BenchmarkProgress.Failed

            assertEquals(BenchmarkFailure.DATABASE_ERROR, failed.reason)
        }
    }

    @Test
    fun `an io error keeps the phase reported by the last engine callback`() = runTest(dispatcher) {
        coEvery { engine.run(any(), any()) } coAnswers {
            val onProgress = arg<(EngineProgress) -> Unit>(1)
            onProgress(progressEvent(BenchmarkPhase.RANDOM_WRITE, phaseIndex = 3, overallFraction = 0.75f))
            throw IOException("disk went away")
        }

        viewModel.progress.test {
            viewModel.start()
            val failed = awaitTerminal() as BenchmarkProgress.Failed

            assertEquals(BenchmarkFailure.IO_ERROR, failed.reason)
            assertEquals(BenchmarkPhase.RANDOM_WRITE, failed.phase)
        }
    }

    @Test
    fun `an unexpected error maps to the unknown failure`() = runTest(dispatcher) {
        coEvery { engine.run(any(), any()) } throws IllegalStateException("unexpected")

        viewModel.progress.test {
            viewModel.start()
            val failed = awaitTerminal() as BenchmarkProgress.Failed

            assertEquals(BenchmarkFailure.UNKNOWN, failed.reason)
        }
    }

    @Test
    fun `cancel marks the run as cancelled`() = runTest(dispatcher) {
        val gate = CompletableDeferred<BenchmarkResult>()
        coEvery { engine.run(any(), any()) } coAnswers { gate.await() }

        viewModel.progress.test {
            viewModel.start()
            viewModel.cancel()

            assertTrue(awaitTerminal() is BenchmarkProgress.Cancelled)
        }
    }

    @Test
    fun `selection changes are ignored while a run is in flight`() = runTest(dispatcher) {
        val gate = CompletableDeferred<BenchmarkResult>()
        coEvery { engine.run(any(), any()) } coAnswers { gate.await() }

        viewModel.start()
        viewModel.selectTarget(BenchmarkTarget.EXTERNAL_APP)
        viewModel.selectPreset(BenchmarkPreset.THOROUGH)
        viewModel.setColdRead(true)

        assertEquals(BenchmarkTarget.INTERNAL_FILES, viewModel.selectedTarget.value)
        assertEquals(BenchmarkPreset.STANDARD, viewModel.selectedPreset.value)
        assertFalse(viewModel.coldRead.value)

        gate.complete(testResult())
    }

    @Test
    fun `selecting another preset clears the previous result`() = runTest(dispatcher) {
        coEvery { engine.run(any(), any()) } returns testResult()

        viewModel.start()
        assertTrue(viewModel.progress.value is BenchmarkProgress.Finished)

        viewModel.selectPreset(BenchmarkPreset.QUICK)

        assertEquals(BenchmarkProgress.Idle, viewModel.progress.value)
        assertEquals(BenchmarkPreset.QUICK, viewModel.selectedPreset.value)
    }

    @Test
    fun `toggling cold read clears the previous result`() = runTest(dispatcher) {
        coEvery { engine.run(any(), any()) } returns testResult()

        viewModel.start()
        assertTrue(viewModel.progress.value is BenchmarkProgress.Finished)

        viewModel.setColdRead(true)

        assertEquals(BenchmarkProgress.Idle, viewModel.progress.value)
        assertTrue(viewModel.coldRead.value)
    }

    /** 跳到第一个终态，跳过 Idle 与 Running，不必依赖调度器的同步性。 */
    private suspend fun ReceiveTurbine<BenchmarkProgress>.awaitTerminal(): BenchmarkProgress {
        var state = awaitItem()
        while (state is BenchmarkProgress.Running || state is BenchmarkProgress.Idle) {
            state = awaitItem()
        }
        return state
    }

    private fun progressEvent(
        phase: BenchmarkPhase,
        phaseIndex: Int,
        overallFraction: Float
    ) = EngineProgress(
        phase = phase,
        phaseIndex = phaseIndex,
        phaseCount = BenchmarkPhase.entries.size,
        phaseFraction = overallFraction,
        overallFraction = overallFraction,
        liveRatePerSecond = 0.0,
        elapsedMillis = 100L
    )
}
