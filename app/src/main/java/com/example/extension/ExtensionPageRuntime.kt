package com.example.extension

import android.webkit.WebView

interface ExtensionPageRuntime {
    fun attach(pageKey: String, webView: WebView, url: String, bridge: Any): ExtensionPageHost
    fun inject(host: ExtensionPageHost, script: String)
}

class WebViewExtensionPageRuntime : ExtensionPageRuntime {
    override fun attach(pageKey: String, webView: WebView, url: String, bridge: Any): ExtensionPageHost {
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        return WebViewExtensionPageHost(webView).also {
            it.addJavascriptInterface(bridge, "ElephantExtensionBridge")
        }
    }

    override fun inject(host: ExtensionPageHost, script: String) {
        host.post { host.evaluateJavascript(script) }
    }
}
