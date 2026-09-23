package com.example.player

import android.content.Context
import android.view.Surface
import androidx.media3.common.MediaItem
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
    private val player = ExoPlayer.Builder(appContext)
        .setMediaSourceFactory(DefaultMediaSourceFactory(httpFactory))
        .build()

    fun setHeaders(pageUrl: String?) {
        val headers = linkedMapOf(
            "User-Agent" to android.webkit.WebSettings.getDefaultUserAgent(appContext),
            "Accept" to "*/*"
        )
        if (!pageUrl.isNullOrBlank()) {
            headers["Referer"] = pageUrl
        }
        httpFactory.setDefaultRequestProperties(headers)
    }

    fun setSurface(surface: Surface?) {
        player.setVideoSurface(surface)
    }

    fun load(video: VideoMediaInfo, startPositionMs: Long = 0L) {
        setHeaders(video.pageUrl)
        val url = video.url.trim()
        if (url.isBlank() || url.startsWith("blob:", ignoreCase = true)) {
            throw IllegalArgumentException("当前视频没有可供原生播放器使用的媒体地址")
        }

        val lower = url.lowercase()
        val mimeType = when {
            lower.contains(".m3u8") || lower.contains("application/vnd.apple.mpegurl") ->
                MimeTypes.APPLICATION_M3U8
            lower.contains(".mpd") || lower.contains("application/dash+xml") ->
                MimeTypes.APPLICATION_MPD
            else -> null
        }

        val itemBuilder = MediaItem.Builder()
            .setUri(url)
            .setMediaId(video.pageUrl.ifBlank { url })

        if (mimeType != null) {
            itemBuilder.setMimeType(mimeType)
        }

        player.setMediaItem(itemBuilder.build())

        player.setMediaItem(item)
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
