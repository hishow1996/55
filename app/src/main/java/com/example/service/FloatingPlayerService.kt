package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.SurfaceTexture
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
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
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.player.FloatingVideoPlayerComponent
import com.example.player.Media3VideoPlayerController
import com.example.model.VideoMediaInfo
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import java.util.Locale
import kotlin.math.max

class FloatingPlayerService : Service() {

    private var windowManager: WindowManager? = null
    private var rootLayout: FrameLayout? = null
    private var textureView: TextureView? = null
    private var mediaPlayer: androidx.media3.exoplayer.ExoPlayer? = null
    private var nativePlayerController: Media3VideoPlayerController? = null
    private var currentSurface: Surface? = null

    private var controlsLayout: FrameLayout? = null
    private var lockOverlay: FrameLayout? = null
    private var playPauseBtn: ImageButton? = null
    private var timeTv: TextView? = null
    private var seekBar: SeekBar? = null

    private var videoUrl: String = ""
    private var videoTitle: String = "网页视频"
    private var videoRatio: Float = 16f / 9f
    private var initialPositionMs: Long = 0L
    private var originTabIndex: Int = 0
    private var sourcePageUrl: String = ""

    private val handler = Handler(Looper.getMainLooper())
    private var isPlaying = true
    private var areControlsVisible = true
    private var isLocked = false
    private var currentPositionMs = 0
    private var durationMs = 0

    private var currentSizeIndex = 0 // 0: Normal (320dp), 1: Large (370dp), 2: Compact (260dp)
    private val sizePresets = floatArrayOf(320f, 370f, 260f)

    private val hideControlsRunnable = Runnable {
        hideControls()
    }

    private val progressUpdater = object : Runnable {
        override fun run() {
            mediaPlayer?.let { mp ->
                try {
                    if (mp.isPlaying) {
                        val cur = mp.currentPosition
                        val dur = max(mp.duration, 1000)
                        currentPositionMs = cur
                        durationMs = dur
                        FloatingVideoPlayerComponent.syncProgress(cur / 1000.0)
                        seekBar?.max = dur
                        seekBar?.progress = cur
                        timeTv?.text = "${formatTime(cur)} / ${formatTime(dur)}"
                    }
                } catch (e: Exception) {}
            }
            handler.postDelayed(this, 500)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startForegroundServiceNotification()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) return START_NOT_STICKY

        val action = intent.action
        if (action == ACTION_STOP) {
            val position = try {
                mediaPlayer?.currentPosition?.toDouble()?.div(1000.0)
                    ?: FloatingVideoPlayerComponent.lastPlaybackPositionSeconds
            } catch (e: Exception) {
                FloatingVideoPlayerComponent.lastPlaybackPositionSeconds
            }
            closeFloatingWindowOrResumeBrowser(position)
            return START_NOT_STICKY
        }

        videoUrl = intent.getStringExtra(EXTRA_VIDEO_URL) ?: ""
        videoTitle = intent.getStringExtra(EXTRA_VIDEO_TITLE) ?: "网页视频"
        videoRatio = intent.getFloatExtra(EXTRA_VIDEO_RATIO, 16f / 9f).coerceIn(0.5f, 3.0f)
        initialPositionMs = intent.getLongExtra(EXTRA_VIDEO_POSITION, 0L)
        originTabIndex = intent.getIntExtra(EXTRA_ORIGIN_TAB_INDEX, 0)
        sourcePageUrl = intent.getStringExtra(EXTRA_VIDEO_PAGE_URL) ?: ""

        if (videoUrl.isNotBlank()) {
            showFloatingWindow()
        }

        return START_STICKY
    }

