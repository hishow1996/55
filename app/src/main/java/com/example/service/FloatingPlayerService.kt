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
import android.graphics.drawable.GradientDrawable
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.VideoView
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import java.util.Locale

class FloatingPlayerService : Service() {

    private var windowManager: WindowManager? = null
    private var rootLayout: FrameLayout? = null
    private var videoView: VideoView? = null
    private var controlsLayout: FrameLayout? = null
    private var playPauseBtn: ImageButton? = null
    private var timeTv: TextView? = null
    private var seekBar: SeekBar? = null

    private var videoUrl: String = ""
    private var videoTitle: String = "网页视频"
    private var videoRatio: Float = 16f / 9f
    private var initialPositionMs: Long = 0L
    private var originTabIndex: Int = 0

    private val handler = Handler(Looper.getMainLooper())
    private var isPlaying = true
    private var areControlsVisible = true
    private var currentSizeIndex = 1 // 0: Mini (220dp), 1: Normal (290dp), 2: Large (360dp)
    private val sizePresets = floatArrayOf(220f, 290f, 360f)

    private val hideControlsRunnable = Runnable {
        hideControls()
    }

    private val progressUpdater = object : Runnable {
        override fun run() {
            videoView?.let { vv ->
                if (vv.isPlaying) {
                    val cur = vv.currentPosition
                    val dur = vv.duration.coerceAtLeast(1000)
                    seekBar?.max = dur
                    seekBar?.progress = cur
                    timeTv?.text = "${formatTime(cur)} / ${formatTime(dur)}"
                }
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
            stopSelf()
            return START_NOT_STICKY
        }

        videoUrl = intent.getStringExtra(EXTRA_VIDEO_URL) ?: ""
        videoTitle = intent.getStringExtra(EXTRA_VIDEO_TITLE) ?: "网页视频"
        videoRatio = intent.getFloatExtra(EXTRA_VIDEO_RATIO, 16f / 9f).coerceIn(0.5f, 3.0f)
        initialPositionMs = intent.getLongExtra(EXTRA_VIDEO_POSITION, 0L)
        originTabIndex = intent.getIntExtra(EXTRA_ORIGIN_TAB_INDEX, 0)

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

        val widthPx = (sizePresets[currentSizeIndex] * density).toInt()
        val heightPx = (widthPx / videoRatio).toInt().coerceAtLeast((120 * density).toInt())

        val params = WindowManager.LayoutParams(
            widthPx,
            heightPx,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = (30 * density).toInt()
            y = (150 * density).toInt()
        }

        // 1. Root Container with rounded corners & shadow
        val root = FrameLayout(this).apply {
            val bgDrawable = GradientDrawable().apply {
                setColor(0xFF0F172A.toInt())
                cornerRadius = 16 * density
                setStroke((1.5f * density).toInt(), 0xFF3B82F6.toInt())
            }
            background = bgDrawable
            clipToOutline = true
        }

        // 2. VideoView
        val vv = VideoView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            setVideoURI(Uri.parse(videoUrl))
            setOnPreparedListener { mp ->
                mp.isLooping = true
                if (initialPositionMs > 0) {
                    seekTo(initialPositionMs.toInt())
                }
                start()
                this@FloatingPlayerService.isPlaying = true
                playPauseBtn?.setImageResource(android.R.drawable.ic_media_pause)
            }
            setOnErrorListener { _, _, _ ->
                true // Handled gracefully
            }
        }
        root.addView(vv)
        videoView = vv

        // 3. Floating Overlay Controls Container
        val controls = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(0x77000000.toInt())
        }

