package com.robin.tools.feature.camera.encoder

import android.opengl.EGLContext
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.view.Surface
import com.robin.tools.feature.camera.opengl.EglCore
import com.robin.tools.feature.camera.opengl.WindowSurface

class TextureMovieEncoder {
    private var videoEncoder: VideoEncoderCore? = null
    private var eglCore: EglCore? = null
    private var windowSurface: WindowSurface? = null
    private var encoderThread: HandlerThread? = null
    private var encoderHandler: Handler? = null
    private var baseTimeStamp: Long = -1
    private var pauseDelayTime: Long = 0
    private var oncePauseTime: Long = 0
    @Volatile var isRecording: Boolean = false
        private set

    fun startRecording(config: EncoderConfig, sharedContext: EGLContext) {
        encoderThread = HandlerThread("TextureMovieEncoder").apply { start() }
        encoderHandler = Handler(encoderThread!!.looper)

        encoderHandler!!.post {
            try {
                videoEncoder = VideoEncoderCore(config.width, config.height, config.bitRate, config.path)
                eglCore = EglCore(sharedContext, EglCore.FLAG_RECORDABLE)
                val inputSurface = videoEncoder!!.getInputSurface()
                windowSurface = WindowSurface(eglCore!!, inputSurface, true)
                windowSurface!!.makeCurrent()
                baseTimeStamp = -1
                isRecording = true
            } catch (e: Exception) {
                Log.e(TAG, "startRecording failed", e)
            }
        }
    }

    fun stopRecording() {
        encoderHandler?.post {
            isRecording = false
            videoEncoder?.drainEncoder(true)
            videoEncoder?.release()
            videoEncoder = null
            windowSurface?.release()
            windowSurface = null
            eglCore?.release()
            eglCore = null
            encoderThread?.quitSafely()
            encoderThread = null
        }
    }

    fun pauseRecording() {
        encoderHandler?.post {
            oncePauseTime = System.nanoTime()
        }
    }

    fun resumeRecording() {
        encoderHandler?.post {
            oncePauseTime = System.nanoTime() - oncePauseTime
            pauseDelayTime += oncePauseTime
        }
    }

    fun frameAvailable(textureId: Int, timestampNanos: Long) {
        encoderHandler?.post {
            if (!isRecording) return@post
            videoEncoder?.drainEncoder(false)
            if (baseTimeStamp == -1L) {
                baseTimeStamp = System.nanoTime()
            }
            val time = System.nanoTime() - baseTimeStamp - pauseDelayTime
            windowSurface?.setPresentationTime(time)
            windowSurface?.swapBuffers()
        }
    }

    companion object {
        private const val TAG = "TextureMovieEncoder"
    }
}
