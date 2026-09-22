package com.example.engine

import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.view.View
import android.view.ViewGroup
import android.webkit.GeolocationPermissions
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import com.example.data.BrowserRepository
import com.example.model.BrowserTab
import java.io.ByteArrayInputStream

class ElephantWebViewClient(
    private val tab: BrowserTab,
    private val repository: BrowserRepository,
    private val onPageStart: (url: String) -> Unit,
    private val onPageFinish: (url: String, title: String) -> Unit,
    private val onAdBlocked: () -> Unit
) : WebViewClient() {

    private val adDomains = listOf(
        "googleads", "doubleclick.net", "pagead2", "adservice.google",
        "admob", "pos.baidu.com", "cpro.baidustatic.com", "union.baidu.com",
        "atanx.com", "alimama.com", "tanx.com", "adash.m.taobao.com",
        "sax.sina.com.cn", "adbox", "adsystem", "analytics", "statcounter"
    )

    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
        val url = request?.url?.toString() ?: return false
        if (url.startsWith("http://") || url.startsWith("https://") || url.startsWith("file://")) {
            return false // Let WebView load it
        }
        return try {
            // Handle external schemes (intent://, tel:, mailto:) gracefully
            false
        } catch (e: Exception) {
            true
        }
    }

    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
        super.onPageStarted(view, url, favicon)
        val currentUrl = url ?: return
        tab.url = currentUrl
        tab.isLoading = true
        onPageStart(currentUrl)

        // Inject document_start plugins
        repository.plugins.value.filter { it.isEnabled && it.runAt == "document_start" }.forEach { plugin ->
            view?.evaluateJavascript(plugin.scriptCode, null)
        }
    }

    override fun onPageFinished(view: WebView?, url: String?) {
        super.onPageFinished(view, url)
        val currentUrl = url ?: return
        tab.isLoading = false
        val currentTitle = view?.title ?: tab.title
        tab.title = currentTitle

        // Update navigation history if not incognito
        if (!tab.isIncognito) {
            repository.addHistory(currentTitle, currentUrl)
        }

        tab.canGoBack = view?.canGoBack() ?: false
        tab.canGoForward = view?.canGoForward() ?: false

        // Inject document_end plugins
        repository.plugins.value.filter { it.isEnabled && it.runAt == "document_end" }.forEach { plugin ->
            view?.evaluateJavascript(plugin.scriptCode, null)
        }

        // Apply night mode if tab or global night mode is enabled
        if (tab.isNightMode || repository.isNightMode.value) {
            view?.evaluateJavascript(Scripts.NIGHT_MODE_CSS, null)
        }

        // If translation is active for this tab, translate
        if (tab.isTranslated) {
            view?.evaluateJavascript(Scripts.TRANSLATION_SCRIPT, null)
        }

        // Probe for video elements
        view?.evaluateJavascript(Scripts.VIDEO_SNIFFER_PROBE, null)

        onPageFinish(currentUrl, currentTitle)
    }

    override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
        if (repository.isAdBlockEnabled.value) {
            val url = request?.url?.toString()?.lowercase() ?: return null
            for (domain in adDomains) {
                if (url.contains(domain)) {
                    repository.addSavedData(0.04f)
                    onAdBlocked()
                    // Return empty response to block request
                    return WebResourceResponse("text/plain", "UTF-8", ByteArrayInputStream("".toByteArray()))
                }
            }
        }
        return super.shouldInterceptRequest(view, request)
    }
}

class ElephantWebChromeClient(
    private val tab: BrowserTab,
    private val onProgressChange: (progress: Int) -> Unit,
    private val onTitleChange: (title: String) -> Unit,
    private val onIconChange: (icon: Bitmap?) -> Unit,
    private val onShowCustomVideo: (view: View, callback: WebChromeClient.CustomViewCallback) -> Unit,
    private val onHideCustomVideo: () -> Unit
) : WebChromeClient() {

    override fun onProgressChanged(view: WebView?, newProgress: Int) {
        super.onProgressChanged(view, newProgress)
        tab.progress = newProgress
        onProgressChange(newProgress)
    }

    override fun onReceivedTitle(view: WebView?, title: String?) {
        super.onReceivedTitle(view, title)
        title?.let {
            tab.title = it
            onTitleChange(it)
        }
    }

    override fun onReceivedIcon(view: WebView?, icon: Bitmap?) {
        super.onReceivedIcon(view, icon)
        tab.favicon = icon
        onIconChange(icon)
    }

    override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
        if (view != null && callback != null) {
            onShowCustomVideo(view, callback)
        }
    }

    override fun onHideCustomView() {
        onHideCustomVideo()
    }

    override fun onGeolocationPermissionsShowPrompt(
        origin: String?,
        callback: GeolocationPermissions.Callback?
    ) {
        callback?.invoke(origin, true, false)
    }
}
