package com.robin.tools.feature.media.utils

import org.junit.Assert.assertEquals
import org.junit.Test

class FileUtilsTest {

    @Test
    fun `sanitizeFileName returns fallback for null blank and whitespace names`() {
        assertEquals("temp.bin", FileUtils.sanitizeFileName(null))
        assertEquals("temp.bin", FileUtils.sanitizeFileName(""))
        assertEquals("temp.bin", FileUtils.sanitizeFileName("   "))
    }

    @Test
    fun `sanitizeFileName removes unix and windows path traversal`() {
        assertEquals("secret.jpg", FileUtils.sanitizeFileName("../../secret.jpg"))
        assertEquals("video.mp4", FileUtils.sanitizeFileName("folder/subfolder/video.mp4"))
        assertEquals("secret.jpg", FileUtils.sanitizeFileName("..\\..\\secret.jpg"))
    }

    @Test
    fun `sanitizeFileName replaces unsafe characters and preserves safe characters`() {
        assertEquals("my_photo__1_.jpg", FileUtils.sanitizeFileName("my photo (1).jpg"))
        assertEquals("archive-1.2_final.txt", FileUtils.sanitizeFileName("archive-1.2_final.txt"))
    }

    @Test
    fun `sanitizeFileName limits names to one hundred twenty characters`() {
        val name = "a".repeat(121) + ".jpg"

        val result = FileUtils.sanitizeFileName(name)

        assertEquals(120, result.length)
        assertEquals("a".repeat(120), result)
    }

    @Test
    fun `sanitizeFileName replaces non ascii names without exposing path separators`() {
        assertEquals("___1.jpg", FileUtils.sanitizeFileName("照片 1.jpg"))
        assertEquals("___", FileUtils.sanitizeFileName("<>:"))
    }
}
