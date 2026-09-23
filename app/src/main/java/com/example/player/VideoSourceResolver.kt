package com.example.player

import com.example.model.VideoMediaInfo
import com.example.model.VideoSource

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
            lower.startsWith("http://") || lower.startsWith("https://") ->
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
}
