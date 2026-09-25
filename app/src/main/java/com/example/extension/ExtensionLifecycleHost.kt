package com.example.extension

/**
 * Lifecycle boundary for an installed extension.
 *
 * The compatibility backend can start/stop a WebView background host.
 * A Chromium-native backend can map these calls to the extension service
 * worker/background context lifecycle.
 */
interface ExtensionLifecycleHost {
    fun start(extension: BrowserExtension): Boolean
    fun stop(extensionId: String): Boolean
    fun isRunning(extensionId: String): Boolean
}
