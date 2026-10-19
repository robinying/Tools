package com.robin.tools.feature.media.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.robin.tools.feature.media.R
import com.robin.tools.feature.media.data.*
import com.robin.tools.feature.media.delegate.CompressionDelegateFactory
import com.robin.tools.feature.media.delegate.AudioConcatDelegate
import com.robin.tools.feature.media.delegate.AudioTrimDelegate
import com.robin.tools.feature.media.delegate.CustomSpeedDelegate
import com.robin.tools.feature.media.delegate.PipPosition
import com.robin.tools.feature.media.delegate.VideoBgmDelegate
import com.robin.tools.feature.media.delegate.VideoPipDelegate
import com.robin.tools.feature.media.delegate.ConcatDelegate
import com.robin.tools.feature.media.delegate.VideoCompressionDelegate
import kotlinx.coroutines.*

class CompressionService : Service() {

    companion object {
        private const val TAG = "CompressionService"
        const val CHANNEL_ID = "compression_channel"
        const val NOTIFICATION_ID = 1
        
        const val EXTRA_URIS = "extra_uris"
        /** Absolute filesystem paths; converted to file:// URIs when [EXTRA_URIS] is absent. */
        const val EXTRA_FILE_PATHS = "extra_file_paths"
        const val EXTRA_TYPE = "extra_type"
        const val EXTRA_LEVEL = "extra_level"
        const val EXTRA_TRIM_START_MS = "extra_trim_start_ms"
        const val EXTRA_TRIM_END_MS = "extra_trim_end_ms"
        const val EXTRA_VIDEO_MIN_DIMENSION = "extra_video_min_dimension"
        const val EXTRA_VIDEO_BITRATE_KBPS = "extra_video_bitrate_kbps"
        const val EXTRA_BGM_MIX = "extra_bgm_mix"
        const val EXTRA_PIP_POSITION = "extra_pip_position"
        const val EXTRA_SPEED_FACTOR = "extra_speed_factor"
        const val EXTRA_AUDIO_RINGTONE = "extra_audio_ringtone"

        /**
         * Start processing without going through the system multi-select UI.
         * Intended for instrumentation / debug harnesses (same-app only; service is not exported).
         */
        fun startWithFilePaths(
            context: Context,
            absolutePaths: List<String>,
            type: CompressionType,
            level: CompressionLevel = CompressionLevel.MEDIUM
        ) {
            val intent = Intent(context, CompressionService::class.java).apply {
                putStringArrayListExtra(EXTRA_FILE_PATHS, ArrayList(absolutePaths))
                putExtra(EXTRA_TYPE, type.name)
                putExtra(EXTRA_LEVEL, level.name)
            }
            androidx.core.content.ContextCompat.startForegroundService(context, intent)
        }

        fun startAudioConcat(context: Context, uris: List<Uri>) {
            val intent = Intent(context, CompressionService::class.java).apply {
                putParcelableArrayListExtra(EXTRA_URIS, ArrayList(uris))
                putExtra(EXTRA_TYPE, CompressionType.AUDIO_CONCAT.name)
                putExtra(EXTRA_LEVEL, CompressionLevel.MEDIUM.name)
            }
            androidx.core.content.ContextCompat.startForegroundService(context, intent)
        }

        fun startVideoBgm(
            context: Context,
            videoUri: Uri,
            audioUri: Uri,
            mixWithOriginal: Boolean
        ) {
            val intent = Intent(context, CompressionService::class.java).apply {
                putParcelableArrayListExtra(EXTRA_URIS, arrayListOf(videoUri, audioUri))
                putExtra(EXTRA_TYPE, CompressionType.VIDEO_BGM.name)
                putExtra(EXTRA_LEVEL, CompressionLevel.MEDIUM.name)
                putExtra(EXTRA_BGM_MIX, mixWithOriginal)
            }
            androidx.core.content.ContextCompat.startForegroundService(context, intent)
        }

        fun startVideoPip(
            context: Context,
            videoUri: Uri,
            overlayUri: Uri,
            position: String
        ) {
            val intent = Intent(context, CompressionService::class.java).apply {
                putParcelableArrayListExtra(EXTRA_URIS, arrayListOf(videoUri, overlayUri))
                putExtra(EXTRA_TYPE, CompressionType.VIDEO_PIP.name)
                putExtra(EXTRA_LEVEL, CompressionLevel.MEDIUM.name)
                putExtra(EXTRA_PIP_POSITION, position)
            }
            androidx.core.content.ContextCompat.startForegroundService(context, intent)
        }

        fun startCustomSpeed(context: Context, uri: Uri, speedFactor: Double) {
            val intent = Intent(context, CompressionService::class.java).apply {
                putParcelableArrayListExtra(EXTRA_URIS, arrayListOf(uri))
                putExtra(EXTRA_TYPE, CompressionType.VIDEO_SPEED_CUSTOM.name)
                putExtra(EXTRA_LEVEL, CompressionLevel.MEDIUM.name)
                putExtra(EXTRA_SPEED_FACTOR, speedFactor)
            }
            androidx.core.content.ContextCompat.startForegroundService(context, intent)
        }

        /** Starts a custom video compression job. */
        fun startCustomVideoCompression(
            context: Context,
            uri: Uri,
            minDimension: Int,
            bitrateKbps: Int
        ) {
            val intent = Intent(context, CompressionService::class.java).apply {
                putParcelableArrayListExtra(EXTRA_URIS, arrayListOf(uri))
                putExtra(EXTRA_TYPE, CompressionType.VIDEO_CUSTOM.name)
                putExtra(EXTRA_LEVEL, CompressionLevel.MEDIUM.name)
                putExtra(EXTRA_VIDEO_MIN_DIMENSION, minDimension)
                putExtra(EXTRA_VIDEO_BITRATE_KBPS, bitrateKbps)
            }
            androidx.core.content.ContextCompat.startForegroundService(context, intent)
        }

        /** Starts an audio trim without going through the generic compression screen. */
        fun startAudioTrim(
            context: Context,
            uri: Uri,
            startMs: Long,
            endMs: Long,
            asRingtone: Boolean = false
        ) {
            val intent = Intent(context, CompressionService::class.java).apply {
                putParcelableArrayListExtra(EXTRA_URIS, arrayListOf(uri))
                putExtra(EXTRA_TYPE, CompressionType.AUDIO_TRIM.name)
                putExtra(EXTRA_LEVEL, CompressionLevel.MEDIUM.name)
                putExtra(EXTRA_TRIM_START_MS, startMs)
                putExtra(EXTRA_TRIM_END_MS, endMs)
                putExtra(EXTRA_AUDIO_RINGTONE, asRingtone)
            }
            androidx.core.content.ContextCompat.startForegroundService(context, intent)
        }
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    @Volatile
    private var isRunning = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) return START_NOT_STICKY

