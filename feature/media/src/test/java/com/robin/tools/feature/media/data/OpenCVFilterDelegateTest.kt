package com.robin.tools.feature.media.data

import com.robin.tools.feature.media.delegate.PureBitmapFilterDelegate
import org.junit.Assert.*
import org.junit.Test

/**
 * Behavioural tests for the filter delegate and its data types.
 *
 * Uses [PureBitmapFilterDelegate] which works on JVM without native libraries.
 * For the full OpenCV-powered filter suite, see [OpenCVFilterDelegate]
 * (enabled when opencv-4.10.0.aar is placed in feature/media/libs/).
 */
class OpenCVFilterDelegateTest {

    private val delegate = PureBitmapFilterDelegate()

    @Test
    fun `constructor creates instance without error`() {
        assertNotNull("Delegate should be non-null after construction", delegate)
    }

    @Test
    fun `all FilterType enum values are defined`() {
        for (type in FilterType.entries) {
            assertNotNull("Label for $type must be non-null", type.labelRes)
            assertTrue("Label resource ID must be positive for $type", type.labelRes > 0)
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
        assertNull(state.result) // result is null when no bitmap is supplied
    }

    @Test
    fun `FilterState Finished failure has no result`() {
        val state = FilterState.Finished(false, "Error", result = null)
        assertFalse(state.isSuccess)
        assertEquals("Error", state.message)
        assertNull(state.result)
    }
}
