package com.robin.tools.feature.face.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IdPhotoProcessorTest {

    @Test
    fun `crop rect keeps the requested aspect ratio`() {
        val rect = IdPhotoProcessor.computeCropRect(
            sourceWidth = 2000,
            sourceHeight = 3000,
            faceLeft = 800,
            faceTop = 700,
            faceWidth = 400,
            faceHeight = 520,
            aspect = 295f / 413f
        )
        val aspect = rect[2].toFloat() / rect[3]
        assertEquals(295f / 413f, aspect, 0.02f)
    }

    @Test
    fun `crop rect stays inside the image`() {
        val rect = IdPhotoProcessor.computeCropRect(
            sourceWidth = 800,
            sourceHeight = 600,
            faceLeft = 20,
            faceTop = 10,
            faceWidth = 200,
            faceHeight = 240,
            aspect = 295f / 413f
        )
        assertTrue(rect[0] >= 0)
        assertTrue(rect[1] >= 0)
        assertTrue(rect[0] + rect[2] <= 800)
        assertTrue(rect[1] + rect[3] <= 600)
        assertTrue(rect[2] > 0)
        assertTrue(rect[3] > 0)
    }

    @Test
    fun `crop places the face in the upper part of the frame`() {
        val rect = IdPhotoProcessor.computeCropRect(
            sourceWidth = 2000,
            sourceHeight = 3000,
            faceLeft = 850,
            faceTop = 700,
            faceWidth = 300,
            faceHeight = 400,
            aspect = 1f
        )
        assertTrue(rect[1] <= 700)
        assertTrue(700 < rect[1] + rect[3])
    }
}
