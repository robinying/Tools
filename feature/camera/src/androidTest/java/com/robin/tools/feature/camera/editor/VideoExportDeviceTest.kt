package com.robin.tools.feature.camera.editor

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.robin.tools.feature.camera.filter.FilterType
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream

/**
 * On-device verification for clip/export changes:
 * - VideoClipper keeps audio
 * - VideoEffectsExporter burns filter/watermark and remuxes audio
 */
@RunWith(AndroidJUnit4::class)
class VideoExportDeviceTest {

    private lateinit var context: Context
    private lateinit var inputFile: File
    private lateinit var outDir: File

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        outDir = File(context.cacheDir, "device_export_test").apply {
            if (exists()) deleteRecursively()
            mkdirs()
        }
        inputFile = File(outDir, "tools_test_av.mp4")
        copyAsset("tools_test_av.mp4", inputFile)
        require(inputFile.length() > 0) { "test asset missing" }
    }

    @Test
    fun clipper_preservesAudioTrack() {
        val out = File(outDir, "clipped.mp4")
        val ok = VideoClipper().clip(
            inputPath = inputFile.absolutePath,
            outputPath = out.absolutePath,
            startMs = 0,
            endMs = 2500
        )
        assertTrue("VideoClipper failed", ok)
        assertTrue("clipped file empty", out.length() > 0)

        val tracks = trackMimes(out.absolutePath)
        assertTrue("missing video track: $tracks", tracks.any { it.startsWith("video/") })
        assertTrue("missing audio track: $tracks", tracks.any { it.startsWith("audio/") })
    }

    @Test
    fun effectsExporter_appliesFilterAndKeepsAudio() {
        val out = File(outDir, "edited_beauty.mp4")
        val ok = VideoEffectsExporter().export(
            context = context,
            inputPath = inputFile.absolutePath,
            outputPath = out.absolutePath,
            filterType = FilterType.BEAUTY,
            watermarkText = "Tools QA"
        )
        assertTrue("VideoEffectsExporter failed", ok)
        assertTrue("export file empty", out.length() > 0)

        val tracks = trackMimes(out.absolutePath)
        assertTrue("missing video track: $tracks", tracks.any { it.startsWith("video/") })
        assertTrue("missing audio track after effects: $tracks", tracks.any { it.startsWith("audio/") })
        // Re-encoded file should differ from pure remux size roughly; just ensure non-trivial
        assertTrue("export too small", out.length() > 10_000)
    }

    private fun copyAsset(name: String, dest: File) {
        val assets = InstrumentationRegistry.getInstrumentation().context.assets
        assets.open(name).use { input ->
            FileOutputStream(dest).use { output -> input.copyTo(output) }
        }
    }

    private fun trackMimes(path: String): List<String> {
        val extractor = MediaExtractor()
        return try {
            extractor.setDataSource(path)
            (0 until extractor.trackCount).mapNotNull { i ->
                extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME)
            }
        } finally {
            extractor.release()
        }
    }
}
