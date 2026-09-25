package com.example.extension

import org.junit.Assert.assertTrue
import org.junit.Test

class ExtensionPageRuntimeTest {
    @Test
    fun webViewRuntime_isConcretePageRuntime() {
        val runtime: ExtensionPageRuntime = WebViewExtensionPageRuntime()
        assertTrue(runtime is WebViewExtensionPageRuntime)
    }
}
