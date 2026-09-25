package com.example.extension

import android.webkit.WebView

/**
 * Host boundary between the extension runtime and a browser page.
 *
 * The current build adapts Android WebView. A Chromium-native backend can
 * provide a host backed by a Chromium tab/frame without exposing WebView to
 * the extension runtime layer.
 */
interface ExtensionPageHost {
    fun addJavascriptInterface(obj: Any, name: String)
    fun evaluateJavascript(script: String)
    fun loadUrl(url: String)
    fun post(action: () -> Unit)
    fun destroy()
}

class WebViewExtensionPageHost(
    private val webView: WebView
) : ExtensionPageHost {
    override fun addJavascriptInterface(obj: Any, name: String) {
        webView.addJavascriptInterface(obj, name)
    }

    override fun evaluateJavascript(script: String) {
        webView.evaluateJavascript(script, null)
    }

    override fun loadUrl(url: String) {
        webView.loadUrl(url)
    }

    override fun post(action: () -> Unit) {
        webView.post(action)
    }

    override fun destroy() {
        webView.destroy()
    }

    fun matches(candidate: WebView): Boolean = candidate === webView
}
