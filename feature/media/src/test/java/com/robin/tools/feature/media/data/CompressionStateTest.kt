package com.robin.tools.feature.media.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CompressionStateTest {

    @Test
    fun `CompressionType has all expected values`() {
        val types = CompressionType.values()
        assertEquals(3, types.size)
        assertEquals(CompressionType.VIDEO, types[0])
        assertEquals(CompressionType.IMAGE, types[1])
        assertEquals(CompressionType.GIF, types[2])
    }

    @Test
    fun `CompressionLevel has all expected values`() {
        val levels = CompressionLevel.values()
        assertEquals(3, levels.size)
        assertEquals(CompressionLevel.LOW, levels[0])
        assertEquals(CompressionLevel.MEDIUM, levels[1])
        assertEquals(CompressionLevel.HIGH, levels[2])
    }

    @Test
    fun `CompressionTaskState Idle is correct`() {
        val state = CompressionTaskState.Idle
        assertTrue("Idle should be Idle", state is CompressionTaskState.Idle)
    }

    @Test
    fun `CompressionTaskState Processing holds values`() {
        val state = CompressionTaskState.Processing(
            progress = 0.5f,
            message = "Processing video",
            currentFile = 1,
            totalFiles = 3
        )
        assertEquals(0.5f, state.progress, 0.01f)
        assertEquals("Processing video", state.message)
        assertEquals(1, state.currentFile)
        assertEquals(3, state.totalFiles)
    }

    @Test
    fun `CompressionTaskState Finished holds values`() {
        val state = CompressionTaskState.Finished(
            isSuccess = true,
            message = "All done",
            outputUri = "content://media/video/1"
        )
        assertTrue(state.isSuccess)
        assertEquals("All done", state.message)
        assertEquals("content://media/video/1", state.outputUri)
    }

    @Test
    fun `CompressionTaskState Finished with null outputUri`() {
        val state = CompressionTaskState.Finished(
            isSuccess = false,
            message = "Failed",
            outputUri = null
        )
        assertTrue("Should not be success", !state.isSuccess)
        assertEquals(null, state.outputUri)
    }
}