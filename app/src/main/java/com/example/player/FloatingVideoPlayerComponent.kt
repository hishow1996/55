package com.example.player

import android.app.Activity
import android.app.AppOpsManager
import android.app.PictureInPictureParams
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Process
import android.provider.Settings
import android.util.Rational
import android.widget.Toast
import com.example.model.VideoMediaInfo
import com.example.service.FloatingPlayerService

/**
 * FloatingVideoPlayerComponent
 * Manager for Android WindowManager Floating Window API & PiP.
 * Handles overlay and PiP permissions, service lifecycle, tab-switching continuation,
 * and exiting-app mini-window playback.
 */
object FloatingVideoPlayerComponent {

    @Volatile
    var lastPlaybackPositionSeconds: Double = 0.0

    @Volatile
    var activeVideoInfo: VideoMediaInfo? = null

    /** Video request waiting for the user to return from a system permission page. */
    @Volatile
    var pendingGlobalVideo: VideoMediaInfo? = null

    var onProgressSyncListener: ((seconds: Double) -> Unit)? = null

    fun syncProgress(seconds: Double) {
        lastPlaybackPositionSeconds = seconds
        onProgressSyncListener?.invoke(seconds)
    }

    /**
     * Checks if the app has Picture-in-Picture (PiP) permission.
     */
    fun hasPipPermission(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return false
        }
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager ?: return false
        return try {
            val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                appOps.unsafeCheckOpNoThrow(
                    AppOpsManager.OPSTR_PICTURE_IN_PICTURE,
                    Process.myUid(),
                    context.packageName
                )
            } else {
                appOps.checkOpNoThrow(
                    AppOpsManager.OPSTR_PICTURE_IN_PICTURE,
                    Process.myUid(),
                    context.packageName
                )
            }
            mode == AppOpsManager.MODE_ALLOWED
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Directly navigates to the Picture-in-Picture permission settings page for this app,
     * allowing the user to manually enable PiP without any intermediate prompt dialog.
     */
    fun openPipSettings(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val intent = Intent(
                    "android.settings.PICTURE_IN_PICTURE_SETTINGS",
                    Uri.parse("package:${context.packageName}")
                ).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            } catch (e: Exception) {
                try {
                    val intent = Intent("android.settings.PICTURE_IN_PICTURE_SETTINGS").apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                } catch (e2: Exception) {
                    try {
                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.parse("package:${context.packageName}")
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(intent)
                    } catch (e3: Exception) {
                        Toast.makeText(context, "无法直接打开画中画设置页面", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        } else {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }

    /**
     * Builds PictureInPictureParams with safe aspect ratio constraints (between 1:2.39 and 2.39:1).
     */
    fun buildPipParams(video: VideoMediaInfo): PictureInPictureParams {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            throw UnsupportedOperationException("PiP requires Android O (API 26) or higher")
        }
        val builder = PictureInPictureParams.Builder()
        val rational = if (video.videoWidth > 0 && video.videoHeight > 0) {
            val w = video.videoWidth.coerceIn(1, 10000)
            val h = video.videoHeight.coerceIn(1, 10000)
            val ratio = w.toFloat() / h.toFloat()
            when {
                ratio > 2.38f -> Rational(238, 100)
                ratio < 0.42f -> Rational(100, 238)
                else -> Rational(w, h)
            }
        } else {
            Rational(16, 9)
        }
        builder.setAspectRatio(rational)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setAutoEnterEnabled(true)
        }
        return builder.build()
    }

    /**
     * Checks if the app has the system overlay permission (SYSTEM_ALERT_WINDOW)
     */
    fun hasOverlayPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(context)
        } else {
            true
        }
    }

    /**
     * Guides the user to enable the Android Floating Window (Overlay) permission
     */
    fun requestOverlayPermission(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:${context.packageName}")
                ).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                Toast.makeText(context, "请在设置中允许大象浏览器显示悬浮窗", Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(context, "无法直接跳转悬浮窗设置，请在应用设置中开启", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * Launch or update the system floating window player via WindowManager Overlay Service
     */
    fun startSystemFloatingPlayer(
        context: Context,
        video: VideoMediaInfo,
        originTabIndex: Int? = null
    ) {
        activeVideoInfo = video
        pendingGlobalVideo = video
        if (!hasOverlayPermission(context)) {
            requestOverlayPermission(context)
            return
        }

        val intent = Intent(context, FloatingPlayerService::class.java).apply {
            putExtra(FloatingPlayerService.EXTRA_VIDEO_URL, video.url)
            putExtra(FloatingPlayerService.EXTRA_VIDEO_TITLE, video.title)
            putExtra(FloatingPlayerService.EXTRA_VIDEO_RATIO, video.aspectRatio)
            putExtra(FloatingPlayerService.EXTRA_VIDEO_POSITION, (video.currentTime * 1000).toLong())
            putExtra(FloatingPlayerService.EXTRA_VIDEO_PAGE_URL, video.pageUrl)
            putExtra(FloatingPlayerService.EXTRA_ORIGIN_TAB_INDEX, originTabIndex ?: video.originTabIndex ?: 0)
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
            pendingGlobalVideo = null
            Toast.makeText(context, "已开启桌面悬浮窗播放", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "启动悬浮窗失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Stop the system floating window player
     */
    fun stopSystemFloatingPlayer(context: Context) {
        val intent = Intent(context, FloatingPlayerService::class.java).apply {
            action = FloatingPlayerService.ACTION_STOP
        }
        try {
            context.startService(intent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
