package com.robin.tools.core.ext.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StringExtTest {

    @Test
    fun `valid non-null non-empty string returns true`() {
        assertTrue("hello".valid())
    }

    @Test
    fun `valid null string returns false`() {
        assertFalse((null as String?).valid())
    }

    @Test
    fun `valid empty string returns false`() {
        assertFalse("".valid())
    }

    @Test
    fun `valid blank string returns false`() {
        assertFalse("   ".valid())
    }

    @Test
    fun `valid literal null string returns false`() {
        assertFalse("null".valid())
    }

    @Test
    fun `valid literal NULL string returns false`() {
        assertFalse("NULL".valid())
    }

    @Test
    fun `isEmail valid email`() {
        assertTrue("test@gmail.com".isEmail())
    }

    @Test
    fun `isEmail null returns false`() {
        assertFalse((null as String?).isEmail())
    }

    @Test
    fun `isEmail without domain returns false`() {
        assertFalse("test@".isEmail())
    }

    @Test
    fun `toJson converts object to JSON string`() {
        val data = mapOf("key" to "value")
        val json = data.toJson()
        assertTrue(json.contains("key"))
        assertTrue(json.contains("value"))
    }

    @Test
    fun `toJson with null returns null string`() {
        val json = (null as Any?).toJson()
        assertEquals("null", json)
    }

    @Test
    fun `toJson with string`() {
        val json = "hello".toJson()
        assertEquals("\"hello\"", json)
    }
}
