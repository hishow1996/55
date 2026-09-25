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
    fun evaluateJavascript(script: String)
    fun post(action: () -> Unit)
    fun destroy()
}

class WebViewExtensionBackgroundHost(
    private val webView: WebView
) : ExtensionBackgroundHost {
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
