package com.robin.tools.feature.camera.ui.trim

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.robin.tools.feature.camera.editor.VideoClipper
import com.robin.tools.feature.camera.storage.VideoPathResolver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

data class TrimUiState(
    val durationMs: Long = 0,
    val startMs: Long = 0,
    val endMs: Long = 0,
    val currentPositionMs: Long = 0,
    val isPlaying: Boolean = false,
    val rotation: Int = 0,
    val thumbnails: List<Bitmap> = emptyList(),
    val isExporting: Boolean = false,
    val exportDone: Boolean = false,
    val error: String? = null
)

class TrimViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(TrimUiState())
    val uiState: StateFlow<TrimUiState> = _uiState.asStateFlow()
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
                val thumbs = mutableListOf<Bitmap>()
                val interval = (dur / 10).coerceAtLeast(1)
                for (i in 0 until 10) {
                    val frame = retriever.getFrameAtTime(interval * i * 1000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                    if (frame != null) {
                        thumbs.add(Bitmap.createScaledBitmap(frame, 120, 68, false))
                        if (frame != thumbs.last()) frame.recycle()
                    }
                }
                val oldThumbs = _uiState.value.thumbnails
                _uiState.update { it.copy(durationMs = dur, endMs = dur, thumbnails = thumbs, error = null) }
                recycleBitmaps(oldThumbs)
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

    fun setStartTime(ms: Long) {
        val clamped = ms.coerceIn(0, _uiState.value.endMs - 1000)
        _uiState.update { it.copy(startMs = clamped) }
        if (!_uiState.value.isPlaying) seekTo(clamped)
    }

    fun setEndTime(ms: Long) {
        val clamped = ms.coerceIn(_uiState.value.startMs + 1000, _uiState.value.durationMs)
        _uiState.update { it.copy(endMs = clamped) }
    }

    fun setRotation(deg: Int) { _uiState.update { it.copy(rotation = deg % 360) } }

    fun togglePlayback() { if (_uiState.value.isPlaying) pause() else play() }

    fun play() {
        val p = mediaPlayer ?: return
        p.seekTo(_uiState.value.startMs.toInt())
        p.start()
        _uiState.update { it.copy(isPlaying = true) }
        viewModelScope.launch(Dispatchers.IO) {
            while (_uiState.value.isPlaying) {
                val pos = mediaPlayer?.currentPosition?.toLong() ?: break
                _uiState.update { it.copy(currentPositionMs = pos) }
                if (pos >= _uiState.value.endMs) {
                    launch(Dispatchers.Main) { pause() }
                    break
                }
                kotlinx.coroutines.delay(100)
            }
        }
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
            setOnPreparedListener { mp ->
                mp.seekTo(_uiState.value.startMs.toInt())
                _uiState.update { it.copy(currentPositionMs = _uiState.value.startMs) }
            }
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
            val ok = videoClipper.clip(videoPath, outputFile.absolutePath, s.startMs, s.endMs)
            _uiState.update {
                if (ok) it.copy(isExporting = false, exportDone = true)
                else it.copy(isExporting = false, error = "Export failed")
            }
            if (ok) launch(Dispatchers.Main) { onComplete(outputFile.absolutePath) }
        }
    }

    override fun onCleared() {
        mediaPlayer?.release()
        recycleBitmaps(_uiState.value.thumbnails)
        super.onCleared()
    }

    private fun recycleBitmaps(bitmaps: List<Bitmap>) {
        bitmaps.forEach { bmp ->
            if (!bmp.isRecycled) bmp.recycle()
        }
    }
}
