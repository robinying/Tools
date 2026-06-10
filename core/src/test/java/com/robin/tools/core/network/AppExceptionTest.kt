package com.robin.tools.core.network

import org.junit.Assert.assertEquals
import org.junit.Test

class AppExceptionTest {

    @Test
    fun `constructor with code and message`() {
        val ex = AppException(404, "Not Found")
        assertEquals(404, ex.errCode)
        assertEquals("Not Found", ex.errorMsg)
        assertEquals("", ex.errorLog)
    }

    @Test
    fun `constructor with null error message uses default`() {
        val ex = AppException(500, null)
        assertEquals("请求失败，请稍后再试", ex.errorMsg)
    }

    @Test
    fun `constructor with errorLog`() {
        val ex = AppException(404, "Not Found", "detailed log")
        assertEquals("detailed log", ex.errorLog)
    }

    @Test
    fun `constructor with null errorLog uses error message`() {
        val ex = AppException(401, "Unauthorized", null)
        assertEquals("Unauthorized", ex.errorLog)
    }

    @Test
    fun `constructor with Error enum`() {
        val cause = RuntimeException("network issue")
        val ex = AppException(Error.NETWORK_ERROR, cause)
        assertEquals(Error.NETWORK_ERROR.getKey(), ex.errCode)
        assertEquals(Error.NETWORK_ERROR.getValue(), ex.errorMsg)
        assertEquals("network issue", ex.errorLog)
    }

    @Test
    fun `constructor with Error enum and null cause`() {
        val ex = AppException(Error.UNKNOWN, null)
        assertEquals(Error.UNKNOWN.getKey(), ex.errCode)
        assertEquals(Error.UNKNOWN.getValue(), ex.errorMsg)
        assertEquals(null, ex.errorLog)
    }

    @Test
    fun `exception message is the error message`() {
        val ex = AppException(400, "Bad Request")
        assertEquals("Bad Request", ex.message)
    }
}
