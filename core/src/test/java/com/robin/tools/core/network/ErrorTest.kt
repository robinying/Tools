package com.robin.tools.core.network

import org.junit.Assert.assertEquals
import org.junit.Test

class ErrorTest {

    @Test
    fun `UNKNOWN has correct code and message`() {
        assertEquals(1000, Error.UNKNOWN.getKey())
        assertEquals("请求失败，请稍后再试", Error.UNKNOWN.getValue())
    }

    @Test
    fun `PARSE_ERROR has correct code and message`() {
        assertEquals(1001, Error.PARSE_ERROR.getKey())
        assertEquals("解析错误，请稍后再试", Error.PARSE_ERROR.getValue())
    }

    @Test
    fun `NETWORK_ERROR has correct code and message`() {
        assertEquals(1002, Error.NETWORK_ERROR.getKey())
        assertEquals("网络连接错误，请稍后重试", Error.NETWORK_ERROR.getValue())
    }

    @Test
    fun `SSL_ERROR has correct code and message`() {
        assertEquals(1004, Error.SSL_ERROR.getKey())
        assertEquals("证书出错，请稍后再试", Error.SSL_ERROR.getValue())
    }

    @Test
    fun `TIMEOUT_ERROR has correct code and message`() {
        assertEquals(1006, Error.TIMEOUT_ERROR.getKey())
        assertEquals("网络连接超时，请稍后重试", Error.TIMEOUT_ERROR.getValue())
    }

    @Test
    fun `all error codes are unique`() {
        val codes = Error.values().map { it.getKey() }.toSet()
        assertEquals(Error.values().size, codes.size)
    }
}
