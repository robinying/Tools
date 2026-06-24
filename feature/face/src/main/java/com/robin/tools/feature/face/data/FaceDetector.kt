package com.robin.tools.feature.face.data

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlin.math.abs

class FaceDetector {

    private val detector by lazy {
        val options = FaceDetectorOptions.Builder()
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
            .setContourMode(FaceDetectorOptions.CONTOUR_MODE_NONE)
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_NONE)
            .setMinFaceSize(0.15f)
            .build()
        FaceDetection.getClient(options)
    }

    suspend fun detect(bitmap: Bitmap, rotation: Int = 0): List<Face> =
        withContext(Dispatchers.IO) {
            val image = InputImage.fromBitmap(bitmap, rotation)
            val faces = detector.process(image).await()
            faces.sortedBy { abs(it.headEulerAngleY) + abs(it.headEulerAngleZ) }
        }

    fun getRotationFromUri(context: Context, uri: Uri): Int {
        runCatching {
            context.contentResolver.openInputStream(uri)?.use { input ->
                val exif = ExifInterface(input)
                return when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, 1)) {
                    3 -> 180
                    6 -> 90
                    8 -> 270
                    else -> 0
                }
            }
        }
        return 0
    }
}
