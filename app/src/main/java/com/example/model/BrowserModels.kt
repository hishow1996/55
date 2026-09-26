package com.example.model

import android.graphics.Bitmap
import java.util.UUID

data class BrowserTab(
    val id: String = UUID.randomUUID().toString(),
    var url: String = "",
    var title: String = "大象浏览器",
    var favicon: Bitmap? = null,
    var isIncognito: Boolean = false,
    var isDesktopMode: Boolean = false,
    var progress: Int = 0,
    var isLoading: Boolean = false,
    var canGoBack: Boolean = false,
    var canGoForward: Boolean = false,
    var isNightMode: Boolean = false,
    var isTranslated: Boolean = false
) {
    val isAtHome: Boolean get() = url.isBlank() || url == "about:blank"
}

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

data class VideoMediaInfo(
    val url: String,
    val pageUrl: String = "",
    val title: String = "网页视频",
    val duration: Double = 0.0,
    val currentTime: Double = 0.0,
    val videoWidth: Int = 16,
    val videoHeight: Int = 9,
    val isPlaying: Boolean = true,
    val playbackRate: Float = 1.0f,
    val originTabIndex: Int? = null,
    val originTabId: String? = null,
    // Authorized DRM playback metadata discovered from the page's own license request.
    // These values are only used to configure Media3; no keys or decrypted media are exposed.
    val drmScheme: String? = null,
    val drmLicenseUri: String? = null,
    val drmLicenseHeaders: Map<String, String> = emptyMap()
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
    val bgColor: Long = 0xFFF1F5F9,
    val isCustom: Boolean = false
)

enum class DownloadStatus {
    PENDING,
    DOWNLOADING,
    PAUSED,
    COMPLETED,
    FAILED,
    CANCELLED
}

data class DownloadItem(
    val id: String = UUID.randomUUID().toString(),
    val fileName: String,
    val url: String,
    val filePath: String = "",
    val mimeType: String = "",
    val totalBytes: Long = 0L,
    val downloadedBytes: Long = 0L,
    val speedBytesPerSec: Long = 0L,
    val status: DownloadStatus = DownloadStatus.PENDING,
    val startTime: Long = System.currentTimeMillis(),
    val finishTime: Long? = null,
    val errorMessage: String? = null
) {
    val progress: Float
        get() = if (totalBytes > 0) (downloadedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f) else 0f

    val formattedSpeed: String
        get() = when {
            speedBytesPerSec >= 1024 * 1024 -> String.format(java.util.Locale.getDefault(), "%.1f MB/s", speedBytesPerSec / (1024f * 1024f))
            speedBytesPerSec >= 1024 -> String.format(java.util.Locale.getDefault(), "%.1f KB/s", speedBytesPerSec / 1024f)
            speedBytesPerSec > 0 -> "$speedBytesPerSec B/s"
            else -> "0 B/s"
        }

    val formattedDownloadedSize: String
        get() = formatBytes(downloadedBytes)

    val formattedTotalSize: String
        get() = if (totalBytes > 0) formatBytes(totalBytes) else "未知大小"

    val formattedProgressSize: String
        get() = "${formatBytes(downloadedBytes)} / ${if (totalBytes > 0) formatBytes(totalBytes) else "未知"}"

    companion object {
        fun formatBytes(bytes: Long): String {
            return when {
                bytes >= 1024 * 1024 * 1024 -> String.format(java.util.Locale.getDefault(), "%.2f GB", bytes / (1024f * 1024f * 1024f))
                bytes >= 1024 * 1024 -> String.format(java.util.Locale.getDefault(), "%.1f MB", bytes / (1024f * 1024f))
                bytes >= 1024 -> String.format(java.util.Locale.getDefault(), "%.1f KB", bytes / 1024f)
                bytes > 0 -> "$bytes B"
                else -> "0 B"
            }
        }
    }
}
