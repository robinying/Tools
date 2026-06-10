package com.robin.tools.core.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BooleanExtensionTest {

    @Test
    fun `true then value returns value`() {
        val result = true.then("hello")
        assertEquals("hello", result)
    }

    @Test
    fun `false then value returns null`() {
        val result = false.then("hello")
        assertNull(result)
    }

    @Test
    fun `true then null returns null`() {
        val result: Boolean? = true.then(null)
        assertNull(result)
    }

    @Test
    fun `true then value with default returns value`() {
        val result = true.then("hello", "default")
        assertEquals("hello", result)
    }

    @Test
    fun `false then value with default returns default`() {
        val result = false.then("hello", "default")
        assertEquals("default", result)
    }

    @Test
    fun `true then lambda with default returns lambda result`() {
        val result = true.then({ "fromLambda" }, "default")
        assertEquals("fromLambda", result)
    }

    @Test
    fun `false then lambda with default returns default`() {
        val result = false.then({ "fromLambda" }, "default")
        assertEquals("default", result)
    }

    @Test
    fun `true then lambda with lambda default`() {
        val result = true.then({ "fromLambda" }, { "defaultLambda" })
        assertEquals("fromLambda", result)
    }

    @Test
    fun `false then lambda with lambda default`() {
        val result = false.then({ "fromLambda" }, { "defaultLambda" })
        assertEquals("defaultLambda", result)
    }

    @Test
    fun `true then value null with default returns value`() {
        val result = true.then<String?>(null, "default")
        assertNull(result)
    }

    @Test
    fun `false then value null with default returns default`() {
        val result = false.then(null as String?, "default")
        assertEquals("default", result)
    }
}
