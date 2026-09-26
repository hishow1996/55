package com.example.service

import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.SurfaceTexture
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.view.Gravity
import android.view.MotionEvent
import android.view.Surface
import android.view.TextureView
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.example.MainActivity
import com.example.R
import com.example.player.FloatingVideoPlayerComponent
import com.example.player.NativeVideoPlaybackManager
import com.example.player.VideoPlaybackSessionManager
import com.example.player.VideoSourceResolver
import com.example.model.VideoMediaInfo
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import java.util.Locale
import kotlin.math.max
import kotlin.math.roundToInt
import org.json.JSONObject

class FloatingPlayerService : MediaSessionService() {

    private var windowManager: WindowManager? = null
    private var rootLayout: View? = null
    private var composeView: androidx.compose.ui.platform.ComposeView? = null
    private var composeLifecycleOwner: FloatingComposeLifecycleOwner? = null
    private var textureView: TextureView? = null
    private var currentSurface: Surface? = null


    private var videoUrl: String = ""
    private var videoTitle: String = "网页视频"
    private var videoRatio: Float = 16f / 9f
    private var initialPositionMs: Long = 0L
    private var originTabIndex: Int = 0
    private var originTabId: String? = null
    private var sourcePageUrl: String = ""

    private var requestedShouldPlay: Boolean = true
    private var requestedPlaybackRate: Float = 1.0f
    private var drmScheme: String? = null
    private var drmLicenseUri: String? = null
    private var drmLicenseHeaders: Map<String, String> = emptyMap()

    private var isPlaying = true
    // Prevent duplicate close/error callbacks from racing during teardown.
    private var closing = false
    private var currentPositionMs = 0
    private var durationMs = 0

    // The native Media3 player is the single playback owner. This listener only
    // observes its decoded video size so the overlay can follow the actual
    // rendered frame ratio (for example a 16:9 source displayed as 4:3).
    private val videoSizeListener = object : Player.Listener {
        override fun onVideoSizeChanged(videoSize: VideoSize) {
            val w = videoSize.width
            val h = videoSize.height
            if (w <= 0 || h <= 0) return
            val ratio = w.toFloat() * videoSize.pixelWidthHeightRatio / h.toFloat()
            if (!ratio.isFinite() || ratio !in 0.42f..2.38f) return
            videoRatio = ratio
            updateFloatingWindowRatio()
        }
    }

    private fun currentValidRatio(): Float =
        videoRatio.takeIf { it.isFinite() && it in 0.5f..3.0f } ?: (16f / 9f)

    private fun sizeForRatio(preferredWidth: Int, minWidth: Int, maxWidth: Int, minHeight: Int, maxHeight: Int): Pair<Int, Int> {
        val ratio = currentValidRatio()
        val lowerW = max(minWidth, kotlin.math.ceil(minHeight * ratio).toInt())
        val upperW = minOf(maxWidth, kotlin.math.floor(maxHeight * ratio).toInt())
        val width = preferredWidth.coerceIn(lowerW.coerceAtMost(upperW), upperW.coerceAtLeast(lowerW))
        val height = kotlin.math.round(width / ratio).toInt()
        return width to height
    }

    private fun updateFloatingWindowRatio() {
        val root = rootLayout ?: return
        val wm = windowManager ?: return
        val p = root.layoutParams as? WindowManager.LayoutParams ?: return
        val density = resources.displayMetrics.density
        val screenW = resources.displayMetrics.widthPixels
        val screenH = resources.displayMetrics.heightPixels
        val minW = (180 * density).toInt()
        val maxW = (screenW * 0.92f).toInt().coerceAtLeast(minW)
        val minH = (100 * density).toInt()
        val maxH = (screenH * 0.70f).toInt().coerceAtLeast(minH)
        val (w, h) = sizeForRatio(p.width, minW, maxW, minH, maxH)
        p.width = w
        p.height = h
        wm.updateViewLayout(root, p)
    }

    private var currentSizeIndex = 0 // 0: Normal (320dp), 1: Large (370dp), 2: Compact (260dp)
    private val sizePresets = floatArrayOf(320f, 370f, 260f)

    override fun onCreate() {
        super.onCreate()
        NativeVideoPlaybackManager.ensureMediaSession(this)
        // Observe the same ExoPlayer used by the service so the floating window
        // follows the actual decoded video aspect ratio.
        NativeVideoPlaybackManager.player()?.addListener(videoSizeListener)
        // MediaSessionService supplies the media notification for this service.
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return NativeVideoPlaybackManager.currentMediaSession()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) return START_NOT_STICKY

