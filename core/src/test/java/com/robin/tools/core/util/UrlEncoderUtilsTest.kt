package com.robin.tools.core.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UrlEncoderUtilsTest {

    @Test
    fun `hasUrlEncoded with percent-encoded string returns true`() {
        assertTrue(UrlEncoderUtils.hasUrlEncoded("hello%20world"))
    }

    @Test
    fun `hasUrlEncoded with multiple encodings returns true`() {
        assertTrue(UrlEncoderUtils.hasUrlEncoded("a%2Fb%2Fc"))
    }

    @Test
    fun `hasUrlEncoded with uppercase hex returns true`() {
        assertTrue(UrlEncoderUtils.hasUrlEncoded("%2Fpath"))
    }

    @Test
    fun `hasUrlEncoded with lowercase hex returns true`() {
        assertTrue(UrlEncoderUtils.hasUrlEncoded("%2fpath"))
    }

    @Test
    fun `hasUrlEncoded with plain string returns false`() {
        assertFalse(UrlEncoderUtils.hasUrlEncoded("hello world"))
    }

    @Test
    fun `hasUrlEncoded with only percent sign returns false`() {
        assertFalse(UrlEncoderUtils.hasUrlEncoded("hello%"))
    }

    @Test
    fun `hasUrlEncoded with percent and only one hex char returns false`() {
        assertFalse(UrlEncoderUtils.hasUrlEncoded("hello%2"))
    }

    @Test
    fun `hasUrlEncoded with invalid hex after percent returns false`() {
        assertFalse(UrlEncoderUtils.hasUrlEncoded("hello%XX"))
    }

    @Test
    fun `hasUrlEncoded with empty string returns false`() {
        assertFalse(UrlEncoderUtils.hasUrlEncoded(""))
    }

    @Test
    fun `hasUrlEncoded with no percent sign returns false`() {
        assertFalse(UrlEncoderUtils.hasUrlEncoded("abcdef"))
    }

    @Test
    fun `hasUrlEncoded with path segments`() {
        assertTrue(UrlEncoderUtils.hasUrlEncoded("/api/v1/items?name=hello%20world"))
    }
}
