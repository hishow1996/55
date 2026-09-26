package com.example.engine

import android.net.Uri

/**
 * Lightweight network-level blocker for common advertising/tracking endpoints.
 * Host rules are preferred; only highly specific ad/pixel paths are blocked.
 * Generic paths such as /analytics/, /tracking/ and /tracker/ are intentionally
 * allowed because video sites often use those paths for required API requests.
 */
object BrowserContentBlocker {
    private val blockedHosts = setOf(
        "doubleclick.net",
        "googlesyndication.com",
        "googleadservices.com",
        "adservice.google.com",
        "ads.yahoo.com",
        "adnxs.com",
        "adsrvr.org",
        "scorecardresearch.com",
        "criteo.com",
        "taboola.com",
        "outbrain.com",
        "quantserve.com",
        "zedo.com",
        "moatads.com"
    )

    private val blockedPathTokens = listOf(
        "/ads/",
        "/adserver/",
        "/pixel.gif",
        "/pixel.png"
    )

    fun shouldBlock(url: String): Boolean {
        if (url.isBlank()) return false
        val uri = runCatching { Uri.parse(url) }.getOrNull() ?: return false
        val host = uri.host?.lowercase().orEmpty()
        if (host.isBlank()) return false
        if (blockedHosts.any { host == it || host.endsWith(".$it") }) return true
        val path = uri.path?.lowercase().orEmpty()
        return blockedPathTokens.any { path.contains(it) }
    }
}
