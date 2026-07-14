package com.robin.tools.feature.media

import android.content.Context
import android.graphics.Color
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.provider.MediaStore
import android.view.Surface
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

/**
 * Resolves a short seed MP4 for instrumented tests without relying on UI multi-select.
 * Order: MediaStore → known paths → synthetic encode.
 */
object TestVideoSeed {

    fun resolveOrCreate(context: Context): File {
        copyFromMediaStore(context)?.let { return it }
        copyFromKnownPaths(context)?.let { return it }
        return createSynthetic(context)
    }

    private fun copyFromMediaStore(context: Context): File? {
        return try {
            val projection = arrayOf(
                MediaStore.Video.Media._ID,
                MediaStore.Video.Media.SIZE,
                MediaStore.Video.Media.DISPLAY_NAME
            )
            val sort = "${MediaStore.Video.Media.SIZE} ASC"
            context.contentResolver.query(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                projection,
                "${MediaStore.Video.Media.SIZE}>?",
                arrayOf("50000"),
                sort
            )?.use { cursor ->
                if (!cursor.moveToFirst()) return null
                val id = cursor.getLong(0)
                val uri = android.content.ContentUris.withAppendedId(
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                    id
                )
                val out = File(context.cacheDir, "seed_mediastore.mp4")
                context.contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(out).use { output -> input.copyTo(output) }
                } ?: return null
                if (out.length() > 10_000) out else null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun copyFromKnownPaths(context: Context): File? {
        val candidates = listOf(
            File("/sdcard/Download/tools_test_av.mp4"),
            File("/storage/emulated/0/Download/tools_test_av.mp4")
        )
        val seed = candidates.firstOrNull { it.exists() && it.canRead() && it.length() > 10_000 }
            ?: return null
        val out = File(context.cacheDir, "seed_path.mp4")
        return try {
            FileInputStream(seed).use { input ->
                FileOutputStream(out).use { output -> input.copyTo(output) }
            }
            if (out.length() > 10_000) out else null
        } catch (_: Exception) {
            null
        }
    }

    /** ~1s solid-color H.264 (no audio) for concat self-test. */
    private fun createSynthetic(context: Context): File {
        val out = File(context.cacheDir, "seed_synthetic.mp4")
        if (out.exists()) out.delete()
        val w = 320
        val h = 240
        val fps = 15
        val frames = fps // 1 second
        var encoder: MediaCodec? = null
        var muxer: MediaMuxer? = null
        var surface: Surface? = null
        var started = false
        var track = -1
        try {
            val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, w, h).apply {
                setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
                setInteger(MediaFormat.KEY_BIT_RATE, 400_000)
                setInteger(MediaFormat.KEY_FRAME_RATE, fps)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
            }
            encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC).apply {
                configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            }
            surface = encoder.createInputSurface()
            encoder.start()
            muxer = MediaMuxer(out.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            val info = MediaCodec.BufferInfo()

            fun drain(eos: Boolean) {
                var idle = 0
                while (true) {
                    val st = encoder!!.dequeueOutputBuffer(info, if (eos) 50_000 else 5_000)
                    when {
                        st == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                            if (!eos) return
                            if (++idle > 20) return
                        }
                        st == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                            track = muxer!!.addTrack(encoder!!.outputFormat)
                            muxer!!.start()
                            started = true
                        }
                        st >= 0 -> {
                            idle = 0
                            val buf = encoder!!.getOutputBuffer(st)
                            if (buf != null && info.size > 0 &&
                                info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0 &&
                                started && track >= 0
                            ) {
                                muxer!!.writeSampleData(track, buf, info)
                            }
                            encoder!!.releaseOutputBuffer(st, false)
                            if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) return
                        }
                    }
                }
            }

            for (i in 0 until frames) {
                val canvas = surface!!.lockCanvas(null)
                try {
                    canvas.drawColor(Color.rgb(30 + i * 3, 60, 120))
                } finally {
                    surface.unlockCanvasAndPost(canvas)
                }
                Thread.sleep(20)
                drain(false)
            }
            encoder.signalEndOfInputStream()
            drain(true)
        } finally {
            try {
                encoder?.stop(); encoder?.release()
            } catch (_: Exception) {
            }
            try {
                if (started) muxer?.stop()
                muxer?.release()
            } catch (_: Exception) {
            }
            try {
                surface?.release()
            } catch (_: Exception) {
            }
        }
        check(out.exists() && out.length() > 2_000) { "synthetic seed failed size=${out.length()}" }
        return out
    }
}
