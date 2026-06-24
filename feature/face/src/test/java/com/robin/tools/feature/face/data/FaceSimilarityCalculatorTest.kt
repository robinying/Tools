package com.robin.tools.feature.face.data

import org.junit.Assert.assertEquals
import org.junit.Test

class FaceSimilarityCalculatorTest {

    @Test
    fun `identical vectors give 1f similarity`() {
        val a = floatArrayOf(1f, 0f, 0f)
        val b = floatArrayOf(1f, 0f, 0f)
        assertEquals(1f, FaceSimilarityCalculator.cosineSimilarity(a, b), 0.001f)
    }

    @Test
    fun `orthogonal unit vectors give 0f similarity`() {
        val a = floatArrayOf(1f, 0f)
        val b = floatArrayOf(0f, 1f)
        assertEquals(0f, FaceSimilarityCalculator.cosineSimilarity(a, b), 0.001f)
    }

    @Test
    fun `opposite vectors give -1f similarity`() {
        val a = floatArrayOf(1f, 0f, 0f)
        val b = floatArrayOf(-1f, 0f, 0f)
        assertEquals(-1f, FaceSimilarityCalculator.cosineSimilarity(a, b), 0.001f)
    }

    @Test
    fun `L2-normalized identical vectors give 1f`() {
        val norm = 1f / kotlin.math.sqrt(3f)
        val a = floatArrayOf(norm, norm, norm)
        val b = floatArrayOf(norm, norm, norm)
        assertEquals(1f, FaceSimilarityCalculator.cosineSimilarity(a, b), 0.001f)
    }

    @Test
    fun `empty vectors give 0f`() {
        val a = floatArrayOf()
        val b = floatArrayOf()
        assertEquals(0f, FaceSimilarityCalculator.cosineSimilarity(a, b), 0.001f)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `different dimension vectors throw exception`() {
        val a = floatArrayOf(1f, 0f)
        val b = floatArrayOf(1f)
        FaceSimilarityCalculator.cosineSimilarity(a, b)
    }

    @Test
    fun `score is clamped to -1f to 1f`() {
        val a = floatArrayOf(100f)
        val b = floatArrayOf(100f)
        assertEquals(1f, FaceSimilarityCalculator.cosineSimilarity(a, b), 0.001f)
    }

    @Test
    fun `classify returns HIGH for score abov 0_65`() {
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
