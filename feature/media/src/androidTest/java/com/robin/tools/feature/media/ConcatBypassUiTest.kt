package com.robin.tools.feature.media

import android.content.Context
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.robin.tools.feature.media.data.CompressionLevel
import com.robin.tools.feature.media.delegate.ConcatDelegate
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import kotlinx.coroutines.runBlocking

/**
 * Bypasses system multi-select UI by feeding file:// URIs directly into [ConcatDelegate].
 *
 * Note: full [CompressionService] path is covered by app-module instrumented tests
 * (library androidTest host app does not merge the app-level service registration).
 */
@RunWith(AndroidJUnit4::class)
class ConcatBypassUiTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
    }

    @Test
    fun concatDelegate_twoCopiesOfSeed_succeeds() = runBlocking {
        val seed = TestVideoSeed.resolveOrCreate(context)
        val a = copyToCache(seed, "concat_a.mp4")
        val b = copyToCache(seed, "concat_b.mp4")
        val uris = listOf(Uri.fromFile(a), Uri.fromFile(b))

        val result = ConcatDelegate().processAll(
            context = context,
            uris = uris,
            level = CompressionLevel.MEDIUM,
            onProgress = { _, _ -> }
        )

        assertTrue(
            "Concat should succeed: ${result.exceptionOrNull()?.message}",
            result.isSuccess
        )
        val out = result.getOrNull().orEmpty()
        assertTrue("Expected output path/uri, got: $out", out.isNotBlank())
    }

    private fun copyToCache(seed: File, name: String): File {
        val dir = File(context.cacheDir, "bypass_ui_test").apply { mkdirs() }
        val out = File(dir, name)
        FileInputStream(seed).use { input ->
            FileOutputStream(out).use { output -> input.copyTo(output) }
        }
        assertTrue("copy failed: $out", out.exists() && out.length() > 0)
        return out
    }
}