        val action = intent.action
        if (action == ACTION_STOP) {
            val position = try {
                NativeVideoPlaybackManager.currentPositionMs().toDouble() / 1000.0
                    ?: FloatingVideoPlayerComponent.lastPlaybackPositionSeconds
            } catch (e: Exception) {
                FloatingVideoPlayerComponent.lastPlaybackPositionSeconds
            }
            closeFloatingWindowOrResumeBrowser(position)
            return START_NOT_STICKY
        }

        // A new playback request starts a fresh close/error lifecycle.
        // Without this reset, a previously closed service instance could ignore
        // later Media3 errors because `closing` remained true.
        closing = false

        videoUrl = intent.getStringExtra(EXTRA_VIDEO_URL) ?: ""
        videoTitle = intent.getStringExtra(EXTRA_VIDEO_TITLE) ?: "网页视频"
        videoRatio = intent.getFloatExtra(EXTRA_VIDEO_RATIO, 16f / 9f).coerceIn(0.5f, 3.0f)
        initialPositionMs = intent.getLongExtra(EXTRA_VIDEO_POSITION, 0L)
        requestedShouldPlay = intent.getBooleanExtra(EXTRA_VIDEO_SHOULD_PLAY, true)
        requestedPlaybackRate = intent.getFloatExtra(EXTRA_VIDEO_PLAYBACK_RATE, 1.0f).coerceIn(0.25f, 4.0f)
        drmScheme = intent.getStringExtra(EXTRA_DRM_SCHEME)?.trim()?.lowercase()?.takeIf { it.isNotBlank() }
        drmLicenseUri = intent.getStringExtra(EXTRA_DRM_LICENSE_URI)?.trim()?.takeIf { it.isNotBlank() }
        drmLicenseHeaders = runCatching {
            val raw = intent.getStringExtra(EXTRA_DRM_LICENSE_HEADERS).orEmpty()
            if (raw.isBlank()) emptyMap() else {
                val json = JSONObject(raw)
                buildMap {
                    json.keys().forEach { key ->
                        json.optString(key).takeIf { it.isNotBlank() }?.let { put(key, it) }
                    }
                }
            }
        }.getOrDefault(emptyMap())
        originTabIndex = intent.getIntExtra(EXTRA_ORIGIN_TAB_INDEX, 0)
        originTabId = intent.getStringExtra(EXTRA_ORIGIN_TAB_ID)
        sourcePageUrl = intent.getStringExtra(EXTRA_VIDEO_PAGE_URL) ?: ""

        // The intent payload identifies the exact playback request. Do not
        // re-handoff from the global activeVideoInfo here: a rapid A -> B switch
        // can otherwise make the service read a stale snapshot.
        val session = VideoPlaybackSessionManager.current()
        val requestedSource = if (videoUrl.isNotBlank()) {
            VideoSourceResolver.resolve(
                VideoMediaInfo(
                    url = videoUrl,
                    pageUrl = sourcePageUrl,
                    title = videoTitle,
                    currentTime = initialPositionMs / 1000.0,
                    videoWidth = (videoRatio * 1000).toInt().coerceAtLeast(1),
                    videoHeight = 1000,
                    originTabIndex = originTabIndex,
                    originTabId = originTabId,
                    drmScheme = drmScheme,
                    drmLicenseUri = drmLicenseUri,
                    drmLicenseHeaders = drmLicenseHeaders
                )
            )
        } else null
        val sessionMatchesRequest = session != null &&
            session.tabId == originTabId &&
            session.pageUrl == sourcePageUrl &&
            (requestedSource == null || session.source == requestedSource)
        if (sessionMatchesRequest) {
            initialPositionMs = session!!.positionMs.coerceAtLeast(0L)
            isPlaying = session.isPlaying
        } else {
            // This is a genuinely new request. The caller's position/state is
            // authoritative until Media3 reaches READY.
            isPlaying = requestedShouldPlay
            VideoPlaybackSessionManager.updatePlaying(requestedShouldPlay)
            VideoPlaybackSessionManager.updatePlaybackRate(requestedPlaybackRate)
        }

        // MainActivity owns the global native-playback error listener. Do not
        // replace it here: replacing it would make later in-browser native
        // playback lose its WebView fallback after the floating service exits.


