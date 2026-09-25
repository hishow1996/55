package com.example.extension

import org.json.JSONObject

interface ExtensionEventHost {
    fun dispatch(extensionId: String, event: ExtensionBrowserEvent)
}

class NoOpExtensionEventHost : ExtensionEventHost {
    override fun dispatch(extensionId: String, event: ExtensionBrowserEvent) = Unit
}

class WebViewExtensionEventHost(
    private val backgroundHosts: () -> Map<String, ExtensionBackgroundHost>
) : ExtensionEventHost {
    override fun dispatch(extensionId: String, event: ExtensionBrowserEvent) {
        val host = backgroundHosts()[extensionId] ?: return
        val payload = when (event) {
            is ExtensionBrowserEvent.TabsCreated -> event.tab.toJson().toString()
            is ExtensionBrowserEvent.TabsUpdated -> event.tab.toJson().toString()
            is ExtensionBrowserEvent.RuntimeMessage -> event.message
        }
        val json = JSONObject.quote(payload)
        val script = when (event) {
            is ExtensionBrowserEvent.TabsCreated ->
                "window.__elephantTabsCreated&&window.__elephantTabsCreated(JSON.parse(" + json + "));"
            is ExtensionBrowserEvent.TabsUpdated ->
                "window.__elephantTabsUpdated&&window.__elephantTabsUpdated(JSON.parse(" + json + "),{},{});"
            is ExtensionBrowserEvent.RuntimeMessage ->
                "window.__elephantOnMessage&&window.__elephantOnMessage(JSON.parse(" + json + "),{id:" + JSONObject.quote(event.senderId) + "},function(){});"
        }
        host.post { host.evaluateJavascript(script) }
    }
}

sealed class ExtensionBrowserEvent {
    data class TabsCreated(val tab: TabSnapshot) : ExtensionBrowserEvent()
    data class TabsUpdated(val tab: TabSnapshot) : ExtensionBrowserEvent()
    data class RuntimeMessage(val message: String, val senderId: String) : ExtensionBrowserEvent()
}
