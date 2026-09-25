package com.example.extension

import android.webkit.WebView

interface ExtensionPageSurface {
    fun webView(): WebView
}

class WebViewExtensionPageSurface(private val view: WebView) : ExtensionPageSurface {
    override fun webView(): WebView = view
}

interface ExtensionPageRuntime {
    fun attach(pageKey: String, surface: ExtensionPageSurface, url: String, bridge: Any): ExtensionPageHost
    fun inject(host: ExtensionPageHost, script: String)
    fun matches(host: ExtensionPageHost, surface: ExtensionPageSurface): Boolean
}

class WebViewExtensionPageRuntime : ExtensionPageRuntime {
    override fun attach(pageKey: String, surface: ExtensionPageSurface, url: String, bridge: Any): ExtensionPageHost {
        val webView = surface.webView()
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        return WebViewExtensionPageHost(webView).also {
            it.addJavascriptInterface(bridge, "ElephantExtensionBridge")
        }
    }

    override fun inject(host: ExtensionPageHost, script: String) {
        host.post { host.evaluateJavascript(script) }
    }

    override fun matches(host: ExtensionPageHost, surface: ExtensionPageSurface): Boolean =
        (host as? WebViewExtensionPageHost)?.matches(surface.webView()) == true
}