        if (videoUrl.isNotBlank()) {
            val nativeInfo = VideoMediaInfo(
                url = videoUrl,
                pageUrl = sourcePageUrl,
                title = videoTitle,
                currentTime = initialPositionMs / 1000.0,
                videoWidth = (videoRatio * 1000).toInt().coerceAtLeast(1),
                videoHeight = 1000,
                originTabIndex = originTabIndex,
                originTabId = originTabId,
                isPlaying = isPlaying,
                drmScheme = drmScheme,
                drmLicenseUri = drmLicenseUri,
                drmLicenseHeaders = drmLicenseHeaders
            )
            val started = try {
                NativeVideoPlaybackManager.start(this, nativeInfo, autoPlay = isPlaying)
            } catch (e: Exception) {
                android.util.Log.e("FloatingPlayerService", "Failed to start shared Native Media3 player", e)
                false
            }

            if (!started) {
                // Never expose an empty/black overlay when the native media source
                // could not be loaded. Keep the WebView handoff state intact so
                // the caller can recover instead of presenting a dead player UI.
                VideoPlaybackSessionManager.updatePlaying(false)
                FloatingVideoPlayerComponent.syncProgress(initialPositionMs / 1000.0)
                Toast.makeText(this, "原生播放器无法打开当前视频", Toast.LENGTH_SHORT).show()
                stopSelf(startId)
                return START_NOT_STICKY
            }

            NativeVideoPlaybackManager.setPlaybackRate(
                VideoPlaybackSessionManager.current()?.playbackRate ?: requestedPlaybackRate
            )
            showFloatingWindow()
        }

