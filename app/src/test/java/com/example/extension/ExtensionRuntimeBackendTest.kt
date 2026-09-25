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
