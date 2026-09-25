package com.example.extension

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExtensionLifecycleHostTest {
    @Test
    fun lifecycle_contract_is_independent_of_webview() {
        var running = false
        val host = object : ExtensionLifecycleHost {
            override fun start(extension: BrowserExtension): Boolean {
                running = true
                return true
            }
            override fun stop(extensionId: String): Boolean {
                running = false
                return true
            }
            override fun isRunning(extensionId: String) = running
        }

        val extension = BrowserExtension(
            id = "test",
            rootPath = "/tmp/test",
            manifest = ExtensionManifest(3, "Test", "1")
        )

        assertTrue(host.start(extension))
        assertTrue(host.isRunning("test"))
        assertTrue(host.stop("test"))
        assertFalse(host.isRunning("test"))
    }
}
