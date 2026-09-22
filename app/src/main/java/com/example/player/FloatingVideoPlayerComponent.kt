package com.example.player

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import com.example.model.VideoMediaInfo
import com.example.service.FloatingPlayerService

/**
 * FloatingVideoPlayerComponent
 * Manager for Android WindowManager Floating Window API & PiP.
 * Handles overlay permissions, service lifecycle, tab-switching continuation,
 * and exiting-app mini-window playback.
 */
object FloatingVideoPlayerComponent {

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
        if (!hasOverlayPermission(context)) {
            requestOverlayPermission(context)
            return
        }

        val intent = Intent(context, FloatingPlayerService::class.java).apply {
            putExtra(FloatingPlayerService.EXTRA_VIDEO_URL, video.url)
            putExtra(FloatingPlayerService.EXTRA_VIDEO_TITLE, video.title)
            putExtra(FloatingPlayerService.EXTRA_VIDEO_RATIO, video.aspectRatio)
            putExtra(FloatingPlayerService.EXTRA_VIDEO_POSITION, (video.currentTime * 1000).toLong())
            putExtra(FloatingPlayerService.EXTRA_ORIGIN_TAB_INDEX, originTabIndex ?: video.originTabIndex ?: 0)
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
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
