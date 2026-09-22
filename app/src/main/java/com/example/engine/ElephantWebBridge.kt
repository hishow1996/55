package com.example.engine

import android.webkit.JavascriptInterface

class ElephantWebBridge(
    private val onVideoFound: (url: String, title: String, duration: Double, currentTime: Double, width: Int, height: Int) -> Unit,
    private val onTranslationFinished: (success: Boolean, count: Int) -> Unit,
    private val onTextSelected: (text: String) -> Unit = {}
) {
    @JavascriptInterface
    fun onVideoDetected(url: String, title: String, duration: Double, currentTime: Double, width: Int, height: Int) {
        onVideoFound(url, title, duration, currentTime, width, height)
    }

    @JavascriptInterface
    fun onTranslationResult(success: Boolean, count: Int) {
        onTranslationFinished(success, count)
    }

    @JavascriptInterface
    fun onSelection(text: String) {
        onTextSelected(text)
    }
}
