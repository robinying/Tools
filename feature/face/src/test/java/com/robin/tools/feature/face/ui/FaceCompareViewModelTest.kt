package com.robin.tools.feature.face.ui

import android.content.Context
import android.net.Uri
import com.robin.tools.feature.face.data.FaceDetector
import com.robin.tools.feature.face.data.FaceEmbeddingExtractor
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FaceCompareViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var context: Context
    private lateinit var faceDetector: FaceDetector
    private lateinit var embeddingExtractor: FaceEmbeddingExtractor
    private lateinit var viewModel: FaceCompareViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        context = mockk(relaxed = true)
        faceDetector = mockk(relaxed = true)
        embeddingExtractor = mockk(relaxed = true)
        every { embeddingExtractor.isModelLoaded } returns true
        viewModel = FaceCompareViewModel(context, faceDetector, embeddingExtractor)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state has no images and no result`() = runTest {
        val state = viewModel.uiState.value
        assertNull(state.leftImageUri)
        assertNull(state.rightImageUri)
        assertFalse(state.isProcessing)
        assertNull(state.result)
    }

    @Test
    fun `swapImages swaps Uris and face counts`() = runTest {
        val leftUri = mockk<Uri>(name = "left")
        val rightUri = mockk<Uri>(name = "right")
        viewModel.setLeftImage(leftUri)
        viewModel.setRightImage(rightUri)
        viewModel.swapImages()

        val state = viewModel.uiState.value
        assertEquals(rightUri, state.leftImageUri)
        assertEquals(leftUri, state.rightImageUri)
    }

    @Test
    fun `swapImages resets result`() = runTest {
        val leftUri = mockk<Uri>(name = "left")
        val rightUri = mockk<Uri>(name = "right")
        viewModel.setLeftImage(leftUri)
        viewModel.setRightImage(rightUri)
        viewModel.swapImages()

        assertNull(viewModel.uiState.value.result)
    }

    @Test
    fun `clearResult clears processing flag and result`() = runTest {
        val leftUri = mockk<Uri>(name = "left")
        val rightUri = mockk<Uri>(name = "right")
        viewModel.setLeftImage(leftUri)
        viewModel.setRightImage(rightUri)
        viewModel.clearResult()

        val state = viewModel.uiState.value
        assertFalse(state.isProcessing)
        assertNull(state.result)
        assertNotNull(state.leftImageUri)
        assertNotNull(state.rightImageUri)
    }

    @Test
    fun `clearResult preserves selected images`() = runTest {
        val leftUri = mockk<Uri>(name = "left")
        val rightUri = mockk<Uri>(name = "right")
        viewModel.setLeftImage(leftUri)
        viewModel.setRightImage(rightUri)
        viewModel.clearResult()

        assertEquals(leftUri, viewModel.uiState.value.leftImageUri)
        assertEquals(rightUri, viewModel.uiState.value.rightImageUri)
    }

    @Test
    fun `reset clears everything back to initial state`() = runTest {
        viewModel.setLeftImage(mockk(name = "left"))
        viewModel.setRightImage(mockk(name = "right"))
        viewModel.reset()

        val state = viewModel.uiState.value
        assertNull(state.leftImageUri)
        assertNull(state.rightImageUri)
        assertFalse(state.isProcessing)
        assertNull(state.result)
    }

    @Test
    fun `compare does nothing when leftImage is null`() = runTest {
        viewModel.setRightImage(mockk(name = "right"))
        viewModel.compare()
        assertNull(viewModel.uiState.value.result)
    }

    @Test
    fun `compare does nothing when rightImage is null`() = runTest {
        viewModel.setLeftImage(mockk(name = "left"))
        viewModel.compare()
        assertNull(viewModel.uiState.value.result)
    }

    @Test
    fun `setLeftImage updates leftImageUri and resets result`() = runTest {
        val uri = mockk<Uri>(name = "left")
        viewModel.setLeftImage(uri)
        assertEquals(uri, viewModel.uiState.value.leftImageUri)
        assertNull(viewModel.uiState.value.result)
    }

    @Test
    fun `setRightImage updates rightImageUri and resets result`() = runTest {
        val uri = mockk<Uri>(name = "right")
        viewModel.setRightImage(uri)
        assertEquals(uri, viewModel.uiState.value.rightImageUri)
        assertNull(viewModel.uiState.value.result)
    }

    @Test
    fun `processing guard blocks compare when isProcessing is true`() {
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
    fun `compare is enabled when both images are set and not processing`() {
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
}
