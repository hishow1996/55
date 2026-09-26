package com.example.engine

import android.os.Build
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView

/**
 * Browser-wide WebView tuning kept in one place so every tab gets the same
 * compatibility/performance policy.
 */
object BrowserPerformanceManager {
    fun configure(webView: WebView, incognito: Boolean, desktop: Boolean, nightMode: Boolean) {
        val settings = webView.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.databaseEnabled = true
        settings.mediaPlaybackRequiresUserGesture = false
        // Web pages use the dedicated PinchZoomWebView gesture layer.
        // Disable WebView's legacy zoom/double-tap path so there is only one
        // zoom implementation and pinch response stays predictable.
        settings.setSupportZoom(false)
        settings.builtInZoomControls = false
        settings.displayZoomControls = false
        // Desktop pages already provide their own responsive CSS. Keep the
        // browser's CSS layout dimensions intact in desktop mode; text
        // autosizing can change measured font boxes and make grid/flex layouts
        // (notably the Chrome Web Store) overlap on a phone-sized WebView.
        settings.layoutAlgorithm = if (desktop) {
            WebSettings.LayoutAlgorithm.NORMAL
        } else {
            WebSettings.LayoutAlgorithm.TEXT_AUTOSIZING
        }
        settings.textZoom = 100
        settings.useWideViewPort = desktop
        settings.loadWithOverviewMode = desktop
        settings.cacheMode = if (incognito) WebSettings.LOAD_NO_CACHE else WebSettings.LOAD_DEFAULT
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            settings.safeBrowsingEnabled = true
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            settings.forceDark = if (nightMode) WebSettings.FORCE_DARK_ON else WebSettings.FORCE_DARK_OFF
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            settings.isAlgorithmicDarkeningAllowed = nightMode
        }

        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)

        // Explicitly tell WebView that network connectivity is available. This
        // avoids a stale connectivity=false state after the app resumes or the
        // system network changes.
        webView.setNetworkAvailable(true)

        webView.setLayerType(WebView.LAYER_TYPE_HARDWARE, null)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            webView.setRendererPriorityPolicy(
                WebView.RENDERER_PRIORITY_IMPORTANT,
                false
            )
        }
    }

    fun onAppTrimMemory(level: Int, webViews: Collection<WebView>) {
        if (level >= android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL) {
            webViews.forEach { view ->
                view.stopLoading()
                view.clearMatches()
            }
        }
    }
}
