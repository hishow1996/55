package com.example.extension

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExtensionRuntimeBackendTest {
    @Test
    fun currentRuntimeIsExplicitlyWebViewCompatibility() {
        assertTrue(CurrentExtensionRuntime.kind == ExtensionRuntimeBackend.Kind.WEBVIEW_COMPATIBILITY)
        assertFalse(CurrentExtensionRuntime.supportsNativeChromiumApis)
        assertTrue(CurrentExtensionRuntime.descriptor.supportsManifestV2)
        assertTrue(CurrentExtensionRuntime.descriptor.supportsManifestV3)
    }
}


    @Test
    fun resourceUrlRejectsPathTraversal() {
        val ext = BrowserExtension(
            id = "test",
            rootPath = System.getProperty("java.io.tmpdir"),
            manifest = ExtensionManifest(3, "Test", "1.0")
        )
        try {
            CurrentExtensionRuntime.resourceUrl(ext, "../outside.js")
            throw AssertionError("path traversal must be rejected")
        } catch (_: IllegalArgumentException) {
            // expected
        }
    }
