package com.robin.tools.feature.face.data

enum class SimilarityLevel {
    HIGH,
    MEDIUM,
    LOW,
    NONE
}

data class CompareResult(
    val similarityScore: Float,
    val level: SimilarityLevel,
    val faceCountLeft: Int,
    val faceCountRight: Int,
    val errorMessage: String? = null
)

object FaceSimilarityCalculator {

    /**
     * Cosine similarity on L2-normalized vectors.
     * Used for deep embeddings (TFLite model output).
     * Range [-1, 1]; in practice face embeddings fall in [0, 1].
     */
    fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
        require(a.size == b.size) {
            "Vector dimension mismatch: ${a.size} vs ${b.size}"
        }
        var dot = 0f
        for (i in a.indices) dot += a[i] * b[i]
        return dot.coerceIn(-1f, 1f)
    }

    /**
     * Euclidean-distance-based similarity for geometric feature vectors.
     *
     * Geometric landmark features are all-positive ratio values; cosine similarity
     * on such vectors is always close to 1 (all vectors point into the same
     * positive orthant). Euclidean distance correctly captures how far apart two
     * facial geometry profiles are. We map the distance to a [0, 1] similarity
     * via 1 / (1 + d) so identical faces yield 1.0 and distant faces approach 0.
     *
     * @param scale characteristic distance at which similarity should drop to ~0.5.
     *              Tuned for the LandmarkFeatureExtractor output.
     */
    fun euclideanSimilarity(a: FloatArray, b: FloatArray, scale: Float = 0.5f): Float {
        require(a.size == b.size) {
            "Vector dimension mismatch: ${a.size} vs ${b.size}"
        }
        var sumSq = 0f
        for (i in a.indices) {
            val diff = a[i] - b[i]
            sumSq += diff * diff
        }
        val dist = kotlin.math.sqrt(sumSq)
        val sim = 1f / (1f + dist / scale)
        return sim.coerceIn(0f, 1f)
    }

    fun classify(score: Float): SimilarityLevel = when {
        score >= 0.65f -> SimilarityLevel.HIGH
        score >= 0.50f -> SimilarityLevel.MEDIUM
        score >= 0.35f -> SimilarityLevel.LOW
        else -> SimilarityLevel.NONE
    }
}
