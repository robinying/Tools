package com.robin.tools

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.robin.tools.feature.media.data.CompressionLevel
import com.robin.tools.feature.media.data.CompressionManager
import com.robin.tools.feature.media.data.CompressionTaskState
import com.robin.tools.feature.media.data.CompressionType
import com.robin.tools.feature.media.service.CompressionService
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

/**
 * App-process harness: start [CompressionService] with absolute file paths
 * (no system multi-select). Uses the real app package where the service is registered.
 */
@RunWith(AndroidJUnit4::class)
class ConcatServiceBypassUiTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        CompressionManager.reset()
    }

    @Test
    fun startWithFilePaths_concatFinishes() = runBlocking {
        val seed = resolveSeed()
        val a = copy(seed, "svc_a.mp4")
        val b = copy(seed, "svc_b.mp4")

        CompressionService.startWithFilePaths(
            context = context,
            absolutePaths = listOf(a.absolutePath, b.absolutePath),
            type = CompressionType.CONCAT,
            level = CompressionLevel.MEDIUM
        )

        val finished = withTimeout(TimeUnit.MINUTES.toMillis(3)) {
            while (true) {
                val s = CompressionManager.taskState.value
                if (s is CompressionTaskState.Finished) return@withTimeout s
                delay(400)
            }
            @Suppress("UNREACHABLE_CODE")
            error("loop")
        }

        assertTrue("Expected success: ${finished.message}", finished.isSuccess)
        assertTrue("Expected outputUri", !finished.outputUri.isNullOrBlank())
    }

    private fun resolveSeed(): File {
        val known = listOf(
            File("/sdcard/Download/tools_test_av.mp4"),
            File("/storage/emulated/0/Download/tools_test_av.mp4")
        ).firstOrNull { it.exists() && it.canRead() && it.length() > 10_000 }
        if (known != null) return known

        // MediaStore fallback
        val projection = arrayOf(android.provider.MediaStore.Video.Media._ID)
        context.contentResolver.query(
            android.provider.MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            projection,
            "${android.provider.MediaStore.Video.Media.SIZE}>?",
            arrayOf("50000"),
            "${android.provider.MediaStore.Video.Media.SIZE} ASC"
        )?.use { c ->
            if (c.moveToFirst()) {
                val id = c.getLong(0)
                val uri = android.content.ContentUris.withAppendedId(
                    android.provider.MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                    id
                )
                val out = File(context.cacheDir, "seed_ms.mp4")
                context.contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(out).use { output -> input.copyTo(output) }
                }
                if (out.exists() && out.length() > 10_000) return out
            }
        }
        fail("No seed video; push /sdcard/Download/tools_test_av.mp4")
        error("unreachable")
    }

    private fun copy(seed: File, name: String): File {
        val dir = File(context.cacheDir, "svc_bypass").apply { mkdirs() }
        val out = File(dir, name)
        FileInputStream(seed).use { i -> FileOutputStream(out).use { o -> i.copyTo(o) } }
        return out
    }
}
