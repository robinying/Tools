package com.robin.tools.feature.face.data

import android.graphics.PointF
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceLandmark
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Produces a normalized geometric feature vector from ML Kit face landmarks.
 *
 * Used as a fallback when the TFLite recognition model is not available.
 * All dimensions are ratios of landmark distances relative to face dimensions,
 * capturing inter-person geometric variation while being scale/translation invariant.
 *
 * Feature vector (15 dimensions, all ratios, L2-normalized):
 *   [0]  inter-eye / face width
 *   [1]  mouth width / inter-eye
 *   [2]  eye-to-nose / face height
 *   [3]  nose-to-mouth / face height
 *   [4]  face aspect (width / height)
 *   [5]  eye center X / face width
 *   [6]  eye center Y / face height
 *   [7]  nose X / face width
 *   [8]  nose Y / face height
 *   [9]  mouth center X / face width
 *   [10] mouth center Y / face height
 *   [11] eye tilt (|leftEye.y - rightEye.y| / inter-eye)
 *   [12] mouth tilt (|leftMouth.y - rightMouth.y| / mouth width)
 *   [13] nose-to-left-eye / nose-to-right-eye
 *   [14] nose-to-left-mouth / nose-to-right-mouth
 */
object LandmarkFeatureExtractor {

    fun extract(face: Face): FloatArray? {
        val leftEye = face.getLandmark(FaceLandmark.LEFT_EYE)?.position ?: return null
        val rightEye = face.getLandmark(FaceLandmark.RIGHT_EYE)?.position ?: return null
        val nose = face.getLandmark(FaceLandmark.NOSE_BASE)?.position ?: return null
        val leftMouth = face.getLandmark(FaceLandmark.MOUTH_LEFT)?.position ?: return null
        val rightMouth = face.getLandmark(FaceLandmark.MOUTH_RIGHT)?.position ?: return null

        val faceW = face.boundingBox.width().toFloat()
        val faceH = face.boundingBox.height().toFloat()
        if (faceW <= 0f || faceH <= 0f) return null

        val eyeDist = distance(leftEye, rightEye)
        if (eyeDist <= 0f) return null

        val mouthWidth = distance(leftMouth, rightMouth)
        val eyeMidX = (leftEye.x + rightEye.x) / 2f
        val eyeMidY = (leftEye.y + rightEye.y) / 2f
        val mouthMidX = (leftMouth.x + rightMouth.x) / 2f
        val mouthMidY = (leftMouth.y + rightMouth.y) / 2f

        val eyeNoseDist = distance(PointF(eyeMidX, eyeMidY), nose)
        val noseMouthDist = distance(nose, PointF(mouthMidX, mouthMidY))
        val noseLeftEye = distance(nose, leftEye)
        val noseRightEye = distance(nose, rightEye)
        val noseLeftMouth = distance(nose, leftMouth)
        val noseRightMouth = distance(nose, rightMouth)

        val features = FloatArray(15)
        // Proportions relative to face dimensions
        features[0] = eyeDist / faceW
        features[1] = mouthWidth / eyeDist
        features[2] = eyeNoseDist / faceH
        features[3] = noseMouthDist / faceH
        features[4] = faceW / faceH
        // Normalized landmark positions within the face bounding box
        features[5] = eyeMidX / faceW
        features[6] = eyeMidY / faceH
        features[7] = nose.x / faceW
        features[8] = nose.y / faceH
        features[9] = mouthMidX / faceW
        features[10] = mouthMidY / faceH
        // Tilt / asymmetry signals
        features[11] = abs(leftEye.y - rightEye.y) / eyeDist
        features[12] = abs(leftMouth.y - rightMouth.y) / mouthWidth.coerceAtLeast(1f)
        features[13] = noseLeftEye / noseRightEye.coerceAtLeast(1f)
        features[14] = noseLeftMouth / noseRightMouth.coerceAtLeast(1f)

        l2Normalize(features)
        return features
    }

    private fun distance(a: PointF, b: PointF): Float {
        val dx = a.x - b.x
        val dy = a.y - b.y
        return sqrt(dx * dx + dy * dy)
    }

    private fun l2Normalize(vector: FloatArray) {
        var norm = 0f
        for (v in vector) norm += v * v
        norm = sqrt(norm)
        if (norm > 0f) {
            for (i in vector.indices) vector[i] /= norm
        }
    }
}
