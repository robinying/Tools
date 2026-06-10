package com.robin.tools.core.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RegexToolTest {

    @Test
    fun `checkEmail valid email`() {
        assertTrue(RegexTool.checkEmail("user@gmail.com"))
    }

    @Test
    fun `checkEmail with subdomain`() {
        assertTrue(RegexTool.checkEmail("user@mail.example.com"))
    }

    @Test
    fun `checkEmail without domain`() {
        assertFalse(RegexTool.checkEmail("user@"))
    }

    @Test
    fun `checkEmail without at sign`() {
        assertFalse(RegexTool.checkEmail("usergmail.com"))
    }

    @Test
    fun `checkEmail empty string`() {
        assertFalse(RegexTool.checkEmail(""))
    }

    @Test
    fun `checkMobile valid number`() {
        assertTrue(RegexTool.checkMobile("13812345678"))
    }

    @Test
    fun `checkMobile with international prefix`() {
        assertTrue(RegexTool.checkMobile("+8613812345678"))
    }

    @Test
    fun `checkMobile too short`() {
        assertFalse(RegexTool.checkMobile("1381234567"))
    }

    @Test
    fun `checkMobile invalid prefix`() {
        assertFalse(RegexTool.checkMobile("12345678901"))
    }

    @Test
    fun `checkMobile empty`() {
        assertFalse(RegexTool.checkMobile(""))
    }

    @Test
    fun `checkDigit positive integer`() {
        assertTrue(RegexTool.checkDigit("123"))
    }

    @Test
    fun `checkDigit negative integer`() {
        assertTrue(RegexTool.checkDigit("-123"))
    }

    @Test
    fun `checkDigit zero alone is not matched`() {
        assertFalse(RegexTool.checkDigit("0"))
    }

    @Test
    fun `checkDigit with decimal returns false`() {
        assertFalse(RegexTool.checkDigit("12.3"))
    }

    @Test
    fun `checkDigit empty`() {
        assertFalse(RegexTool.checkDigit(""))
    }

    @Test
    fun `checkChinese valid Chinese characters`() {
        assertTrue(RegexTool.checkChinese("中文"))
    }

    @Test
    fun `checkChinese with mixed characters returns false`() {
        assertFalse(RegexTool.checkChinese("中文abc"))
    }

    @Test
    fun `checkChinese empty`() {
        assertFalse(RegexTool.checkChinese(""))
    }

    @Test
    fun `checkIdCard valid 18-digit`() {
        assertTrue(RegexTool.checkIdCard("110101199001011234"))
    }

    @Test
    fun `checkIdCard too short`() {
        assertFalse(RegexTool.checkIdCard("12345"))
    }

    @Test
    fun `checkPostcode valid`() {
        assertTrue(RegexTool.checkPostcode("100001"))
    }

    @Test
    fun `checkPostcode too short`() {
        assertFalse(RegexTool.checkPostcode("12345"))
    }

    @Test
    fun `checkPostcode starts with zero`() {
        assertFalse(RegexTool.checkPostcode("012345"))
    }

    @Test
    fun `checkBlankSpace space`() {
        assertTrue(RegexTool.checkBlankSpace("   "))
    }

    @Test
    fun `checkBlankSpace tab`() {
        assertTrue(RegexTool.checkBlankSpace("\t"))
    }

    @Test
    fun `checkBlankSpace newline`() {
        assertTrue(RegexTool.checkBlankSpace("\n"))
    }

    @Test
    fun `checkBlankSpace non-blank returns false`() {
        assertFalse(RegexTool.checkBlankSpace("abc"))
    }

    @Test
    fun `checkURL valid http`() {
        assertTrue(RegexTool.checkURL("http://example.com"))
    }

    @Test
    fun `checkURL valid https`() {
        assertTrue(RegexTool.checkURL("https://www.example.com/path"))
    }

    @Test
    fun `checkURL without protocol`() {
        assertTrue(RegexTool.checkURL("example.com"))
    }

    @Test
    fun `checkURL empty`() {
        assertFalse(RegexTool.checkURL(""))
    }

    @Test
    fun `checkIpAddress valid`() {
        assertTrue(RegexTool.checkIpAddress("192.168.1.1"))
    }

    @Test
    fun `checkIpAddress localhost`() {
        assertTrue(RegexTool.checkIpAddress("127.0.0.1"))
    }

    @Test
    fun `checkIpAddress invalid format`() {
        assertFalse(RegexTool.checkIpAddress("abc.def.ghi.jkl"))
    }

    @Test
    fun `checkIpAddress empty`() {
        assertFalse(RegexTool.checkIpAddress(""))
    }

    @Test
    fun `checkDecimals positive number`() {
        assertTrue(RegexTool.checkDecimals("123.45"))
    }

    @Test
    fun `checkDecimals negative number`() {
        assertTrue(RegexTool.checkDecimals("-123.45"))
    }

    @Test
    fun `checkDecimals integer`() {
        assertTrue(RegexTool.checkDecimals("123"))
    }

    @Test
    fun `checkUserPasswordLength valid`() {
        assertTrue(RegexTool.checkUserPasswordLength("abcdef"))
    }

    @Test
    fun `checkUserPasswordLength too short`() {
        assertFalse(RegexTool.checkUserPasswordLength("abcde"))
    }

    @Test
    fun `checkUserPasswordLength too long`() {
        assertFalse(RegexTool.checkUserPasswordLength("abcdefghijklmnopqrstu"))
    }

    @Test
    fun `checkContainsDot returns true`() {
        assertTrue(RegexTool.checkContainsDot("name.surname"))
    }

    @Test
    fun `checkContainsDot returns false`() {
        assertFalse(RegexTool.checkContainsDot("namesurname"))
    }

    @Test
    fun `checkContainsHyphen returns true`() {
        assertTrue(RegexTool.checkContainsHyphen("user-name"))
    }

    @Test
    fun `checkContainsHyphen returns false`() {
        assertFalse(RegexTool.checkContainsHyphen("username"))
    }

    @Test
    fun `isValidUserName with letters`() {
        assertTrue(RegexTool.isValidUserName("UserName123"))
    }

    @Test
    fun `isValidUserName with Chinese`() {
        assertTrue(RegexTool.isValidUserName("用户名"))
    }

    @Test
    fun `isValidUserName with hyphen`() {
        assertTrue(RegexTool.isValidUserName("user-name"))
    }
}
