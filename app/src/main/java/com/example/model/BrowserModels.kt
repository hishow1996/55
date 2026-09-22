package com.example.model

import android.graphics.Bitmap
import java.util.UUID

data class BrowserTab(
    val id: String = UUID.randomUUID().toString(),
    var url: String = "about:blank",
    var title: String = "新标签页",
    var favicon: Bitmap? = null,
    var isIncognito: Boolean = false,
    var isDesktopMode: Boolean = false,
    var progress: Int = 0,
    var isLoading: Boolean = false,
    var canGoBack: Boolean = false,
    var canGoForward: Boolean = false,
    var isNightMode: Boolean = false,
    var isTranslated: Boolean = false
)

data class BookmarkItem(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val url: String,
    val createTime: Long = System.currentTimeMillis()
)

data class HistoryItem(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val url: String,
    val visitTime: Long = System.currentTimeMillis()
)

data class PluginItem(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val description: String,
    val author: String = "大象开发者",
    val version: String = "1.0",
    var isEnabled: Boolean = true,
    val matchPattern: String = "*", // * or URL domain
    val runAt: String = "document_end", // "document_start" or "document_end"
    val scriptCode: String,
    val isBuiltIn: Boolean = false
)

data class VideoMediaInfo(
    val url: String,
    val pageUrl: String = "",
    val title: String = "网页视频",
    val duration: Double = 0.0,
    val currentTime: Double = 0.0,
    val videoWidth: Int = 16,
    val videoHeight: Int = 9,
    val isPlaying: Boolean = true,
    val playbackRate: Float = 1.0f
) {
    val aspectRatio: Float
        get() = if (videoHeight > 0 && videoWidth > 0) {
            videoWidth.toFloat() / videoHeight.toFloat()
        } else {
            16f / 9f
        }
}

data class QuickSite(
    val title: String,
    val url: String,
    val iconName: String,
    val bgColor: Long = 0xFFF1F5F9
)
