package com.example.viewmodel

import android.app.Application
import android.graphics.Bitmap
import android.view.View
import android.webkit.WebChromeClient
import android.webkit.WebView
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.BrowserRepository
import com.example.engine.Scripts
import com.example.model.BookmarkItem
import com.example.model.BrowserTab
import com.example.model.HistoryItem
import com.example.model.PluginItem
import com.example.model.VideoMediaInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class BrowserViewModel(application: Application) : AndroidViewModel(application) {
    val repository = BrowserRepository(application)

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

    // Translation Banner Status
    private val _translationBannerText = MutableStateFlow<String?>(null)
    val translationBannerText: StateFlow<String?> = _translationBannerText.asStateFlow()

    // Active WebView reference for executing actions
    var activeWebView: WebView? = null

    fun setUrlInput(input: String) {
        _urlInput.value = input
    }

    fun navigateTo(queryOrUrl: String) {
        if (queryOrUrl.isBlank()) return
        val finalUrl = repository.getSearchUrl(queryOrUrl)
        _urlInput.value = finalUrl

        val current = currentTab
        current.url = finalUrl
        current.title = "加载中..."
        current.progress = 10
        current.isLoading = true
        _tabs.value = _tabs.value.toList() // Trigger recomposition

        activeWebView?.loadUrl(finalUrl)
    }

    fun goHome() {
        val current = currentTab
        current.url = ""
        current.title = if (current.isIncognito) "无痕新标签" else "大象浏览器"
        current.progress = 0
        current.isLoading = false
        _urlInput.value = ""
        _tabs.value = _tabs.value.toList()
        activeWebView?.loadUrl("about:blank")
    }

    fun goBack() {
        if (activeWebView?.canGoBack() == true) {
            activeWebView?.goBack()
        } else if (currentTab.url.isNotBlank()) {
            goHome()
        }
    }

    fun goForward() {
        if (activeWebView?.canGoForward() == true) {
            activeWebView?.goForward()
        }
    }

    fun reload() {
        activeWebView?.reload()
    }

    fun stopLoading() {
        activeWebView?.stopLoading()
    }

    // Tab Management
    fun addNewTab(isIncognito: Boolean = repository.isIncognito.value, initialUrl: String = "") {
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
            _currentTabIndex.value = index
            _urlInput.value = _tabs.value[index].url
            _isTabManagerVisible.value = false
        }
    }

    fun closeTab(index: Int) {
        val currentList = _tabs.value.toMutableList()
        if (index !in currentList.indices) return
        if (currentList.size <= 1) {
            // Keep at least one tab
            currentList[0] = BrowserTab(url = "", title = "大象浏览器")
            _tabs.value = currentList
            _currentTabIndex.value = 0
            _urlInput.value = ""
            goHome()
            return
        }

        currentList.removeAt(index)
        _tabs.value = currentList
        val newIndex = _currentTabIndex.value.coerceAtMost(currentList.lastIndex)
        _currentTabIndex.value = newIndex
        _urlInput.value = currentList[newIndex].url
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
        currentTab.isNightMode = newMode
        _tabs.value = _tabs.value.toList()

        if (newMode) {
            activeWebView?.evaluateJavascript(Scripts.NIGHT_MODE_CSS, null)
        } else {
            activeWebView?.evaluateJavascript(Scripts.REMOVE_NIGHT_MODE_CSS, null)
        }
    }

    fun toggleDesktopMode() {
        val newMode = !currentTab.isDesktopMode
        currentTab.isDesktopMode = newMode
        repository.setDesktopMode(newMode)
        _tabs.value = _tabs.value.toList()

        activeWebView?.settings?.let { s ->
            s.userAgentString = if (newMode) BrowserRepository.DESKTOP_USER_AGENT else BrowserRepository.MOBILE_USER_AGENT
            s.useWideViewPort = newMode
            s.loadWithOverviewMode = newMode
        }
        activeWebView?.reload()
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

        tab.isTranslated = !tab.isTranslated
        _tabs.value = _tabs.value.toList()

        if (tab.isTranslated) {
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
        val info = VideoMediaInfo(
            url = url,
            pageUrl = currentTab.url,
            title = cleanTitle,
            duration = duration,
            currentTime = currentTime,
            videoWidth = if (width > 0) width else 16,
            videoHeight = if (height > 0) height else 9,
            isPlaying = true
        )
        _detectedVideo.value = info
    }

    fun startFloatingPlayer(customVideo: VideoMediaInfo? = null) {
        val video = customVideo ?: _detectedVideo.value
        if (video != null) {
            _detectedVideo.value = video
            _isFloatingPlayerVisible.value = true
            // Pause page video so only floating player plays
            activeWebView?.evaluateJavascript(Scripts.PAUSE_WEB_VIDEOS, null)
        } else {
            // If no video is currently reported, probe page again
            activeWebView?.evaluateJavascript(Scripts.VIDEO_SNIFFER_PROBE, null)
            // Or create playable stream from active page
            if (currentTab.url.isNotBlank()) {
                val fallback = VideoMediaInfo(
                    url = currentTab.url,
                    title = currentTab.title,
                    videoWidth = 16,
                    videoHeight = 9
                )
                _detectedVideo.value = fallback
                _isFloatingPlayerVisible.value = true
            }
        }
    }

    fun closeFloatingPlayer() {
        _isFloatingPlayerVisible.value = false
        activeWebView?.evaluateJavascript(Scripts.RESUME_WEB_VIDEOS, null)
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
