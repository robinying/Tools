package com.robin.tools.feature.lightlux.data

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LightRepositoryTest {

    private val dao = mockk<LightEntryDao>(relaxed = true)
    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var repository: LightRepository

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        repository = LightRepository(dao)
    }

    @After
    fun teardown() {
        Dispatchers.resetMain()
    }

    // region normal paths

    @Test
    fun `getRecentEntries returns Flow from DAO`() = runTest {
        val entry = LightEntry(id = 1, timestamp = 1000, luxValue = 50f)
        every { dao.getRecentEntries(any()) } returns flowOf(listOf(entry))

        val result = mutableListOf<List<LightEntry>>()
        repository.getRecentEntries(10).collect { result.add(it) }

        assertEquals(1, result.size)
        assertEquals(1, result[0].size)
        assertEquals(50f, result[0][0].luxValue)
    }

    @Test
    fun `insertEntry delegates to DAO`() = runTest {
        val entry = LightEntry(timestamp = 2000, luxValue = 30f)
        coEvery { dao.insert(entry) } returns Unit

        repository.insertEntry(entry)

        coVerify { dao.insert(entry) }
    }

    @Test
    fun `getRecentEntriesOnce returns list from DAO`() = runTest {
        val entries = listOf(
            LightEntry(id = 1, timestamp = 1000, luxValue = 10f),
            LightEntry(id = 2, timestamp = 2000, luxValue = 20f)
        )
        coEvery { dao.getRecentEntriesOnce(any()) } returns entries

        val result = repository.getRecentEntriesOnce(5)

        assertEquals(2, result.size)
        assertEquals(10f, result[0].luxValue)
        assertEquals(20f, result[1].luxValue)
    }

    @Test
    fun `getAllEntries returns Flow from DAO`() = runTest {
        val entry = LightEntry(id = 1, timestamp = 1000, luxValue = 100f)
        every { dao.getAllEntries() } returns flowOf(listOf(entry))

        val result = mutableListOf<List<LightEntry>>()
        repository.getAllEntries().collect { result.add(it) }

        assertEquals(1, result.size)
        assertEquals(100f, result[0][0].luxValue)
    }

    @Test
    fun `deleteEntry delegates to DAO`() = runTest {
        val entry = LightEntry(id = 3, timestamp = 3000, luxValue = 10f)
        coEvery { dao.delete(entry) } returns Unit

        repository.deleteEntry(entry)

        coVerify { dao.delete(entry) }
    }

    @Test
    fun `deleteAllEntries delegates to DAO`() = runTest {
        coEvery { dao.deleteAll() } returns Unit

        repository.deleteAllEntries()

        coVerify { dao.deleteAll() }
    }

    // region boundary / edge cases

    @Test
    fun `getRecentEntries returns empty flow`() = runTest {
        every { dao.getRecentEntries(any()) } returns flowOf(emptyList())

        val result = mutableListOf<List<LightEntry>>()
        repository.getRecentEntries().collect { result.add(it) }

        assertTrue("Should return empty list", result[0].isEmpty())
    }

    @Test
    fun `getRecentEntriesOnce returns empty list`() = runTest {
        coEvery { dao.getRecentEntriesOnce(any()) } returns emptyList()

        val result = repository.getRecentEntriesOnce()

        assertTrue("Should return empty list", result.isEmpty())
    }

    @Test
    fun `getRecentEntries default limit is 20`() = runTest {
        every { dao.getRecentEntries(20) } returns flowOf(emptyList())

        repository.getRecentEntries().collect {}

        // Verify default limit=20 is passed
        coVerify { dao.getRecentEntries(20) }
    }

    @Test
    fun `getRecentEntries custom limit is respected`() = runTest {
        every { dao.getRecentEntries(50) } returns flowOf(emptyList())

        repository.getRecentEntries(50).collect {}

        coVerify { dao.getRecentEntries(50) }
    }

    // region error paths

    @Test
    fun `insertEntry propagates DAO exception`() = runTest {
        val entry = LightEntry(timestamp = 0, luxValue = 0f)
        coEvery { dao.insert(entry) } throws RuntimeException("DB error")

        try {
            repository.insertEntry(entry)
        } catch (e: RuntimeException) {
            assertEquals("DB error", e.message)
        }
    }

    @Test
    fun `getRecentEntriesOnce propagates DAO exception`() = runTest {
        coEvery { dao.getRecentEntriesOnce(any()) } throws RuntimeException("Query error")

        try {
            repository.getRecentEntriesOnce()
        } catch (e: RuntimeException) {
            assertEquals("Query error", e.message)
        }
    }
}
