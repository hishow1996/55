package com.example.player

import android.content.Context
import android.view.Surface
import android.os.Looper
import androidx.media3.common.Player
import androidx.media3.common.PlaybackException
import androidx.media3.exoplayer.ExoPlayer
import com.example.model.VideoMediaInfo

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

    @Synchronized
    fun start(context: Context, video: VideoMediaInfo, autoPlay: Boolean = true): Boolean {
        assertMainThread()
        val session = VideoPlaybackSessionManager.start(video)
        val playerController = ensureController(context)

        if (activeSessionId != session.id) {
            activeSessionId = session.id
            return try {
                playerController.load(video, session.positionMs)
                playerController.rawPlayer().setPlaybackSpeed(session.playbackRate)
                if (autoPlay) playerController.play()
                else playerController.pause()
                true
            } catch (_: Exception) {
                activeSessionId = null
                false
            }
        }

        playerController.rawPlayer().setPlaybackSpeed(session.playbackRate)
        if (autoPlay) playerController.play()
        else playerController.pause()
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
        controller?.setSurface(surface)
    }

    @Synchronized
    fun detachSurface() {
        assertMainThread()
        controller?.setSurface(null)
    }

    fun player(): ExoPlayer? {
        assertMainThread()
        return controller?.rawPlayer()
    }

    fun play() {
        assertMainThread()
        controller?.play()
    }

    fun pause() {
        assertMainThread()
        controller?.pause()
    }

    fun seekTo(positionMs: Long) {
        assertMainThread()
        controller?.seekTo(positionMs)
        VideoPlaybackSessionManager.updatePosition(positionMs)
    }

    fun currentPositionMs(): Long {
        assertMainThread()
        return controller?.currentPositionMs() ?: VideoPlaybackSessionManager.current()?.positionMs ?: 0L
    }

    fun durationMs(): Long {
        assertMainThread()
        return controller?.durationMs() ?: VideoPlaybackSessionManager.current()?.durationMs ?: 0L
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
    fun setPlaybackErrorListener(listener: ((PlaybackException) -> Unit)?) {
        assertMainThread()
        playbackErrorListener = listener
    }

    @Synchronized
    fun release() {
        assertMainThread()
        controller?.release()
        controller = null
        activeSessionId = null
        listenerInstalled = false
        playbackErrorListener = null
        VideoPlaybackSessionManager.clear()
    }
}
