package com.robin.tools.feature.media.ui.viewmodel

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.robin.tools.feature.media.data.*
import com.robin.tools.feature.media.service.CompressionService
import kotlinx.coroutines.flow.StateFlow
import java.util.ArrayList

class CompressionViewModel(application: Application) : AndroidViewModel(application) {

    val taskState: StateFlow<CompressionTaskState> = CompressionManager.taskState

    fun startCompression(uris: List<Uri>, type: CompressionType, level: CompressionLevel) {
        val context = getApplication<Application>().applicationContext
        val intent = Intent(context, CompressionService::class.java).apply {
            putParcelableArrayListExtra(CompressionService.EXTRA_URIS, ArrayList(uris))
            putExtra(CompressionService.EXTRA_TYPE, type.name)
            putExtra(CompressionService.EXTRA_LEVEL, level.name)
        }
        ContextCompat.startForegroundService(context, intent)
    }

    fun cancelCompression() {
        CompressionManager.cancelTask()
    }

    fun resetState() {
        CompressionManager.reset()
    }
}
