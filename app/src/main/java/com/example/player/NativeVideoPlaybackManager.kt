package com.example.player

import android.content.Context
import android.view.Surface
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.example.model.VideoMediaInfo

/**
 * Application-wide owner of the single native video player.
 *
 * WebView discovers media, but this object owns the only real playback engine.
 * Player UI surfaces may attach/detach without creating another ExoPlayer.
 */
object NativeVideoPlaybackManager {
    private var controller: Media3VideoPlayerController? = null
    private var activeSessionId: String? = null
    private var listenerInstalled = false

    @Synchronized
    fun start(context: Context, video: VideoMediaInfo, autoPlay: Boolean = true): Boolean {
        val session = VideoPlaybackSessionManager.start(video)
        val playerController = ensureController(context)

        if (activeSessionId != session.id) {
            activeSessionId = session.id
            return try {
                playerController.load(video, session.positionMs)
                playerController.rawPlayer().setPlaybackSpeed(session.playbackRate)
                if (autoPlay && session.isPlaying) playerController.play()
                else playerController.pause()
                true
            } catch (_: Exception) {
                activeSessionId = null
                false
            }
        }

        playerController.rawPlayer().setPlaybackSpeed(session.playbackRate)
        if (autoPlay && session.isPlaying) playerController.play()
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
            })
            listenerInstalled = true
        }
        return created
    }

    @Synchronized
    fun attachSurface(surface: Surface?) {
        controller?.setSurface(surface)
    }

    @Synchronized
    fun detachSurface() {
        controller?.setSurface(null)
    }

    fun player(): ExoPlayer? = controller?.rawPlayer()

    fun play() {
        controller?.play()
    }

    fun pause() {
        controller?.pause()
    }

    fun seekTo(positionMs: Long) {
        controller?.seekTo(positionMs)
        VideoPlaybackSessionManager.updatePosition(positionMs)
    }

    fun currentPositionMs(): Long =
        controller?.currentPositionMs() ?: VideoPlaybackSessionManager.current()?.positionMs ?: 0L

    fun durationMs(): Long =
        controller?.durationMs() ?: VideoPlaybackSessionManager.current()?.durationMs ?: 0L

    fun isPlaying(): Boolean = controller?.isPlaying() ?: false

    fun setPlaybackRate(rate: Float) {
        val normalized = rate.coerceIn(0.25f, 4.0f)
        controller?.rawPlayer()?.setPlaybackSpeed(normalized)
        VideoPlaybackSessionManager.updatePlaybackRate(normalized)
    }

    /**
     * Stops playback but deliberately keeps the native session alive.
     * Closing a player surface must never restart the WebView <video>.
     */
    fun stopForUiClose() {
        val position = currentPositionMs()
        VideoPlaybackSessionManager.updatePosition(position)
        VideoPlaybackSessionManager.updatePlaying(false)
        controller?.pause()
    }

    @Synchronized
    fun release() {
        controller?.release()
        controller = null
        activeSessionId = null
        listenerInstalled = false
        VideoPlaybackSessionManager.clear()
    }
}
