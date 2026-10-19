package com.robin.tools.feature.face.ui

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.robin.tools.core.media.BitmapLoader
import com.robin.tools.feature.face.R
import com.robin.tools.feature.face.data.FaceAligner
import com.robin.tools.feature.face.data.FaceDetector
import com.robin.tools.feature.face.data.FaceEmbeddingExtractor
import com.robin.tools.feature.face.data.FaceSimilarityCalculator
import com.robin.tools.feature.face.data.LandmarkFeatureExtractor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Groups photos that contain the same (largest) face. */
class FaceGroupViewModel(application: Application) : AndroidViewModel(application) {

    data class Group(val uris: List<Uri>)

    data class UiState(
        val isBusy: Boolean = false,
        val progress: Float = 0f,
        val processed: Int = 0,
        val skipped: Int = 0,
        val groups: List<Group> = emptyList(),
        val message: String? = null
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val context get() = getApplication<Application>()
    private val faceDetector = FaceDetector()
    private val embeddingExtractor = FaceEmbeddingExtractor(context.applicationContext)

    fun group(uris: List<Uri>) {
        if (_uiState.value.isBusy || uris.isEmpty()) return
        viewModelScope.launch {
            _uiState.update {
                it.copy(isBusy = true, progress = 0f, processed = 0, skipped = 0, groups = emptyList())
            }
            val result = withContext(Dispatchers.IO) { computeGroups(uris) }
            _uiState.update {
                it.copy(
                    isBusy = false,
                    progress = 1f,
                    groups = result.groups,
                    skipped = result.skipped,
                    message = context.getString(
                        R.string.face_group_result,
                        result.groups.size,
                        result.skipped
                    )
                )
            }
        }
    }

    fun clearMessage() {
        _uiState.update { it.copy(message = null) }
    }

    private suspend fun computeGroups(uris: List<Uri>): GroupingResult {
        val items = mutableListOf<Item>()
        var skipped = 0
        uris.forEachIndexed { index, uri ->
            val bitmap = BitmapLoader.load(context, uri, 2048)
            if (bitmap == null) {
                skipped++
            } else {
                try {
                    val faces = faceDetector.detect(bitmap, 0)
                    val face = faces.maxByOrNull {
                        it.boundingBox.width() * it.boundingBox.height()
                    }
                    if (face == null) {
                        skipped++
                    } else {
                        val embedding = if (embeddingExtractor.isModelLoaded) {
                            val aligned = FaceAligner.align(bitmap, face)
                            try {
                                aligned?.let { embeddingExtractor.extract(it) }
                            } finally {
                                if (aligned != null && aligned !== bitmap && !aligned.isRecycled) {
                                    aligned.recycle()
                                }
                            }
                        } else {
                            LandmarkFeatureExtractor.extract(face)
                        }
                        if (embedding != null) {
                            items += Item(uri, embedding, embeddingExtractor.isModelLoaded)
                        } else {
                            skipped++
                        }
                    }
                } finally {
                    bitmap.recycle()
                }
            }
            _uiState.update {
                it.copy(
                    processed = index + 1,
                    progress = (index + 1f) / uris.size
                )
            }
        }

        val threshold = if (items.firstOrNull()?.deepModel == true) 0.65f else 0.80f
        val clusters = mutableListOf<MutableList<Item>>()
        for (item in items) {
            val match = clusters.firstOrNull { cluster ->
                similarity(cluster.first(), item) >= threshold
            }
            if (match != null) {
                match += item
            } else {
                clusters += mutableListOf(item)
            }
        }
        return GroupingResult(
            groups = clusters.map { cluster -> Group(cluster.map { it.uri }) },
            skipped = skipped
        )
    }

    private fun similarity(a: Item, b: Item): Float =
        if (a.deepModel && b.deepModel) {
            FaceSimilarityCalculator.cosineSimilarity(a.embedding, b.embedding)
        } else {
            FaceSimilarityCalculator.euclideanSimilarity(a.embedding, b.embedding)
        }

    override fun onCleared() {
        embeddingExtractor.dispose()
        super.onCleared()
    }

    private data class Item(
        val uri: Uri,
        val embedding: FloatArray,
        val deepModel: Boolean
    )

    private data class GroupingResult(
        val groups: List<Group>,
        val skipped: Int
    )

    class Factory(private val application: Application) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            FaceGroupViewModel(application) as T
    }
}
