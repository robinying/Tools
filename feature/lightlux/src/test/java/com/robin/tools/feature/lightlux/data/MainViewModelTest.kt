package com.robin.tools.feature.lightlux.data

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModelTest {

    private val repository = mockk<LightRepository>()
    private lateinit var viewModel: MainViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        viewModel = MainViewModel(repository)
    }

    @After
    fun teardown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state`() {
        assertEquals(0f, viewModel.currentLux.value, 0f)
        assertEquals(emptyList<ChartDataPoint>(), viewModel.realtimeChartData.value)
        assertEquals(null, viewModel.saveStatus.value)
    }

    @Test
    fun `updateLuxFromSensor updates currentLux`() {
        viewModel.updateLuxFromSensor(42.5f)
        assertEquals(42.5f, viewModel.currentLux.value, 0.01f)
    }

    @Test
    fun `updateLuxFromSensor adds chart data point`() {
        viewModel.updateLuxFromSensor(10f)
        assertEquals(1, viewModel.realtimeChartData.value.size)
        assertEquals(10f, viewModel.realtimeChartData.value[0].luxValue, 0.01f)
    }

    @Test
    fun `updateLuxFromSensor multiple times adds multiple points`() {
        viewModel.updateLuxFromSensor(10f)
        viewModel.updateLuxFromSensor(20f)
        viewModel.updateLuxFromSensor(30f)

        val data = viewModel.realtimeChartData.value
        assertEquals(3, data.size)
        val luxValues = data.map { it.luxValue }.sorted()
        assertEquals(listOf(10f, 20f, 30f), luxValues)
    }

    @Test
    fun `saveSnapshot inserts entry and sets saveStatus`() = runTest {
        coEvery { repository.insertEntry(any()) } returns Unit

        viewModel.updateLuxFromSensor(50f)
        viewModel.saveSnapshot()

        coVerify { repository.insertEntry(match { it.luxValue == 50f }) }
        assertEquals(true, viewModel.saveStatus.value?.contains("50.0"))
    }

    @Test
    fun `clearSaveStatus resets save status`() {
        coEvery { repository.insertEntry(any()) } returns Unit

        viewModel.updateLuxFromSensor(30f)
        viewModel.saveSnapshot()
        viewModel.clearSaveStatus()

        assertEquals(null, viewModel.saveStatus.value)
    }
}
