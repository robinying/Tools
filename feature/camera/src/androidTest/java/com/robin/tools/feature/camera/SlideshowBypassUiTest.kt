package com.robin.tools.feature.camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.robin.tools.feature.camera.editor.SlideshowGenerator
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Bypasses system image multi-select: builds bitmaps in-process and calls [SlideshowGenerator].
 */
@RunWith(AndroidJUnit4::class)
class SlideshowBypassUiTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
    }

    @Test
    fun slideshow_fromSyntheticBitmaps_writesPlayableMp4() {
        val bitmaps = listOf(
            solidCard(Color.rgb(45, 27, 78), "A"),
            solidCard(Color.rgb(20, 80, 120), "B"),
            solidCard(Color.rgb(120, 40, 40), "C")
        )
        val out = File(context.cacheDir, "slideshow_bypass_${System.currentTimeMillis()}.mp4")
        try {
            val ok = SlideshowGenerator().generate(
                bitmaps = bitmaps,
                outputFile = out,
                secondsPerImage = 1,
                width = 720,
                height = 1280
            )
            assertTrue("SlideshowGenerator failed", ok)
            assertTrue("Output missing", out.exists())
            assertTrue("Output too small: ${out.length()}", out.length() > 8_000)
        } finally {
            bitmaps.forEach { if (!it.isRecycled) it.recycle() }
        }
    }

    private fun solidCard(color: Int, label: String): Bitmap {
        val bmp = Bitmap.createBitmap(720, 1280, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(color)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = Color.WHITE
            textSize = 96f
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText(label, 360f, 640f, paint)
        return bmp
    }
}
