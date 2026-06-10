package com.robin.tools.feature.ebook.ui

import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ConversionStateTest {

    @Test
    fun `Idle is the initial state`() {
        val state = ConversionState.Idle
        assertEquals(ConversionState.Idle, state)
    }

    @Test
    fun `Converting holds progress`() {
        val state = ConversionState.Converting(50)
        assertTrue(state is ConversionState.Converting)
        assertEquals(50, state.progress)
    }

    @Test
    fun `Converting with zero progress`() {
        val state = ConversionState.Converting(0)
        assertEquals(0, state.progress)
    }

    @Test
    fun `Converting with full progress`() {
        val state = ConversionState.Converting(100)
        assertEquals(100, state.progress)
    }

    @Test
    fun `Success holds cacheFile and publicUri`() {
        val cacheFile = File("/tmp/test.pdf")
        val publicUri = mockk<android.net.Uri>()
        val state = ConversionState.Success(cacheFile, publicUri)
        assertEquals(cacheFile, state.cacheFile)
        assertEquals(publicUri, state.publicUri)
    }

    @Test
    fun `Error holds message`() {
        val state = ConversionState.Error("Something went wrong")
        assertTrue(state is ConversionState.Error)
        assertEquals("Something went wrong", state.message)
    }

    @Test
    fun `Error with empty message`() {
        val state = ConversionState.Error("")
        assertEquals("", state.message)
    }

    @Test
    fun `Idle is a singleton`() {
        val state1 = ConversionState.Idle
        val state2 = ConversionState.Idle
        assertTrue(state1 === state2)
    }

    @Test
    fun `Different Converting instances with same progress are equal`() {
        val state1 = ConversionState.Converting(50)
        val state2 = ConversionState.Converting(50)
        assertEquals(state1, state2)
    }
}