    private fun startForegroundServiceNotification() {
        val channelId = "elephant_floating_player"
        val channelName = "大象全局悬浮播放"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val chan = NotificationChannel(channelId, channelName, NotificationManager.IMPORTANCE_LOW)
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(chan)
        }

        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_SELECT_TAB, originTabIndex)
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification: Notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("大象浏览器正在悬浮播放")
            .setContentText(videoTitle.ifBlank { "点击返回大象浏览器标签页" })
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()

        startForeground(1001, notification)
    }

    private fun showFloatingWindow() {
        removeFloatingWindow()

        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val density = resources.displayMetrics.density

        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val screenWidth = resources.displayMetrics.widthPixels
        val defaultW = (sizePresets[currentSizeIndex] * density).toInt().coerceAtMost((screenWidth * 0.92f).toInt())
        val effectiveRatio = if (videoRatio >= 1.2f) videoRatio else (16f / 9f)
        val heightPx = (defaultW / effectiveRatio).toInt().coerceAtLeast((140 * density).toInt())

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

        // 1. Root Container with rounded corners & border (Figure 1 matching style)
        val root = FrameLayout(this).apply {
            val bgDrawable = GradientDrawable().apply {
                setColor(0xFF0F172A.toInt())
                cornerRadius = 16 * density
                setStroke((1.5f * density).toInt(), 0xFF3B82F6.toInt())
            }
            background = bgDrawable
            clipToOutline = true
        }

        // 2. TextureView Video Surface (Fixes Black Screen completely!)
        val tv = TextureView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            isClickable = false
            isFocusable = false
            surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                override fun onSurfaceTextureAvailable(st: SurfaceTexture, w: Int, h: Int) {
                    val surface = Surface(st)
                    currentSurface = surface

                    val activeInfo = FloatingVideoPlayerComponent.activeVideoInfo
                    var streamUrl = videoUrl.trim()
                    if (streamUrl.isBlank() || streamUrl.startsWith("blob:") || !streamUrl.startsWith("http")) {
                        streamUrl = activeInfo?.url?.trim() ?: ""
                    }

                    if (streamUrl.isBlank() || streamUrl.startsWith("blob:")) {
                        handler.post {
                            Toast.makeText(
                                this@FloatingPlayerService,
                                "当前视频无法转换为原生播放地址",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                        return
                    }

                    val effectiveVideo = (activeInfo ?: VideoMediaInfo(
                        url = streamUrl,
                        pageUrl = sourcePageUrl,
                        title = videoTitle,
                        currentTime = initialPositionMs / 1000.0,
                        videoWidth = (videoRatio * 1000).toInt().coerceAtLeast(1),
                        videoHeight = 1000,
                        originTabIndex = originTabIndex
                    )).copy(
                        url = streamUrl,
                        pageUrl = if (sourcePageUrl.isNotBlank()) sourcePageUrl else activeInfo?.pageUrl.orEmpty(),
                        currentTime = initialPositionMs / 1000.0
                    )

                    val controller = Media3VideoPlayerController(this@FloatingPlayerService)
                    nativePlayerController = controller
                    val player = controller.rawPlayer()
                    mediaPlayer = player
                    controller.setSurface(surface)
                    controller.addListener(object : Player.Listener {
                        override fun onPlaybackStateChanged(playbackState: Int) {
                            if (playbackState == Player.STATE_READY) {
                                durationMs = player.duration.coerceAtLeast(0L).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
                                if (initialPositionMs > 0L) {
                                    player.seekTo(initialPositionMs)
                                    currentPositionMs = initialPositionMs.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
                                }
                                player.repeatMode = Player.REPEAT_MODE_ONE
                                player.play()
                                this@FloatingPlayerService.isPlaying = true
                                playPauseBtn?.setImageResource(android.R.drawable.ic_media_pause)
                            }
                        }

                        override fun onPlayerError(error: PlaybackException) {
                            android.util.Log.e(
                                "FloatingPlayerService",
                                "Media3 playback error: code=${error.errorCode} url=$streamUrl",
                                error
                            )
                            handler.post {
                                Toast.makeText(
                                    this@FloatingPlayerService,
                                    "悬浮视频流无法播放，请重新点击悬浮按钮",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }

                        override fun onIsPlayingChanged(playing: Boolean) {
                            this@FloatingPlayerService.isPlaying = playing
                            playPauseBtn?.setImageResource(
                                if (playing) android.R.drawable.ic_media_pause
                                else android.R.drawable.ic_media_play
                            )
                            resetHideTimer()
                        }
                    })
                    try {
                        controller.load(effectiveVideo, initialPositionMs)
                        controller.play()
                    } catch (e: Exception) {
                        android.util.Log.e(
                            "FloatingPlayerService",
                            "Failed to load Media3 source: $streamUrl",
                            e
                        )
                        handler.post {
                            Toast.makeText(
                                this@FloatingPlayerService,
                                "悬浮视频加载失败",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                }

                override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, w: Int, h: Int) {}
                override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean {
                    try {
                        mediaPlayer?.setSurface(null)
                        currentSurface?.release()
                        currentSurface = null
                    } catch (e: Exception) {}
                    return true
                }
                override fun onSurfaceTextureUpdated(st: SurfaceTexture) {}
            }
        }
        root.addView(tv)
        textureView = tv

        // 3. Floating Overlay Controls Container (Figure 1 UI Match)
        val controls = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(0x70000000.toInt())
        }

        // 3.1 Top Header Bar (Only Close button on top-right, clean and minimal)
        val topBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL or Gravity.END
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                (42 * density).toInt()
            ).apply {
                gravity = Gravity.TOP
            }
            setPadding((12 * density).toInt(), 0, (8 * density).toInt(), 0)
            setBackgroundColor(0x88000000.toInt())
        }

        // Close Button (Only X button retained)
        val closeBtn = ImageButton(this).apply {
            setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
            setBackgroundColor(Color.TRANSPARENT)
            background = null
            setColorFilter(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams((36 * density).toInt(), (36 * density).toInt())
            setOnClickListener {
                val position = try { mediaPlayer?.currentPosition?.toDouble()?.div(1000.0) ?: FloatingVideoPlayerComponent.lastPlaybackPositionSeconds } catch (e: Exception) { FloatingVideoPlayerComponent.lastPlaybackPositionSeconds }
                closeFloatingWindowOrResumeBrowser(position)
            }
        }
        topBar.addView(closeBtn)
        controls.addView(topBar)

        // 3.4 Center Controls (Rewind 10s, Enlarged Play/Pause, Forward 10s)
        val centerBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.CENTER
            }
        }

        val rewBtn = ImageButton(this).apply {
            setImageResource(android.R.drawable.ic_media_rew)
            setBackgroundColor(Color.TRANSPARENT)
            setColorFilter(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams((44 * density).toInt(), (44 * density).toInt())
            setOnClickListener {
                mediaPlayer?.let { mp ->
                    try {
                        val pos = max(0, mp.currentPosition - 10000)
                        mp.seekTo(pos)
                        currentPositionMs = pos
                    } catch (e: Exception) {}
                }
                resetHideTimer()
            }
        }
        centerBar.addView(rewBtn)

        // Play/Pause button (Clean icon without circle background)
        val playBtn = ImageButton(this).apply {
            setImageResource(android.R.drawable.ic_media_pause)
            setBackgroundColor(Color.TRANSPARENT)
            background = null
            setColorFilter(Color.WHITE)
            setPadding(0, 0, 0, 0)
            layoutParams = LinearLayout.LayoutParams((46 * density).toInt(), (46 * density).toInt()).apply {
                setMargins((16 * density).toInt(), 0, (16 * density).toInt(), 0)
            }
            setOnClickListener {
                mediaPlayer?.let { mp ->
                    try {
                        if (mp.isPlaying) {
                            mp.pause()
                            isPlaying = false
                            setImageResource(android.R.drawable.ic_media_play)
                        } else {
                            mp.start()
                            isPlaying = true
                            setImageResource(android.R.drawable.ic_media_pause)
                        }
                    } catch (e: Exception) {}
                }
                resetHideTimer()
            }
        }
        playPauseBtn = playBtn
        centerBar.addView(playBtn)

        val ffBtn = ImageButton(this).apply {
            setImageResource(android.R.drawable.ic_media_ff)
            setBackgroundColor(Color.TRANSPARENT)
            setColorFilter(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams((44 * density).toInt(), (44 * density).toInt())
            setOnClickListener {
                mediaPlayer?.let { mp ->
                    try {
                        val pos = (mp.currentPosition + 10000).coerceAtMost(mp.duration)
                        mp.seekTo(pos)
                        currentPositionMs = pos
                    } catch (e: Exception) {}
                }
                resetHideTimer()
            }
        }
        centerBar.addView(ffBtn)
        controls.addView(centerBar)

        // 3.5 Bottom Bar (Time, SeekBar, Return to Tab button)
        val bottomBar = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.BOTTOM
            }
            setBackgroundColor(0x88000000.toInt())
            setPadding((8 * density).toInt(), (2 * density).toInt(), (24 * density).toInt(), (4 * density).toInt())
        }

        val sk = SeekBar(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (fromUser) {
                        timeTv?.text = "${formatTime(progress)} / ${formatTime(durationMs)}"
                    }
                }
                override fun onStartTrackingTouch(sb: SeekBar?) {
                    handler.removeCallbacks(hideControlsRunnable)
                }
                override fun onStopTrackingTouch(sb: SeekBar?) {
                    sb?.progress?.let { pos ->
                        mediaPlayer?.seekTo(pos)
                        currentPositionMs = pos
                    }
                    resetHideTimer()
                }
            })
        }
        seekBar = sk
        bottomBar.addView(sk)

        val bottomRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        val tTv = TextView(this).apply {
            text = "00:00 / 00:00"
            setTextColor(Color.WHITE)
            textSize = 10f
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        timeTv = tTv
        bottomRow.addView(tTv)
        bottomBar.addView(bottomRow)
        controls.addView(bottomBar)

        root.addView(controls)
        controlsLayout = controls

        // 3.6 Lock Overlay (Shown when screen lock is active)
        val lockOv = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            visibility = View.GONE
        }
        val unlockBtn = ImageButton(this).apply {
            setImageResource(R.drawable.ic_lock_closed)
            setBackgroundColor(Color.TRANSPARENT)
            background = null
            setColorFilter(0xFF38BDF8.toInt())
            setPadding(0, 0, 0, 0)
            layoutParams = FrameLayout.LayoutParams((44 * density).toInt(), (44 * density).toInt()).apply {
                gravity = Gravity.CENTER_VERTICAL or Gravity.START
                marginStart = (12 * density).toInt()
            }
            setOnClickListener {
                isLocked = false
                lockOverlay?.visibility = View.GONE
                showControls()
            }
        }
        lockOv.addView(unlockBtn)
        root.addView(lockOv)
        lockOverlay = lockOv

        // 3.7 UC-Style Subtle Corner Resize Gripper (Bottom-Right Corner)
        var resizeInitialW = 0
        var resizeTouchX = 0f
        var isResizeDragging = false
        var currentSizePresetIndex = 0
        val sizePresetsDp = floatArrayOf(300f, 360f, 240f)

        val resizeBtn = FrameLayout(this).apply {
            val sizePx = (32 * density).toInt()
            layoutParams = FrameLayout.LayoutParams(sizePx, sizePx).apply {
                gravity = Gravity.BOTTOM or Gravity.END
            }
            background = null

            val iconIv = ImageView(this@FloatingPlayerService).apply {
                setImageResource(R.drawable.ic_resize_corner)
                setColorFilter(0xB3FFFFFF.toInt())
                val iconSize = (10 * density).toInt()
                val m = (3 * density).toInt()
                layoutParams = FrameLayout.LayoutParams(iconSize, iconSize).apply {
                    gravity = Gravity.BOTTOM or Gravity.END
                    setMargins(0, 0, m, m)
                }
            }
            addView(iconIv)
        }

        resizeBtn.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    resizeInitialW = params.width
                    resizeTouchX = event.rawX
                    isResizeDragging = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - resizeTouchX
                    if (Math.abs(dx) > 6) {
                        isResizeDragging = true
                        val screenW = resources.displayMetrics.widthPixels
                        val screenH = resources.displayMetrics.heightPixels
                        val minW = (180 * density).toInt()
                        val maxW = (screenW - params.x).coerceAtLeast(minW)
                        val minH = (110 * density).toInt()
                        val maxH = (screenH - params.y).coerceAtLeast(minH)

                        val effRatio = if (videoRatio >= 0.5f) videoRatio else (16f / 9f)
                        val newW = (resizeInitialW + dx).toInt().coerceIn(minW, maxW)
                        val newH = (newW / effRatio).toInt().coerceIn(minH, maxH)
                        params.width = newW
                        params.height = newH
                        windowManager?.updateViewLayout(root, params)
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!isResizeDragging) {
                        currentSizePresetIndex = (currentSizePresetIndex + 1) % sizePresetsDp.size
                        val targetWDp = sizePresetsDp[currentSizePresetIndex]
                        val screenW = resources.displayMetrics.widthPixels
                        val targetW = (targetWDp * density).toInt().coerceIn((180 * density).toInt(), (screenW - params.x).coerceAtLeast((180 * density).toInt()))
                        val effRatio = if (videoRatio >= 0.5f) videoRatio else (16f / 9f)
                        val targetH = (targetW / effRatio).toInt().coerceAtLeast((110 * density).toInt())
                        params.width = targetW
                        params.height = targetH
                        windowManager?.updateViewLayout(root, params)
                    }
                    true
                }
                else -> false
            }
        }
        root.addView(resizeBtn)

        rootLayout = root

        // 4. Ultra-Smooth Touch & Drag Handling (Fixes any stutter / lag when moving)
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        var isDragging = false

        val touchListener = View.OnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    isDragging = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - initialTouchX
                    val dy = event.rawY - initialTouchY
                    if (dx * dx + dy * dy > 16) {
                        isDragging = true
                        val screenW = resources.displayMetrics.widthPixels
                        val screenH = resources.displayMetrics.heightPixels
                        val maxX = (screenW - params.width).coerceAtLeast(0)
                        val maxY = (screenH - params.height).coerceAtLeast(0)
                        params.x = (initialX + dx).toInt().coerceIn(0, maxX)
                        params.y = (initialY + dy).toInt().coerceIn(0, maxY)
                        windowManager?.updateViewLayout(root, params)
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!isDragging) {
                        if (isLocked) {
                            lockOverlay?.let { lo ->
                                lo.visibility = if (lo.visibility == View.VISIBLE) View.GONE else View.VISIBLE
                            }
                        } else {
                            toggleControls()
                        }
                    }
                    true
                }
                else -> false
            }
        }

        root.setOnTouchListener(touchListener)
        controls.setOnTouchListener(touchListener)
        lockOv.setOnTouchListener(touchListener)

        try {
            windowManager?.addView(root, params)
            handler.post(progressUpdater)
            resetHideTimer()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun showLockOverlay() {
        lockOverlay?.visibility = View.VISIBLE
    }

    private fun toggleControls() {
        if (areControlsVisible) {
            hideControls()
        } else {
            showControls()
        }
    }

    private fun showControls() {
        controlsLayout?.visibility = View.VISIBLE
        areControlsVisible = true
        resetHideTimer()
    }

    private fun hideControls() {
        controlsLayout?.visibility = View.GONE
        areControlsVisible = false
    }

    private fun resetHideTimer() {
        handler.removeCallbacks(hideControlsRunnable)
        if (isPlaying && !isLocked) {
            handler.postDelayed(hideControlsRunnable, 3500)
        }
    }

    private fun closeFloatingWindowOrResumeBrowser(positionSeconds: Double) {
        FloatingVideoPlayerComponent.syncProgress(positionSeconds)
        val shouldResumeWeb = MainActivity.shouldResumeFloatingVideo(originTabIndex, sourcePageUrl)
        if (shouldResumeWeb) {
            val intent = Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(EXTRA_SELECT_TAB, originTabIndex)
                putExtra(EXTRA_RESUME_WEB_VIDEO, true)
                putExtra(EXTRA_VIDEO_POSITION_SECONDS, positionSeconds.coerceAtLeast(0.0))
            }
            try { startActivity(intent) } catch (e: Exception) { e.printStackTrace() }
        }
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
            handler.removeCallbacks(progressUpdater)
            handler.removeCallbacks(hideControlsRunnable)
            mediaPlayer?.let { mp ->
                try {
                    FloatingVideoPlayerComponent.syncProgress(mp.currentPosition / 1000.0)
                } catch (e: Exception) {}
            }
            try { nativePlayerController?.release() } catch (e: Exception) {}
            nativePlayerController = null
            mediaPlayer = null
            currentSurface?.release()
            currentSurface = null
            rootLayout?.let { windowManager?.removeView(it) }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            rootLayout = null
            textureView = null
            controlsLayout = null
            lockOverlay = null
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        removeFloatingWindow()
    }

    companion object {
        const val ACTION_STOP = "com.example.service.STOP_FLOATING"
        const val EXTRA_VIDEO_URL = "extra_video_url"
        const val EXTRA_VIDEO_TITLE = "extra_video_title"
        const val EXTRA_VIDEO_RATIO = "extra_video_ratio"
        const val EXTRA_VIDEO_POSITION = "extra_video_position"
        const val EXTRA_ORIGIN_TAB_INDEX = "extra_origin_tab_index"
        const val EXTRA_SELECT_TAB = "select_tab_index"
        const val EXTRA_RESUME_WEB_VIDEO = "resume_web_video"
        const val EXTRA_VIDEO_POSITION_SECONDS = "video_position_seconds"
        const val EXTRA_VIDEO_PAGE_URL = "extra_video_page_url"
    }
}