        // 3.1 Top Header Bar
        val topBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                (36 * density).toInt()
            ).apply {
                gravity = Gravity.TOP
            }
            setPadding((8 * density).toInt(), 0, (6 * density).toInt(), 0)
            setBackgroundColor(0x88000000.toInt())
        }

        val titleTv = TextView(this).apply {
            text = videoTitle
            setTextColor(Color.WHITE)
            textSize = 11f
            maxLines = 1
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        topBar.addView(titleTv)

        // Size Toggle Button (Mini, Normal, Large)
        val sizeBtn = TextView(this).apply {
            text = when (currentSizeIndex) {
                0 -> "小"
                1 -> "中"
                else -> "大"
            }
            setTextColor(0xFF93C5FD.toInt())
            textSize = 10f
            setPadding((6 * density).toInt(), (4 * density).toInt(), (6 * density).toInt(), (4 * density).toInt())
            setOnClickListener {
                currentSizeIndex = (currentSizeIndex + 1) % sizePresets.size
                text = when (currentSizeIndex) {
                    0 -> "小"
                    1 -> "中"
                    else -> "大"
                }
                val newW = (sizePresets[currentSizeIndex] * density).toInt()
                val newH = (newW / videoRatio).toInt().coerceAtLeast((120 * density).toInt())
                params.width = newW
                params.height = newH
                windowManager?.updateViewLayout(root, params)
                resetHideTimer()
            }
        }
        topBar.addView(sizeBtn)

        // Close Button
        val closeBtn = ImageButton(this).apply {
            setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
            setBackgroundColor(Color.TRANSPARENT)
            setColorFilter(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams((30 * density).toInt(), (30 * density).toInt())
            setOnClickListener {
                stopSelf()
            }
        }
        topBar.addView(closeBtn)
        controls.addView(topBar)

        // 3.2 Center Controls (Rewind 10s, Play/Pause, Forward 10s)
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
            layoutParams = LinearLayout.LayoutParams((36 * density).toInt(), (36 * density).toInt())
            setOnClickListener {
                videoView?.let {
                    val pos = (it.currentPosition - 10000).coerceAtLeast(0)
                    it.seekTo(pos)
                }
                resetHideTimer()
            }
        }
        centerBar.addView(rewBtn)

        val playBtn = ImageButton(this).apply {
            setImageResource(android.R.drawable.ic_media_pause)
            setBackgroundColor(0x55000000.toInt())
            setColorFilter(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams((44 * density).toInt(), (44 * density).toInt()).apply {
                setMargins((12 * density).toInt(), 0, (12 * density).toInt(), 0)
            }
            setOnClickListener {
                videoView?.let { vv ->
                    if (vv.isPlaying) {
                        vv.pause()
                        isPlaying = false
                        setImageResource(android.R.drawable.ic_media_play)
                    } else {
                        vv.start()
                        isPlaying = true
                        setImageResource(android.R.drawable.ic_media_pause)
                    }
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
            layoutParams = LinearLayout.LayoutParams((36 * density).toInt(), (36 * density).toInt())
            setOnClickListener {
                videoView?.let {
                    val pos = (it.currentPosition + 10000).coerceAtMost(it.duration)
                    it.seekTo(pos)
                }
                resetHideTimer()
            }
        }
        centerBar.addView(ffBtn)
        controls.addView(centerBar)

        // 3.3 Bottom Bar (Time, SeekBar, Return to Tab button)
        val bottomBar = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.BOTTOM
            }
            setBackgroundColor(0x88000000.toInt())
            setPadding((6 * density).toInt(), (2 * density).toInt(), (6 * density).toInt(), (4 * density).toInt())
        }

        val sk = SeekBar(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (fromUser) {
                        videoView?.seekTo(progress)
                    }
                }
                override fun onStartTrackingTouch(sb: SeekBar?) {
                    handler.removeCallbacks(hideControlsRunnable)
                }
                override fun onStopTrackingTouch(sb: SeekBar?) {
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
            setTextColor(0xFFCBD5E1.toInt())
            textSize = 10f
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        timeTv = tTv
        bottomRow.addView(tTv)

        // "返回网页标签" Button
        val returnBtn = TextView(this).apply {
            text = "返回大象标签"
            setTextColor(0xFF38BDF8.toInt())
            textSize = 11f
            setPadding((6 * density).toInt(), (2 * density).toInt(), (6 * density).toInt(), (2 * density).toInt())
            setOnClickListener {
                returnToBrowserTab()
            }
        }
        bottomRow.addView(returnBtn)
        bottomBar.addView(bottomRow)
        controls.addView(bottomBar)

        root.addView(controls)
        controlsLayout = controls
        rootLayout = root

        // 4. Touch & Drag Listener
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        var isDragging = false

        root.setOnTouchListener { _, event ->
            when (event.action) {
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
                    if (dx * dx + dy * dy > 25) {
                        isDragging = true
                        params.x = (initialX + dx).toInt()
                        params.y = (initialY + dy).toInt()
                        windowManager?.updateViewLayout(root, params)
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!isDragging) {
                        // Clicked: toggle controls
                        toggleControls()
                    }
                    true
                }
                else -> false
            }
        }

        try {
            windowManager?.addView(root, params)
            handler.post(progressUpdater)
            resetHideTimer()
        } catch (e: Exception) {
            e.printStackTrace()
        }
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
        if (isPlaying) {
            handler.postDelayed(hideControlsRunnable, 3500)
        }
    }

    private fun returnToBrowserTab() {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_SELECT_TAB, originTabIndex)
        }
        startActivity(intent)
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
            videoView?.stopPlayback()
            rootLayout?.let { windowManager?.removeView(it) }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            rootLayout = null
            videoView = null
            controlsLayout = null
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
    }
}
