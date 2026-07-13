package com.robin.tools.feature.lightlux.data

import android.app.Application
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModelTest {

    private val repository = mockk<LightRepository>(relaxed = true)
    private lateinit var viewModel: MainViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val app = mockk<Application>(relaxed = true)
        every { app.getString(any<Int>(), any()) } returns "Saved: 50.0 lux"
        viewModel = MainViewModel(app, repository)
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
    fun `saveSnapshot persists current lux and publishes success status`() = runTest {
        coEvery { repository.insertEntry(any()) } returns Unit
        viewModel.updateLuxFromSensor(50f)

        viewModel.saveSnapshot()

        coVerify { repository.insertEntry(match { it.id == 0L && it.luxValue == 50f && it.timestamp > 0L }) }
        assertEquals("Saved: 50.0 lux", viewModel.saveStatus.value)
    }

    @Test
    fun `clearSaveStatus clears a published status`() = runTest {
        coEvery { repository.insertEntry(any()) } returns Unit
        viewModel.updateLuxFromSensor(30f)
        viewModel.saveSnapshot()

        assertEquals("Saved: 50.0 lux", viewModel.saveStatus.value)

        viewModel.clearSaveStatus()

        assertEquals(null, viewModel.saveStatus.value)
    }
}
