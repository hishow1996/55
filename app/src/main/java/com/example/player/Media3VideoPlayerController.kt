package com.example.player

import android.content.Context
import android.view.Surface
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.example.model.VideoMediaInfo

/**
 * Unified native video playback core.
 *
 * UI is intentionally kept outside this class: the existing in-app player and
 * floating-window controls remain responsible for presentation, while this
 * controller owns the actual Media3/ExoPlayer playback state.
 */
class Media3VideoPlayerController(context: Context) {

    private val appContext = context.applicationContext
    private val httpFactory = DefaultHttpDataSource.Factory()
        .setAllowCrossProtocolRedirects(true)
        .setConnectTimeoutMs(15_000)
        .setReadTimeoutMs(30_000)
    private val player = ExoPlayer.Builder(appContext)
        .setMediaSourceFactory(DefaultMediaSourceFactory(httpFactory))
        .setWakeMode(C.WAKE_MODE_NETWORK)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                .build(),
            true
        )
        .setHandleAudioBecomingNoisy(true)
        .build()

    fun setHeaders(pageUrl: String?, mediaUrl: String? = null) {
        val headers = linkedMapOf(
            "User-Agent" to android.webkit.WebSettings.getDefaultUserAgent(appContext),
            "Accept" to "*/*"
        )
        if (!pageUrl.isNullOrBlank()) headers["Referer"] = pageUrl

        val cookieManager = android.webkit.CookieManager.getInstance()
        val mediaCookies = mediaUrl?.takeIf { it.isNotBlank() }
            ?.let { cookieManager.getCookie(it) }
            ?.takeIf { it.isNotBlank() }
        val pageCookies = pageUrl?.takeIf { it.isNotBlank() }
            ?.let { cookieManager.getCookie(it) }
            ?.takeIf { it.isNotBlank() }
        (mediaCookies ?: pageCookies)?.let { headers["Cookie"] = it }
        httpFactory.setDefaultRequestProperties(headers)
    }

    fun setSurface(surface: Surface?) {
        player.setVideoSurface(surface)
    }

    fun load(video: VideoMediaInfo, startPositionMs: Long = 0L) {
        val url = video.url.trim()
        setHeaders(video.pageUrl, url)
        if (url.isBlank() || url.startsWith("blob:", ignoreCase = true)) {
            throw IllegalArgumentException("当前视频没有可供原生播放器使用的媒体地址")
        }

        val lower = url.lowercase()
        val decoded = runCatching {
            java.net.URLDecoder.decode(lower, "UTF-8")
        }.getOrDefault(lower)

        // CDN/media endpoints frequently hide their actual MIME type in a query
        // parameter instead of using a .mp4/.webm extension. Give Media3 an
        // explicit type in those cases so DefaultMediaSourceFactory can choose
        // the correct media source instead of guessing from the URI.
        val mimeType = when {
            lower.contains(".m3u8") ||
                lower.contains("application/vnd.apple.mpegurl") ||
                lower.contains("application/x-mpegurl") ||
                decoded.contains("application/vnd.apple.mpegurl") ->
                MimeTypes.APPLICATION_M3U8
            lower.contains(".mpd") ||
                lower.contains("application/dash+xml") ||
                decoded.contains("application/dash+xml") ->
                MimeTypes.APPLICATION_MPD
            lower.contains("mime=video/mp4") ||
                lower.contains("type=video/mp4") ||
                decoded.contains("mime=video/mp4") ||
                decoded.contains("type=video/mp4") ->
                MimeTypes.VIDEO_MP4
            lower.contains("mime=video/webm") ||
                lower.contains("type=video/webm") ||
                decoded.contains("mime=video/webm") ||
                decoded.contains("type=video/webm") ->
                MimeTypes.VIDEO_WEBM
            lower.contains("mime=video/3gpp") ||
                lower.contains("type=video/3gpp") ||
                decoded.contains("mime=video/3gpp") ||
                decoded.contains("type=video/3gpp") ->
                MimeTypes.VIDEO_MP4
            else -> null
        }

        val itemBuilder = MediaItem.Builder()
            .setUri(url)
            .setMediaId(video.pageUrl.ifBlank { url })
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(video.title.ifBlank { "网页视频" })
                    .build()
            )

        if (mimeType != null) {
            itemBuilder.setMimeType(mimeType)
        }

        // Use Android MediaDrm/Media3 for authorized protected streams. The
        // license endpoint and request headers come from the page's own DRM
        // request; the browser never handles or stores decrypted keys.
        val drmUri = video.drmLicenseUri?.trim().orEmpty()
        if (drmUri.isNotBlank()) {
            val scheme = video.drmScheme?.lowercase()
            val drmUuid = when (scheme) {
                "playready" -> C.PLAYREADY_UUID
                "clearkey" -> C.CLEARKEY_UUID
                else -> C.WIDEVINE_UUID
            }
            val drmBuilder = MediaItem.DrmConfiguration.Builder(drmUuid)
                .setLicenseUri(drmUri)
                .setMultiSession(true)
                .setForceDefaultLicenseUri(true)

            if (video.drmLicenseHeaders.isNotEmpty()) {
                drmBuilder.setLicenseRequestHeaders(video.drmLicenseHeaders)
            }
            itemBuilder.setDrmConfiguration(drmBuilder.build())
        }

        player.setMediaItem(itemBuilder.build())
        player.repeatMode = Player.REPEAT_MODE_OFF
        player.prepare()
        if (startPositionMs > 0L) {
            player.seekTo(startPositionMs)
        }
    }

    fun play() {
        player.play()
    }

    fun pause() {
        player.pause()
    }

    fun seekTo(positionMs: Long) {
        player.seekTo(positionMs.coerceAtLeast(0L))
    }

    fun isPlaying(): Boolean = player.isPlaying

    fun currentPositionMs(): Long = player.currentPosition

    fun durationMs(): Long = player.duration.takeIf { it >= 0L } ?: 0L

    fun addListener(listener: Player.Listener) {
        player.addListener(listener)
    }

    fun removeListener(listener: Player.Listener) {
        player.removeListener(listener)
    }

    fun release() {
        player.release()
    }

    fun rawPlayer(): ExoPlayer = player
}
