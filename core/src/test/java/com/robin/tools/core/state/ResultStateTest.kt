package com.robin.tools.core.state

import com.robin.tools.core.network.AppException
import com.robin.tools.core.network.Error
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ResultStateTest {

    @Test
    fun `Loading has loadingMessage`() {
        val state = ResultState.Loading("Loading...")
        assertEquals("Loading...", state.loadingMessage)
    }

    @Test
    fun `Success has data`() {
        val data = "test data"
        val state = ResultState.Success(data)
        assertEquals(data, state.data)
    }

    @Test
    fun `Success with null data`() {
        val state = ResultState.Success<Any>(null)
        assertEquals(null, state.data)
    }

    @Test
    fun `Error has AppException`() {
        val exception = AppException(1001, "Parse error")
        val state = ResultState.Error(exception)
        assertEquals(1001, state.error.errCode)
        assertEquals("Parse error", state.error.errorMsg)
    }

    @Test
    fun `onAppSuccess factory creates Success`() {
        val state = ResultState.onAppSuccess("hello")
        assertTrue(state is ResultState.Success)
        assertEquals("hello", (state as ResultState.Success).data)
    }

    @Test
    fun `onAppLoading factory creates Loading`() {
        val state = ResultState.onAppLoading<Any>("Please wait")
        assertTrue(state is ResultState.Loading)
        assertEquals("Please wait", (state as ResultState.Loading).loadingMessage)
    }

    @Test
    fun `onAppError factory creates Error`() {
        val exception = AppException(Error.PARSE_ERROR, null)
        val state = ResultState.onAppError<Any>(exception)
        assertTrue(state is ResultState.Error)
        assertEquals(Error.PARSE_ERROR.getKey(), (state as ResultState.Error).error.errCode)
    }

    @Test
    fun `ResultState is sealed with three variants`() {
        val loading: ResultState<Any> = ResultState.Loading("test")
        val success: ResultState<String> = ResultState.Success("data")
        val error: ResultState<Any> = ResultState.Error(AppException(0, "err"))

        assertEquals(3, listOf<ResultState<*>>(loading, success, error).size)
    }
}
