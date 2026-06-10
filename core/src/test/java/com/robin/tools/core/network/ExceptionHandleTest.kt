package com.robin.tools.core.network

import com.google.gson.JsonParseException
import com.google.gson.stream.MalformedJsonException
import org.json.JSONException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

class ExceptionHandleTest {

    @Test
    fun `handleException with null returns UNKNOWN`() {
        val result = ExceptionHandle.handleException(null)
        assertEquals(Error.UNKNOWN.getKey(), result.errCode)
    }

    @Test
    fun `handleException with ConnectException returns NETWORK_ERROR`() {
        val result = ExceptionHandle.handleException(ConnectException())
        assertEquals(Error.NETWORK_ERROR.getKey(), result.errCode)
    }

    @Test
    fun `handleException with SocketTimeoutException returns TIMEOUT_ERROR`() {
        val result = ExceptionHandle.handleException(SocketTimeoutException())
        assertEquals(Error.TIMEOUT_ERROR.getKey(), result.errCode)
    }

    @Test
    fun `handleException with UnknownHostException returns TIMEOUT_ERROR`() {
        val result = ExceptionHandle.handleException(UnknownHostException())
        assertEquals(Error.TIMEOUT_ERROR.getKey(), result.errCode)
    }

    @Test
    fun `handleException with SSLException returns SSL_ERROR`() {
        val result = ExceptionHandle.handleException(SSLException("cert error"))
        assertEquals(Error.SSL_ERROR.getKey(), result.errCode)
    }

    @Test
    fun `handleException with JSONException returns PARSE_ERROR`() {
        val result = ExceptionHandle.handleException(JSONException("parse error"))
        assertEquals(Error.PARSE_ERROR.getKey(), result.errCode)
    }

    @Test
    fun `handleException with JsonParseException returns PARSE_ERROR`() {
        val result = ExceptionHandle.handleException(JsonParseException("json error"))
        assertEquals(Error.PARSE_ERROR.getKey(), result.errCode)
    }

    @Test
    fun `handleException with MalformedJsonException returns PARSE_ERROR`() {
        val result = ExceptionHandle.handleException(MalformedJsonException("malformed"))
        assertEquals(Error.PARSE_ERROR.getKey(), result.errCode)
    }

    @Test
    fun `handleException with java text ParseException returns PARSE_ERROR`() {
        val result = ExceptionHandle.handleException(java.text.ParseException("parse error", 0))
        assertEquals(Error.PARSE_ERROR.getKey(), result.errCode)
    }

    @Test
    fun `handleException with generic exception returns UNKNOWN`() {
        val result = ExceptionHandle.handleException(RuntimeException("something happened"))
        assertEquals(Error.UNKNOWN.getKey(), result.errCode)
    }

    @Test
    fun `handleException with AppException returns it unchanged`() {
        val original = AppException(999, "custom error")
        val result = ExceptionHandle.handleException(original)
        assertEquals(999, result.errCode)
        assertEquals("custom error", result.errorMsg)
    }

    @Test
    fun `handleException preserves error log`() {
        val result = ExceptionHandle.handleException(ConnectException("connection refused"))
        assertEquals("connection refused", result.errorLog)
    }
}
