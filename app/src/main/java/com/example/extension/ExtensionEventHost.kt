package com.example.extension

/**
 * Event boundary between an extension runtime and the browser.
 *
 * The WebView compatibility runtime may dispatch events as JavaScript calls.
 * A Chromium-native implementation can map the same events to ExtensionHost
 * / ExtensionService event dispatch without exposing WebView details.
 */
interface ExtensionEventHost {
    fun dispatch(extensionId: String, event: ExtensionBrowserEvent)
}

sealed class ExtensionBrowserEvent {
    data class TabsCreated(val tab: TabSnapshot) : ExtensionBrowserEvent()
    data class TabsUpdated(val tab: TabSnapshot) : ExtensionBrowserEvent()
    data class RuntimeMessage(val message: String, val senderId: String) : ExtensionBrowserEvent()
}
