package com.robin.tools.feature.lightlux.data

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SnapshotListViewModelTest {

    private val repository = mockk<LightRepository>()
    private lateinit var viewModel: SnapshotListViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun teardown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `init collects entries from repository`() {
        val entries = listOf(
            LightEntry(id = 1L, timestamp = 100L, luxValue = 10f),
            LightEntry(id = 2L, timestamp = 200L, luxValue = 20f)
        )
        every { repository.getAllEntries() } returns MutableStateFlow(entries)

        viewModel = SnapshotListViewModel(repository)

        assertEquals(2, viewModel.entries.value.size)
        assertEquals(10f, viewModel.entries.value[0].luxValue, 0.01f)
        assertEquals(20f, viewModel.entries.value[1].luxValue, 0.01f)
    }

    @Test
    fun `init with empty entries`() {
        every { repository.getAllEntries() } returns MutableStateFlow(emptyList())

        viewModel = SnapshotListViewModel(repository)

        assertEquals(0, viewModel.entries.value.size)
    }

    @Test
    fun `deleteAll calls repository deleteAllEntries`() = runTest {
        every { repository.getAllEntries() } returns MutableStateFlow(emptyList())
        coEvery { repository.deleteAllEntries() } returns Unit

        viewModel = SnapshotListViewModel(repository)
        viewModel.deleteAll()

        coVerify { repository.deleteAllEntries() }
    }

    @Test
    fun `deleteEntry calls repository deleteEntry`() = runTest {
        every { repository.getAllEntries() } returns MutableStateFlow(emptyList())
        coEvery { repository.deleteEntry(any()) } returns Unit

        val entry = LightEntry(id = 1L, timestamp = 100L, luxValue = 10f)
        viewModel = SnapshotListViewModel(repository)
        viewModel.deleteEntry(entry)

        coVerify { repository.deleteEntry(entry) }
    }
}