        if (isRunning) {
            // 如果已有任务在运行，取消当前任务再启动新任务
            CompressionManager.cancelTask()
            serviceScope.coroutineContext.cancelChildren()
            // 使用 START_REDELIVER_INTENT 确保服务被系统杀死后能重新启动
        }

        if (!startForegroundService()) {
            stopSelf()
            return START_NOT_STICKY
        }

        val urisFromExtra = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableArrayListExtra(EXTRA_URIS, Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableArrayListExtra(EXTRA_URIS)
        }
        val pathList = intent.getStringArrayListExtra(EXTRA_FILE_PATHS).orEmpty()
        val uris = when {
            !urisFromExtra.isNullOrEmpty() -> urisFromExtra
            pathList.isNotEmpty() -> ArrayList(
                pathList.map { path -> Uri.fromFile(java.io.File(path)) }
            )
            else -> null
        }
        
        val typeName = intent.getStringExtra(EXTRA_TYPE)
        val levelName = intent.getStringExtra(EXTRA_LEVEL)

        if (uris.isNullOrEmpty() || typeName == null || levelName == null) {
            Log.e(TAG, "Missing uris/paths or type/level")
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        val type = CompressionType.valueOf(typeName)
        val level = CompressionLevel.valueOf(levelName)

        val trimStartMs = intent.getLongExtra(EXTRA_TRIM_START_MS, 0L)
        val trimEndMs = intent.getLongExtra(EXTRA_TRIM_END_MS, Long.MAX_VALUE)
        val videoMinDimension = intent.getIntExtra(EXTRA_VIDEO_MIN_DIMENSION, 0)
        val videoBitrateKbps = intent.getIntExtra(EXTRA_VIDEO_BITRATE_KBPS, 0)
        val bgmMix = intent.getBooleanExtra(EXTRA_BGM_MIX, true)
        val pipPosition = intent.getStringExtra(EXTRA_PIP_POSITION)
            ?.let { name -> PipPosition.entries.firstOrNull { it.name == name } }
            ?: PipPosition.TOP_RIGHT
        val speedFactor = intent.getDoubleExtra(EXTRA_SPEED_FACTOR, 1.0)
        val audioRingtone = intent.getBooleanExtra(EXTRA_AUDIO_RINGTONE, false)

        startCompression(
            uris = uris,
            type = type,
            level = level,
            trimStartMs = trimStartMs,
            trimEndMs = trimEndMs,
            videoMinDimension = videoMinDimension,
            videoBitrateKbps = videoBitrateKbps,
            bgmMix = bgmMix,
            pipPosition = pipPosition,
            speedFactor = speedFactor,
            audioRingtone = audioRingtone
        )

        return START_NOT_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        CompressionManager.cancelTask()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = getString(R.string.notification_channel_name)
            val descriptionText = getString(R.string.notification_channel_desc)
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun startForegroundService(): Boolean {
        val notification = createNotification(0, 100, getString(R.string.notification_ready))
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                 startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROCESSING)
            } else {
                 startForeground(NOTIFICATION_ID, notification)
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start foreground service", e)
            false
        }
    }

    private fun createNotification(progress: Int, max: Int, content: String): Notification {
        val intent = packageManager.getLaunchIntentForPackage(packageName) ?: Intent()
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(content)
            .setProgress(max, progress, false)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
    }

    private fun updateNotification(progress: Int, max: Int, content: String) {
        val notification = createNotification(progress, max, content)
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private fun startCompression(
        uris: List<Uri>,
        type: CompressionType,
        level: CompressionLevel,
        trimStartMs: Long,
        trimEndMs: Long,
        videoMinDimension: Int,
        videoBitrateKbps: Int,
        bgmMix: Boolean,
        pipPosition: PipPosition,
        speedFactor: Double,
        audioRingtone: Boolean
    ) {
        isRunning = true
        CompressionManager.startTask(type)
        
        serviceScope.launch {
            var successCount = 0
            var failCount = 0
            val isSingleJob = type == CompressionType.CONCAT ||
                type == CompressionType.AUDIO_CONCAT ||
                type == CompressionType.VIDEO_BGM ||
                type == CompressionType.VIDEO_PIP
            val total = if (isSingleJob) 1 else uris.size
            var lastOutputUri = ""
            var lastErrorMsg = ""

            if (isSingleJob) {
                val onProgress: (Float, String) -> Unit = { p, msg ->
                    val statusMsg = getString(R.string.processing, 1, 1, msg)
                    updateNotification((p * 100).toInt(), 100, statusMsg)
                    CompressionManager.updateState(
                        CompressionTaskState.Processing(p, statusMsg, 1, 1)
                    )
                }
                val result = try {
                    when (type) {
                        CompressionType.CONCAT ->
                            ConcatDelegate().processAll(this@CompressionService, uris, level, onProgress)
                        CompressionType.AUDIO_CONCAT ->
                            AudioConcatDelegate().processAll(this@CompressionService, uris, level, onProgress)
                        CompressionType.VIDEO_BGM ->
                            if (uris.size >= 2) {
                                VideoBgmDelegate(bgmMix).processPair(
                                    this@CompressionService, uris[0], uris[1], onProgress
                                )
                            } else {
                                Result.failure(Exception("Select a video and an audio file"))
                            }
                        CompressionType.VIDEO_PIP ->
                            if (uris.size >= 2) {
                                VideoPipDelegate(pipPosition).processPair(
                                    this@CompressionService, uris[0], uris[1], onProgress
                                )
                            } else {
                                Result.failure(Exception("Select a video and an overlay video"))
                            }
                        else -> Result.failure(Exception("Unsupported job"))
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Multi-input job failed", e)
                    Result.failure(e)
                }
                if (result.isSuccess) {
                    successCount = 1
                    lastOutputUri = result.getOrThrow()
                } else {
                    failCount = 1
                    lastErrorMsg = result.exceptionOrNull()?.message
                        ?: getString(R.string.service_unknown_error)
                }
            } else {
                for ((index, uri) in uris.withIndex()) {
                    if (CompressionManager.isCancelled()) break

                    val currentFileNum = index + 1

                    val onProgress: (Float, String) -> Unit = { p, msg ->
                        val globalProgress = (index.toFloat() + p) / total.toFloat()
                        val statusMsg = getString(R.string.processing, currentFileNum, total, msg)

                        updateNotification((globalProgress * 100).toInt(), 100, statusMsg)
                        CompressionManager.updateState(
                            CompressionTaskState.Processing(
                                globalProgress,
                                statusMsg,
                                currentFileNum,
                                total
                            )
                        )
                    }

                    val delegate = when {
                        type == CompressionType.AUDIO_TRIM ->
                            AudioTrimDelegate(trimStartMs, trimEndMs, audioRingtone)
                        type == CompressionType.VIDEO_CUSTOM && videoMinDimension > 0 ->
                            VideoCompressionDelegate(videoMinDimension, videoBitrateKbps)
                        type == CompressionType.VIDEO_SPEED_CUSTOM ->
                            CustomSpeedDelegate(speedFactor)
                        else -> CompressionDelegateFactory.create(type)
                    }
                    val result = try {
                        delegate.process(this@CompressionService, uri, level, onProgress)
                    } catch (e: Exception) {
                        Log.e(TAG, "Unexpected error processing file $currentFileNum", e)
                        Result.failure(e)
                    }

                    if (result.isSuccess) {
                        successCount++
                        lastOutputUri = result.getOrThrow()
                    } else {
                        failCount++
                        lastErrorMsg = result.exceptionOrNull()?.message
                            ?: getString(R.string.service_unknown_error)
                    }
                }
            }

            val finalMessage = if (CompressionManager.isCancelled()) {
                getString(R.string.service_cancelled)
            } else if (failCount == 0) {
                getString(R.string.notification_finished_all)
            } else if (total == 1) {
                getString(R.string.notification_failed_single, lastErrorMsg)
            } else {
                getString(R.string.notification_finished_with_errors, successCount, failCount)
            }
            
            CompressionManager.updateState(CompressionTaskState.Finished(
                isSuccess = successCount > 0 && !CompressionManager.isCancelled(),
                message = finalMessage,
                outputUri = if (total == 1) lastOutputUri else null
            ))

            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            isRunning = false
        }
    }
}
