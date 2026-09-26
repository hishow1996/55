package com.example.player

import android.content.Context
import android.view.Surface
import android.os.Looper
import androidx.media3.common.Player
import androidx.media3.common.PlaybackException
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import com.example.model.VideoMediaInfo
import com.example.model.VideoSource

/**
 * Application-wide owner of the single native video player.
 *
 * WebView discovers media, but this object owns the only real playback engine.
 * Player UI surfaces may attach/detach without creating another ExoPlayer.
 */
object NativeVideoPlaybackManager {
    private fun assertMainThread() {
        check(Looper.myLooper() == Looper.getMainLooper()) { "NativeVideoPlaybackManager must be accessed from the main thread" }
    }
    private var controller: Media3VideoPlayerController? = null
    private var activeSessionId: String? = null
    private var listenerInstalled = false
    private var playbackErrorListener: ((PlaybackException) -> Unit)? = null
    private var mediaSession: MediaSession? = null
    // Keeps playback intent independent from TextureView/Surface lifecycle.
    // A configuration change can temporarily detach the video surface without
    // meaning that the user paused the video.
    private var resumeAfterSurfaceReattach = false
    // Explicit user playback intent. Surface/configuration lifecycle events must not overwrite it.
    private var playIntent = false

    @Synchronized
    fun start(context: Context, video: VideoMediaInfo, autoPlay: Boolean = true): Boolean {
        assertMainThread()

        // The native player is only allowed to receive an actual media source.
        // Page URLs, blob URLs and other unresolved WebView URLs must stay in
        // the WebView fallback path rather than being handed to ExoPlayer.
        val resolvedSource = VideoSourceResolver.resolve(video)
        if (resolvedSource is VideoSource.WebFallback) {
            return false
        }

        val session = VideoPlaybackSessionManager.start(video, resolvedSource)
        val playerController = ensureController(context)

        if (activeSessionId != session.id) {
            activeSessionId = session.id
            return try {
                playerController.load(video, session.positionMs)
                playerController.rawPlayer().setPlaybackSpeed(session.playbackRate)
                if (autoPlay) {
                    playIntent = true
                    playerController.play()
                } else {
                    playIntent = false
                    playerController.pause()
                }
                true
            } catch (_: Exception) {
                activeSessionId = null
                false
            }
        }

        playerController.rawPlayer().setPlaybackSpeed(session.playbackRate)
        if (autoPlay) {
            playIntent = true
            playerController.play()
        } else {
            playIntent = false
            playerController.pause()
        }
        return true
    }

    @Synchronized
    private fun ensureController(context: Context): Media3VideoPlayerController {
        controller?.let { return it }
        val created = Media3VideoPlayerController(context.applicationContext)
        controller = created
        if (!listenerInstalled) {
            created.addListener(object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    VideoPlaybackSessionManager.updatePlaying(isPlaying)
                }

                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == Player.STATE_READY) {
                        VideoPlaybackSessionManager.updateDuration(created.durationMs())
                        // The Surface may have been recreated while ExoPlayer was preparing.
                        // Restore the explicit playback intent once the new pipeline is ready.
                        if (playIntent && !created.isPlaying) {
                            created.play()
                        }
                    }
                }

