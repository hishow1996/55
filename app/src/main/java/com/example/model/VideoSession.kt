package com.example.model

sealed class VideoSource {
    data class Direct(val url: String) : VideoSource()
    data class Hls(val url: String) : VideoSource()
    data class Dash(val url: String) : VideoSource()
    data class WebFallback(val url: String?) : VideoSource()
}

data class VideoSession(
    val id: String,
    val pageUrl: String,
    val tabIndex: Int,
    val tabId: String? = null,
    val title: String,
    val source: VideoSource,
    val drmScheme: String? = null,
    val drmLicenseUri: String? = null,
    val drmLicenseHeaders: Map<String, String> = emptyMap(),
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val isPlaying: Boolean = false,
    val playbackRate: Float = 1.0f
)
