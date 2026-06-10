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
import kotlinx.coroutines.*

class CompressionService : Service() {

    companion object {
        private const val TAG = "CompressionService"
        const val CHANNEL_ID = "compression_channel"
        const val NOTIFICATION_ID = 1
        
        const val EXTRA_URIS = "extra_uris"
        const val EXTRA_TYPE = "extra_type"
        const val EXTRA_LEVEL = "extra_level"
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
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

        startForegroundService()

        val uris = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableArrayListExtra(EXTRA_URIS, Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableArrayListExtra(EXTRA_URIS)
        }
        
        val typeName = intent.getStringExtra(EXTRA_TYPE)
        val levelName = intent.getStringExtra(EXTRA_LEVEL)

        if (uris.isNullOrEmpty() || typeName == null || levelName == null) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        val type = CompressionType.valueOf(typeName)
        val level = CompressionLevel.valueOf(levelName)

        startCompression(uris, type, level)

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

    private fun startForegroundService() {
        val notification = createNotification(0, 100, getString(R.string.notification_ready))
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                 startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROCESSING)
            } else {
                 startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start foreground service", e)
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

    private fun startCompression(uris: List<Uri>, type: CompressionType, level: CompressionLevel) {
        isRunning = true
        CompressionManager.startTask()
        
        serviceScope.launch {
            var successCount = 0
            var failCount = 0
            val total = uris.size
            var lastOutputUri = ""
            var lastErrorMsg = ""

            for ((index, uri) in uris.withIndex()) {
                if (CompressionManager.isCancelled()) break

                val currentFileNum = index + 1
                
                val onProgress: (Float, String) -> Unit = { p, msg ->
                    val globalProgress = (index.toFloat() + p) / total.toFloat()
                    val statusMsg = getString(R.string.processing, currentFileNum, total, msg)
                    
                    updateNotification((globalProgress * 100).toInt(), 100, statusMsg)
                    CompressionManager.updateState(CompressionTaskState.Processing(globalProgress, statusMsg, currentFileNum, total))
                }

                val delegate = CompressionDelegateFactory.create(type)
                val result = delegate.process(this@CompressionService, uri, level, onProgress)

                if (result.isSuccess) {
                    successCount++
                    lastOutputUri = result.getOrThrow()
                } else {
                    failCount++
                    lastErrorMsg = result.exceptionOrNull()?.message ?: getString(R.string.service_unknown_error)
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
