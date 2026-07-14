package com.robin.tools.feature.camera.ui.edit

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.robin.tools.feature.camera.editor.SubtitleCue
import com.robin.tools.feature.camera.editor.VideoClipper
import com.robin.tools.feature.camera.editor.VideoEffectsExporter
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
    val subtitles: List<SubtitleCue> = emptyList(),
    val isExporting: Boolean = false,
    val exportDone: Boolean = false,
    val error: String? = null
)

class VideoEditViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(VideoEditUiState())
    val uiState: StateFlow<VideoEditUiState> = _uiState.asStateFlow()
    private var mediaPlayer: MediaPlayer? = null
    private var videoPath: String = ""
    private var appContext: Context? = null
    private val videoClipper = VideoClipper()
    private val effectsExporter = VideoEffectsExporter()

    fun loadVideo(context: Context, path: String) {
        appContext = context.applicationContext
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

    fun setFilter(type: FilterType) {
        _uiState.update { it.copy(currentFilter = type) }
    }

    fun setWatermark(text: String) {
        _uiState.update { it.copy(watermarkText = text) }
    }

    fun addSticker(bitmap: Bitmap) {
        _uiState.update { it.copy(stickers = it.stickers + bitmap) }
    }

    fun addSubtitle(cue: SubtitleCue) {
        if (cue.text.isBlank() || cue.endMs <= cue.startMs) return
        _uiState.update { it.copy(subtitles = (it.subtitles + cue).sortedBy { c -> c.startMs }) }
    }

    fun clearSubtitles() {
        _uiState.update { it.copy(subtitles = emptyList()) }
    }

    fun importSrt(content: String) {
        val cues = parseSrt(content)
        if (cues.isEmpty()) {
            _uiState.update { it.copy(error = "No subtitles found in SRT") }
            return
        }
        _uiState.update { it.copy(subtitles = (it.subtitles + cues).sortedBy { c -> c.startMs }) }
    }

    private fun parseSrt(content: String): List<SubtitleCue> {
        val blocks = content.replace("\r\n", "\n").trim().split(Regex("\n\n+"))
        val out = mutableListOf<SubtitleCue>()
        val timeRe =
            Regex("""(\d{2}):(\d{2}):(\d{2})[,.](\d{3})\s*-->\s*(\d{2}):(\d{2}):(\d{2})[,.](\d{3})""")
        for (block in blocks) {
            val lines = block.lines().filter { it.isNotBlank() }
            if (lines.size < 2) continue
            val timeLine = lines.firstOrNull { timeRe.containsMatchIn(it) } ?: continue
            val m = timeRe.find(timeLine) ?: continue
            fun toMs(h: String, min: String, s: String, ms: String): Long =
                h.toLong() * 3_600_000 + min.toLong() * 60_000 + s.toLong() * 1000 + ms.toLong()
            val start = toMs(m.groupValues[1], m.groupValues[2], m.groupValues[3], m.groupValues[4])
            val end = toMs(m.groupValues[5], m.groupValues[6], m.groupValues[7], m.groupValues[8])
            val textStart = lines.indexOf(timeLine) + 1
            val text = lines.drop(textStart).joinToString(" ").trim()
            if (text.isNotBlank() && end > start) {
                out.add(SubtitleCue(start, end, text))
            }
        }
        return out
    }

    fun togglePlayback() {
        if (_uiState.value.isPlaying) pause() else play()
    }

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

    /**
     * Export with real filter / watermark burn-in when needed.
     * Passthrough remux (with audio) when no visual effects are applied.
     */
    fun export(outputFile: File, onComplete: (String) -> Unit) {
        val s = _uiState.value
        if (videoPath.isEmpty()) {
            _uiState.update { it.copy(error = "No video loaded") }
            return
        }
        val context = appContext
        if (context == null) {
            _uiState.update { it.copy(error = "Context unavailable") }
            return
        }

        _uiState.update { it.copy(isExporting = true, error = null) }
        viewModelScope.launch(Dispatchers.IO) {
            val needsEffects =
                s.currentFilter != FilterType.NONE ||
                    s.watermarkText.isNotBlank() ||
                    s.stickers.isNotEmpty() ||
                    s.subtitles.isNotEmpty()

            val ok = if (needsEffects) {
                effectsExporter.export(
                    context = context,
                    inputPath = videoPath,
                    outputPath = outputFile.absolutePath,
                    filterType = s.currentFilter,
                    watermarkText = s.watermarkText,
                    stickers = s.stickers,
                    subtitles = s.subtitles
                )
            } else {
                // Fast path: remux full range, keep audio
                videoClipper.clip(videoPath, outputFile.absolutePath, 0, s.durationMs.coerceAtLeast(1))
            }

            _uiState.update {
                if (ok) it.copy(isExporting = false, exportDone = true)
                else it.copy(isExporting = false, error = "Export failed")
            }
            if (ok) {
                launch(Dispatchers.Main) { onComplete(outputFile.absolutePath) }
            }
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
