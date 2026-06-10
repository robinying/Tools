package com.robin.tools.core.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

class NumberExtensionTest {

    // formatNumber for Number

    @Test
    fun `formatNumber default decimal places`() {
        val result = 123.45678.formatNumber()
        assertEquals("123.45", result)
    }

    @Test
    fun `formatNumber with custom decimal places`() {
        val result = 123.45678.formatNumber(decimalNum = 3)
        assertEquals("123.456", result)
    }

    @Test
    fun `formatNumber with modeFloor false rounds up`() {
        val result = 123.45678.formatNumber(modeFloor = false, decimalNum = 2)
        assertEquals("123.46", result)
    }

    @Test
    fun `formatNumber with modeFloor true truncates`() {
        val result = 123.459.formatNumber(modeFloor = true, decimalNum = 2)
        assertEquals("123.45", result)
    }

    @Test
    fun `formatNumber with comma separator`() {
        val result = 1234567.0.formatNumber(addComma = true, decimalNum = 0)
        assertEquals("1,234,567", result)
    }

    @Test
    fun `formatNumber integer`() {
        val result = 42.formatNumber()
        assertEquals("42.00", result)
    }

    @Test
    fun `formatNumber zero`() {
        val result = 0.0.formatNumber()
        assertEquals("0.00", result)
    }

    // formatNumber for String

    @Test
    fun `formatNumber from string`() {
        val result = "123.456".formatNumber()
        assertEquals("123.45", result)
    }

    // toBigDecimalWithNull

    @Test
    fun `toBigDecimalWithNull valid string`() {
        val result = "123.45".toBigDecimalWithNull()
        assertEquals(BigDecimal("123.45"), result)
    }

    @Test
    fun `toBigDecimalWithNull null string returns default`() {
        val result = (null as String?).toBigDecimalWithNull()
        assertEquals(BigDecimal.ZERO, result)
    }

    @Test
    fun `toBigDecimalWithNull blank string returns default`() {
        val result = "".toBigDecimalWithNull()
        assertEquals(BigDecimal.ZERO, result)
    }

    @Test
    fun `toBigDecimalWithNull invalid string returns default`() {
        val result = "not-a-number".toBigDecimalWithNull()
        assertEquals(BigDecimal.ZERO, result)
    }

    @Test
    fun `toBigDecimalWithNull custom default`() {
        val result = "".toBigDecimalWithNull(default = BigDecimal.TEN)
        assertEquals(BigDecimal.TEN, result)
    }

    // toIntWithNull

    @Test
    fun `toIntWithNull valid string`() {
        val result = "42".toIntWithNull()
        assertEquals(42, result)
    }

    @Test
    fun `toIntWithNull null string returns default`() {
        val result = (null as String?).toIntWithNull()
        assertEquals(0, result)
    }

    @Test
    fun `toIntWithNull blank string returns default`() {
        val result = "   ".toIntWithNull()
        assertEquals(0, result)
    }

    @Test
    fun `toIntWithNull invalid returns default`() {
        val result = "abc".toIntWithNull()
        assertEquals(0, result)
    }

    @Test
    fun `toIntWithNull custom default`() {
        val result = "abc".toIntWithNull(default = -1)
        assertEquals(-1, result)
    }

    // toFloatWithNull

    @Test
    fun `toFloatWithNull valid string`() {
        val result = "3.14".toFloatWithNull()
        assertEquals(3.14f, result, 0.001f)
    }

    @Test
    fun `toFloatWithNull null returns default`() {
        val result = (null as String?).toFloatWithNull()
        assertEquals(0f, result, 0f)
    }

    @Test
    fun `toFloatWithNull invalid returns default`() {
        val result = "abc".toFloatWithNull()
        assertEquals(0f, result, 0f)
    }

    // toDoubleWithNull

    @Test
    fun `toDoubleWithNull valid string`() {
        val result = "2.71828".toDoubleWithNull()
        assertEquals(2.71828, result, 0.00001)
    }

    @Test
    fun `toDoubleWithNull null returns default`() {
        val result = (null as String?).toDoubleWithNull()
        assertEquals(0.0, result, 0.0)
    }

    @Test
    fun `toDoubleWithNull invalid returns default`() {
        val result = "abc".toDoubleWithNull()
        assertEquals(0.0, result, 0.0)
    }

    @Test
    fun `toDoubleWithNull custom default`() {
        val result = "abc".toDoubleWithNull(default = -1.0)
        assertEquals(-1.0, result, 0.0)
    }
}
