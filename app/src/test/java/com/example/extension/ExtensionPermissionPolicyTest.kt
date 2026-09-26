package com.example.extension

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ExtensionPermissionPolicyTest {
    private val policy = ManifestExtensionPermissionPolicy()

    private fun extension(enabled: Boolean, permissions: List<String>, hosts: List<String>): BrowserExtension {
        return BrowserExtension(
            id = "test",
            rootPath = "/tmp/test",
            manifest = ExtensionManifest(
                manifestVersion = 3,
                name = "Test",
                version = "1",
                description = "",
                permissions = permissions,
                hostPermissions = hosts
            ),
            enabled = enabled
        )
    }

    @Test
    fun disabled_extension_has_no_permissions() {
        assertFalse(policy.hasPermission(extension(false, listOf("tabs"), emptyList()), "tabs"))
    }

    @Test
    fun manifest_permission_is_checked_without_webview() {
        assertTrue(policy.hasPermission(extension(true, listOf("tabs"), emptyList()), "tabs"))
        assertFalse(policy.hasPermission(extension(true, emptyList(), emptyList()), "tabs"))
    }

    @Test
    fun host_access_uses_manifest_match_patterns() {
        val ext = extension(true, emptyList(), listOf("https://*.example.com/*"))
        assertTrue(policy.canAccessUrl(ext, "https://www.example.com/page"))
        assertFalse(policy.canAccessUrl(ext, "https://example.org/page"))
    }
}
