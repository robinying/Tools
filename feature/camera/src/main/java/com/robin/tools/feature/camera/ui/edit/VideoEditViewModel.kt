package com.robin.tools.feature.camera.ui.edit

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.robin.tools.feature.camera.editor.VideoClipper
import com.robin.tools.feature.camera.filter.FilterType
import com.robin.tools.feature.camera.storage.VideoPathResolver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

data class VideoEditUiState(
    val durationMs: Long = 0,
    val currentPositionMs: Long = 0,
    val isPlaying: Boolean = false,
    val currentFilter: FilterType = FilterType.NONE,
    val watermarkText: String = "",
    val stickers: List<Bitmap> = emptyList(),
    val isExporting: Boolean = false,
    val exportDone: Boolean = false,
    val error: String? = null
)

class VideoEditViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(VideoEditUiState())
    val uiState: StateFlow<VideoEditUiState> = _uiState.asStateFlow()
    private var mediaPlayer: MediaPlayer? = null
    private var videoPath: String = ""
    private val videoClipper = VideoClipper()

    fun loadVideo(context: Context, path: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val retriever = MediaMetadataRetriever()
            try {
                videoPath = VideoPathResolver.resolve(context, path)
                retriever.setDataSource(videoPath)
                val dur = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
                _uiState.update { it.copy(durationMs = dur, error = null) }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "Failed to load: ${e.message}") }
            } finally {
                try {
                    retriever.release()
                } catch (_: Exception) {
                }
            }
        }
    }

    fun setFilter(type: FilterType) { _uiState.update { it.copy(currentFilter = type) } }
    fun setWatermark(text: String) { _uiState.update { it.copy(watermarkText = text) } }
    fun addSticker(bitmap: Bitmap) {
        _uiState.update { it.copy(stickers = it.stickers + bitmap) }
    }

    fun togglePlayback() { if (_uiState.value.isPlaying) pause() else play() }
    fun play() {
        mediaPlayer?.start()
        _uiState.update { it.copy(isPlaying = true) }
    }
    fun pause() {
        mediaPlayer?.pause()
        _uiState.update { it.copy(isPlaying = false) }
    }
    fun seekTo(ms: Long) {
        mediaPlayer?.seekTo(ms.toInt())
        _uiState.update { it.copy(currentPositionMs = ms) }
    }

    fun setupPlayer(context: Context, surface: android.view.Surface) {
        if (videoPath.isEmpty()) return
        mediaPlayer?.release()
        mediaPlayer = MediaPlayer().apply {
            setDataSource(videoPath)
            setSurface(surface)
            setOnCompletionListener { _uiState.update { it.copy(isPlaying = false) } }
            prepareAsync()
        }
    }

    fun export(outputFile: File, onComplete: (String) -> Unit) {
        val s = _uiState.value
        if (videoPath.isEmpty()) {
            _uiState.update { it.copy(error = "No video loaded") }
            return
        }
        _uiState.update { it.copy(isExporting = true) }
        viewModelScope.launch(Dispatchers.IO) {
            val ok = videoClipper.clip(videoPath, outputFile.absolutePath, 0, s.durationMs)
            _uiState.update {
                if (ok) it.copy(isExporting = false, exportDone = true)
                else it.copy(isExporting = false, error = "Export failed")
            }
            if (ok) launch(Dispatchers.Main) { onComplete(outputFile.absolutePath) }
        }
    }

    override fun onCleared() {
        mediaPlayer?.release()
        _uiState.value.stickers.forEach { bmp ->
            if (!bmp.isRecycled) bmp.recycle()
        }
        super.onCleared()
    }
}
