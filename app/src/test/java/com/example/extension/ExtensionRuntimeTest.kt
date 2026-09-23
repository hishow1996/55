package com.example.extension

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExtensionRuntimeTest {
    @Test
    fun manifestParsesMv2AndMv3Fields() {
        val manifest = ExtensionManifest.parse("""{
          "manifest_version": 3,
          "name": "Demo",
          "version": "2.0.0",
          "permissions": ["storage", "tabs"],
          "host_permissions": ["https://*.example.com/*"],
          "action": {"default_popup": "popup.html"},
          "content_scripts": [{
            "matches": ["https://*.example.com/*"],
            "js": ["content.js"],
            "run_at": "document_start"
          }]
        }""")
        assertEquals(3, manifest.manifestVersion)
        assertEquals("Demo", manifest.name)
        assertEquals("popup.html", manifest.popup)
        assertEquals("document_start", manifest.contentScripts[0].runAt)
        assertTrue(manifest.permissions.contains("storage"))
    }

    @Test
    fun chromeMatchPatternsRespectSchemeHostAndPath() {
        assertTrue(ExtensionManager.matches(listOf("https://*.example.com/*"), "https://www.example.com/a"))
        assertTrue(ExtensionManager.matches(listOf("https://*.example.com/*"), "https://example.com/a"))
        assertTrue(!ExtensionManager.matches(listOf("https://*.example.com/*"), "http://www.example.com/a"))
        assertTrue(!ExtensionManager.matches(listOf("https://*.example.com/private/*"), "https://www.example.com/public/a"))
        assertTrue(ExtensionManager.matches(listOf("<all_urls>"), "https://example.org/"))
    }
}
