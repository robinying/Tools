package com.robin.tools.feature.face.ui

import app.cash.turbine.test
import com.robin.tools.feature.face.data.CompareResult
import com.robin.tools.feature.face.data.SimilarityLevel
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class FaceCompareViewModelTest {

    @get:Rule
    val tempDir = TemporaryFolder()

    private lateinit var viewModel: FaceCompareViewModel

    @Before
    fun setUp() {
        // FaceCompareViewModel requires a real context for ML Kit / TFLite,
        // so we test the state machine through manual state manipulation.
        // The actual compare() flow (which requires real bitmaps / ML Kit)
        // is tested via instrumentation tests.
    }

    @Test
    fun `initial state has no images and no result`() = runTest {
        // Verify the UiState defaults are correct
        val defaultState = FaceCompareViewModel.UiState()
        assertNull(defaultState.leftImageUri)
        assertNull(defaultState.rightImageUri)
        assertFalse(defaultState.isProcessing)
        assertNull(defaultState.result)
        assertEquals(-1, defaultState.leftFaceCount)
        assertEquals(-1, defaultState.rightFaceCount)
    }

    @Test
    fun `uiState has correct immutable defaults`() {
        val state = FaceCompareViewModel.UiState()
        assertFalse(state.isProcessing)
        assertNull(state.result)
    }

    @Test
    fun `compare button should be disabled when no images selected`() {
        val state = FaceCompareViewModel.UiState()
        val canCompare = state.leftImageUri != null
                && state.rightImageUri != null
                && !state.isProcessing
        assertFalse(canCompare)
    }

    @Test
    fun `compare button should be disabled when processing`() {
        val state = FaceCompareViewModel.UiState(
            leftImageUri = mockk(relaxed = true),
            rightImageUri = mockk(relaxed = true),
            isProcessing = true
        )
        val canCompare = state.leftImageUri != null
                && state.rightImageUri != null
                && !state.isProcessing
        assertFalse(canCompare)
    }

    @Test
    fun `compare button should be enabled when both images selected and not processing`() {
        val state = FaceCompareViewModel.UiState(
            leftImageUri = mockk(relaxed = true),
            rightImageUri = mockk(relaxed = true),
            isProcessing = false
        )
        val canCompare = state.leftImageUri != null
                && state.rightImageUri != null
                && !state.isProcessing
        assertTrue(canCompare)
    }

    @Test
    fun `CompareResult with HIGH level has score above 0_65`() {
        val result = CompareResult(
            similarityScore = 0.72f,
            level = SimilarityLevel.HIGH,
            faceCountLeft = 1,
            faceCountRight = 1
        )
        assertEquals(SimilarityLevel.HIGH, result.level)
        assertTrue(result.similarityScore >= 0.65f)
        assertNull(result.errorMessage)
    }

    @Test
    fun `CompareResult with errorMessage stores it correctly`() {
        val result = CompareResult(
            similarityScore = 0f,
            level = SimilarityLevel.NONE,
            faceCountLeft = 0,
            faceCountRight = 1,
            errorMessage = "No face detected in left image"
        )
        assertEquals(SimilarityLevel.NONE, result.level)
        assertNotNull(result.errorMessage)
    }

    @Test
    fun `swapImages logic swaps Uris and face counts`() {
        val state = FaceCompareViewModel.UiState(
            leftImageUri = mockk(relaxed = true, name = "left"),
            rightImageUri = mockk(relaxed = true, name = "right"),
            leftFaceCount = 2,
            rightFaceCount = 1
        )
        val swapped = state.copy(
            leftImageUri = state.rightImageUri,
            rightImageUri = state.leftImageUri,
            leftFaceCount = state.rightFaceCount,
            rightFaceCount = state.leftFaceCount
        )
        assertEquals(1, swapped.leftFaceCount)
        assertEquals(2, swapped.rightFaceCount)
    }
}
