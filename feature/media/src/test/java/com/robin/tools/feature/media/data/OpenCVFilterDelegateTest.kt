package com.robin.tools.feature.media.data

import com.robin.tools.feature.media.delegate.PureBitmapFilterDelegate
import org.junit.Assert.*
import org.junit.Test

/**
 * Tests for filter data types and the pure-Android delegate fallback.
 *
 * Note: [com.robin.tools.feature.media.delegate.OpenCVFilterDelegate] cannot
 * be tested in JVM unit tests (native libraries unavailable). Integration
 * tests should cover the OpenCV path.
 */
class FilterFeatureTest {

    private val delegate = PureBitmapFilterDelegate()

    @Test
    fun `delegate can be constructed`() {
        assertNotNull(delegate)
    }

    @Test
    fun `all FilterType enum values have label resources`() {
        for (type in FilterType.entries) {
            assertTrue("Label resource must be positive for $type", type.labelRes > 0)
        }
    }

    @Test
    fun `six filter types are registered`() {
        assertEquals("There should be exactly 6 filter types", 6, FilterType.entries.size)
    }

    @Test
    fun `FilterManager initial state is Idle`() {
        FilterManager.reset()
        assertTrue("Initial state should be Idle", FilterManager.state.value is FilterState.Idle)
    }

    @Test
    fun `FilterManager startTask resets cancelled state`() {
        FilterManager.reset()
        FilterManager.cancelTask()
        assertTrue(FilterManager.isCancelled())
        FilterManager.startTask()
        assertFalse("Should not be cancelled after start", FilterManager.isCancelled())
    }

    @Test
    fun `FilterManager reset returns to Idle`() {
        FilterManager.reset()
        FilterManager.updateState(FilterState.Processing(0.5f, "Test"))
        FilterManager.reset()
        assertTrue("State should be Idle after reset", FilterManager.state.value is FilterState.Idle)
    }

    @Test
    fun `FilterState Processing holds correct values`() {
        val state = FilterState.Processing(0.75f, "Processing…")
        assertEquals(0.75f, state.progress, 0.001f)
        assertEquals("Processing…", state.message)
    }

    @Test
    fun `FilterState Finished success holds correct message`() {
        val state = FilterState.Finished(true, "Done")
        assertTrue(state.isSuccess)
        assertEquals("Done", state.message)
    }

    @Test
    fun `FilterState Finished failure has correct message`() {
        val state = FilterState.Finished(false, "Error")
        assertFalse(state.isSuccess)
        assertEquals("Error", state.message)
    }
}
