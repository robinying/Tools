package com.robin.tools.feature.face.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FaceSimilarityCalculatorTest {

    // ---- cosineSimilarity ----

    @Test
    fun `identical vectors give 1f cosine similarity`() {
        val a = floatArrayOf(1f, 0f, 0f)
        val b = floatArrayOf(1f, 0f, 0f)
        assertEquals(1f, FaceSimilarityCalculator.cosineSimilarity(a, b), 0.001f)
    }

    @Test
    fun `orthogonal unit vectors give 0f cosine similarity`() {
        val a = floatArrayOf(1f, 0f)
        val b = floatArrayOf(0f, 1f)
        assertEquals(0f, FaceSimilarityCalculator.cosineSimilarity(a, b), 0.001f)
    }

    @Test
    fun `opposite vectors give -1f cosine similarity`() {
        val a = floatArrayOf(1f, 0f, 0f)
        val b = floatArrayOf(-1f, 0f, 0f)
        assertEquals(-1f, FaceSimilarityCalculator.cosineSimilarity(a, b), 0.001f)
    }

    @Test
    fun `L2-normalized identical vectors give 1f cosine`() {
        val norm = 1f / kotlin.math.sqrt(3f)
        val a = floatArrayOf(norm, norm, norm)
        val b = floatArrayOf(norm, norm, norm)
        assertEquals(1f, FaceSimilarityCalculator.cosineSimilarity(a, b), 0.001f)
    }

    @Test
    fun `cosine empty vectors give 0f`() {
        val a = floatArrayOf()
        val b = floatArrayOf()
        assertEquals(0f, FaceSimilarityCalculator.cosineSimilarity(a, b), 0.001f)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `cosine different dimension vectors throw exception`() {
        val a = floatArrayOf(1f, 0f)
        val b = floatArrayOf(1f)
        FaceSimilarityCalculator.cosineSimilarity(a, b)
    }

    @Test
    fun `cosine score is clamped to -1f to 1f`() {
        val a = floatArrayOf(100f)
        val b = floatArrayOf(100f)
        assertEquals(1f, FaceSimilarityCalculator.cosineSimilarity(a, b), 0.001f)
    }

    // ---- euclideanSimilarity ----

    @Test
    fun `identical vectors give 1f euclidean similarity`() {
        val a = floatArrayOf(0.1f, 0.2f, 0.3f)
        val b = floatArrayOf(0.1f, 0.2f, 0.3f)
        assertEquals(1f, FaceSimilarityCalculator.euclideanSimilarity(a, b), 0.001f)
    }

    @Test
    fun `zero vectors give 1f euclidean similarity`() {
        val a = floatArrayOf(0f, 0f, 0f)
        val b = floatArrayOf(0f, 0f, 0f)
        assertEquals(1f, FaceSimilarityCalculator.euclideanSimilarity(a, b), 0.001f)
    }

    @Test
    fun `euclidean similarity decreases as distance increases`() {
        val a = floatArrayOf(0f, 0f)
        val near = floatArrayOf(0.1f, 0f)
        val far = floatArrayOf(1f, 0f)
        val nearScore = FaceSimilarityCalculator.euclideanSimilarity(a, near)
        val farScore = FaceSimilarityCalculator.euclideanSimilarity(a, far)
        assertTrue("near ($nearScore) should be > far ($farScore)", nearScore > farScore)
    }

    @Test
    fun `euclidean similarity is in 0 to 1 range`() {
        val a = floatArrayOf(0f, 0f)
        val b = floatArrayOf(100f, 100f)
        val score = FaceSimilarityCalculator.euclideanSimilarity(a, b)
        assertTrue("score $score should be in [0,1]", score in 0f..1f)
    }

    @Test
    fun `euclidean distance of scale gives 0_5 similarity`() {
        // distance == scale (0.5) => sim = 1 / (1 + 1) = 0.5
        val a = floatArrayOf(0f)
        val b = floatArrayOf(0.5f)
        assertEquals(0.5f, FaceSimilarityCalculator.euclideanSimilarity(a, b, scale = 0.5f), 0.001f)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `euclidean different dimension vectors throw exception`() {
        val a = floatArrayOf(1f, 0f)
        val b = floatArrayOf(1f)
        FaceSimilarityCalculator.euclideanSimilarity(a, b)
    }

    @Test
    fun `euclidean two distinct faces differ from two identical faces`() {
        // Simulate two "typical" faces (near-zero after mean centering) vs a distinctive one.
        val typicalA = floatArrayOf(0.01f, 0.0f, -0.01f, 0.02f)
        val typicalB = floatArrayOf(0.0f, 0.01f, 0.0f, -0.01f)
        val distinctive = floatArrayOf(0.6f, 0.5f, 0.4f, 0.7f)

        val sameScore = FaceSimilarityCalculator.euclideanSimilarity(typicalA, typicalB)
        val diffScore = FaceSimilarityCalculator.euclideanSimilarity(typicalA, distinctive)
        assertTrue(
            "identical faces ($sameScore) should score higher than distinct ($diffScore)",
            sameScore > diffScore
        )
    }

    // ---- classify ----

    @Test
    fun `classify returns HIGH for score at or above 0_65`() {
        assertEquals(SimilarityLevel.HIGH, FaceSimilarityCalculator.classify(0.65f))
        assertEquals(SimilarityLevel.HIGH, FaceSimilarityCalculator.classify(0.80f))
        assertEquals(SimilarityLevel.HIGH, FaceSimilarityCalculator.classify(1.0f))
    }

    @Test
    fun `classify returns MEDIUM for score between 0_50 and 0_65`() {
        assertEquals(SimilarityLevel.MEDIUM, FaceSimilarityCalculator.classify(0.50f))
        assertEquals(SimilarityLevel.MEDIUM, FaceSimilarityCalculator.classify(0.55f))
        assertEquals(SimilarityLevel.MEDIUM, FaceSimilarityCalculator.classify(0.64f))
    }

    @Test
    fun `classify returns LOW for score between 0_35 and 0_50`() {
        assertEquals(SimilarityLevel.LOW, FaceSimilarityCalculator.classify(0.35f))
        assertEquals(SimilarityLevel.LOW, FaceSimilarityCalculator.classify(0.40f))
        assertEquals(SimilarityLevel.LOW, FaceSimilarityCalculator.classify(0.49f))
    }

    @Test
    fun `classify returns NONE for score below 0_35`() {
        assertEquals(SimilarityLevel.NONE, FaceSimilarityCalculator.classify(0.34f))
        assertEquals(SimilarityLevel.NONE, FaceSimilarityCalculator.classify(0f))
        assertEquals(SimilarityLevel.NONE, FaceSimilarityCalculator.classify(-1f))
    }
}
