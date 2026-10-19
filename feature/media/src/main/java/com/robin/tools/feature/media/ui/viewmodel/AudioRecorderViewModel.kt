package com.robin.tools.feature.media.ui.viewmodel

import android.app.Application
import android.media.MediaRecorder
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.robin.tools.core.media.MediaStoreSaver
import com.robin.tools.core.media.SavedMedia
import com.robin.tools.feature.media.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class AudioRecorderViewModel(application: Application) : AndroidViewModel(application) {

    data class UiState(
        val isRecording: Boolean = false,
        val elapsedMs: Long = 0L,
        val amplitudes: List<Int> = emptyList(),
        val saved: SavedMedia? = null,
        val isSaving: Boolean = false,
        val message: String? = null
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val context get() = getApplication<Application>()
    private var recorder: MediaRecorder? = null
    private var outputFile: File? = null
    private var pollJob: Job? = null
    private var startedAt = 0L

    fun start() {
        if (_uiState.value.isRecording) return
        val file = File(context.cacheDir, "recording_${System.currentTimeMillis()}.m4a")
        val mediaRecorder = createRecorder()
        try {
            mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC)
            mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            mediaRecorder.setAudioSamplingRate(44_100)
            mediaRecorder.setAudioEncodingBitRate(128_000)
            mediaRecorder.setOutputFile(file.absolutePath)
            mediaRecorder.prepare()
            mediaRecorder.start()
        } catch (e: Exception) {
            mediaRecorder.release()
            file.delete()
            _uiState.update {
                it.copy(message = context.getString(R.string.audio_recorder_failed, e.message))
            }
            return
        }
        recorder = mediaRecorder
        outputFile = file
        startedAt = System.currentTimeMillis()
        _uiState.update {
            it.copy(
                isRecording = true,
                elapsedMs = 0L,
                amplitudes = emptyList(),
                saved = null,
                message = null
            )
        }
        pollJob = viewModelScope.launch {
            while (isActive) {
                val amplitude = runCatching { mediaRecorder.maxAmplitude }.getOrDefault(0)
                val elapsed = System.currentTimeMillis() - startedAt
                _uiState.update {
                    it.copy(
                        elapsedMs = elapsed,
                        amplitudes = (it.amplitudes + amplitude).takeLast(MAX_SAMPLES)
                    )
                }
                delay(100)
            }
        }
    }

    fun stopAndSave() {
        val mediaRecorder = recorder ?: return
        val file = outputFile
        pollJob?.cancel()
        pollJob = null
        recorder = null
        outputFile = null
        var stopped = false
        try {
            mediaRecorder.stop()
            stopped = true
        } catch (e: Exception) {
            // Too-short recordings throw from stop().
        } finally {
            runCatching { mediaRecorder.release() }
        }
        _uiState.update { it.copy(isRecording = false, isSaving = true) }
        if (!stopped || file == null || !file.exists() || file.length() == 0L) {
            file?.delete()
            _uiState.update {
                it.copy(
                    isSaving = false,
                    message = context.getString(
                        R.string.audio_recorder_failed,
                        "Recording was too short"
                    )
                )
            }
            return
        }
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                MediaStoreSaver.saveAudio(
                    context = context,
                    source = file,
                    mimeType = "audio/mp4",
                    fileName = "recording_${System.currentTimeMillis()}.m4a"
                )
            }
            file.delete()
            result.fold(
                onSuccess = { saved ->
                    _uiState.update {
                        it.copy(
                            isSaving = false,
                            saved = saved,
                            message = context.getString(R.string.audio_recorder_saved)
                        )
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            isSaving = false,
                            message = context.getString(
                                R.string.audio_recorder_failed,
                                error.message ?: error.javaClass.simpleName
                            )
                        )
                    }
                }
            )
        }
    }

    fun clearMessage() {
        _uiState.update { it.copy(message = null) }
    }

    override fun onCleared() {
        pollJob?.cancel()
        recorder?.let { runCatching { it.release() } }
        outputFile?.delete()
        recorder = null
        outputFile = null
        super.onCleared()
    }

    @Suppress("DEPRECATION")
    private fun createRecorder(): MediaRecorder =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            MediaRecorder()
        }

    class Factory(private val application: Application) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            AudioRecorderViewModel(application) as T
    }

    private companion object {
        const val MAX_SAMPLES = 120
    }
}
