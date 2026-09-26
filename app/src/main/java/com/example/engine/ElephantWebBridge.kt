package com.example.engine

import android.webkit.JavascriptInterface

class ElephantWebBridge(
    private val onVideoFound: (url: String, title: String, duration: Double, currentTime: Double, width: Int, height: Int) -> Unit,
    private val onVideoPlaybackState: ((currentTime: Double, isPlaying: Boolean) -> Unit)? = null,
    private val onDrmDetected: ((licenseUri: String, scheme: String, headers: Map<String, String>) -> Unit)? = null,
    private val onTranslationFinished: (success: Boolean, count: Int) -> Unit,
    private val onTextSelected: (text: String) -> Unit = {},
    private val onAdjustBrightness: ((delta: Float) -> Float)? = null,
    private val onAdjustVolume: ((delta: Float) -> Float)? = null,
    private val onOpenFloatingPlayer: ((url: String, title: String, currentTime: Double, duration: Double, width: Int, height: Int) -> Unit)? = null,
    private val onDownloadVideo: ((url: String, title: String) -> Unit)? = null,
    private val onShowToast: ((message: String) -> Unit)? = null
) {
    @JavascriptInterface
    fun onVideoDetected(url: String, title: String, duration: Double, currentTime: Double, width: Int, height: Int) {
        onVideoFound(url, title, duration, currentTime, width, height)
    }


    /** Reports the live HTML5 video state without replacing the detected source. */
    @JavascriptInterface
    fun onVideoPlaybackState(currentTime: Double, isPlaying: Boolean) {
        onVideoPlaybackState?.invoke(currentTime, isPlaying)
    }

    @JavascriptInterface
    fun onDrmDetected(licenseUri: String, scheme: String) {
        if (licenseUri.isBlank()) return
        onDrmDetected?.invoke(licenseUri.trim(), scheme.ifBlank { "widevine" }.lowercase(), emptyMap())
    }

    @JavascriptInterface
    fun onTranslationResult(success: Boolean, count: Int) {
        onTranslationFinished(success, count)
    }

    @JavascriptInterface
    fun onSelection(text: String) {
        onTextSelected(text)
    }

    @JavascriptInterface
    fun adjustBrightness(delta: Float): Float {
        return onAdjustBrightness?.invoke(delta) ?: 0.5f
    }

    @JavascriptInterface
    fun adjustVolume(delta: Float): Float {
        return onAdjustVolume?.invoke(delta) ?: 0.5f
    }

    @JavascriptInterface
    fun openFloatingPlayer(url: String, title: String, currentTime: Double, duration: Double, width: Int, height: Int) {
        onOpenFloatingPlayer?.invoke(url, title, currentTime, duration, width, height)
    }

    @JavascriptInterface
    fun downloadVideo(url: String, title: String) {
        onDownloadVideo?.invoke(url, title)
    }

    @JavascriptInterface
    fun showToast(message: String) {
        onShowToast?.invoke(message)
    }
}

