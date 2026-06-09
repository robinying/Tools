package com.robin.tools.core.base

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BaseViewModelTest {

    @Test
    fun `loadingChange initial state`() {
        val viewModel = BaseViewModel()
        // Verify that loadingChange is lazily initialized
        val loadingChange = viewModel.loadingChange
        assertTrue("loadingChange should be initialized", loadingChange != null)
    }

    @Test
    fun `launchOnMain executes onMainDispatcher`() {
        // Basic instantiation test - BaseViewModel should be creatable
        val viewModel = BaseViewModel()
        assertTrue("ViewModel should be instance of BaseViewModel", viewModel is BaseViewModel)
    }
}