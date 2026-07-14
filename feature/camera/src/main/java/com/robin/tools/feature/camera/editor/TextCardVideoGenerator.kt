package com.robin.tools.feature.camera.editor

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.util.Log
import android.view.Surface
import java.io.File

/**
 * Renders a solid-color card with centered text into a short H.264 MP4 (no audio).
 */
class TextCardVideoGenerator {

    fun generate(
        text: String,
        outputFile: File,
        durationSec: Int = 3,
        width: Int = 720,
        height: Int = 1280,
        backgroundColor: Int = Color.parseColor("#2D1B4E"),
        textColor: Int = Color.WHITE
    ): Boolean {
        // Encoders commonly require 16-aligned dimensions.
        val w = width.coerceAtLeast(16) / 16 * 16
        val h = height.coerceAtLeast(16) / 16 * 16
        val fps = 30
        val frameCount = durationSec.coerceIn(1, 15) * fps
        val bitRate = 2_000_000
        val card = renderCardBitmap(text, w, h, backgroundColor, textColor)

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
                setInteger(MediaFormat.KEY_BIT_RATE, bitRate)
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

            fun drain(endOfStream: Boolean, maxIdlePolls: Int = 5) {
                val enc = encoder ?: return
                val mux = muxer ?: return
                var idle = 0
                while (true) {
                    val timeoutUs = if (endOfStream) 50_000L else 5_000L
                    val status = enc.dequeueOutputBuffer(bufferInfo, timeoutUs)
                    when {
                        status == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                            if (!endOfStream) return
                            idle++
                            if (idle >= maxIdlePolls) return
                        }
                        status == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                            if (muxerStarted) {
                                throw IllegalStateException("format changed twice")
                            }
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
                                // Some devices leave PTS at 0 for Surface input; assign a monotonic timeline.
                                if (bufferInfo.presentationTimeUs <= 0L && samplesWritten > 0) {
                                    bufferInfo.presentationTimeUs = samplesWritten * frameDurationUs
                                }
                                buf.position(bufferInfo.offset)
                                buf.limit(bufferInfo.offset + bufferInfo.size)
                                mux.writeSampleData(videoTrack, buf, bufferInfo)
                                samplesWritten++
                            }
                            enc.releaseOutputBuffer(status, false)
                            if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                                return
                            }
                        }
                    }
                }
            }

            // Draw frames at a reduced real-time pace (not full 30fps wall-clock) so encode stays responsive.
            val drawIntervalMs = 16L
            for (i in 0 until frameCount) {
                val surface = inputSurface ?: break
                val canvas = surface.lockCanvas(null)
                try {
                    canvas.drawBitmap(card, 0f, 0f, null)
                } finally {
                    surface.unlockCanvasAndPost(canvas)
                }
                Thread.sleep(drawIntervalMs)
                drain(endOfStream = false)
            }

            encoder.signalEndOfInputStream()
            drain(endOfStream = true, maxIdlePolls = 40)

            val ok = muxerStarted && samplesWritten > 0 && outputFile.exists() && outputFile.length() > 8_000
            if (!ok) {
                Log.e(
                    TAG,
                    "generate incomplete: muxerStarted=$muxerStarted samples=$samplesWritten size=${outputFile.length()}"
                )
            } else {
                Log.i(TAG, "generate ok: samples=$samplesWritten size=${outputFile.length()}")
            }
            ok
        } catch (e: Exception) {
            Log.e(TAG, "generate failed", e)
            false
        } finally {
            if (!card.isRecycled) card.recycle()
            try {
                encoder?.stop()
            } catch (_: Exception) {
            }
            try {
                encoder?.release()
            } catch (_: Exception) {
            }
            try {
                if (muxerStarted) muxer?.stop()
            } catch (_: Exception) {
            }
            try {
                muxer?.release()
            } catch (_: Exception) {
            }
            try {
                inputSurface?.release()
            } catch (_: Exception) {
            }
            if (!muxerStarted || samplesWritten == 0) {
                // Leave no empty / header-only stub for the UI to open.
                if (outputFile.exists() && outputFile.length() < 8_000) {
                    outputFile.delete()
                }
            }
        }
    }

    private fun renderCardBitmap(
        text: String,
        width: Int,
        height: Int,
        bg: Int,
        fg: Int
    ): Bitmap {
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(bg)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = fg
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textSize = width * 0.08f
        }
        val lines = wrapText(text.ifBlank { " " }, paint, width * 0.8f)
        val lineHeight = paint.fontSpacing
        var y = (height - lineHeight * lines.size) / 2f - paint.ascent()
        val cx = width / 2f
        for (line in lines) {
            canvas.drawText(line, cx, y, paint)
            y += lineHeight
        }
        return bmp
    }

    private fun wrapText(text: String, paint: Paint, maxWidth: Float): List<String> {
        val words = text.replace('\n', ' ').split(' ')
        val lines = mutableListOf<String>()
        var current = StringBuilder()
        for (w in words) {
            val trial = if (current.isEmpty()) w else "$current $w"
            if (paint.measureText(trial) <= maxWidth) {
                current = StringBuilder(trial)
            } else {
                if (current.isNotEmpty()) lines.add(current.toString())
                current = StringBuilder(w)
            }
        }
        if (current.isNotEmpty()) lines.add(current.toString())
        return lines.ifEmpty { listOf(" ") }.take(12)
    }

    companion object {
        private const val TAG = "TextCardVideo"
    }
}
