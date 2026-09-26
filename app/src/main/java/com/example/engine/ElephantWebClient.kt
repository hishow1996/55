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

class ElephantWebViewClient(
    private val tab: BrowserTab,
    private val repository: BrowserRepository,
    private val onPageStart: (url: String) -> Unit,
    private val onPageFinish: (url: String, title: String) -> Unit,
    private val onExtensionDownload: (url: String) -> Unit = {},
    private val onUserScriptDownload: (url: String) -> Unit = {}
) : WebViewClient() {

    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
        val url = request?.url?.toString() ?: return false
        val lower = url.lowercase()
        val looksLikeCrx = lower.contains("/service/update2/crx") ||
            lower.substringBefore("?").endsWith(".crx")
        if (looksLikeCrx) {
            onExtensionDownload(url)
            return true
        }
        if (lower.substringBefore("?").endsWith(".user.js")) {
            onUserScriptDownload(url)
            return true
        }
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
        if (currentUrl == "about:blank" || currentUrl.isBlank()) {
            tab.url = ""
            tab.isLoading = false
            tab.canGoBack = false
            tab.canGoForward = false
            tab.title = if (tab.isIncognito) "无痕新标签" else "大象浏览器"
            onPageStart("")
            return
        }
        // A tab can navigate from one video page to another. Drop the previous
        // page's native stream immediately so a new Blob/MSE page cannot inherit
        // and play an unrelated stream from the old page.
        repository.clearDetectedStreamUrl(tab.id)

        tab.url = currentUrl
        tab.isLoading = true
        onPageStart(currentUrl)

        // Inject document_start plugins
        val page = view ?: return
        repository.extensionManager.injectForPage(page, currentUrl, "document_start")
        repository.userScriptManager.injectForPage(page, currentUrl, "document_start")

        // When night mode is OFF, enforce light color scheme at document start
        if (!tab.isNightMode && !repository.isNightMode.value) {
            view?.evaluateJavascript(Scripts.ENFORCE_LIGHT_MODE_HEAD, null)
        }

        // When in Desktop Mode, inject desktop viewport and environment emulation
        if (tab.isDesktopMode) {
            view?.evaluateJavascript(Scripts.DESKTOP_MODE_INJECT, null)
        }

        // Inject stream sniffer early to intercept fetch and XHR video requests
        view?.evaluateJavascript(Scripts.STREAM_SNIFFER_SCRIPT, null)
    }

    override fun onPageFinished(view: WebView?, url: String?) {
        super.onPageFinished(view, url)
        val currentUrl = url ?: return
        if (currentUrl == "about:blank" || currentUrl.isBlank()) {
            tab.url = ""
            tab.isLoading = false
            tab.canGoBack = false
            tab.canGoForward = false
            tab.title = if (tab.isIncognito) "无痕新标签" else "大象浏览器"
            onPageFinish("", tab.title)
            return
        }
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
        val page = view ?: return
        repository.extensionManager.injectForPage(page, currentUrl, "document_end")
        repository.userScriptManager.injectForPage(page, currentUrl, "document_end")
        repository.userScriptManager.injectForPage(page, currentUrl, "document_idle")

        // Apply night mode if tab or global night mode is enabled, otherwise enforce clean white background
        if (tab.isNightMode || repository.isNightMode.value) {
            view?.evaluateJavascript(Scripts.NIGHT_MODE_CSS, null)
        } else {
            view?.evaluateJavascript(Scripts.ENFORCE_LIGHT_MODE_FULL, null)
        }

        // When in Desktop Mode, ensure desktop metrics and viewport override
        if (tab.isDesktopMode) {
            view?.evaluateJavascript(Scripts.DESKTOP_MODE_INJECT, null)
        }

        // If translation is active for this tab, translate
        if (tab.isTranslated) {
            view?.evaluateJavascript(Scripts.TRANSLATION_SCRIPT, null)
        }

        // Stream sniffer probe
        view?.evaluateJavascript(Scripts.STREAM_SNIFFER_SCRIPT, null)

        // Discover media first. The native player now owns playback for
        // directly resolvable MP4/HLS/DASH sources; the WebView player remains
        // available as a compatibility fallback for Blob/MSE/DRM pages.
        view?.evaluateJavascript(Scripts.VIDEO_STATE_MONITOR, null)
        view?.evaluateJavascript(Scripts.VIDEO_SNIFFER_PROBE, null)
        view?.evaluateJavascript(Scripts.UC_INLINE_PLAYER_SCRIPT, null)

        onPageFinish(currentUrl, currentTitle)
    }

    override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
        val reqUrl = request?.url?.toString() ?: return null
        val lowerUrl = reqUrl.lowercase()

        if (repository.isContentBlocking.value && BrowserContentBlocker.shouldBlock(reqUrl)) {
            return WebResourceResponse("text/plain", "UTF-8", 204, "No Content", emptyMap(), null)
        }

        // Sniff real playable streaming media URLs (m3u8, mp4, flv, ts streams)
        if (lowerUrl.contains(".m3u8") || lowerUrl.contains(".mp4") || lowerUrl.contains(".flv") || 
            (lowerUrl.contains("mime=") && lowerUrl.contains("video")) || lowerUrl.contains("/video/") ||
            lowerUrl.contains("googlevideo.com") || lowerUrl.contains(".ts")) {
            repository.setDetectedStreamUrl(tab.id, reqUrl)
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
        if (newProgress >= 90) {
            view?.evaluateJavascript(Scripts.UC_INLINE_PLAYER_SCRIPT, null)
        }
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
