package com.robin.tools.core.state

import com.robin.tools.core.state.ResultState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ResultStateTest {

    @Test
    fun `ResultState loading has progress`() {
        val state = ResultState.Loading(0.5f)
        assertEquals(0.5f, state.progress, 0.01f)
    }

    @Test
    fun `ResultState success has data`() {
        val data = "test data"
        val state = ResultState.Success(data)
        assertEquals(data, state.data)
    }

    @Test
    fun `ResultState error has message`() {
        val errorMsg = "Something went wrong"
        val state = ResultState.Error(errorMsg)
        assertEquals(errorMsg, state.error)
    }
}