package com.example.extension

/**
 * Centralized permission checks used by the compatibility runtime.
 *
 * Keeping this policy independent of WebView makes it replaceable by
 * Chromium's extension permission model later.
 */
interface ExtensionPermissionPolicy {
    fun hasPermission(extension: BrowserExtension, permission: String): Boolean
    fun canAccessUrl(extension: BrowserExtension, url: String): Boolean
}

class ManifestExtensionPermissionPolicy : ExtensionPermissionPolicy {
    override fun hasPermission(extension: BrowserExtension, permission: String): Boolean {
        if (!extension.enabled) return false
        return permission in extension.manifest.permissions ||
            permission in extension.manifest.hostPermissions ||
            (permission == "activeTab" && extension.enabled)
    }

    override fun canAccessUrl(extension: BrowserExtension, url: String): Boolean {
        if (!extension.enabled) return false
        val hosts = extension.manifest.hostPermissions
        if (hosts.isEmpty()) return false
        return ExtensionManager.matches(hosts, url)
    }
}
