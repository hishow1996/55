package com.example.extension

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
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
    fun manifestParsesExtensionIconsAndDefaultIconFallback() {
        val manifest = ExtensionManifest.parse("""{
          "manifest_version": 3,
          "name": "Icons",
          "version": "1.0",
          "icons": {"16": "small.png", "128": "large.png"},
          "action": {"default_icon": {"16": "action16.png", "48": "action48.png"}}
        }""")
        assertEquals("large.png", manifest.iconPath)

        val fallback = ExtensionManifest.parse("""{
          "manifest_version": 3,
          "name": "Action Icon",
          "version": "1.0",
          "action": {"default_icon": {"16": "action16.png", "48": "action48.png"}}
        }""")
        assertEquals("action48.png", fallback.iconPath)
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
