package com.robin.tools.feature.camera.ui.cover

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream

data class CoverUiState(
    val durationMs: Long = 0,
    val thumbnails: List<Pair<Long, Bitmap>> = emptyList(),
    val selectedTimeMs: Long = 0,
    val selectedBitmap: Bitmap? = null,
    val saved: Boolean = false,
    val error: String? = null
)

class CoverViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(CoverUiState())
    val uiState: StateFlow<CoverUiState> = _uiState.asStateFlow()
    private var videoPath: String = ""

    fun loadVideo(context: Context, path: String) {
        videoPath = path
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val retriever = MediaMetadataRetriever()
                retriever.setDataSource(context, Uri.parse(path))
                val dur = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L

                val interval = (dur / 12).coerceAtLeast(500)
                val frames = mutableListOf<Pair<Long, Bitmap>>()
                var t = 0L
                while (t < dur) {
                    val frame = retriever.getFrameAtTime(t * 1000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                    if (frame != null) {
                        frames.add(t to Bitmap.createScaledBitmap(frame, 160, 90, false))
                        if (frame !== frames.last().second) frame.recycle()
                    }
                    t += interval
                }
                retriever.release()
                _uiState.update { it.copy(durationMs = dur, thumbnails = frames) }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "Failed to load: ${e.message}") }
            }
        }
    }

    fun selectFrame(timeMs: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val retriever = MediaMetadataRetriever()
                retriever.setDataSource(videoPath)
                val fullFrame = retriever.getFrameAtTime(timeMs * 1000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                retriever.release()
                _uiState.update { it.copy(selectedTimeMs = timeMs, selectedBitmap = fullFrame) }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "Failed to get frame: ${e.message}") }
            }
        }
    }

    fun saveCover(context: Context, onSaved: (String) -> Unit) {
        val bmp = _uiState.value.selectedBitmap ?: return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val cacheDir = File(context.cacheDir, "camera")
                cacheDir.mkdirs()
                val file = File(cacheDir, "cover_${System.currentTimeMillis()}.jpg")
                FileOutputStream(file).use { out ->
                    bmp.compress(Bitmap.CompressFormat.JPEG, 90, out)
                }
                val saved = file.exists() && file.length() > 0
                _uiState.update { it.copy(saved = saved, error = if (!saved) "Save failed" else null) }
                if (saved) launch(Dispatchers.Main) { onSaved(file.absolutePath) }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "Save failed: ${e.message}") }
            }
        }
    }
}
