package com.example.player

import com.example.model.VideoMediaInfo
import com.example.model.VideoSource
import java.net.URI

object VideoSourceResolver {
    fun resolve(video: VideoMediaInfo): VideoSource {
        val url = video.url.trim()
        if (url.isBlank() || url.startsWith("blob:", ignoreCase = true)) {
            return VideoSource.WebFallback(url.ifBlank { null })
        }

        val lower = url.lowercase()
        return when {
            lower.contains(".m3u8") || lower.contains("application/vnd.apple.mpegurl") ->
                VideoSource.Hls(url)
            lower.contains(".mpd") || lower.contains("application/dash+xml") ->
                VideoSource.Dash(url)
            isLikelyDirectVideo(url, video.pageUrl) ->
                VideoSource.Direct(url)
            else ->
                VideoSource.WebFallback(url)
        }
    }

    fun canUseNativePlayer(video: VideoMediaInfo): Boolean {
        return when (resolve(video)) {
            is VideoSource.Direct,
            is VideoSource.Hls,
            is VideoSource.Dash -> true
            is VideoSource.WebFallback -> false
        }
    }

    private fun isLikelyDirectVideo(url: String, pageUrl: String): Boolean {
        if (!url.startsWith("http://", true) && !url.startsWith("https://", true)) return false

        // Never hand the page itself to Media3. A page URL is navigation content,
        // not a media source, even if its path happens to contain "video".
        if (pageUrl.isNotBlank() && urlsEquivalent(url, pageUrl)) return false

        val lower = url.lowercase()
        val mediaExtensions = listOf(
            ".mp4", ".m4v", ".webm", ".mkv", ".mov", ".avi", ".flv", ".3gp", ".ogv"
        )
        if (mediaExtensions.any { lower.substringBefore('?').substringBefore('#').endsWith(it) }) return true

        // Signed/CDN URLs often hide the extension in query parameters.
        val videoMarkers = listOf(
            "mime=video/", "type=video/", "content-type=video/",
            "format=mp4", "format=webm", "video=true", "media=true"
        )
        if (videoMarkers.any { lower.contains(it) }) return true

        // Google video delivery URLs are media endpoints even when the path
        // does not expose a conventional extension.
        if (lower.contains("googlevideo.com/")) return true

        return false
    }

    private fun urlsEquivalent(first: String, second: String): Boolean {
        return try {
            URI(first).normalize().toString().trimEnd('/') ==
                URI(second).normalize().toString().trimEnd('/')
        } catch (_: Exception) {
            first.trimEnd('/') == second.trimEnd('/')
        }
    }
}
