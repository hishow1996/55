package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.TextView
import android.widget.VideoView
import androidx.core.app.NotificationCompat
import com.example.MainActivity

class FloatingPlayerService : Service() {

    private var windowManager: WindowManager? = null
    private var floatView: View? = null
    private var videoView: VideoView? = null
    private var videoUrl: String = ""
    private var videoTitle: String = ""
    private var videoRatio: Float = 16f / 9f

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
        videoRatio = intent.getFloatExtra(EXTRA_VIDEO_RATIO, 16f / 9f)

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
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification: Notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("大象浏览器正在悬浮播放")
            .setContentText(videoTitle.ifBlank { "点击返回大象浏览器" })
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()

        startForeground(1001, notification)
    }

    private fun showFloatingWindow() {
        if (floatView != null) {
            removeFloatingWindow()
        }

        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager

        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val density = resources.displayMetrics.density
        var initialWidth = (280 * density).toInt()
        var initialHeight = (initialWidth / videoRatio).toInt().coerceAtLeast((140 * density).toInt())

        val params = WindowManager.LayoutParams(
            initialWidth,
            initialHeight,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = (40 * density).toInt()
            y = (120 * density).toInt()
        }

        // Create container view
        val rootLayout = FrameLayout(this).apply {
            setBackgroundColor(0xFF0F172A.toInt())
        }

        val vv = VideoView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            setVideoURI(Uri.parse(videoUrl))
            setOnPreparedListener { mp ->
                mp.isLooping = true
                start()
            }
        }
        rootLayout.addView(vv)
        videoView = vv

        // Top control bar
        val controlBar = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                (36 * density).toInt()
            ).apply {
                gravity = Gravity.TOP
            }
            setBackgroundColor(0x88000000.toInt())
        }

        val titleTv = TextView(this).apply {
            text = videoTitle
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 12f
            maxLines = 1
            setPadding((8 * density).toInt(), (8 * density).toInt(), (40 * density).toInt(), (8 * density).toInt())
        }
        controlBar.addView(titleTv)

        val closeBtn = ImageButton(this).apply {
            setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
            setBackgroundColor(0x00000000)
            layoutParams = FrameLayout.LayoutParams(
                (36 * density).toInt(),
                (36 * density).toInt()
            ).apply {
                gravity = Gravity.END or Gravity.CENTER_VERTICAL
            }
            setOnClickListener {
                stopSelf()
            }
        }
        controlBar.addView(closeBtn)
        rootLayout.addView(controlBar)

        // Draggable touch handler
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f

        controlBar.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = initialX + (event.rawX - initialTouchX).toInt()
                    params.y = initialY + (event.rawY - initialTouchY).toInt()
                    windowManager?.updateViewLayout(rootLayout, params)
                    true
                }
                else -> false
            }
        }

        try {
            windowManager?.addView(rootLayout, params)
            floatView = rootLayout
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun removeFloatingWindow() {
        try {
            videoView?.stopPlayback()
            floatView?.let { windowManager?.removeView(it) }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            floatView = null
            videoView = null
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
    }
}
