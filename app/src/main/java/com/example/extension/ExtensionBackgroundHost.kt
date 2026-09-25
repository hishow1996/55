package com.example.extension

import android.content.Context
import android.webkit.WebView
import android.webkit.WebViewClient

/**
 * Host boundary for an extension background page/service worker.
 *
 * Android WebView is only one implementation. Chromium's ExtensionService /
 * ExtensionHost implementation can replace it without changing ExtensionManager.
 */
interface ExtensionBackgroundHost {
    fun addJavascriptInterface(obj: Any, name: String)
    fun loadDataWithBaseURL(baseUrl: String, data: String)
    fun evaluateJavascript(script: String)
    fun post(action: () -> Unit)
    fun destroy()
}

class WebViewExtensionBackgroundHost(
    private val webView: WebView
) : ExtensionBackgroundHost {
    override fun addJavascriptInterface(obj: Any, name: String) { webView.addJavascriptInterface(obj, name) }

    override fun loadDataWithBaseURL(baseUrl: String, data: String) {
        webView.loadDataWithBaseURL(baseUrl, data, "text/html", "UTF-8", null)
    }

    override fun evaluateJavascript(script: String) {
        webView.evaluateJavascript(script, null)
    }

    override fun post(action: () -> Unit) {
        webView.post(action)
    }

    override fun destroy() {
        webView.destroy()
    }

    fun matches(candidate: WebView): Boolean = candidate === webView
}

fun createWebViewBackgroundHost(context: Context): WebViewExtensionBackgroundHost {
    val webView = WebView(context)
    webView.settings.javaScriptEnabled = true
    webView.settings.domStorageEnabled = true
    webView.webViewClient = WebViewClient()
    return WebViewExtensionBackgroundHost(webView)
}
