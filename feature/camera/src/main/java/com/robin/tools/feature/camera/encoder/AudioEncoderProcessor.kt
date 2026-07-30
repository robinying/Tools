package com.robin.tools.feature.camera.encoder

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.media.MediaRecorder
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import java.nio.ByteBuffer

class AudioEncoderProcessor(
    private val muxer: MediaMuxer,
    private val onTrackAdded: (Int) -> Unit,
    private val onMuxerReady: () -> Boolean
) {
    private val audioMime = "audio/mp4a-latm"
    private var audioEnc: MediaCodec
    private var audioRecord: AudioRecord?
    private val sampleRate = 48000
    private val channelCount = 2
    private val audioRate = 128000
    private val bufferSize: Int
    private var audioTrackIndex: Int = -1
    private var isRecording: Boolean = false
    private var baseTimeStamp: Long = -1
    private var pauseDelayTime: Long = 0
    private var oncePauseTime: Long = 0
    private var pausing: Boolean = false

    private val handlerThread = HandlerThread("AudioEncoder")
    private lateinit var handler: Handler

    init {
        val audioFormat = MediaFormat.createAudioFormat(audioMime, sampleRate, channelCount).apply {
            setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            setInteger(MediaFormat.KEY_BIT_RATE, audioRate)
        }
        audioEnc = MediaCodec.createEncoderByType(audioMime)
        audioEnc.configure(audioFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        audioEnc.start()

        val channelConfig = AudioFormat.CHANNEL_IN_STEREO
        val pcmFormat = AudioFormat.ENCODING_PCM_16BIT
        bufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, pcmFormat) * 2
        audioRecord = createAudioRecord(channelConfig, pcmFormat)
    }

    fun start(externalHandler: Handler) {
        handlerThread.start()
        handler = Handler(handlerThread.looper)
        isRecording = true
        handler.post { startRecord() }
    }

    private fun startRecord() {
        baseTimeStamp = System.nanoTime()
        handler.post(encodeLoop)
    }

    private val encodeLoop = object : Runnable {
        override fun run() {
            if (!isRecording && drainAllAudio()) {
                return
            }
            if (!pausing) {
                encodeAudioStep()
            }
            if (isRecording || !drainAllAudio()) {
                handler.post(this)
            }
        }
    }

    private fun encodeAudioStep() {
        val index = audioEnc.dequeueInputBuffer(0)
        if (index >= 0) {
            val buffer = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                audioEnc.getInputBuffer(index)
            } else {
                @Suppress("DEPRECATION")
                audioEnc.getInputBuffers()[index]
            }
            buffer?.clear()
            val length = audioRecord?.read(buffer!!, bufferSize) ?: 0
            if (length > 0) {
                val time = if (baseTimeStamp != -1L) {
                    (System.nanoTime() - baseTimeStamp - pauseDelayTime) / 1000
                } else 0L
                audioEnc.queueInputBuffer(index, 0, length, time,
                    if (isRecording) 0 else MediaCodec.BUFFER_FLAG_END_OF_STREAM)
            }
        }

        val info = MediaCodec.BufferInfo()
        var outIndex = audioEnc.dequeueOutputBuffer(info, 0)
        while (outIndex >= 0) {
            if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                audioEnc.releaseOutputBuffer(outIndex, false)
                return
            }
            val buffer = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                audioEnc.getOutputBuffer(outIndex)
            } else {
                @Suppress("DEPRECATION")
                audioEnc.getOutputBuffers()[outIndex]
            }
            buffer?.position(info.offset)
            if (onMuxerReady() && info.presentationTimeUs > 0 && buffer != null) {
                try {
                    muxer.writeSampleData(audioTrackIndex, buffer, info)
                } catch (e: Exception) {
                    Log.e(TAG, "writeSampleData failed", e)
                }
            }
            audioEnc.releaseOutputBuffer(outIndex, false)
            outIndex = audioEnc.dequeueOutputBuffer(info, 0)
        }
    }

    private fun drainAllAudio(): Boolean {
        val info = MediaCodec.BufferInfo()
        var outIndex = audioEnc.dequeueOutputBuffer(info, 0)
        while (outIndex >= 0) {
            if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                audioEnc.releaseOutputBuffer(outIndex, false)
                return true
            }
            audioEnc.releaseOutputBuffer(outIndex, false)
            outIndex = audioEnc.dequeueOutputBuffer(info, 0)
        }
        return false
    }

    fun stop() {
        isRecording = false
    }

    fun pause() {
        pausing = true
        oncePauseTime = System.nanoTime()
    }

    fun resume() {
        oncePauseTime = System.nanoTime() - oncePauseTime
        pauseDelayTime += oncePauseTime
        pausing = false
    }

    fun release() {
        audioRecord?.let { recorder ->
            try {
                recorder.stop()
            } catch (exception: IllegalStateException) {
                Log.w(TAG, "Audio recorder was not started", exception)
            }
            recorder.release()
        }
        audioRecord = null
        audioEnc.apply {
            stop()
            release()
        }
        handlerThread.quitSafely()
    }

    private fun createAudioRecord(channelConfig: Int, pcmFormat: Int): AudioRecord? {
        return try {
            AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                channelConfig,
                pcmFormat,
                bufferSize
            ).also { recorder ->
                recorder.startRecording()
            }
        } catch (exception: SecurityException) {
            Log.e(TAG, "Microphone permission is not granted", exception)
            null
        }
    }

    companion object {
        private const val TAG = "AudioEncoderProcessor"
    }
}
