package com.robin.tools.feature.face.data

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceLandmark

object FaceAligner {

    private const val TARGET_SIZE = 112f

    private val TARGET_POINTS = floatArrayOf(
        38.2946f, 51.6963f,  // left eye
        73.5318f, 51.6963f,  // right eye
        56.0252f, 71.7366f,  // nose
        41.5493f, 92.3655f,  // left mouth
        70.7299f, 92.3655f   // right mouth
    )

    fun align(bitmap: Bitmap, face: Face): Bitmap? {
        // FaceLandmark landmark type values: LEFT_EYE=4, RIGHT_EYE=10, NOSE_BASE=6
        val leftEye = face.getLandmark(FaceLandmark.LEFT_EYE)?.position ?: return null
        val rightEye = face.getLandmark(FaceLandmark.RIGHT_EYE)?.position ?: return null
        val nose = face.getLandmark(FaceLandmark.NOSE_BASE)?.position ?: return null

        val srcPoints = floatArrayOf(
            leftEye.x, leftEye.y,
            rightEye.x, rightEye.y,
            nose.x, nose.y
        )
        val dstPoints = floatArrayOf(
            TARGET_POINTS[0], TARGET_POINTS[1],  // left eye target
            TARGET_POINTS[2], TARGET_POINTS[3],  // right eye target
            TARGET_POINTS[4], TARGET_POINTS[5]   // nose target
        )

        val matrix = Matrix()
        matrix.setPolyToPoly(srcPoints, 0, dstPoints, 0, 3)

        val result = Bitmap.createBitmap(
            TARGET_SIZE.toInt(), TARGET_SIZE.toInt(),
            Bitmap.Config.ARGB_8888
        )
        val canvas = Canvas(result)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        canvas.drawBitmap(bitmap, matrix, paint)
        return result
    }
}
