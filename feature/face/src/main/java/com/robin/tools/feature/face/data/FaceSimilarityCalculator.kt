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
     * Score is the raw dot product, range [-1, 1].
     * In practice, face embeddings fall in [0, 1].
     */
    fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
        require(a.size == b.size) {
            "Vector dimension mismatch: ${a.size} vs ${b.size}"
        }
        var dot = 0f
        for (i in a.indices) dot += a[i] * b[i]
        return dot.coerceIn(-1f, 1f)
    }

    fun classify(score: Float): SimilarityLevel = when {
        score >= 0.65f -> SimilarityLevel.HIGH
        score >= 0.50f -> SimilarityLevel.MEDIUM
        score >= 0.35f -> SimilarityLevel.LOW
        else -> SimilarityLevel.NONE
    }
}
