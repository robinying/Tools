package com.robin.tools.feature.media.ui.viewmodel

import android.app.Application
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.robin.tools.core.media.MediaStoreSaver
import com.robin.tools.core.media.SavedMedia
import com.robin.tools.feature.media.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class FrameExtractViewModel(application: Application) : AndroidViewModel(application) {

    data class UiState(
        val sourceUri: Uri? = null,
        val frameCount: Int = 10,
        val isExtracting: Boolean = false,
        val progress: Float = 0f,
        val saved: List<SavedMedia> = emptyList(),
        val message: String? = null
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val context get() = getApplication<Application>()

    fun selectVideo(uri: Uri) {
        _uiState.update { it.copy(sourceUri = uri, saved = emptyList(), message = null) }
    }

    fun setFrameCount(count: Int) {
        if (_uiState.value.isExtracting) return
        _uiState.update { it.copy(frameCount = count, saved = emptyList()) }
    }

    fun extract() {
        val uri = _uiState.value.sourceUri ?: return
        if (_uiState.value.isExtracting) return
        val count = _uiState.value.frameCount
        viewModelScope.launch {
            _uiState.update {
                it.copy(isExtracting = true, progress = 0f, saved = emptyList(), message = null)
            }
            val result = withContext(Dispatchers.IO) { extractFrames(uri, count) }
            result.fold(
                onSuccess = { saved ->
                    _uiState.update {
                        it.copy(
                            isExtracting = false,
                            progress = 1f,
                            saved = saved,
                            message = context.getString(R.string.frame_extract_saved, saved.size)
                        )
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            isExtracting = false,
                            message = context.getString(
                                R.string.frame_extract_failed,
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

    private fun extractFrames(uri: Uri, count: Int): Result<List<SavedMedia>> {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            val durationMs = retriever
                .extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull() ?: 0L
            if (durationMs <= 0L) error("Cannot read video duration")

            val saved = mutableListOf<SavedMedia>()
            for (index in 0 until count) {
                val positionMs = durationMs * index / count
                val frame = retriever.getFrameAtTime(
                    positionMs * 1000L,
                    MediaMetadataRetriever.OPTION_CLOSEST_SYNC
                ) ?: retriever.getFrameAtTime(
                    positionMs * 1000L,
                    MediaMetadataRetriever.OPTION_CLOSEST
                ) ?: continue
                try {
                    val fileName = "frame_${System.currentTimeMillis()}_${index + 1}.jpg"
                    val media = MediaStoreSaver.saveImage(
                        context = context,
                        bitmap = frame,
                        format = Bitmap.CompressFormat.JPEG,
                        mimeType = "image/jpeg",
                        fileName = fileName,
                        quality = 92
                    ).getOrThrow()
                    saved += media
                } finally {
                    frame.recycle()
                }
                _uiState.update { it.copy(progress = (index + 1f) / count) }
            }
            Result.success(saved)
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            runCatching { retriever.release() }
        }
    }

    class Factory(private val application: Application) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            FrameExtractViewModel(application) as T
    }
}