        return START_NOT_STICKY
    }

    private fun showFloatingWindow() {
        // The Android global window is only a host. The actual player UI is the
        // same InAppFloatingPlayer composable used by the browser. This removes
        // the old second, hand-written View-based player implementation.
        if (rootLayout != null) return

        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val density = resources.displayMetrics.density
        val screenWidth = resources.displayMetrics.widthPixels
        val preferredW = (sizePresets[currentSizeIndex] * density).toInt()
        val defaultW = preferredW.coerceAtMost((screenWidth * 0.95f).toInt())
        val effectiveRatio = currentValidRatio()
        val heightPx = (defaultW / effectiveRatio).toInt().coerceAtLeast((100 * density).toInt())

        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            defaultW,
            heightPx,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = ((screenWidth - defaultW) / 2).coerceAtLeast((10 * density).toInt())
            y = (130 * density).toInt()
        }

        val owner = FloatingComposeLifecycleOwner().also { it.create() }
        val compose = androidx.compose.ui.platform.ComposeView(this).apply {
            setViewCompositionStrategy(
                androidx.compose.ui.platform.ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed
            )
        }
        // The Compose tree must use the dedicated overlay owner, not the Service
        // itself, so its composition has a stable lifecycle and saved-state owner.
        androidx.lifecycle.setViewTreeLifecycleOwner(compose, owner)
        androidx.lifecycle.setViewTreeViewModelStoreOwner(compose, owner)
        androidx.savedstate.setViewTreeSavedStateRegistryOwner(compose, owner)

        val activeVideo = VideoMediaInfo(
            url = videoUrl,
            pageUrl = sourcePageUrl,
            title = videoTitle,
            currentTime = initialPositionMs / 1000.0,
            videoWidth = (videoRatio * 1000).toInt().coerceAtLeast(1),
            videoHeight = 1000,
            isPlaying = isPlaying,
            originTabIndex = originTabIndex,
            originTabId = originTabId,
            drmScheme = drmScheme,
            drmLicenseUri = drmLicenseUri,
            drmLicenseHeaders = drmLicenseHeaders
        )

        compose.setContent {
            androidx.compose.material3.MaterialTheme {
                com.example.player.InAppFloatingPlayer(
                    videoInfo = activeVideo,
                    isGlobalFloating = true,
                    isDesktopPiP = false,
                    isFullscreen = false,
                    currentTabIndex = originTabIndex,
                    onClose = { position ->
                        closeFloatingWindowOrResumeBrowser(position)
                    },
                    onEnterGlobalPiP = {},
                    onEnterFullscreen = {},
                    onDownloadVideo = null,
                    onGlobalDrag = { dx, dy ->
                        val wm = windowManager
                        val root = rootLayout
                        val lp = root?.layoutParams as? WindowManager.LayoutParams
                        if (wm != null && root != null && lp != null) {
                            val maxX = (resources.displayMetrics.widthPixels - lp.width).coerceAtLeast(0)
                            val maxY = (resources.displayMetrics.heightPixels - lp.height).coerceAtLeast(0)
                            lp.x = (lp.x + dx.roundToInt()).coerceIn(0, maxX)
                            lp.y = (lp.y + dy.roundToInt()).coerceIn(0, maxY)
                            runCatching { wm.updateViewLayout(root, lp) }
                        }
                    }
                )
            }
        }

        rootLayout = compose
        composeView = compose
        composeLifecycleOwner = owner

        try {
            windowManager?.addView(compose, params)
        } catch (e: Exception) {
            composeLifecycleOwner?.destroy()
            composeLifecycleOwner = null
            composeView = null
            rootLayout = null
            throw e
        }
    }

    private fun closeFloatingWindowOrResumeBrowser(positionSeconds: Double) {
        if (closing) return
        closing = true
        // Read the player state before releasing it so the shared session is authoritative.
        val playerPositionSeconds = try {
            NativeVideoPlaybackManager.currentPositionMs().toDouble() / 1000.0
        } catch (e: Exception) {
            null
        }
        val effectivePosition = playerPositionSeconds
            ?.takeIf { it.isFinite() && it >= 0.0 }
            ?: positionSeconds.coerceAtLeast(0.0)
        val playerIsPlaying = try { NativeVideoPlaybackManager.isPlaying() } catch (_: Exception) { null }
        val shouldPlay = playerIsPlaying ?: VideoPlaybackSessionManager.current()?.isPlaying ?: isPlaying

        FloatingVideoPlayerComponent.syncProgress(effectivePosition)
        VideoPlaybackSessionManager.updatePosition((effectivePosition * 1000.0).toLong())
        VideoPlaybackSessionManager.updatePlaying(shouldPlay)
        try { NativeVideoPlaybackManager.stopForUiClose() } catch (_: Exception) {}
        val shouldResumeWeb = MainActivity.shouldResumeFloatingVideo(originTabIndex, originTabId, sourcePageUrl)
        if (shouldResumeWeb) {
            // The browser Activity already exists behind the overlay. Resume the
            // original WebView directly; never start/recreate MainActivity here.
            val resumed = MainActivity.resumeWebVideoFromFloatingClose(
                originTabId = originTabId,
                originTabIndex = originTabIndex,
                positionSeconds = effectivePosition,
                shouldPlay = shouldPlay
            )
            if (!resumed) {
                MainActivity.unlockFloatingSourceTab(originTabId, originTabIndex)
            }
        } else {
            // The user is on another tab. Release only the source tab's lock;
            // closing the floating window must not navigate or exit the browser.
            MainActivity.unlockFloatingSourceTab(originTabId, originTabIndex)
        }

        // Do not leave the old VideoMediaInfo around: a later video handoff must
        // never rebuild Media3 from a previous tab's stream metadata.
        FloatingVideoPlayerComponent.clearActiveVideo()
        stopSelf()
    }
    private fun formatTime(ms: Int): String {
        val totalSeconds = (ms / 1000).coerceAtLeast(0)
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
    }

    private fun removeFloatingWindow() {
        try {
            try {
                FloatingVideoPlayerComponent.syncProgress(
                    NativeVideoPlaybackManager.currentPositionMs() / 1000.0
                )
                NativeVideoPlaybackManager.detachSurface()
            } catch (_: Exception) {}
            try { currentSurface?.release() } catch (_: Exception) {}
            currentSurface = null
            rootLayout?.let { windowManager?.removeView(it) }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            composeLifecycleOwner?.destroy()
            composeLifecycleOwner = null
            composeView = null
            rootLayout = null
        }
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        if (!NativeVideoPlaybackManager.isPlaying()) {
            stopSelf()
        }
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        try {
            NativeVideoPlaybackManager.player()?.removeListener(videoSizeListener)
        } catch (_: Exception) {}
        removeFloatingWindow()
        NativeVideoPlaybackManager.release()
        super.onDestroy()
    }

    companion object {
        const val ACTION_STOP = "com.example.service.STOP_FLOATING"
        const val EXTRA_VIDEO_URL = "extra_video_url"
        const val EXTRA_VIDEO_TITLE = "extra_video_title"
        const val EXTRA_VIDEO_RATIO = "extra_video_ratio"
        const val EXTRA_VIDEO_POSITION = "extra_video_position"
        const val EXTRA_ORIGIN_TAB_INDEX = "extra_origin_tab_index"
        const val EXTRA_ORIGIN_TAB_ID = "extra_origin_tab_id"
        const val EXTRA_SELECT_TAB = "select_tab_index"
        const val EXTRA_RESUME_WEB_VIDEO = "resume_web_video"
        const val EXTRA_VIDEO_POSITION_SECONDS = "video_position_seconds"
        const val EXTRA_VIDEO_PAGE_URL = "extra_video_page_url"
        const val EXTRA_VIDEO_SHOULD_PLAY = "extra_video_should_play"
        const val EXTRA_VIDEO_PLAYBACK_RATE = "extra_video_playback_rate"
        const val EXTRA_DRM_SCHEME = "extra_drm_scheme"
        const val EXTRA_DRM_LICENSE_URI = "extra_drm_license_uri"
        const val EXTRA_DRM_LICENSE_HEADERS = "extra_drm_license_headers"
    }
}
