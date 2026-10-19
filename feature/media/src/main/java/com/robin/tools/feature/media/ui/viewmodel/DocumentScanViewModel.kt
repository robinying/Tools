package com.robin.tools.feature.media.ui.viewmodel

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.robin.tools.core.media.MediaStoreSaver
import com.robin.tools.core.media.SavedMedia
import com.robin.tools.feature.media.R
import com.robin.tools.feature.media.scanner.DocumentScanProcessor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class DocumentScanViewModel(application: Application) : AndroidViewModel(application) {

    data class UiState(
        val sourceUri: Uri? = null,
        val isBusy: Boolean = false,
        val result: Bitmap? = null,
        val pageDetected: Boolean = false,
        val saved: SavedMedia? = null,
        val message: String? = null
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val context get() = getApplication<Application>()

    fun selectImage(uri: Uri) {
        recycleResult()
        _uiState.update {
            it.copy(sourceUri = uri, result = null, saved = null, message = null)
        }
    }

    fun scan() {
        val uri = _uiState.value.sourceUri ?: return
        if (_uiState.value.isBusy) return
        viewModelScope.launch {
            recycleResult()
            _uiState.update {
                it.copy(isBusy = true, result = null, saved = null, message = null)
            }
            DocumentScanProcessor.scan(context, uri).fold(
                onSuccess = { result ->
                    _uiState.update {
                        it.copy(
                            isBusy = false,
                            result = result.bitmap,
                            pageDetected = result.pageDetected,
                            message = context.getString(
                                if (result.pageDetected) {
                                    R.string.doc_scan_detected
                                } else {
                                    R.string.doc_scan_full_image
                                }
                            )
                        )
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            isBusy = false,
                            message = context.getString(
                                R.string.doc_scan_failed,
                                error.message ?: error.javaClass.simpleName
                            )
                        )
                    }
                }
            )
        }
    }

    fun save() {
        val bitmap = _uiState.value.result ?: return
        if (_uiState.value.isBusy) return
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true, message = null) }
            MediaStoreSaver.saveImage(
                context = context,
                bitmap = bitmap,
                format = Bitmap.CompressFormat.JPEG,
                mimeType = "image/jpeg",
                fileName = "scan_${System.currentTimeMillis()}.jpg",
                quality = 94
            ).fold(
                onSuccess = { saved ->
                    _uiState.update {
                        it.copy(
                            isBusy = false,
                            saved = saved,
                            message = context.getString(R.string.doc_scan_saved, saved.displayName)
                        )
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            isBusy = false,
                            message = context.getString(
                                R.string.doc_scan_failed,
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

    private fun recycleResult() {
        _uiState.value.result?.takeIf { !it.isRecycled }?.recycle()
    }

    override fun onCleared() {
        recycleResult()
        super.onCleared()
    }

    class Factory(private val application: Application) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            DocumentScanViewModel(application) as T
    }
}
