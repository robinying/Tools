package com.robin.tools.core.network.stateCallback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ListDataUiStateTest {

    @Test
    fun `default values`() {
        val state = ListDataUiState<Any>(isSuccess = true)
        assertEquals("", state.errMessage)
        assertFalse(state.isRefresh)
        assertFalse(state.isEmpty)
        assertFalse(state.hasMore)
        assertFalse(state.isFirstEmpty)
        assertEquals(0, state.listData.size)
    }

    @Test
    fun `isSuccess true`() {
        val state = ListDataUiState<Any>(isSuccess = true)
        assertTrue(state.isSuccess)
    }

    @Test
    fun `isSuccess false with error message`() {
        val state = ListDataUiState<Any>(isSuccess = false, errMessage = "Network error")
        assertFalse(state.isSuccess)
        assertEquals("Network error", state.errMessage)
    }

    @Test
    fun `isRefresh true`() {
        val state = ListDataUiState<Any>(isSuccess = true, isRefresh = true)
        assertTrue(state.isRefresh)
    }

    @Test
    fun `isEmpty true`() {
        val state = ListDataUiState<Any>(isSuccess = true, isEmpty = true)
        assertTrue(state.isEmpty)
    }

    @Test
    fun `hasMore true`() {
        val state = ListDataUiState<Any>(isSuccess = true, hasMore = true)
        assertTrue(state.hasMore)
    }

    @Test
    fun `isFirstEmpty true`() {
        val state = ListDataUiState<Any>(isSuccess = true, isFirstEmpty = true)
        assertTrue(state.isFirstEmpty)
    }

    @Test
    fun `withStringListData`() {
        val list = arrayListOf("a", "b", "c")
        val state = ListDataUiState(isSuccess = true, listData = list)
        assertEquals(3, state.listData.size)
        assertEquals("a", state.listData[0])
    }

    @Test
    fun `copy with modification`() {
        val state = ListDataUiState<Any>(isSuccess = true)
        val modified = state.copy(isSuccess = false, errMessage = "Error")
        assertFalse(modified.isSuccess)
        assertEquals("Error", modified.errMessage)
    }
}
