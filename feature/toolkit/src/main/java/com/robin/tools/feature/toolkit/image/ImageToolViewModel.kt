package com.robin.tools.feature.toolkit.image

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.robin.tools.feature.toolkit.R
import com.robin.tools.core.media.SavedMedia
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** A label/value pair rendered by the metadata tool. */
data class ExifField(val label: String, val value: String)

class ImageToolViewModel(application: Application) : AndroidViewModel(application) {

    data class UiState(
        val sourceUri: Uri? = null,
        val isBusy: Boolean = false,
        val message: String? = null,
        val isError: Boolean = false,
        val output: SavedMedia? = null,
        val exifFields: List<ExifField> = emptyList()
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val reportedOutputs = mutableSetOf<String>()

    private val context get() = getApplication<Application>()

    fun selectImage(uri: Uri) {
        _uiState.update {
            it.copy(
                sourceUri = uri,
                output = null,
                message = null,
                isError = false,
                exifFields = emptyList()
            )
        }
    }

    fun loadExif() {
        val uri = _uiState.value.sourceUri ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true, message = null, isError = false) }
            val data = ImageProcessor.readExif(context, uri)
            _uiState.update {
                it.copy(
                    isBusy = false,
                    exifFields = data?.let(::toFields).orEmpty(),
                    message = if (data == null) context.getString(R.string.image_exif_empty) else null,
                    isError = data == null
                )
            }
        }
    }

    fun process(
        tool: ImageToolType,
        format: ImageFormat,
        quality: Int,
        scalePercent: Int,
        rotationDegrees: Int
    ) {
        val uri = _uiState.value.sourceUri ?: return
        if (_uiState.value.isBusy) return
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true, message = null, isError = false, output = null) }
            val result = when (tool) {
                ImageToolType.CONVERT -> ImageProcessor.convert(context, uri, format, quality)
                ImageToolType.RESIZE ->
                    ImageProcessor.resize(context, uri, scalePercent, format, quality)
                ImageToolType.ROTATE ->
                    ImageProcessor.rotate(context, uri, rotationDegrees, format, quality)
                ImageToolType.TO_PDF -> ImageProcessor.toPdf(context, uri)
                ImageToolType.EXIF -> ImageProcessor.stripMetadata(context, uri, quality)
            }
            applyResult(result)
        }
    }

    fun stripMetadata(format: ImageFormat, quality: Int) {
        val uri = _uiState.value.sourceUri ?: return
        if (_uiState.value.isBusy) return
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true, message = null, isError = false, output = null) }
            applyResult(ImageProcessor.stripMetadata(context, uri, quality))
        }
    }

    fun clearMessage() {
        _uiState.update { it.copy(message = null, isError = false) }
    }

    private fun applyResult(result: Result<SavedMedia>) {
        result.fold(
            onSuccess = { saved ->
                _uiState.update {
                    it.copy(
                        isBusy = false,
                        output = saved,
                        message = context.getString(R.string.toolkit_saved, saved.displayName),
                        isError = false
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
                        ),
                        isError = true
                    )
                }
            }
        )
    }

    private fun toFields(data: ExifData): List<ExifField> {
        val orientation = when (data.orientation) {
            3 -> "180°"
            6 -> "90°"
            8 -> "270°"
            else -> context.getString(R.string.image_orientation_normal)
        }
        val placeholder = context.getString(R.string.image_value_none)
        return listOf(
            ExifField(context.getString(R.string.image_width), data.width.toString()),
            ExifField(context.getString(R.string.image_height), data.height.toString()),
            ExifField(context.getString(R.string.image_format_label), data.mimeType ?: placeholder),
            ExifField(context.getString(R.string.image_orientation), orientation),
            ExifField(context.getString(R.string.image_make), data.make ?: placeholder),
            ExifField(context.getString(R.string.image_model), data.model ?: placeholder),
            ExifField(context.getString(R.string.image_datetime), data.dateTime ?: placeholder),
            ExifField(
                context.getString(R.string.image_gps),
                context.getString(
                    if (data.hasLocation) R.string.image_value_yes else R.string.image_value_no
                )
            )
        )
    }

    /** Returns a newly saved output only once, so history is not duplicated. */
    fun consumeNewOutput(): com.robin.tools.core.media.SavedMedia? {
        val output = _uiState.value.output ?: return null
        return if (reportedOutputs.add(output.uri.toString())) output else null
    }

    class Factory(private val application: Application) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            ImageToolViewModel(application) as T
    }
}
