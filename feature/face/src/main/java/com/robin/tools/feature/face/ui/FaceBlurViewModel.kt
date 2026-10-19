package com.robin.tools.feature.face.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.robin.tools.core.media.SavedMedia
import com.robin.tools.feature.face.R
import com.robin.tools.feature.face.data.FaceBlurMode
import com.robin.tools.feature.face.data.FaceBlurProcessor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class FaceBlurViewModel(application: Application) : AndroidViewModel(application) {

    data class UiState(
        val sourceUri: Uri? = null,
        val isBusy: Boolean = false,
        val output: SavedMedia? = null,
        val faceCount: Int = 0,
        val message: String? = null,
        val isError: Boolean = false
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val reportedOutputs = mutableSetOf<String>()

    private val context get() = getApplication<Application>()

    fun selectImage(uri: Uri) {
        _uiState.update {
            it.copy(sourceUri = uri, output = null, faceCount = 0, message = null, isError = false)
        }
    }

    fun process(mode: FaceBlurMode, strength: Int) {
        val uri = _uiState.value.sourceUri ?: return
        if (_uiState.value.isBusy) return
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true, message = null, isError = false, output = null) }
            FaceBlurProcessor.process(context, uri, mode, strength).fold(
                onSuccess = { (saved, count) ->
                    _uiState.update {
                        it.copy(
                            isBusy = false,
                            output = saved,
                            faceCount = count,
                            message = context.getString(R.string.face_blur_saved, saved.displayName),
                            isError = false
                        )
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            isBusy = false,
                            message = context.getString(
                                R.string.face_blur_failed,
                                error.message ?: error.javaClass.simpleName
                            ),
                            isError = true
                        )
                    }
                }
            )
        }
    }

    fun clearMessage() {
        _uiState.update { it.copy(message = null, isError = false) }
    }

    /** Returns a newly saved output only once, so history is not duplicated. */
    fun consumeNewOutput(): com.robin.tools.core.media.SavedMedia? {
        val output = _uiState.value.output ?: return null
        return if (reportedOutputs.add(output.uri.toString())) output else null
    }

    class Factory(private val application: Application) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            FaceBlurViewModel(application) as T
    }
}
