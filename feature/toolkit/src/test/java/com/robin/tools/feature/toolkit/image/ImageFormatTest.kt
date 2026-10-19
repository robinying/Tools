package com.robin.tools.feature.toolkit.image

import org.junit.Assert.assertEquals
import org.junit.Test

class ImageFormatTest {

    @Test
    fun `lossy formats clamp quality`() {
        assertEquals(10, ImageFormat.JPEG.effectiveQuality(1))
        assertEquals(100, ImageFormat.JPEG.effectiveQuality(500))
    }

    @Test
    fun `png always uses lossless quality`() {
        assertEquals(100, ImageFormat.PNG.effectiveQuality(20))
    }

    @Test
    fun `format extension matches mime type`() {
        assertEquals("jpg", ImageFormat.JPEG.extension)
        assertEquals("image/webp", ImageFormat.WEBP.mimeType)
    }
}