                override fun onPlayerError(error: PlaybackException) {
                    VideoPlaybackSessionManager.updatePosition(created.currentPositionMs())
                    VideoPlaybackSessionManager.updatePlaying(false)
                    playbackErrorListener?.invoke(error)
                }
            })
            listenerInstalled = true
        }
        return created
    }

    @Synchronized
    fun attachSurface(surface: Surface?) {
        assertMainThread()
        val playerController = controller ?: return

        // Configuration changes (especially the forced portrait -> landscape
        // transition used by native fullscreen) may destroy and recreate the
        // TextureView Surface. Rebinding the new Surface must also restore the
        // previous playback intent; otherwise ExoPlayer can keep its clock alive
        // while the newly created surface remains visually frozen.
        playerController.setSurface(surface)

        if (surface != null) {
            val session = VideoPlaybackSessionManager.current()
            val shouldResume = resumeAfterSurfaceReattach || playIntent || session?.isPlaying == true
            resumeAfterSurfaceReattach = false
            if (shouldResume) {
                playerController.play()
                VideoPlaybackSessionManager.updatePlaying(true)
            }
        }
    }

    @Synchronized
    fun detachSurface() {
        assertMainThread()
        val currentPlayer = controller
        resumeAfterSurfaceReattach = currentPlayer?.isPlaying == true ||
            VideoPlaybackSessionManager.current()?.isPlaying == true
        currentPlayer?.setSurface(null)
    }

    fun player(): ExoPlayer? {
        assertMainThread()
        return controller?.rawPlayer()
    }

    fun play() {
        assertMainThread()
        playIntent = true
        controller?.play()
        VideoPlaybackSessionManager.updatePlaying(true)
    }

    fun pause() {
        assertMainThread()
        playIntent = false
        resumeAfterSurfaceReattach = false
        controller?.pause()
        VideoPlaybackSessionManager.updatePlaying(false)
    }

    fun seekTo(positionMs: Long) {
        assertMainThread()
        val duration = controller?.durationMs() ?: 0L
        val target = positionMs.coerceAtLeast(0L).let { requested ->
            if (duration > 0L) requested.coerceAtMost(duration) else requested
        }
        controller?.seekTo(target)
        VideoPlaybackSessionManager.updatePosition(target)
    }

    fun currentPositionMs(): Long {
        assertMainThread()
        return controller?.currentPositionMs() ?: VideoPlaybackSessionManager.current()?.positionMs ?: 0L
    }

    fun durationMs(): Long {
        assertMainThread()
        return controller?.durationMs() ?: VideoPlaybackSessionManager.current()?.durationMs ?: 0L
    }

    /**
     * Returns the aspect ratio of the decoded native video frame when Media3 has
     * reported it. This is deliberately read from ExoPlayer rather than from the
     * WebView <video> element, because transport dimensions and displayed/decoded
     * dimensions are not always the same.
     */
    fun videoAspectRatio(fallback: Float = 16f / 9f): Float {
        assertMainThread()
        val size = controller?.rawPlayer()?.videoSize
        return if (size != null && size.width > 0 && size.height > 0) {
            (size.width.toFloat() * size.pixelWidthHeightRatio / size.height.toFloat())
                .coerceIn(0.42f, 2.38f)
        } else {
            fallback.coerceIn(0.42f, 2.38f)
        }
    }

    fun bufferedPositionMs(): Long {
        assertMainThread()
        return controller?.rawPlayer()?.bufferedPosition ?: 0L
    }

    fun playbackState(): Int {
        assertMainThread()
        return controller?.rawPlayer()?.playbackState ?: Player.STATE_IDLE
    }

    /** Re-assert playback after fullscreen/orientation transitions without overriding a real user pause. */
    fun resumeIfNeeded() {
        assertMainThread()
        if (playIntent) {
            controller?.play()
            VideoPlaybackSessionManager.updatePlaying(true)
        }
    }

    /**
     * Reconnect the existing video surface after a presentation/size transition.
     * Some Android 9/vendor MediaCodec implementations can keep the decoder clock
     * alive while the current frame stops updating after a TextureView resize.
     * A tiny seek to the authoritative native clock forces a fresh decoded frame
     * without reloading the media item or resetting the playback session.
     */
    fun refreshSurfaceAfterPresentation(surface: Surface?, positionMs: Long) {
        assertMainThread()
        val playerController = controller ?: return
        if (surface == null) return

        playerController.setSurface(surface)
        val target = positionMs.coerceAtLeast(0L)
        if (target > 0L) {
            playerController.seekTo(target)
        }
        if (playIntent) {
            playerController.play()
            VideoPlaybackSessionManager.updatePlaying(true)
        }
    }

    fun isPlaying(): Boolean {
        assertMainThread()
        return controller?.isPlaying() ?: false
    }

    fun setPlaybackRate(rate: Float) {
        assertMainThread()
        val normalized = rate.coerceIn(0.25f, 4.0f)
        controller?.rawPlayer()?.setPlaybackSpeed(normalized)
        VideoPlaybackSessionManager.updatePlaybackRate(normalized)
    }

    /**
     * Stops playback but deliberately keeps the native session alive.
     * Closing a player surface must never restart the WebView <video>.
     */
    fun stopForUiClose() {
        assertMainThread()
        val position = currentPositionMs()
        VideoPlaybackSessionManager.updatePosition(position)
        VideoPlaybackSessionManager.updatePlaying(false)
        controller?.pause()
    }

    @Synchronized
    fun ensureMediaSession(context: Context): MediaSession {
        assertMainThread()
        mediaSession?.let { return it }
        val playerController = ensureController(context)
        val session = MediaSession.Builder(context.applicationContext, playerController.rawPlayer())
            .setId("elephant-browser-video")
            .build()
        mediaSession = session
        return session
    }

    @Synchronized
    fun currentMediaSession(): MediaSession? {
        assertMainThread()
        return mediaSession
    }

    @Synchronized
    fun releaseMediaSession() {
        assertMainThread()
        mediaSession?.release()
        mediaSession = null
    }

    @Synchronized
    fun setPlaybackErrorListener(listener: ((PlaybackException) -> Unit)?) {
        assertMainThread()
        playbackErrorListener = listener
    }

    /**
     * Abort a failed native takeover and hand the same source back to WebView.
     * This is intentionally different from release(): the browser may still be
     * alive and should be able to resume the page video at the last native clock.
     */
    @Synchronized
    fun resetAfterPlaybackError() {
        assertMainThread()
        val position = controller?.currentPositionMs() ?: 0L
        controller?.pause()
        VideoPlaybackSessionManager.updatePosition(position)
        VideoPlaybackSessionManager.updatePlaying(false)
        VideoPlaybackSessionManager.clear()
        activeSessionId = null
    }

    @Synchronized
    fun release() {
        assertMainThread()
        controller?.release()
        controller = null
        activeSessionId = null
        resumeAfterSurfaceReattach = false
        playIntent = false
        listenerInstalled = false
        mediaSession?.release()
        mediaSession = null
        VideoPlaybackSessionManager.clear()
    }
}
