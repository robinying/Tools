package com.robin.tools.feature.camera.editor

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.util.Log
import android.view.Surface
import java.io.File

/**
 * Builds a simple slideshow MP4 from still images (no audio, no fancy transitions).
 */
class SlideshowGenerator {

    fun generate(
        bitmaps: List<Bitmap>,
        outputFile: File,
        secondsPerImage: Int = 2,
        width: Int = 720,
        height: Int = 1280
    ): Boolean {
        if (bitmaps.isEmpty()) return false
        val w = width.coerceAtLeast(16) / 16 * 16
        val h = height.coerceAtLeast(16) / 16 * 16
        val fps = 30
        val framesPerImage = secondsPerImage.coerceIn(1, 10) * fps
        val cards = bitmaps.map { fitBitmap(it, w, h) }

        var encoder: MediaCodec? = null
        var muxer: MediaMuxer? = null
        var inputSurface: Surface? = null
        var muxerStarted = false
        var videoTrack = -1
        var samplesWritten = 0

        return try {
            outputFile.parentFile?.mkdirs()
            if (outputFile.exists()) outputFile.delete()

            val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, w, h).apply {
                setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
                setInteger(MediaFormat.KEY_BIT_RATE, 2_500_000)
                setInteger(MediaFormat.KEY_FRAME_RATE, fps)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
            }
            encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC).apply {
                configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            }
            inputSurface = encoder.createInputSurface()
            encoder.start()
            muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            val bufferInfo = MediaCodec.BufferInfo()
            val frameDurationUs = 1_000_000L / fps

            fun drain(endOfStream: Boolean, maxIdle: Int = 8) {
                val enc = encoder ?: return
                val mux = muxer ?: return
                var idle = 0
                while (true) {
                    val status = enc.dequeueOutputBuffer(bufferInfo, if (endOfStream) 50_000L else 5_000L)
                    when {
                        status == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                            if (!endOfStream) return
                            idle++
                            if (idle >= maxIdle) return
                        }
                        status == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                            videoTrack = mux.addTrack(enc.outputFormat)
                            mux.start()
                            muxerStarted = true
                            idle = 0
                        }
                        status >= 0 -> {
                            idle = 0
                            val buf = enc.getOutputBuffer(status)
                            val isConfig = bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0
                            if (buf != null && bufferInfo.size > 0 && !isConfig && muxerStarted && videoTrack >= 0) {
                                if (bufferInfo.presentationTimeUs <= 0L && samplesWritten > 0) {
                                    bufferInfo.presentationTimeUs = samplesWritten * frameDurationUs
                                }
                                buf.position(bufferInfo.offset)
                                buf.limit(bufferInfo.offset + bufferInfo.size)
                                mux.writeSampleData(videoTrack, buf, bufferInfo)
                                samplesWritten++
                            }
                            enc.releaseOutputBuffer(status, false)
                            if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) return
                        }
                    }
                }
            }

            for (card in cards) {
                for (i in 0 until framesPerImage) {
                    val surface = inputSurface ?: break
                    val canvas = surface.lockCanvas(null)
                    try {
                        canvas.drawBitmap(card, 0f, 0f, null)
                    } finally {
                        surface.unlockCanvasAndPost(canvas)
                    }
                    Thread.sleep(12)
                    drain(false)
                }
            }
            encoder.signalEndOfInputStream()
            drain(true, maxIdle = 40)

            val ok = muxerStarted && samplesWritten > 0 && outputFile.length() > 8_000
            if (!ok) {
                Log.e(TAG, "slideshow incomplete samples=$samplesWritten size=${outputFile.length()}")
            }
            ok
        } catch (e: Exception) {
            Log.e(TAG, "slideshow failed", e)
            false
        } finally {
            cards.forEach { if (!it.isRecycled) it.recycle() }
            try {
                encoder?.stop(); encoder?.release()
            } catch (_: Exception) {
            }
            try {
                if (muxerStarted) muxer?.stop()
                muxer?.release()
            } catch (_: Exception) {
            }
            try {
                inputSurface?.release()
            } catch (_: Exception) {
            }
            if (!muxerStarted || samplesWritten == 0) {
                if (outputFile.exists() && outputFile.length() < 8_000) outputFile.delete()
            }
        }
    }

    private fun fitBitmap(src: Bitmap, width: Int, height: Int): Bitmap {
        val out = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        canvas.drawColor(Color.BLACK)
        val scale = minOf(width.toFloat() / src.width, height.toFloat() / src.height)
        val dx = (width - src.width * scale) / 2f
        val dy = (height - src.height * scale) / 2f
        val matrix = Matrix().apply {
            postScale(scale, scale)
            postTranslate(dx, dy)
        }
        canvas.drawBitmap(src, matrix, Paint(Paint.FILTER_BITMAP_FLAG))
        return out
    }

    companion object {
        private const val TAG = "SlideshowGen"
    }
}
