package com.robin.tools.core.network.stateCallback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateUiStateTest {

    @Test
    fun `default values`() {
        val state = UpdateUiState<Any>()
        assertTrue(state.isSuccess)
        assertEquals(null, state.data)
        assertEquals("", state.errorMsg)
    }

    @Test
    fun `isSuccess true`() {
        val state = UpdateUiState<Any>(isSuccess = true)
        assertTrue(state.isSuccess)
    }

    @Test
    fun `with data`() {
        val state = UpdateUiState(isSuccess = true, data = "result")
        assertEquals("result", state.data)
    }

    @Test
    fun `with error message`() {
        val state = UpdateUiState<Any>(isSuccess = false, errorMsg = "Update failed")
        assertEquals("Update failed", state.errorMsg)
    }

    @Test
    fun `copy with modification`() {
        val state = UpdateUiState(isSuccess = true, data = "original")
        val modified = state.copy(isSuccess = false, errorMsg = "Failed") as UpdateUiState<String>
        assertEquals("Failed", modified.errorMsg)
        assertEquals("original", modified.data)
    }
}
