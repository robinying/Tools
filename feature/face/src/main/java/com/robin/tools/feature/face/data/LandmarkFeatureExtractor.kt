package com.robin.tools.feature.face.data

import android.graphics.PointF
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceLandmark
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Produces a geometric feature vector from ML Kit face landmarks.
 *
 * Used as a fallback when the TFLite recognition model is not available.
 *
 * Design principles (fixes the "everything looks 98% similar" problem):
 *
 * 1. **Centroid-relative coordinates, not bbox-relative.** Landmark positions
 *    are expressed relative to the centroid of the 5 landmarks, divided by
 *    inter-eye distance. This removes dependence on where the face sits inside
 *    its bounding box (which varies with head pose and is not an identity
 *    signal). The bounding box itself was polluting the old features.
 *
 * 2. **Single scale reference (inter-eye distance).** All distances are
 *    normalized by eye distance, not by a mix of faceW / faceH. The bounding
 *    box width/height swings with yaw/roll; inter-eye distance is the most
 *    stable in-plane scale anchor.
 *
 * 3. **Mean-centered signed features.** Pure ratio features are all positive
 *    and cluster in a narrow cone, so cosine similarity between any two faces
 *    is artificially high. We instead emit signed deviations from typical
 *    human-face proportions, which spread different faces across directions.
 *
 * 4. **Compared via Euclidean distance**, not cosine (see FaceSimilarityCalculator).
 *
 * Feature vector (13 dimensions):
 *   [0]  mouth width / eye distance           (typical ~0.50)
 *   [1]  eye-to-nose / eye distance           (typical ~0.55)
 *   [2]  nose-to-mouth / eye distance         (typical ~0.45)
 *   [3]  face height / eye distance           (typical ~2.20, from bbox)
 *   [4]  nose-to-left-eye / eye distance      (typical ~0.70)
 *   [5]  nose-to-right-eye / eye distance     (typical ~0.70)
 *   [6]  nose horizontal offset / eye dist    (typical ~0.00, signed)
 *   [7]  mouth horizontal offset / eye dist   (typical ~0.00, signed)
 *   [8]  eye-line to nose vertical / eye dist (typical ~0.45, signed)
 *   [9]  nose to mouth vertical / eye dist    (typical ~0.40, signed)
 *   [10] eye tilt  (|dY eyes| / eye dist)
 *   [11] mouth tilt (|dY mouth| / mouth width)
 *   [12] face aspect ratio (bbox w / h)
 */
object LandmarkFeatureExtractor {

    // Approximate population means for mean-centering.
    // Features are stored as (value - mean) so identical-to-typical faces map near zero
    // and distinctive faces map away from zero in a signed direction.
    private val FEATURE_MEANS = floatArrayOf(
        0.50f,  // mouth width / eye dist
        0.55f,  // eye-to-nose / eye dist
        0.45f,  // nose-to-mouth / eye dist
        2.20f,  // face height / eye dist
        0.70f,  // nose-to-left-eye / eye dist
        0.70f,  // nose-to-right-eye / eye dist
        0.00f,  // nose horizontal offset (signed)
        0.00f,  // mouth horizontal offset (signed)
        0.45f,  // eye-line to nose vertical (signed)
        0.40f,  // nose to mouth vertical (signed)
        0.00f,  // eye tilt
        0.00f,  // mouth tilt
        0.80f   // face aspect ratio
    )

    fun extract(face: Face): FloatArray? {
        val leftEye = face.getLandmark(FaceLandmark.LEFT_EYE)?.position ?: return null
        val rightEye = face.getLandmark(FaceLandmark.RIGHT_EYE)?.position ?: return null
        val nose = face.getLandmark(FaceLandmark.NOSE_BASE)?.position ?: return null
        val leftMouth = face.getLandmark(FaceLandmark.MOUTH_LEFT)?.position ?: return null
        val rightMouth = face.getLandmark(FaceLandmark.MOUTH_RIGHT)?.position ?: return null

        val eyeDist = distance(leftEye, rightEye)
        if (eyeDist <= 0f) return null

        val mouthWidth = distance(leftMouth, rightMouth)

        // Centroids — reference frame independent of bounding box placement.
        val eyeMidX = (leftEye.x + rightEye.x) / 2f
        val eyeMidY = (leftEye.y + rightEye.y) / 2f
        val mouthMidX = (leftMouth.x + rightMouth.x) / 2f
        val mouthMidY = (leftMouth.y + rightMouth.y) / 2f

        // Inter-eye forms the x-axis unit. Project offsets onto eye axis (unit vector)
        // and its perpendicular to get signed horizontal/vertical components that are
        // invariant to in-plane rotation.
        val eyeAxisX = (rightEye.x - leftEye.x) / eyeDist
        val eyeAxisY = (rightEye.y - leftEye.y) / eyeDist
        // Perpendicular (rotate +90°): points "down" in face space when eyes are horizontal.
        val perpX = -eyeAxisY
        val perpY = eyeAxisX

        fun signedOffset(p: PointF, origin: PointF): Pair<Float, Float> {
            val dx = p.x - origin.x
            val dy = p.y - origin.y
            val along = dx * eyeAxisX + dy * eyeAxisY        // horizontal-ish
            val across = dx * perpX + dy * perpY              // vertical-ish
            return (along / eyeDist) to (across / eyeDist)
        }

        val (noseH, noseV) = signedOffset(nose, PointF(eyeMidX, eyeMidY))
        val (mouthH, mouthV) = signedOffset(PointF(mouthMidX, mouthMidY), PointF(eyeMidX, eyeMidY))

        val noseLeftEye = distance(nose, leftEye) / eyeDist
        val noseRightEye = distance(nose, rightEye) / eyeDist
        val eyeNose = distance(PointF(eyeMidX, eyeMidY), nose) / eyeDist
        val noseMouth = distance(nose, PointF(mouthMidX, mouthMidY)) / eyeDist
        val faceH = face.boundingBox.height().toFloat()
        val faceW = face.boundingBox.width().toFloat()
        val faceAspect = if (faceH > 0f) faceW / faceH else 0.8f
        val faceHeightOverEye = if (eyeDist > 0f) faceH / eyeDist else 2.2f

        val eyeTilt = abs(leftEye.y - rightEye.y) / eyeDist
        val mouthTilt = if (mouthWidth > 0f) abs(leftMouth.y - rightMouth.y) / mouthWidth else 0f

        val raw = floatArrayOf(
            mouthWidth / eyeDist,
            eyeNose,
            noseMouth,
            faceHeightOverEye,
            noseLeftEye,
            noseRightEye,
            noseH,
            mouthH,
            noseV,
            mouthV,
            eyeTilt,
            mouthTilt,
            faceAspect
        )

        // Mean-center each dimension so the vector points away from zero for distinctive faces.
        val features = FloatArray(raw.size)
        for (i in raw.indices) {
            features[i] = raw[i] - FEATURE_MEANS[i]
        }
        return features
    }

    private fun distance(a: PointF, b: PointF): Float {
        val dx = a.x - b.x
        val dy = a.y - b.y
        return sqrt(dx * dx + dy * dy)
    }
}
