package com.robin.tools.feature.camera.editor

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.SurfaceTexture
import android.graphics.Typeface
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import android.opengl.GLES20
import android.opengl.GLUtils
import android.opengl.Matrix
import android.util.Log
import android.view.Surface
import com.robin.tools.feature.camera.filter.CameraFilter
import com.robin.tools.feature.camera.filter.FilterFactory
import com.robin.tools.feature.camera.filter.FilterType
import com.robin.tools.feature.camera.filter.GpuImageFilter
import com.robin.tools.feature.camera.filter.NoFilter
import com.robin.tools.feature.camera.opengl.EglCore
import com.robin.tools.feature.camera.opengl.GlFrameBuffer
import com.robin.tools.feature.camera.opengl.GlTexture
import com.robin.tools.feature.camera.opengl.WindowSurface
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

/**
 * Re-encodes a video while applying a GPU filter and optional watermark/sticker overlays.
 * Audio is remuxed from the source without re-encoding.
 */
class VideoEffectsExporter {

    fun export(
        context: Context,
        inputPath: String,
        outputPath: String,
        filterType: FilterType,
        watermarkText: String = "",
        stickers: List<Bitmap> = emptyList()
    ): Boolean {
        val meta = readMeta(inputPath) ?: return false
        val width = alignEven(meta.width.coerceAtLeast(16))
        val height = alignEven(meta.height.coerceAtLeast(16))
        val bitRate = (width * height * 4).coerceIn(1_000_000, 8_000_000)

        var extractor: MediaExtractor? = null
        var decoder: MediaCodec? = null
        var encoder: MediaCodec? = null
        var muxer: MediaMuxer? = null
        var eglCore: EglCore? = null
        var windowSurface: WindowSurface? = null
        var surfaceTexture: SurfaceTexture? = null
        var decoderSurface: Surface? = null
        var oesTextureId = 0
        var overlayTextureId = 0
        var cameraFilter: CameraFilter? = null
        var effectFilter: GpuImageFilter? = null
        var displayFilter: NoFilter? = null
        var overlayFilter: OverlayFilter? = null
        var fbo: GlFrameBuffer? = null
        var muxerStarted = false
        var videoTrackIndex = -1

        return try {
            extractor = MediaExtractor().apply { setDataSource(inputPath) }
            val videoTrack = selectTrack(extractor, "video/")
                ?: throw IllegalStateException("No video track")
            extractor.selectTrack(videoTrack)
            val inputFormat = extractor.getTrackFormat(videoTrack)
            val mime = inputFormat.getString(MediaFormat.KEY_MIME)
                ?: throw IllegalStateException("Missing video mime")

            // --- Encoder ---
            val outputFormat = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height).apply {
                setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
                setInteger(MediaFormat.KEY_BIT_RATE, bitRate)
                setInteger(MediaFormat.KEY_FRAME_RATE, meta.frameRate)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
            }
            encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC).apply {
                configure(outputFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            }
            val inputSurface = encoder.createInputSurface()
            encoder.start()

            // --- EGL + GL pipeline ---
            eglCore = EglCore(null, EglCore.FLAG_RECORDABLE)
            windowSurface = WindowSurface(eglCore, inputSurface, false)
            windowSurface.makeCurrent()

            oesTextureId = GlTexture.createOesTexture()
            surfaceTexture = SurfaceTexture(oesTextureId).apply {
                setDefaultBufferSize(width, height)
            }
            decoderSurface = Surface(surfaceTexture)

            cameraFilter = CameraFilter(context.resources, false).also {
                it.init()
                it.onInputSizeChanged(width, height)
                it.onDisplaySizeChanged(width, height)
                Matrix.setIdentityM(it.mvpMatrix, 0)
            }
            effectFilter = FilterFactory.create(filterType, context.resources).also {
                it.init()
                it.onInputSizeChanged(width, height)
                it.onDisplaySizeChanged(width, height)
                Matrix.setIdentityM(it.mvpMatrix, 0)
            }
            displayFilter = NoFilter(context.resources).also {
                it.init()
                Matrix.setIdentityM(it.mvpMatrix, 0)
            }
            overlayFilter = OverlayFilter().also { it.init() }

            fbo = GlFrameBuffer().also { it.create(width, height) }

            val overlayBitmap = buildOverlayBitmap(width, height, watermarkText, stickers)
            if (overlayBitmap != null) {
                overlayTextureId = uploadBitmapTexture(overlayBitmap)
                if (!overlayBitmap.isRecycled) overlayBitmap.recycle()
            }

            // --- Decoder ---
            decoder = MediaCodec.createDecoderByType(mime).apply {
                configure(inputFormat, decoderSurface, null, 0)
                start()
            }

            muxer = MediaMuxer(outputPath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            if (meta.rotation != 0) {
                muxer.setOrientationHint(meta.rotation)
            }

            val bufferInfo = MediaCodec.BufferInfo()
            val transform = FloatArray(16)
            var inputDone = false
            var outputDone = false
            var sawDecoderEos = false

            while (!outputDone) {
                // Feed decoder
                if (!inputDone) {
                    val inIndex = decoder.dequeueInputBuffer(TIMEOUT_US)
                    if (inIndex >= 0) {
                        val buffer = decoder.getInputBuffer(inIndex)
                        if (buffer != null) {
                            val sampleSize = extractor.readSampleData(buffer, 0)
                            if (sampleSize < 0) {
                                decoder.queueInputBuffer(
                                    inIndex, 0, 0, 0L,
                                    MediaCodec.BUFFER_FLAG_END_OF_STREAM
                                )
                                inputDone = true
                            } else {
                                decoder.queueInputBuffer(
                                    inIndex, 0, sampleSize, extractor.sampleTime, 0
                                )
                                extractor.advance()
                            }
                        }
                    }
                }

                // Drain decoder → render → encoder surface
                val outIndex = decoder.dequeueOutputBuffer(bufferInfo, TIMEOUT_US)
                when {
                    outIndex == MediaCodec.INFO_TRY_AGAIN_LATER -> Unit
                    outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> Unit
                    outIndex >= 0 -> {
                        val doRender = bufferInfo.size > 0
                        decoder.releaseOutputBuffer(outIndex, doRender)
                        if (doRender) {
                            surfaceTexture.updateTexImage()
                            surfaceTexture.getTransformMatrix(transform)

                            // OES → FBO
                            fbo.bind()
                            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
                            cameraFilter.setTextureTransform(transform)
                            cameraFilter.draw(oesTextureId)
                            fbo.unbind()

                            // FBO → encoder surface (with filter)
                            windowSurface.makeCurrent()
                            GLES20.glViewport(0, 0, width, height)
                            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
                            val frameTex = fbo.frameBufferTextureId
                            if (filterType == FilterType.NONE) {
                                displayFilter.draw(frameTex)
                            } else {
                                effectFilter.draw(frameTex)
                            }

                            // Watermark / stickers overlay
                            if (overlayTextureId != 0) {
                                GLES20.glEnable(GLES20.GL_BLEND)
                                GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)
                                overlayFilter.draw(overlayTextureId)
                                GLES20.glDisable(GLES20.GL_BLEND)
                            }

                            windowSurface.setPresentationTime(bufferInfo.presentationTimeUs * 1000L)
                            windowSurface.swapBuffers()
                        }
                        if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                            encoder.signalEndOfInputStream()
                            sawDecoderEos = true
                        }
                    }
                }

                // Drain encoder → muxer
                while (true) {
                    val encStatus = encoder.dequeueOutputBuffer(bufferInfo, 0)
                    when {
                        encStatus == MediaCodec.INFO_TRY_AGAIN_LATER -> break
                        encStatus == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                            if (muxerStarted) throw IllegalStateException("format changed twice")
                            videoTrackIndex = muxer.addTrack(encoder.outputFormat)
                            // Defer start until after we optionally add audio? Audio is remuxed after.
                            // Start muxer now for video; audio remux uses a second-pass merge if needed.
                            muxer.start()
                            muxerStarted = true
                        }
                        encStatus >= 0 -> {
                            val encoded = encoder.getOutputBuffer(encStatus)
                                ?: throw IllegalStateException("null encoder buffer")
                            if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) {
                                bufferInfo.size = 0
                            }
                            if (bufferInfo.size > 0 && muxerStarted) {
                                muxer.writeSampleData(videoTrackIndex, encoded, bufferInfo)
                            }
                            encoder.releaseOutputBuffer(encStatus, false)
                            if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                                outputDone = true
                                break
                            }
                        }
                    }
                }

                if (sawDecoderEos && !outputDone) {
                    // Keep draining encoder until EOS
                }
            }

            // Release video pipeline before audio remux of the same file
            try {
                decoder.stop(); decoder.release(); decoder = null
            } catch (_: Exception) {
            }
            try {
                encoder.stop(); encoder.release(); encoder = null
            } catch (_: Exception) {
            }
            try {
                extractor.release(); extractor = null
            } catch (_: Exception) {
            }
            try {
                muxer.stop(); muxer.release(); muxer = null
            } catch (_: Exception) {
            }
            muxerStarted = false

            // Remux audio from source into a temp final file if audio exists
            remuxWithAudio(inputPath, outputPath)

            true
        } catch (e: Exception) {
            Log.e(TAG, "export failed", e)
            false
        } finally {
            try {
                decoder?.stop(); decoder?.release()
            } catch (_: Exception) {
            }
            try {
                encoder?.stop(); encoder?.release()
            } catch (_: Exception) {
            }
            try {
                extractor?.release()
            } catch (_: Exception) {
            }
            try {
                if (muxerStarted) muxer?.stop()
                muxer?.release()
            } catch (_: Exception) {
            }
            try {
                surfaceTexture?.release()
            } catch (_: Exception) {
            }
            try {
                decoderSurface?.release()
            } catch (_: Exception) {
            }
            try {
                cameraFilter?.destroy()
                effectFilter?.destroy()
                displayFilter?.destroy()
                overlayFilter?.release()
                fbo?.release()
            } catch (_: Exception) {
            }
            if (oesTextureId != 0) GlTexture.deleteTexture(oesTextureId)
            if (overlayTextureId != 0) GlTexture.deleteTexture(overlayTextureId)
            try {
                windowSurface?.release()
            } catch (_: Exception) {
            }
            try {
                eglCore?.release()
            } catch (_: Exception) {
            }
        }
    }

    /**
     * If [videoOnlyPath] has no audio but [sourcePath] does, produce a new file with both
     * by replacing [videoOnlyPath] in place.
     */
    private fun remuxWithAudio(sourcePath: String, videoOnlyPath: String) {
        val src = MediaExtractor()
        val videoEx = MediaExtractor()
        var muxer: MediaMuxer? = null
        var started = false
        val tempOut = "$videoOnlyPath.tmp_audio.mp4"
        try {
            src.setDataSource(sourcePath)
            videoEx.setDataSource(videoOnlyPath)

            val audioTrack = selectTrack(src, "audio/") ?: return
            val videoTrack = selectTrack(videoEx, "video/") ?: return

            muxer = MediaMuxer(tempOut, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            val vFormat = videoEx.getTrackFormat(videoTrack)
            val aFormat = src.getTrackFormat(audioTrack)
            val vOut = muxer.addTrack(vFormat)
            val aOut = muxer.addTrack(aFormat)
            if (vFormat.containsKey(MediaFormat.KEY_ROTATION)) {
                muxer.setOrientationHint(vFormat.getInteger(MediaFormat.KEY_ROTATION))
            }
            muxer.start()
            started = true

            copyTrack(videoEx, videoTrack, muxer, vOut)
            copyTrack(src, audioTrack, muxer, aOut)

            muxer.stop()
            muxer.release()
            muxer = null
            started = false

            val finalFile = java.io.File(videoOnlyPath)
            val tempFile = java.io.File(tempOut)
            if (tempFile.exists()) {
                finalFile.delete()
                tempFile.renameTo(finalFile)
            }
        } catch (e: Exception) {
            Log.w(TAG, "audio remux skipped: ${e.message}")
            java.io.File(tempOut).delete()
        } finally {
            try {
                src.release()
            } catch (_: Exception) {
            }
            try {
                videoEx.release()
            } catch (_: Exception) {
            }
            try {
                if (started) muxer?.stop()
                muxer?.release()
            } catch (_: Exception) {
            }
        }
    }

    private fun copyTrack(
        extractor: MediaExtractor,
        trackIndex: Int,
        muxer: MediaMuxer,
        muxerTrack: Int
    ) {
        extractor.selectTrack(trackIndex)
        extractor.seekTo(0, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
        val buffer = ByteBuffer.allocate(1024 * 1024)
        val info = MediaCodec.BufferInfo()
        while (true) {
            buffer.clear()
            val size = extractor.readSampleData(buffer, 0)
            if (size < 0) break
            info.offset = 0
            info.size = size
            info.presentationTimeUs = extractor.sampleTime
            info.flags = extractor.sampleFlags
            muxer.writeSampleData(muxerTrack, buffer, info)
            extractor.advance()
        }
        extractor.unselectTrack(trackIndex)
    }

    private fun selectTrack(extractor: MediaExtractor, prefix: String): Int? {
        for (i in 0 until extractor.trackCount) {
            val mime = extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME) ?: continue
            if (mime.startsWith(prefix)) return i
        }
        return null
    }

    private data class Meta(
        val width: Int,
        val height: Int,
        val frameRate: Int,
        val rotation: Int
    )

    private fun readMeta(path: String): Meta? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(path)
            val w = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
            val h = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
            val rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
            // Keep encoded frame size as-is; apply rotation via muxer orientation hint.
            Meta(
                width = if (w > 0) w else 720,
                height = if (h > 0) h else 1280,
                frameRate = 30,
                rotation = rotation
            )
        } catch (e: Exception) {
            Log.e(TAG, "readMeta failed", e)
            null
        } finally {
            try {
                retriever.release()
            } catch (_: Exception) {
            }
        }
    }

    private fun buildOverlayBitmap(
        width: Int,
        height: Int,
        watermarkText: String,
        stickers: List<Bitmap>
    ): Bitmap? {
        if (watermarkText.isBlank() && stickers.isEmpty()) return null
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.TRANSPARENT)

        if (watermarkText.isNotBlank()) {
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                textSize = (height * 0.06f).coerceIn(28f, 72f)
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                setShadowLayer(4f, 2f, 2f, Color.BLACK)
                alpha = 200
            }
            val textWidth = paint.measureText(watermarkText)
            val x = (width - textWidth) / 2f
            val y = height * 0.5f
            canvas.drawText(watermarkText, x, y, paint)
        }

        stickers.forEachIndexed { index, sticker ->
            if (sticker.isRecycled) return@forEachIndexed
            val size = (width * 0.2f).toInt().coerceAtLeast(48)
            val scaled = Bitmap.createScaledBitmap(sticker, size, size, true)
            val left = (width * 0.1f + index * size * 0.5f).toFloat()
            val top = height * 0.15f
            canvas.drawBitmap(scaled, left, top, null)
            if (scaled !== sticker) scaled.recycle()
        }
        return bitmap
    }

    private fun uploadBitmapTexture(bitmap: Bitmap): Int {
        val textures = IntArray(1)
        GLES20.glGenTextures(1, textures, 0)
        val id = textures[0]
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, id)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0)
        return id
    }

    private fun alignEven(v: Int): Int = if (v % 2 == 0) v else v - 1

    /**
     * Simple textured full-screen quad with alpha for watermark overlays.
     */
    private class OverlayFilter {
        private var program = 0
        private var posHandle = -1
        private var texHandle = -1
        private var sampHandle = -1
        private lateinit var verts: FloatBuffer
        private lateinit var texCoords: FloatBuffer

        fun init() {
            val vs = """
                attribute vec4 aPosition;
                attribute vec2 aTexCoord;
                varying vec2 vTexCoord;
                void main() {
                    gl_Position = aPosition;
                    vTexCoord = aTexCoord;
                }
            """.trimIndent()
            val fs = """
                precision mediump float;
                varying vec2 vTexCoord;
                uniform sampler2D uTexture;
                void main() {
                    gl_FragColor = texture2D(uTexture, vTexCoord);
                }
            """.trimIndent()
            program = linkProgram(vs, fs)
            posHandle = GLES20.glGetAttribLocation(program, "aPosition")
            texHandle = GLES20.glGetAttribLocation(program, "aTexCoord")
            sampHandle = GLES20.glGetUniformLocation(program, "uTexture")
            verts = floatBuffer(
                floatArrayOf(
                    -1f, -1f,
                    1f, -1f,
                    -1f, 1f,
                    1f, 1f
                )
            )
            texCoords = floatBuffer(
                floatArrayOf(
                    0f, 1f,
                    1f, 1f,
                    0f, 0f,
                    1f, 0f
                )
            )
        }

        fun draw(textureId: Int) {
            GLES20.glUseProgram(program)
            GLES20.glVertexAttribPointer(posHandle, 2, GLES20.GL_FLOAT, false, 0, verts)
            GLES20.glEnableVertexAttribArray(posHandle)
            GLES20.glVertexAttribPointer(texHandle, 2, GLES20.GL_FLOAT, false, 0, texCoords)
            GLES20.glEnableVertexAttribArray(texHandle)
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId)
            GLES20.glUniform1i(sampHandle, 0)
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
            GLES20.glDisableVertexAttribArray(posHandle)
            GLES20.glDisableVertexAttribArray(texHandle)
        }

        fun release() {
            if (program != 0) {
                GLES20.glDeleteProgram(program)
                program = 0
            }
        }

        private fun linkProgram(vs: String, fs: String): Int {
            val v = compile(GLES20.GL_VERTEX_SHADER, vs)
            val f = compile(GLES20.GL_FRAGMENT_SHADER, fs)
            val p = GLES20.glCreateProgram()
            GLES20.glAttachShader(p, v)
            GLES20.glAttachShader(p, f)
            GLES20.glLinkProgram(p)
            GLES20.glDeleteShader(v)
            GLES20.glDeleteShader(f)
            return p
        }

        private fun compile(type: Int, src: String): Int {
            val s = GLES20.glCreateShader(type)
            GLES20.glShaderSource(s, src)
            GLES20.glCompileShader(s)
            return s
        }

        private fun floatBuffer(data: FloatArray): FloatBuffer =
            ByteBuffer.allocateDirect(data.size * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer()
                .apply {
                    put(data)
                    position(0)
                }
    }

    companion object {
        private const val TAG = "VideoEffectsExporter"
        private const val TIMEOUT_US = 10_000L
    }
}
