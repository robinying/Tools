package com.robin.tools.feature.toolkit.ocr

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import com.robin.tools.feature.toolkit.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * On-device OCR with the Chinese recognizer, which also covers Latin text.
 */
class OcrToolViewModel(application: Application) : AndroidViewModel(application) {

    data class UiState(
        val sourceUri: Uri? = null,
        val text: String = "",
        val isBusy: Boolean = false,
        val message: String? = null
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val recognizer: TextRecognizer by lazy {
        TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
    }

    private val context get() = getApplication<Application>()

    fun recognize(uri: Uri) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(sourceUri = uri, isBusy = true, text = "", message = null)
            }
            runCatching {
                val image = InputImage.fromFilePath(context, uri)
                recognizer.process(image).await().text
            }.onSuccess { text ->
                _uiState.update {
                    it.copy(
                        isBusy = false,
                        text = text,
                        message = if (text.isBlank()) {
                            context.getString(R.string.ocr_no_text)
                        } else {
                            null
                        }
                    )
                }
            }.onFailure { error ->
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
        recognizer.close()
        super.onCleared()
    }

    class Factory(private val application: Application) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            OcrToolViewModel(application) as T
    }
}
