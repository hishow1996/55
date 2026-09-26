package com.example.extension

import org.json.JSONObject

interface ExtensionEventHost {
    fun dispatch(extensionId: String, event: ExtensionBrowserEvent)
}

class NoOpExtensionEventHost : ExtensionEventHost {
    override fun dispatch(extensionId: String, event: ExtensionBrowserEvent) = Unit
}

class WebViewExtensionEventHost(
    private val backgroundHosts: () -> Map<String, ExtensionBackgroundHost>,
    private val pageHosts: () -> Collection<ExtensionPageHost> = { emptyList() },
    private val extensionPageHosts: (String) -> Collection<ExtensionPageHost> = { pageHosts() }
) : ExtensionEventHost {
    override fun dispatch(extensionId: String, event: ExtensionBrowserEvent) {
        val host = when (event) {
            is ExtensionBrowserEvent.RuntimeMessage ->
                if (event.destination == ExtensionBrowserEvent.MessageDestination.PAGES) null else backgroundHosts()[extensionId]
            else -> backgroundHosts()[extensionId]
        }
        if (event is ExtensionBrowserEvent.RuntimeMessage && event.destination == ExtensionBrowserEvent.MessageDestination.PAGES) {
            val message = JSONObject.quote(event.message)
            val sender = JSONObject.quote(event.senderId)
            extensionPageHosts(extensionId).distinct().forEach { page ->
                page.post {
                    page.evaluateJavascript(
                        "window.dispatchEvent(new CustomEvent('elephant-extension-message',{detail:JSON.parse($message)}));" +
                            "if(window.__elephantRuntimeOnMessage)window.__elephantRuntimeOnMessage(JSON.parse($message),{id:$sender},function(){});"
                    )
                }
            }
            return
        }
        host ?: return
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
            is ExtensionBrowserEvent.RuntimeMessage -> {
                val callback = "window.__elephantRuntimeOnMessage&&window.__elephantRuntimeOnMessage(JSON.parse(" + json + "),{id:" + JSONObject.quote(event.senderId) + "},function(){});"
                callback
            }
        }
        host.post { host.evaluateJavascript(script) }
    }
}

sealed class ExtensionBrowserEvent {
    data class TabsCreated(val tab: TabSnapshot) : ExtensionBrowserEvent()
    data class TabsUpdated(val tab: TabSnapshot) : ExtensionBrowserEvent()
    data class RuntimeMessage(
        val message: String,
        val senderId: String,
        val destination: MessageDestination
    ) : ExtensionBrowserEvent()

    enum class MessageDestination {
        BACKGROUND,
        PAGES
    }
}
