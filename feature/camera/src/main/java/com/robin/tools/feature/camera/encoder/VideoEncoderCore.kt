package com.robin.tools.feature.camera.encoder

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.view.Surface
import java.io.IOException

class EncoderConfig(
    val path: String,
    val width: Int,
    val height: Int,
    val bitRate: Int
)

class VideoEncoderCore(
    private val width: Int,
    private val height: Int,
    private val bitRate: Int,
    private val outputPath: String
) {
    private var videoEncoder: MediaCodec
    private var inputSurface: Surface
    private var muxer: MediaMuxer
    private var videoTrackIndex: Int = -1
    private var muxerStarted: Boolean = false
    private val bufferInfo = MediaCodec.BufferInfo()
    private val lock = Any()

    init {
        val format = MediaFormat.createVideoFormat(MIME_TYPE, width, height).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
            setInteger(MediaFormat.KEY_BIT_RATE, bitRate)
            setInteger(MediaFormat.KEY_FRAME_RATE, FRAME_RATE)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, I_FRAME_INTERVAL)
        }

        videoEncoder = MediaCodec.createEncoderByType(MIME_TYPE)
        videoEncoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        inputSurface = videoEncoder.createInputSurface()
        videoEncoder.start()

        muxer = MediaMuxer(outputPath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
    }

    fun getInputSurface(): Surface = inputSurface

    fun drainEncoder(endOfStream: Boolean) {
        if (endOfStream) {
            videoEncoder.signalEndOfInputStream()
        }

        while (true) {
            val encoderStatus = videoEncoder.dequeueOutputBuffer(bufferInfo, TIMEOUT_USEC)
            when {
                encoderStatus == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                    if (!endOfStream) break
                }
                encoderStatus == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    synchronized(lock) {
                        if (muxerStarted) throw RuntimeException("format changed twice")
                        val newFormat = videoEncoder.outputFormat
                        videoTrackIndex = muxer.addTrack(newFormat)
                        if (videoTrackIndex >= 0) {
                            muxer.start()
                            muxerStarted = true
                        }
                    }
                }
                encoderStatus < 0 -> {
                    // ignore unexpected result
                }
                else -> {
                    val encodedData = videoEncoder.getOutputBuffer(encoderStatus)
                        ?: throw RuntimeException("encoderOutputBuffer $encoderStatus was null")

                    if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) {
                        bufferInfo.size = 0
                    }

                    if (bufferInfo.size != 0 && muxerStarted) {
                        muxer.writeSampleData(videoTrackIndex, encodedData, bufferInfo)
                    }

                    videoEncoder.releaseOutputBuffer(encoderStatus, false)

                    if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                        break
                    }
                }
            }
        }
    }

    fun release() {
        videoEncoder.apply {
            stop()
            release()
        }
        muxer.apply {
            stop()
            release()
        }
    }

    companion object {
        private const val MIME_TYPE = "video/avc"
        private const val FRAME_RATE = 30
        private const val I_FRAME_INTERVAL = 1
        private const val TIMEOUT_USEC = 10000L
    }
}
