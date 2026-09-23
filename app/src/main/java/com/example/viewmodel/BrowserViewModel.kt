package com.example.viewmodel

import android.app.Application
import android.graphics.Bitmap
import android.view.View
import android.webkit.WebChromeClient
import android.webkit.WebView
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.BrowserRepository
import com.example.download.ElephantDownloadManager
import com.example.engine.Scripts
import com.example.model.BookmarkItem
import com.example.model.BrowserTab
import com.example.model.HistoryItem
import com.example.model.PluginItem
import com.example.model.VideoMediaInfo
import com.example.player.VideoSourceResolver
import com.example.player.VideoPlaybackSessionManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class BrowserViewModel(application: Application) : AndroidViewModel(application) {
    val repository = BrowserRepository(application)
    val downloadManager = ElephantDownloadManager(application)

    data class PendingDownload(
        val url: String,
        val fileName: String,
        val mimeType: String?,
        val contentLength: Long
    )

    // Current open tabs
    private val _tabs = MutableStateFlow<List<BrowserTab>>(
        listOf(BrowserTab(url = "", title = "大象浏览器"))
    )
    val tabs: StateFlow<List<BrowserTab>> = _tabs.asStateFlow()

    private val _currentTabIndex = MutableStateFlow(0)
    val currentTabIndex: StateFlow<Int> = _currentTabIndex.asStateFlow()

    val currentTab: BrowserTab
        get() = _tabs.value.getOrNull(_currentTabIndex.value) ?: _tabs.value.first()

    // Address / Search bar text input
    private val _urlInput = MutableStateFlow("")
    val urlInput: StateFlow<String> = _urlInput.asStateFlow()

    // Detected Video for Floating Player
    private val _detectedVideo = MutableStateFlow<VideoMediaInfo?>(null)
    val detectedVideo: StateFlow<VideoMediaInfo?> = _detectedVideo.asStateFlow()

    // In-App Floating Player visibility
    private val _isFloatingPlayerVisible = MutableStateFlow(false)
    val isFloatingPlayerVisible: StateFlow<Boolean> = _isFloatingPlayerVisible.asStateFlow()

    // Fullscreen Web Custom View (HTML5 video)
    private val _customVideoView = MutableStateFlow<View?>(null)
    val customVideoView: StateFlow<View?> = _customVideoView.asStateFlow()
    private var customViewCallback: WebChromeClient.CustomViewCallback? = null

    // UI Dialog & BottomSheet visibility states
    private val _isMenuVisible = MutableStateFlow(false)
    val isMenuVisible: StateFlow<Boolean> = _isMenuVisible.asStateFlow()

    private val _isTabManagerVisible = MutableStateFlow(false)
    val isTabManagerVisible: StateFlow<Boolean> = _isTabManagerVisible.asStateFlow()

    private val _isSettingsVisible = MutableStateFlow(false)
    val isSettingsVisible: StateFlow<Boolean> = _isSettingsVisible.asStateFlow()

    private val _isPluginManagerVisible = MutableStateFlow(false)
    val isPluginManagerVisible: StateFlow<Boolean> = _isPluginManagerVisible.asStateFlow()

    private val _isHistoryBookmarksVisible = MutableStateFlow(false)
    val isHistoryBookmarksVisible: StateFlow<Boolean> = _isHistoryBookmarksVisible.asStateFlow()

    private val _historyBookmarksInitialTab = MutableStateFlow(0)
    val historyBookmarksInitialTab: StateFlow<Int> = _historyBookmarksInitialTab.asStateFlow()

    private val _isAiChatVisible = MutableStateFlow(false)
    val isAiChatVisible: StateFlow<Boolean> = _isAiChatVisible.asStateFlow()

    private val _isDownloadManagerVisible = MutableStateFlow(false)
    val isDownloadManagerVisible: StateFlow<Boolean> = _isDownloadManagerVisible.asStateFlow()

    private val _isSearchOverlayVisible = MutableStateFlow(false)
    val isSearchOverlayVisible: StateFlow<Boolean> = _isSearchOverlayVisible.asStateFlow()

    private val _pendingDownload = MutableStateFlow<PendingDownload?>(null)
    val pendingDownload: StateFlow<PendingDownload?> = _pendingDownload.asStateFlow()

    // Translation Banner Status
    private val _translationBannerText = MutableStateFlow<String?>(null)
    val translationBannerText: StateFlow<String?> = _translationBannerText.asStateFlow()

    // Active WebView reference for executing actions.
    // Keep a per-tab reference as well so a floating video can be restored to
    // its original page even after the user switched tabs.
    var activeWebView: WebView? = null
    private val tabWebViews = mutableMapOf<String, WebView>()

    private data class PendingWebVideoResume(
        val tabIndex: Int,
        val pageUrl: String,
        val positionSeconds: Double,
        val shouldPlay: Boolean
    )

    private var pendingWebVideoResume: PendingWebVideoResume? = null

    fun registerTabWebView(tabId: String, webView: WebView) {
        tabWebViews[tabId] = webView
        if (currentTab.id == tabId) {
            activeWebView = webView
        }
        val pending = pendingWebVideoResume
        if (pending != null && pending.tabIndex == _currentTabIndex.value &&
            (pending.pageUrl.isBlank() || pending.pageUrl == currentTab.url)) {
            pendingWebVideoResume = null
            webView.postDelayed({
                webView.evaluateJavascript(
                    Scripts.RESUME_WEB_VIDEO_AT(
                        pending.positionSeconds.coerceAtLeast(0.0),
                        pending.shouldPlay
                    ),
                    null
                )
            }, 50L)
        }
    }

    fun queueWebVideoResume(
        tabIndex: Int,
        pageUrl: String,
        positionSeconds: Double,
        shouldPlay: Boolean
    ) {
        pendingWebVideoResume = PendingWebVideoResume(
            tabIndex = tabIndex,
            pageUrl = pageUrl,
            positionSeconds = positionSeconds,
            shouldPlay = shouldPlay
        )
        if (tabIndex == _currentTabIndex.value) {
            tabWebViews[_tabs.value.getOrNull(tabIndex)?.id]?.let { webView ->
                pendingWebVideoResume = null
                webView.post {
                    webView.evaluateJavascript(
                        Scripts.RESUME_WEB_VIDEO_AT(
                            positionSeconds.coerceAtLeast(0.0),
                            shouldPlay
                        ),
                        null
                    )
                }
            }
        }
    }

    override fun onCleared() {
        tabWebViews.clear()
        pendingWebVideoResume = null
        activeWebView = null
        super.onCleared()
    }

    fun setSearchOverlayVisible(visible: Boolean) {
        _isSearchOverlayVisible.value = visible
    }

    fun setDownloadManagerVisible(visible: Boolean) {
        _isDownloadManagerVisible.value = visible
    }

    fun openDownloads() {
        _isMenuVisible.value = false
        _isDownloadManagerVisible.value = true
    }

    fun onDownloadRequested(
        url: String,
        userAgent: String?,
        contentDisposition: String?,
        mimetype: String?,
        contentLength: Long
    ) {
        val fileName = android.webkit.URLUtil.guessFileName(url, contentDisposition, mimetype)
        _pendingDownload.value = PendingDownload(
            url = url,
            fileName = fileName,
            mimeType = mimetype,
            contentLength = contentLength
        )
    }

    fun startVideoDownload(url: String, title: String) {
        // Downloads follow the same per-tab isolation rule as playback.
        // Never use the repository-wide lastDetectedStreamUrl or the page URL
        // as a video fallback.
        val effectiveUrl = if (url.isBlank() || url.startsWith("blob:") || !url.startsWith("http")) {
            repository.getDetectedStreamUrlForTab(currentTab.id)
                ?.trim()
                ?.takeIf { it.startsWith("http://", true) || it.startsWith("https://", true) }
                ?: ""
        } else {
            url.trim()
        }
        val cleanTitle = title.trim()
            .replace(Regex("[\\\\/:*?\"<>|\\r\\n]"), "_")
            .ifBlank { "video_${System.currentTimeMillis()}" }
        val isHls = effectiveUrl.contains(".m3u8", ignoreCase = true) || effectiveUrl.contains("application/vnd.apple.mpegurl", ignoreCase = true)
        val ext = if (isHls) ".ts" else ".mp4"
        val fileName = if (cleanTitle.endsWith(".mp4", true) || cleanTitle.endsWith(".ts", true)) cleanTitle else "$cleanTitle$ext"
        val mimeType = if (isHls) "video/mp2t" else "video/mp4"

        downloadManager.enqueueDownload(
            url = effectiveUrl,
            suggestedFileName = fileName,
            mimeType = mimeType,
            contentLength = 0L
        )
        _isDownloadManagerVisible.value = true
    }

    fun confirmPendingDownload() {
        val pending = _pendingDownload.value ?: return
        downloadManager.enqueueDownload(
            url = pending.url,
            suggestedFileName = pending.fileName,
            mimeType = pending.mimeType,
            contentLength = pending.contentLength
        )
        _pendingDownload.value = null
        _isDownloadManagerVisible.value = true
    }

    fun dismissPendingDownload() {
        _pendingDownload.value = null
    }

    fun setUrlInput(input: String) {
        _urlInput.value = input
    }

    fun updateCurrentTab(transform: (BrowserTab) -> BrowserTab) {
        val currentIndex = _currentTabIndex.value
        val list = _tabs.value
        if (currentIndex in list.indices) {
            _tabs.value = list.mapIndexed { idx, tab ->
                if (idx == currentIndex) transform(tab) else tab
            }
        }
    }

    fun navigateTo(queryOrUrl: String) {
        val q = queryOrUrl.trim()
        if (q.isBlank()) return
        _isSearchOverlayVisible.value = false
        val finalUrl = repository.getSearchUrl(q)
        _urlInput.value = finalUrl

        updateCurrentTab { tab ->
            tab.copy(
                url = finalUrl,
                title = "加载中...",
                progress = 10,
                isLoading = true
            )
        }

        activeWebView?.post {
            activeWebView?.loadUrl(finalUrl)
        }
    }

    fun onPageStarted(url: String) {
        if (url == "about:blank" || url.isBlank()) {
            val isIncog = currentTab.isIncognito
            updateCurrentTab {
                it.copy(
                    url = "",
                    title = if (isIncog) "无痕新标签" else "大象浏览器",
                    isLoading = false,
                    canGoBack = false,
                    canGoForward = false
                )
            }
            _urlInput.value = ""
            return
        }
        updateCurrentTab { it.copy(url = url, isLoading = true) }
        _urlInput.value = url
    }

    fun onPageFinished(url: String, title: String) {
        if (url == "about:blank" || url.isBlank()) {
            val isIncog = currentTab.isIncognito
            updateCurrentTab {
                it.copy(
                    url = "",
                    title = if (isIncog) "无痕新标签" else "大象浏览器",
                    isLoading = false,
                    canGoBack = false,
                    canGoForward = false
                )
            }
            _urlInput.value = ""
            return
        }
        updateCurrentTab {
            it.copy(
                url = url,
                title = title.ifBlank { url },
                isLoading = false,
                canGoBack = activeWebView?.canGoBack() ?: false,
                canGoForward = activeWebView?.canGoForward() ?: false
            )
        }
        _urlInput.value = url
    }

    fun onProgressChanged(progress: Int) {
        updateCurrentTab {
            it.copy(progress = progress, isLoading = progress < 100)
        }
    }

    fun onTitleChanged(title: String) {
        if (title.isBlank()) return
        updateCurrentTab { it.copy(title = title) }
    }

    fun onIconChanged(icon: Bitmap?) {
        if (icon == null) return
        updateCurrentTab { it.copy(favicon = icon) }
    }

    fun goHome() {
        val isIncog = currentTab.isIncognito
        updateCurrentTab {
            it.copy(
                url = "",
                title = if (isIncog) "无痕新标签" else "大象浏览器",
                progress = 0,
                isLoading = false,
                canGoBack = false,
                canGoForward = false
            )
        }
        _urlInput.value = ""
        activeWebView?.post {
            activeWebView?.stopLoading()
            activeWebView?.loadUrl("about:blank")
            activeWebView?.clearHistory()
        }
    }

    fun goBack() {
        if (currentTab.isAtHome) return
        if (activeWebView?.canGoBack() == true) {
            activeWebView?.goBack()
        } else {
            goHome()
        }
    }

    fun goForward() {
        if (activeWebView?.canGoForward() == true) {
            activeWebView?.goForward()
        }
    }

    fun reload() {
        if (currentTab.url.isNotBlank()) {
            updateCurrentTab { it.copy(isLoading = true, progress = 10) }
            activeWebView?.post {
                activeWebView?.reload()
            }
        }
    }

    fun stopLoading() {
        updateCurrentTab { it.copy(isLoading = false) }
        activeWebView?.post {
            activeWebView?.stopLoading()
        }
    }

    // Tab Management
    fun addNewTab(isIncognito: Boolean = repository.isIncognito.value, initialUrl: String = "") {
        val oldIndex = _currentTabIndex.value
        val video = _detectedVideo.value
        if (video != null && video.isPlaying && (video.originTabIndex == oldIndex || video.originTabIndex == null)) {
            _detectedVideo.value = video.copy(originTabIndex = oldIndex)
            _isFloatingPlayerVisible.value = true
        }

        val newTab = BrowserTab(
            url = initialUrl,
            title = if (isIncognito) "无痕新标签" else "新标签页",
            isIncognito = isIncognito,
            isDesktopMode = repository.isDesktopMode.value,
            isNightMode = repository.isNightMode.value
        )
        val updatedList = _tabs.value + newTab
        _tabs.value = updatedList
        _currentTabIndex.value = updatedList.lastIndex
        _urlInput.value = initialUrl
        _isTabManagerVisible.value = false
        if (initialUrl.isNotBlank()) {
            activeWebView?.loadUrl(initialUrl)
        }
    }

    fun selectTab(index: Int) {
        if (index in _tabs.value.indices) {
            val oldIndex = _currentTabIndex.value
            if (oldIndex != index) {
                val video = _detectedVideo.value
                if (video != null && video.isPlaying && (video.originTabIndex == oldIndex || video.originTabIndex == null)) {
                    _detectedVideo.value = video.copy(originTabIndex = oldIndex)
                    _isFloatingPlayerVisible.value = true
                }
            }
            _currentTabIndex.value = index
            _urlInput.value = _tabs.value[index].url
            _isTabManagerVisible.value = false
        }
    }

    fun closeTab(index: Int) {
        val currentList = _tabs.value.toMutableList()
        if (index !in currentList.indices) return

        val closingTab = currentList[index]
        val closedVideo = _detectedVideo.value
        val closingOwnsVideo = closedVideo != null &&
            (closedVideo.originTabIndex == index || closedVideo.pageUrl == closingTab.url)

        // A closed source tab can no longer be a valid WebView resume target.
        // Drop its WebView reference and any detected stream owned by that tab.
        tabWebViews.remove(closingTab.id)
        repository.clearDetectedStreamUrl(closingTab.id)

        if (closingOwnsVideo) {
            _isFloatingPlayerVisible.value = false
            _detectedVideo.value = null
            val session = VideoPlaybackSessionManager.current()
            if (session != null && (session.tabIndex == index || session.pageUrl == closingTab.url)) {
                VideoPlaybackSessionManager.clear()
            }
            pendingWebVideoResume = null
        }

        if (currentList.size <= 1) {
            // Keep at least one tab
            currentList[0] = BrowserTab(url = "", title = "大象浏览器")
            _tabs.value = currentList
            _currentTabIndex.value = 0
            _urlInput.value = ""
            activeWebView = null
            goHome()
            return
        }

        currentList.removeAt(index)
        _tabs.value = currentList

        // Removing a tab shifts later indexes. Rebind the session/detected-video
        // origin index so playback ownership remains attached to the same tab.
        fun remapIndex(old: Int): Int? = when {
            old < index -> old
            old > index -> old - 1
            else -> null
        }

        _detectedVideo.value?.let { video ->
            val mapped = video.originTabIndex?.let(::remapIndex)
            if (video.originTabIndex != null && mapped == null) {
                _detectedVideo.value = null
                _isFloatingPlayerVisible.value = false
            } else if (mapped != null) {
                _detectedVideo.value = video.copy(originTabIndex = mapped)
            }
        }

        VideoPlaybackSessionManager.current()?.let { session ->
            val mapped = remapIndex(session.tabIndex)
            if (mapped == null) {
                VideoPlaybackSessionManager.clear()
            } else if (mapped != session.tabIndex) {
                VideoPlaybackSessionManager.rebindTab(mapped)
            }
        }

        val newIndex = _currentTabIndex.value.let { current ->
            when {
                current > index -> current - 1
                current == index -> current.coerceAtMost(currentList.lastIndex)
                else -> current
            }
        }
        _currentTabIndex.value = newIndex
        _urlInput.value = currentList[newIndex].url
        activeWebView = tabWebViews[currentList[newIndex].id]
    }

    fun closeTab(tab: BrowserTab) {
        val index = _tabs.value.indexOfFirst { it.id == tab.id }
        if (index != -1) {
            closeTab(index)
        }
    }

    // Toggle Modes
    fun toggleNightMode() {
        val newMode = !repository.isNightMode.value
        repository.setNightMode(newMode)
        updateCurrentTab { it.copy(isNightMode = newMode) }

        if (newMode) {
            activeWebView?.evaluateJavascript(Scripts.NIGHT_MODE_CSS, null)
        } else {
            activeWebView?.evaluateJavascript(Scripts.REMOVE_NIGHT_MODE_CSS, null)
        }
    }

    fun toggleDesktopMode() {
        val newMode = !currentTab.isDesktopMode
        updateCurrentTab { it.copy(isDesktopMode = newMode) }
        repository.setDesktopMode(newMode)

        val targetUa = repository.getUserAgent(newMode)
        val toastMessage = if (newMode) "已切换为电脑版 (Desktop UA)" else "已切换为手机版"
        android.widget.Toast.makeText(getApplication(), toastMessage, android.widget.Toast.LENGTH_SHORT).show()

        activeWebView?.post {
            activeWebView?.settings?.let { s ->
                s.userAgentString = targetUa
                s.useWideViewPort = newMode
                s.loadWithOverviewMode = newMode
            }
            val currentUrl = currentTab.url
            if (newMode && currentUrl.isNotBlank()) {
                val desktopUrl = repository.convertToDesktopUrl(currentUrl)
                if (desktopUrl != currentUrl) {
                    navigateTo(desktopUrl)
                    return@post
                }
            }
            activeWebView?.reload()
        }
    }

    fun toggleIncognito() {
        val newIncognito = !currentTab.isIncognito
        repository.setIncognito(newIncognito)
        addNewTab(isIncognito = newIncognito)
    }

    // Translation
    fun toggleTranslation() {
        val tab = currentTab
        if (tab.url.isBlank()) return

        val newTranslated = !tab.isTranslated
        updateCurrentTab { it.copy(isTranslated = newTranslated) }

        if (newTranslated) {
            _translationBannerText.value = "正在翻译网页内容..."
            activeWebView?.evaluateJavascript(Scripts.TRANSLATION_SCRIPT) {
                _translationBannerText.value = "已翻译为中文"
            }
        } else {
            _translationBannerText.value = null
            activeWebView?.evaluateJavascript(Scripts.RESTORE_ORIGINAL_SCRIPT, null)
        }
    }

    fun dismissTranslationBanner() {
        _translationBannerText.value = null
    }

    // Video Detection & Floating Player
    fun onVideoFound(url: String, title: String, duration: Double, currentTime: Double, width: Int, height: Int) {
        val cleanTitle = title.ifBlank { currentTab.title }
        val effectiveUrl = if (url.isBlank() || url.startsWith("blob:") || !url.startsWith("http")) {
            repository.getDetectedStreamUrlForTab(currentTab.id) ?: url
        } else {
            url
        }
        if (effectiveUrl.isNotBlank() && !effectiveUrl.startsWith("blob:")) {
            repository.setDetectedStreamUrl(currentTab.id, effectiveUrl)
        }
        val info = VideoMediaInfo(
            url = effectiveUrl,
            pageUrl = currentTab.url,
            title = cleanTitle,
            duration = duration,
            currentTime = currentTime,
            videoWidth = if (width > 0) width else 16,
            videoHeight = if (height > 0) height else 9,
            isPlaying = true,
            originTabIndex = _currentTabIndex.value,
            originTabId = currentTab.id
        )
        // Native Media3 takeover is used only for sources the app can reliably
        // play itself. Blob/MSE pages remain on the WebView as a compatibility fallback.
        _detectedVideo.value = info
    }

    fun startFloatingPlayer(customVideo: VideoMediaInfo? = null) {
        val baseVideo = customVideo ?: _detectedVideo.value
        if (baseVideo == null) {
            activeWebView?.evaluateJavascript(Scripts.VIDEO_SNIFFER_PROBE, null)
            return
        }

        val candidateUrl = when {
            baseVideo.url.startsWith("http://", true) || baseVideo.url.startsWith("https://", true) ->
                baseVideo.url.trim()
            else ->
                repository.getDetectedStreamUrlForTab(currentTab.id)
                    ?.trim()
                    ?.takeIf { it.startsWith("http://", true) || it.startsWith("https://", true) }
                    ?: ""
        }

        val updatedVideo = baseVideo.copy(
            url = candidateUrl,
            originTabIndex = baseVideo.originTabIndex ?: _currentTabIndex.value
        )

        // Never manufacture the current page URL as a media URL. A webpage URL
        // handed to Media3 produces the black-player/fake-stream failure mode.
        if (!VideoSourceResolver.canUseNativePlayer(updatedVideo)) {
            android.widget.Toast.makeText(
                getApplication(),
                "当前视频暂未获得可用原生播放地址，继续使用网页播放器",
                android.widget.Toast.LENGTH_SHORT
            ).show()
            activeWebView?.evaluateJavascript(
                Scripts.RESUME_WEB_VIDEO_AT(baseVideo.currentTime.coerceAtLeast(0.0)),
                null
            )
            return
        }

        _detectedVideo.value = updatedVideo
        VideoPlaybackSessionManager.handoffState(updatedVideo)
        _isFloatingPlayerVisible.value = true

        // Hand the playback over to the native player first. The WebView is
        // locked before the native surface starts so both players never race.
        activeWebView?.evaluateJavascript(Scripts.LOCK_WEB_VIDEOS, null)
    }

    /**
     * Hide the in-app player because an external/system floating player is taking
     * ownership of playback. Unlike closeFloatingPlayer(), this must NOT resume
     * the WebView video.
     */
    fun hideFloatingPlayerForExternalPlayback() {
        _isFloatingPlayerVisible.value = false
    }

    fun closeFloatingPlayer(resumePositionSeconds: Double? = null) {
        val video = _detectedVideo.value
        _isFloatingPlayerVisible.value = false

        val sameOriginTab = video?.originTabIndex == null || video.originTabIndex == _currentTabIndex.value
        val sameSourcePage = video?.pageUrl.isNullOrBlank() || video?.pageUrl == currentTab.url
        if (!sameOriginTab || !sameSourcePage) {
            return
        }

        val session = VideoPlaybackSessionManager.current()
        val position = resumePositionSeconds
            ?: (session?.positionMs?.div(1000.0))
            ?: video?.currentTime
            ?: 0.0
        val shouldPlay = session?.isPlaying ?: true
        VideoPlaybackSessionManager.updatePosition((position * 1000.0).toLong().coerceAtLeast(0L))
        VideoPlaybackSessionManager.updatePlaying(shouldPlay)
        activeWebView?.evaluateJavascript(
            Scripts.RESUME_WEB_VIDEO_AT(position.coerceAtLeast(0.0), shouldPlay),
            null
        )
    }


    // Fullscreen Custom View (Web Video)
    fun showCustomVideoView(view: View, callback: WebChromeClient.CustomViewCallback) {
        _customVideoView.value = view
        customViewCallback = callback
    }

    fun hideCustomVideoView() {
        customViewCallback?.onCustomViewHidden()
        customViewCallback = null
        _customVideoView.value = null
    }

    // Bookmarking
    fun bookmarkCurrentPage() {
        if (currentTab.url.isNotBlank()) {
            repository.addBookmark(currentTab.title, currentTab.url)
        }
    }

    // Dialog toggles
    fun setMenuVisible(visible: Boolean) { _isMenuVisible.value = visible }
    fun setTabManagerVisible(visible: Boolean) { _isTabManagerVisible.value = visible }
    fun setSettingsVisible(visible: Boolean) { _isSettingsVisible.value = visible }
    fun setPluginManagerVisible(visible: Boolean) { _isPluginManagerVisible.value = visible }
    fun setHistoryBookmarksVisible(visible: Boolean) { _isHistoryBookmarksVisible.value = visible }
    fun openHistory() {
        _historyBookmarksInitialTab.value = 1
        _isHistoryBookmarksVisible.value = true
    }
    fun openBookmarks() {
        _historyBookmarksInitialTab.value = 0
        _isHistoryBookmarksVisible.value = true
    }
    fun setAiChatVisible(visible: Boolean) { _isAiChatVisible.value = visible }
}
