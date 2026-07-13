package com.robin.tools.feature.media.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CompressionManagerTest {

    @Test
    fun `initial state is Idle`() {
        CompressionManager.reset()
        val state = CompressionManager.taskState.value
        assertTrue("Initial state should be Idle", state is CompressionTaskState.Idle)
    }

    @Test
    fun `startTask resets cancelled state`() {
        CompressionManager.reset()
        CompressionManager.cancelTask()
        assertTrue("Should be cancelled", CompressionManager.isCancelled())
        CompressionManager.startTask()
        assertTrue("Should not be cancelled after start", !CompressionManager.isCancelled())
    }

    @Test
    fun `cancelTask sets cancelled state`() {
        CompressionManager.reset()
        CompressionManager.startTask()
        CompressionManager.cancelTask()
        assertTrue("Should be cancelled", CompressionManager.isCancelled())
    }

    @Test
    fun `updateState changes taskState`() {
        CompressionManager.reset()
        val processingState = CompressionTaskState.Processing(0.5f, "Processing", 1, 2)
        CompressionManager.updateState(processingState)
        assertEquals(0.5f, (CompressionManager.taskState.value as CompressionTaskState.Processing).progress, 0.01f)
    }

    @Test
    fun `complete task lifecycle resets state and cancellation`() {
        val finished = CompressionTaskState.Finished(true, "Done", "content://output")

        CompressionManager.reset()
        CompressionManager.startTask()
        CompressionManager.updateState(CompressionTaskState.Processing(0.5f, "Processing", 1, 2))
        CompressionManager.updateState(finished)
        CompressionManager.cancelTask()

        assertEquals(finished, CompressionManager.taskState.value)
        assertTrue(CompressionManager.isCancelled())

        CompressionManager.reset()

        assertTrue("Should be Idle after reset", CompressionManager.taskState.value is CompressionTaskState.Idle)
        assertTrue("Reset should clear cancellation", !CompressionManager.isCancelled())
    }

    @Test
    fun `updateState preserves complete processing details`() {
        val state = CompressionTaskState.Processing(0.75f, "Compressing second file", 2, 3)

        CompressionManager.reset()
        CompressionManager.updateState(state)

        assertEquals(state, CompressionManager.taskState.value)
    }
}