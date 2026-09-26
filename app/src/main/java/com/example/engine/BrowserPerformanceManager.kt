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
        settings.setSupportZoom(true)
        settings.builtInZoomControls = true
        settings.displayZoomControls = false
        // Match Chromium/Chrome Android's wide-viewport text layout instead of
        // WebView's legacy narrow-column layout. The latter can make desktop
        // sites (especially Chrome Web Store) collapse multiple columns into
        // the phone width and appear visually mixed together.
        settings.layoutAlgorithm = WebSettings.LayoutAlgorithm.TEXT_AUTOSIZING
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
