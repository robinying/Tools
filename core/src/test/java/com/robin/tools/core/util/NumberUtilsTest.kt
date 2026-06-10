package com.robin.tools.core.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NumberUtilsTest {

    // range

    @Test
    fun `range float clamps to min`() {
        assertEquals(0f, NumberUtils.range(-5f, 0f, 10f), 0f)
    }

    @Test
    fun `range float clamps to max`() {
        assertEquals(10f, NumberUtils.range(15f, 0f, 10f), 0f)
    }

    @Test
    fun `range float within range`() {
        assertEquals(5f, NumberUtils.range(5f, 0f, 10f), 0f)
    }

    @Test
    fun `range int clamps to min`() {
        assertEquals(0, NumberUtils.range(-5, 0, 10))
    }

    @Test
    fun `range int clamps to max`() {
        assertEquals(10, NumberUtils.range(15, 0, 10))
    }

    @Test
    fun `range int within range`() {
        assertEquals(7, NumberUtils.range(7, 0, 10))
    }

    @Test
    fun `range double clamps to min`() {
        assertEquals(0.0, NumberUtils.range(-1.0, 0.0, 10.0), 0.0)
    }

    @Test
    fun `range long clamps to min`() {
        assertEquals(0L, NumberUtils.range(-5L, 0L, 10L))
    }

    // inRange

    @Test
    fun `inRange value inside returns true`() {
        assertTrue(NumberUtils.inRange(5.0, 0.0, 10.0))
    }

    @Test
    fun `inRange value at min returns true`() {
        assertTrue(NumberUtils.inRange(0.0, 0.0, 10.0))
    }

    @Test
    fun `inRange value at max returns true`() {
        assertTrue(NumberUtils.inRange(10.0, 0.0, 10.0))
    }

    @Test
    fun `inRange value below min returns false`() {
        assertFalse(NumberUtils.inRange(-1.0, 0.0, 10.0))
    }

    @Test
    fun `inRange value above max returns false`() {
        assertFalse(NumberUtils.inRange(11.0, 0.0, 10.0))
    }

    // formatDecimal

    @Test
    fun `formatDecimal default no decimal places`() {
        assertEquals("123", NumberUtils.formatDecimal(123.456))
    }

    @Test
    fun `formatDecimal with 2 decimal places`() {
        assertEquals("123.46", NumberUtils.formatDecimal(123.456, 2))
    }

    @Test
    fun `formatDecimal with 0 decimal places`() {
        assertEquals("123", NumberUtils.formatDecimal(123.456, 0))
    }

    @Test
    fun `formatDecimal integer value`() {
        assertEquals("42", NumberUtils.formatDecimal(42.0, 0))
    }
}
