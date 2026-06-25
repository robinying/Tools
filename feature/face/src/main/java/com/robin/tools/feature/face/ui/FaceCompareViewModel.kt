package com.robin.tools.feature.face.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.robin.tools.feature.face.data.CompareResult
import com.robin.tools.feature.face.data.FaceAligner
import com.robin.tools.feature.face.data.FaceDetector
import com.robin.tools.feature.face.data.FaceEmbeddingExtractor
import com.robin.tools.feature.face.data.FaceSimilarityCalculator
import com.robin.tools.feature.face.data.LandmarkFeatureExtractor
import com.robin.tools.feature.face.data.SimilarityLevel
import com.robin.tools.feature.face.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class FaceCompareViewModel(context: Context) : ViewModel() {

    data class UiState(
        val leftImageUri: Uri? = null,
        val rightImageUri: Uri? = null,
        val isProcessing: Boolean = false,
        val result: CompareResult? = null,
        val leftFaceCount: Int = -1,
        val rightFaceCount: Int = -1
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val appContext = context.applicationContext
    private val faceDetector = FaceDetector()
    private val embeddingExtractor = FaceEmbeddingExtractor(appContext)

    fun setLeftImage(uri: Uri) {
        _uiState.update { it.copy(leftImageUri = uri, result = null, leftFaceCount = -1) }
        detectFaceCount(uri, isLeft = true)
    }

    fun setRightImage(uri: Uri) {
        _uiState.update { it.copy(rightImageUri = uri, result = null, rightFaceCount = -1) }
        detectFaceCount(uri, isLeft = false)
    }

    fun swapImages() {
        _uiState.update {
            it.copy(
                leftImageUri = it.rightImageUri,
                rightImageUri = it.leftImageUri,
                leftFaceCount = it.rightFaceCount,
                rightFaceCount = it.leftFaceCount,
                result = null
            )
        }
    }

    fun reset() {
        _uiState.update { UiState() }
    }

    fun compare() {
        val left = _uiState.value.leftImageUri ?: return
        val right = _uiState.value.rightImageUri ?: return
        if (_uiState.value.isProcessing) return

        viewModelScope.launch {
            _uiState.update { it.copy(isProcessing = true, result = null) }
            try {
                val compareResult = withContext(Dispatchers.IO) {
                    val leftBitmap = loadBitmap(left)
                    val rightBitmap = loadBitmap(right)

                    val leftRotation = faceDetector.getRotationFromUri(appContext, left)
                    val rightRotation = faceDetector.getRotationFromUri(appContext, right)

                    val leftFaces = faceDetector.detect(leftBitmap, leftRotation)
                    val rightFaces = faceDetector.detect(rightBitmap, rightRotation)

                    if (leftFaces.isEmpty() || rightFaces.isEmpty()) {
                        val errorMsg = when {
                            leftFaces.isEmpty() && rightFaces.isEmpty() ->
                                appContext.getString(R.string.face_compare_no_faces)
                            leftFaces.isEmpty() ->
                                appContext.getString(R.string.face_compare_no_face_left)
                            else ->
                                appContext.getString(R.string.face_compare_no_face_right)
                        }
                        return@withContext CompareResult(
                            similarityScore = 0f,
                            level = SimilarityLevel.NONE,
                            faceCountLeft = leftFaces.size,
                            faceCountRight = rightFaces.size,
                            errorMessage = errorMsg
                        )
                    }

                    val leftAligned = FaceAligner.align(leftBitmap, leftFaces.first())
                    val rightAligned = FaceAligner.align(rightBitmap, rightFaces.first())

                    if (leftAligned == null || rightAligned == null) {
                        val errorMsg = when {
                            leftAligned == null && rightAligned == null ->
                                appContext.getString(R.string.face_compare_align_failed_both)
                            leftAligned == null ->
                                appContext.getString(R.string.face_compare_align_failed_left)
                            else ->
                                appContext.getString(R.string.face_compare_align_failed_right)
                        }
                        return@withContext CompareResult(
                            similarityScore = 0f,
                            level = SimilarityLevel.NONE,
                            faceCountLeft = leftFaces.size,
                            faceCountRight = rightFaces.size,
                            errorMessage = errorMsg
                        )
                    }

                    val leftEmbedding: FloatArray
                    val rightEmbedding: FloatArray
                    val useEuclidean: Boolean
                    if (embeddingExtractor.isModelLoaded) {
                        leftEmbedding = embeddingExtractor.extract(leftAligned)
                        rightEmbedding = embeddingExtractor.extract(rightAligned)
                        useEuclidean = false
                    } else {
                        leftEmbedding = LandmarkFeatureExtractor.extract(leftFaces.first())
                            ?: return@withContext CompareResult(
                                similarityScore = 0f,
                                level = SimilarityLevel.NONE,
                                faceCountLeft = leftFaces.size,
                                faceCountRight = rightFaces.size,
                                errorMessage = appContext.getString(R.string.face_compare_align_failed_left)
                            )
                        rightEmbedding = LandmarkFeatureExtractor.extract(rightFaces.first())
                            ?: return@withContext CompareResult(
                                similarityScore = 0f,
                                level = SimilarityLevel.NONE,
                                faceCountLeft = leftFaces.size,
                                faceCountRight = rightFaces.size,
                                errorMessage = appContext.getString(R.string.face_compare_align_failed_right)
                            )
                        useEuclidean = true
                    }

                    val score = if (useEuclidean) {
                        FaceSimilarityCalculator.euclideanSimilarity(leftEmbedding, rightEmbedding)
                    } else {
                        FaceSimilarityCalculator.cosineSimilarity(leftEmbedding, rightEmbedding)
                    }
                    val level = FaceSimilarityCalculator.classify(score)
                    CompareResult(
                        similarityScore = score,
                        level = level,
                        faceCountLeft = leftFaces.size,
                        faceCountRight = rightFaces.size
                    )
                }
                _uiState.update { it.copy(isProcessing = false, result = compareResult) }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isProcessing = false,
                        result = CompareResult(
                            similarityScore = 0f,
                            level = SimilarityLevel.NONE,
                            faceCountLeft = 0,
                            faceCountRight = 0,
                            errorMessage = e.message ?: appContext.getString(R.string.face_compare_error)
                        )
                    )
                }
            }
        }
    }

    private fun detectFaceCount(uri: Uri, isLeft: Boolean) {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val bitmap = loadBitmap(uri)
                    val rotation = faceDetector.getRotationFromUri(appContext, uri)
                    val faces = faceDetector.detect(bitmap, rotation)
                    _uiState.update {
                        if (isLeft) it.copy(leftFaceCount = faces.size)
                        else it.copy(rightFaceCount = faces.size)
                    }
                }
            } catch (_: Exception) {
                _uiState.update {
                    if (isLeft) it.copy(leftFaceCount = 0)
                    else it.copy(rightFaceCount = 0)
                }
            }
        }
    }

    private fun loadBitmap(uri: Uri): Bitmap {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        appContext.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, options)
        }
        options.apply {
            inJustDecodeBounds = false
            inSampleSize = calculateSampleSize(
                outWidth = options.outWidth,
                outHeight = options.outHeight,
                maxEdge = 1600
            )
        }
        return appContext.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, options)
        } ?: throw IllegalStateException("Failed to load image: $uri")
    }

    private fun calculateSampleSize(outWidth: Int, outHeight: Int, maxEdge: Int): Int {
        val maxDimension = maxOf(outWidth, outHeight)
        var sampleSize = 1
        while (maxDimension / sampleSize > maxEdge) {
            sampleSize *= 2
        }
        return sampleSize
    }
}
