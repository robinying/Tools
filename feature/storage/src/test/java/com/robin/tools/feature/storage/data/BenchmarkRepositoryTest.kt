package com.robin.tools.feature.storage.data

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BenchmarkRepositoryTest {

    private val dao = mockk<BenchmarkResultDao>()
    private val repository = BenchmarkRepository(dao)

    @Test
    fun `save maps the result onto the entity columns`() = runTest {
        val inserted = slot<BenchmarkResultEntity>()
        coEvery { dao.insert(capture(inserted)) } returns 7L
        coEvery { dao.trimTo(any()) } returns Unit

        val id = repository.save(testResult(coldReadAttempted = true))

        assertEquals(7L, id)
        val entity = inserted.captured
        assertEquals("INTERNAL_FILES", entity.targetName)
        assertEquals("STANDARD", entity.presetName)
        assertEquals(true, entity.coldReadAttempted)
        // 顺序写取的是含 fsync 的平均值，不是缓冲写入的对照值。
        assertEquals(256.0 * MB, entity.sequentialWriteBytesPerSecond, 0.001)
        assertEquals(75_673.0, entity.randomReadIops, 0.001)
        assertEquals(39_002.0, entity.randomWriteIops, 0.001)
        assertEquals(2_080.0, entity.smallFilesPerSecond, 0.001)
        assertEquals(20_900.0, entity.sqliteRowsPerSecond ?: 0.0, 0.001)
        assertEquals("Test Device", entity.deviceModel)
    }

    @Test
    fun `save trims the history after inserting`() = runTest {
        coEvery { dao.insert(any()) } returns 1L
        coEvery { dao.trimTo(any()) } returns Unit

        repository.save(testResult())

        // 先插后裁：单次插入不会因为要先腾位置而失败。
        coVerify(exactly = 1) { dao.insert(any()) }
        coVerify(exactly = 1) { dao.trimTo(BenchmarkRepository.MAX_HISTORY_ROWS) }
    }

    @Test
    fun `a result without a preset is stored with a null preset name`() = runTest {
        val inserted = slot<BenchmarkResultEntity>()
        coEvery { dao.insert(capture(inserted)) } returns 1L
        coEvery { dao.trimTo(any()) } returns Unit

        repository.save(testResult(preset = null))

        assertNull(inserted.captured.presetName)
    }

    @Test
    fun `a result without the sqlite phase is stored with a null row rate`() = runTest {
        val inserted = slot<BenchmarkResultEntity>()
        coEvery { dao.insert(capture(inserted)) } returns 1L
        coEvery { dao.trimTo(any()) } returns Unit

        repository.save(testResult().copy(sqliteInsert = null))

        assertNull(inserted.captured.sqliteRowsPerSecond)
    }

    @Test
    fun `observeRecent maps entities onto history entries`() = runTest {
        every { dao.observeRecent(any()) } returns flowOf(listOf(testEntity()))

        val entries = repository.observeRecent().first()

        assertEquals(1, entries.size)
        val entry = entries.single()
        assertEquals(11L, entry.id)
        assertEquals(BenchmarkTarget.EXTERNAL_APP, entry.target)
        assertEquals(BenchmarkPreset.THOROUGH, entry.preset)
        assertEquals(1_700_000_000_000L, entry.startedAtEpochMillis)
        assertEquals(2_625_214_091.0, entry.sequentialReadBytesPerSecond, 0.001)
    }

    @Test
    fun `an unrecognised target name falls back instead of failing`() = runTest {
        // 旧版本写入、枚举后来被重命名的记录，不该让整个历史列表打不开。
        every { dao.observeRecent(any()) } returns flowOf(listOf(testEntity(targetName = "SOMETHING_OLD")))

        val entry = repository.observeRecent().first().single()

        assertEquals(BenchmarkTarget.INTERNAL_FILES, entry.target)
    }

    @Test
    fun `an unrecognised preset name becomes a custom workload`() = runTest {
        every { dao.observeRecent(any()) } returns flowOf(listOf(testEntity(presetName = "SOMETHING_OLD")))

        val entry = repository.observeRecent().first().single()

        assertNull(entry.preset)
    }

    @Test
    fun `deleteAll delegates to the dao`() = runTest {
        coEvery { dao.deleteAll() } returns Unit

        repository.deleteAll()

        coVerify(exactly = 1) { dao.deleteAll() }
    }

    private fun testEntity(
        targetName: String = "EXTERNAL_APP",
        presetName: String? = "THOROUGH"
    ) = BenchmarkResultEntity(
        id = 11L,
        startedAtEpochMillis = 1_700_000_000_000L,
        targetName = targetName,
        presetName = presetName,
        coldReadAttempted = true,
        sequentialWriteBytesPerSecond = 1_172_338_415.0,
        sequentialReadBytesPerSecond = 2_625_214_091.0,
        randomReadIops = 75_673.0,
        randomWriteIops = 39_002.0,
        smallFilesPerSecond = 2_080.0,
        sqliteRowsPerSecond = 20_900.0,
        totalElapsedMillis = 1_964L,
        deviceModel = "Fairphone 6"
    )
}
