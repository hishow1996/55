package com.example.engine

import android.net.Uri

/**
 * Lightweight network-level blocker for common advertising/tracking endpoints.
 * It deliberately uses conservative host/path rules so ordinary site assets
 * are not accidentally intercepted.
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
        "/advert/",
        "/advertising/",
        "/tracking/",
        "/tracker/",
        "/analytics/",
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
