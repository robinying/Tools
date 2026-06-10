package com.robin.tools.feature.ebook.ui

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.robin.tools.feature.ebook.R
import com.robin.tools.feature.ebook.converter.EpubToPdfConverter
import com.robin.tools.feature.ebook.util.StorageUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

sealed class ConversionState {
    object Idle : ConversionState()
    data class Converting(val progress: Int) : ConversionState()
    data class Success(val cacheFile: File, val publicUri: Uri) : ConversionState()
    data class Error(val message: String) : ConversionState()
}

class ConversionViewModel(private val context: Context) : ViewModel() {
    private val converter = EpubToPdfConverter(context)
    private val _uiState = MutableStateFlow<ConversionState>(ConversionState.Idle)
    val uiState = _uiState.asStateFlow()

    fun convertFile(uri: Uri) {
        viewModelScope.launch {
            _uiState.value = ConversionState.Converting(0)
            converter.convert(uri, "converted_${System.currentTimeMillis()}.pdf", object : EpubToPdfConverter.ProgressCallback {
                override fun onProgress(percent: Int) { _uiState.value = ConversionState.Converting(percent) }
                override fun onSuccess(file: File) {
                    viewModelScope.launch {
                        val publicUri = StorageUtils.savePdfToDownloads(context, file, file.name)
                        _uiState.value = if (publicUri != null) ConversionState.Success(file, publicUri) else ConversionState.Error(context.getString(R.string.failed_to_save))
                    }
                }
                override fun onError(e: Exception) { _uiState.value = ConversionState.Error(e.message ?: context.getString(R.string.unknown_error)) }
            })
        }
    }
    fun reset() { _uiState.value = ConversionState.Idle }
}
