package com.example.player

import com.example.model.VideoSession
import com.example.model.VideoSource
import com.example.model.VideoMediaInfo
import java.util.UUID

/**
 * Single source of truth for the currently handed-off video.
 *
 * WebView discovers the source, Media3 plays it, and both in-app/system
 * floating windows update this same session. This prevents position/state
 * drift when moving between player surfaces.
 */
object VideoPlaybackSessionManager {
    @Volatile
    private var session: VideoSession? = null

    @Synchronized
    fun start(video: VideoMediaInfo, source: VideoSource? = null): VideoSession {
        val resolvedSource = source ?: VideoSourceResolver.resolve(video)
        val current = session
        val sameVideo = current != null &&
            current.pageUrl == video.pageUrl &&
            current.tabIndex == (video.originTabIndex ?: -1) &&
            current.source == resolvedSource

        val next = if (sameVideo) {
            current!!.copy(
                title = video.title.ifBlank { current.title },
                durationMs = if (video.duration > 0) (video.duration * 1000).toLong() else current.durationMs
            )
        } else {
            VideoSession(
                id = UUID.randomUUID().toString(),
                pageUrl = video.pageUrl,
                tabIndex = video.originTabIndex ?: -1,
                title = video.title.ifBlank { "网页视频" },
                source = resolvedSource,
                positionMs = (video.currentTime * 1000).toLong().coerceAtLeast(0L),
                durationMs = (video.duration * 1000).toLong().coerceAtLeast(0L),
                isPlaying = true
            )
        }
        session = next
        return next
    }

    fun current(): VideoSession? = session

    @Synchronized
    fun updatePosition(positionMs: Long) {
        session = session?.copy(positionMs = positionMs.coerceAtLeast(0L))
    }

    @Synchronized
    fun updateDuration(durationMs: Long) {
        session = session?.copy(durationMs = durationMs.coerceAtLeast(0L))
    }

    @Synchronized
    fun updatePlaying(isPlaying: Boolean) {
        session = session?.copy(isPlaying = isPlaying)
    }

    /**
     * Returns the authoritative state for a handoff. If the same video is
     * already active, preserve its live position/play state/rate instead of
     * reinitializing from a stale VideoMediaInfo snapshot.
     */
    @Synchronized
    fun handoffState(video: VideoMediaInfo): VideoSession {
        val current = start(video)
        return current
    }

    @Synchronized
    fun rebindTab(newTabIndex: Int) {
        session = session?.copy(tabIndex = newTabIndex)
    }

    @Synchronized
    fun updatePlaybackRate(rate: Float) {
        session = session?.copy(playbackRate = rate.coerceIn(0.25f, 4.0f))
    }

    fun positionMsOr(fallbackMs: Long): Long =
        session?.positionMs?.coerceAtLeast(0L) ?: fallbackMs.coerceAtLeast(0L)

    @Synchronized
    fun clear() {
        session = null
    }
}
