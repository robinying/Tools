package com.robin.tools.core.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LogUtilsExtTest {

    @Test
    fun `Collection print returns formatted string`() {
        val list = listOf("a", "b", "c")
        val result = list.print { it }
        assertTrue(result.startsWith("\n["))
        assertTrue(result.endsWith("\n]"))
        assertTrue(result.contains("a"))
        assertTrue(result.contains("b"))
        assertTrue(result.contains("c"))
    }

    @Test
    fun `Collection print with transform`() {
        val list = listOf(1, 2, 3)
        val result = list.print { "num:$it" }
        assertTrue(result.contains("num:1"))
        assertTrue(result.contains("num:3"))
    }

    @Test
    fun `Collection print empty`() {
        val list = emptyList<String>()
        val result = list.print { it }
        assertEquals("\n[", result.substring(0, 2))
        assertEquals("\n]", result.takeLast(2))
    }

    @Test
    fun `Map print returns formatted string for simple map`() {
        val map = mapOf("key1" to "value1", "key2" to "value2")
        val result = map.print()
        assertTrue(result.contains("[key1]"))
        assertTrue(result.contains("value1"))
        assertTrue(result.contains("[key2]"))
        assertTrue(result.contains("value2"))
    }

    @Test
    fun `Map print empty`() {
        val map = emptyMap<String, String>()
        val result = map.print()
        assertTrue(result.startsWith("\n{"))
        assertTrue(result.endsWith("}"))
    }

    @Test
    fun `Map print with null value`() {
        val map = mapOf("key" to null)
        val result = map.print()
        assertTrue(result.contains("null"))
    }

    @Test
    fun `Map print with nested map`() {
        val inner = mapOf("innerKey" to "innerValue")
        val outer = mapOf("outerKey" to inner)
        val result = outer.print()
        assertTrue(result.contains("outerKey"))
        assertTrue(result.contains("innerKey"))
        assertTrue(result.contains("innerValue"))
    }
}
