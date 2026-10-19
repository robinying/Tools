package com.robin.tools.feature.toolkit.qr

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.robin.tools.feature.toolkit.R
import com.robin.tools.core.media.MediaStoreSaver
import com.robin.tools.core.media.SavedMedia
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class QrMode { GENERATE, DECODE }

class QrToolViewModel(application: Application) : AndroidViewModel(application) {

    data class UiState(
        val mode: QrMode = QrMode.GENERATE,
        val content: String = "",
        val generated: Bitmap? = null,
        val decodedValues: List<String> = emptyList(),
        val scannedUri: Uri? = null,
        val saved: SavedMedia? = null,
        val isBusy: Boolean = false,
        val message: String? = null
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val reportedOutputs = mutableSetOf<String>()

    private val context get() = getApplication<Application>()

    fun setMode(mode: QrMode) {
        _uiState.update { it.copy(mode = mode, message = null) }
    }

    fun setContent(content: String) {
        _uiState.update { it.copy(content = content) }
    }

    fun generate() {
        val content = _uiState.value.content.trim()
        if (content.isEmpty()) return
        runCatching { QrCodeTools.generate(content) }
            .onSuccess { bitmap ->
                _uiState.update { state ->
                    if (state.generated !== bitmap && state.generated?.isRecycled == false) {
                        state.generated.recycle()
                    }
                    state.copy(generated = bitmap, saved = null, message = null)
                }
            }
            .onFailure { error ->
                _uiState.update {
                    it.copy(message = context.getString(R.string.toolkit_failed, error.message))
                }
            }
    }

    fun saveGenerated() {
        val bitmap = _uiState.value.generated ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true, message = null) }
            MediaStoreSaver.saveImage(
                context = context,
                bitmap = bitmap,
                format = Bitmap.CompressFormat.PNG,
                mimeType = "image/png",
                fileName = "qr_${System.currentTimeMillis()}.png",
                quality = 100
            ).fold(
                onSuccess = { saved ->
                    _uiState.update {
                        it.copy(
                            isBusy = false,
                            saved = saved,
                            message = context.getString(R.string.toolkit_saved, saved.displayName)
                        )
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            isBusy = false,
                            message = context.getString(
                                R.string.toolkit_failed,
                                error.message ?: error.javaClass.simpleName
                            )
                        )
                    }
                }
            )
        }
    }

    fun decode(uri: Uri) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    mode = QrMode.DECODE,
                    scannedUri = uri,
                    isBusy = true,
                    decodedValues = emptyList(),
                    message = null
                )
            }
            runCatching { QrCodeTools.decode(context, uri) }
                .onSuccess { values ->
                    _uiState.update {
                        it.copy(
                            isBusy = false,
                            decodedValues = values,
                            message = if (values.isEmpty()) {
                                context.getString(R.string.qr_no_result)
                            } else {
                                null
                            }
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            isBusy = false,
                            message = context.getString(
                                R.string.toolkit_failed,
                                error.message ?: error.javaClass.simpleName
                            )
                        )
                    }
                }
        }
    }

    fun clearMessage() {
        _uiState.update { it.copy(message = null) }
    }

    override fun onCleared() {
        _uiState.value.generated?.takeIf { !it.isRecycled }?.recycle()
        super.onCleared()
    }

    /** Returns a newly saved output only once, so history is not duplicated. */
    fun consumeNewOutput(): com.robin.tools.core.media.SavedMedia? {
        val saved = _uiState.value.saved ?: return null
        return if (reportedOutputs.add(saved.uri.toString())) saved else null
    }

    class Factory(private val application: Application) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            QrToolViewModel(application) as T
    }
}
