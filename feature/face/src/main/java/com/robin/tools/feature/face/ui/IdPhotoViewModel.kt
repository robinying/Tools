package com.robin.tools.feature.face.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.robin.tools.core.media.SavedMedia
import com.robin.tools.feature.face.R
import com.robin.tools.feature.face.data.IdPhotoBackground
import com.robin.tools.feature.face.data.IdPhotoProcessor
import com.robin.tools.feature.face.data.IdPhotoSize
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class IdPhotoViewModel(application: Application) : AndroidViewModel(application) {

    data class UiState(
        val sourceUri: Uri? = null,
        val isBusy: Boolean = false,
        val output: SavedMedia? = null,
        val message: String? = null
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val context get() = getApplication<Application>()

    fun selectImage(uri: Uri) {
        _uiState.update { it.copy(sourceUri = uri, output = null, message = null) }
    }

    fun process(size: IdPhotoSize, background: IdPhotoBackground) {
        val uri = _uiState.value.sourceUri ?: return
        if (_uiState.value.isBusy) return
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true, output = null, message = null) }
            IdPhotoProcessor.process(context, uri, size, background).fold(
                onSuccess = { saved ->
                    _uiState.update {
                        it.copy(
                            isBusy = false,
                            output = saved,
                            message = context.getString(R.string.id_photo_saved, saved.displayName)
                        )
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            isBusy = false,
                            message = context.getString(
                                R.string.id_photo_failed,
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

    class Factory(private val application: Application) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            IdPhotoViewModel(application) as T
    }
}
