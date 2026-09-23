package com.example

import android.app.PictureInPictureParams
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Rational
import android.view.View
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.BrowserRepository
import com.example.engine.ElephantWebBridge
import com.example.engine.ElephantWebChromeClient
import com.example.engine.ElephantWebViewClient
import com.example.model.VideoMediaInfo
import com.example.player.FloatingVideoPlayerComponent
import com.example.player.InAppFloatingPlayer
import com.example.service.FloatingPlayerService
import com.example.ui.ai.AiChatDialog
import com.example.ui.browser.BottomNavBar
import com.example.ui.browser.BrowserTopBar
import com.example.ui.download.DownloadManagerScreen
import com.example.ui.history.HistoryBookmarksScreen
import com.example.ui.home.HomeScreen
import com.example.ui.menu.BrowserBottomSheetMenu
import com.example.ui.plugin.PluginManagerScreen
import com.example.ui.search.SearchOverlayScreen
import com.example.ui.settings.SettingsScreen
import com.example.ui.tabs.TabManagerScreen
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {

    private var viewModelRef: com.example.viewmodel.BrowserViewModel? = null
    private val isPipModeState = mutableStateOf(false)

    private val downloadReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "com.example.ACTION_DOWNLOAD_VIDEO") {
                val url = intent.getStringExtra("video_url") ?: ""
                val title = intent.getStringExtra("video_title") ?: "下载视频"
                if (url.isNotBlank()) {
                    viewModelRef?.startVideoDownload(url, title)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        try {
            val filter = android.content.IntentFilter("com.example.ACTION_DOWNLOAD_VIDEO")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(downloadReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                registerReceiver(downloadReceiver, filter)
            }
        } catch (e: Exception) {}

        setContent {
            val viewModel: com.example.viewmodel.BrowserViewModel = viewModel()
            viewModelRef = viewModel

            LaunchedEffect(Unit) {
                handleTabIntent(intent)
            }

            val isNightMode by viewModel.repository.isNightMode.collectAsState()
            val isDesktopMode by viewModel.repository.isDesktopMode.collectAsState()
            val isIncognito by viewModel.repository.isIncognito.collectAsState()
            val searchEngine by viewModel.repository.searchEngine.collectAsState()
            val quickSites by viewModel.repository.quickSites.collectAsState()
            val bookmarks by viewModel.repository.bookmarks.collectAsState()
            val dataSavedMb by viewModel.repository.dataSavedMb.collectAsState()

            val tabs by viewModel.tabs.collectAsState()
            val currentTabIndex by viewModel.currentTabIndex.collectAsState()
            val currentTab = tabs.getOrNull(currentTabIndex) ?: tabs.first()

            val detectedVideo by viewModel.detectedVideo.collectAsState()
            val isFloatingPlayerVisible by viewModel.isFloatingPlayerVisible.collectAsState()
            val customVideoView by viewModel.customVideoView.collectAsState()
            val translationStatus by viewModel.translationBannerText.collectAsState()

            val isMenuVisible by viewModel.isMenuVisible.collectAsState()
            val isTabManagerVisible by viewModel.isTabManagerVisible.collectAsState()
            val isSettingsVisible by viewModel.isSettingsVisible.collectAsState()
            val isPluginManagerVisible by viewModel.isPluginManagerVisible.collectAsState()
            val isHistoryBookmarksVisible by viewModel.isHistoryBookmarksVisible.collectAsState()
            val historyBookmarksInitialTab by viewModel.historyBookmarksInitialTab.collectAsState()
            val isAiChatVisible by viewModel.isAiChatVisible.collectAsState()
            val isDownloadManagerVisible by viewModel.isDownloadManagerVisible.collectAsState()
            val isSearchOverlayVisible by viewModel.isSearchOverlayVisible.collectAsState()
            val searchHistory by viewModel.repository.searchHistory.collectAsState()
            val pendingDownload by viewModel.pendingDownload.collectAsState()
            val inPipMode by remember { isPipModeState }

            // Back Press Handling
            BackHandler(enabled = true) {
                when {
                    isSearchOverlayVisible -> viewModel.setSearchOverlayVisible(false)
                    customVideoView != null -> viewModel.hideCustomVideoView()
                    isFloatingPlayerVisible -> viewModel.closeFloatingPlayer()
                    isDownloadManagerVisible -> viewModel.setDownloadManagerVisible(false)
                    isPluginManagerVisible -> viewModel.setPluginManagerVisible(false)
                    isSettingsVisible -> viewModel.setSettingsVisible(false)
                    isHistoryBookmarksVisible -> viewModel.setHistoryBookmarksVisible(false)
                    isTabManagerVisible -> viewModel.setTabManagerVisible(false)
                    isMenuVisible -> viewModel.setMenuVisible(false)
                    currentTab.url.isNotBlank() -> viewModel.goBack()
                    else -> finish()
                }
            }

            MyApplicationTheme(darkTheme = isNightMode, dynamicColor = false) {
                if (inPipMode) {
                    // DESKTOP PICTURE-IN-PICTURE (FIGURE 2 SCENARIO)
                    // Render the exact same Unified Floating Player matching Figure 1 UI!
                    val rawVideo = detectedVideo ?: VideoMediaInfo(
                        url = currentTab.url,
                        pageUrl = currentTab.url,
                        title = currentTab.title.ifBlank { "正在播放" },
                        videoWidth = 16,
                        videoHeight = 9,
                        originTabIndex = currentTabIndex
                    )
                    val activeVideo = if (FloatingVideoPlayerComponent.lastPlaybackPositionSeconds > 0) {
                        rawVideo.copy(currentTime = FloatingVideoPlayerComponent.lastPlaybackPositionSeconds)
                    } else {
                        rawVideo
                    }
                    InAppFloatingPlayer(
                        videoInfo = activeVideo,
                        isDesktopPiP = true,
                        currentTabIndex = currentTabIndex,
                        onClose = { currentPos ->
                            viewModel.closeFloatingPlayer(currentPos)
                            finish()
                        },
                        onEnterGlobalPiP = {},
                        onEnterFullscreen = {},
                        onDownloadVideo = { url, title ->
                            viewModel.startVideoDownload(url, title)
                        }
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(if (isNightMode) Color(0xFF111418) else Color(0xFFFFFFFF))
                    ) {
                    // --- MAIN BROWSING CONTENT ---
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .statusBarsPadding()
                    ) {
                        // Top Bar: Visible when on a webpage
                        if (!currentTab.isAtHome && customVideoView == null) {
                            BrowserTopBar(
                                tab = currentTab,
                                detectedVideo = detectedVideo,
                                translationStatus = translationStatus,
                                isNightMode = isNightMode,
                                onNavigate = { viewModel.navigateTo(it) },
                                onReload = { viewModel.reload() },
                                onStop = { viewModel.stopLoading() },
                                onToggleTranslation = { viewModel.toggleTranslation() },
                                onDismissTranslation = { viewModel.dismissTranslationBanner() },
                                onOpenFloatingPlayer = { triggerGlobalFloatingOrPiP(viewModel.detectedVideo.value ?: createFallbackVideoForCurrentTab(viewModel)) },
                                onToggleDesktopMode = { viewModel.toggleDesktopMode() }
                            )
                        }

                        // Middle View: Either HomeScreen or WebView
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                        ) {
                            if (currentTab.isAtHome) {
                                HomeScreen(
                                    searchEngine = searchEngine,
                                    isIncognito = currentTab.isIncognito,
                                    isNightMode = isNightMode,
                                    quickSites = quickSites,
                                    bookmarks = bookmarks,
                                    onSearch = { viewModel.navigateTo(it) },
                                    onOpenSearch = { viewModel.setSearchOverlayVisible(true) },
                                    onSelectEngine = { viewModel.repository.setSearchEngine(it) },
                                    onAddQuickSite = { title, url, bgColor ->
                                        viewModel.repository.addQuickSite(title, url, bgColor = bgColor)
                                    },
                                    onRemoveQuickSite = { url ->
                                        viewModel.repository.removeQuickSite(url)
                                    },
                                    onAddBookmarkToQuickSites = { bookmark ->
                                        viewModel.repository.addBookmarkToQuickSites(bookmark)
                                    },
                                    onOpenDownloads = { viewModel.openDownloads() },
                                    onOpenAi = { viewModel.setAiChatVisible(true) },
                                    onOpenHistory = { viewModel.openHistory() },
                                    onOpenBookmarks = { viewModel.openBookmarks() }
                                )
                            } else {
                                // Chromium Core WebView Host keyed by tab ID
                                androidx.compose.runtime.key(currentTab.id) {
                                    ChromiumWebViewContainer(
                                        tab = currentTab,
                                        viewModel = viewModel,
                                        onAdjustBrightness = { adjustWindowBrightness(it) },
                                        onAdjustVolume = { adjustSystemVolume(it) },
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                            }

                            // Fullscreen Web Video (HTML5 Custom View)
                            if (customVideoView != null) {
                                AndroidView(
                                    factory = {
                                        FrameLayout(it).apply {
                                            layoutParams = ViewGroup.LayoutParams(
                                                ViewGroup.LayoutParams.MATCH_PARENT,
                                                ViewGroup.LayoutParams.MATCH_PARENT
                                            )
                                            setBackgroundColor(0xFF000000.toInt())
                                            (customVideoView?.parent as? ViewGroup)?.removeView(customVideoView)
                                            addView(customVideoView)
                                        }
                                    },
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }

                        // Bottom Navigation Bar (Image 2 style)
                        if (customVideoView == null) {
                            BottomNavBar(
                                canGoBack = !currentTab.isAtHome && (currentTab.canGoBack || viewModel.activeWebView?.canGoBack() == true),
                                canGoForward = !currentTab.isAtHome && (currentTab.canGoForward || viewModel.activeWebView?.canGoForward() == true),
                                tabCount = tabs.size,
                                isIncognito = currentTab.isIncognito,
                                isNightMode = isNightMode,
                                onBack = { viewModel.goBack() },
                                onForward = { viewModel.goForward() },
                                onMenu = { viewModel.setMenuVisible(true) },
                                onTabs = { viewModel.setTabManagerVisible(true) },
                                onHome = { viewModel.goHome() }
                            )
                        }
                    }

                    // --- IN-APP FLOATING WINDOW PLAYER ---
                    // Supports arbitrary resizing in all directions (top, bottom, left, right)
                    // and matches video aspect ratio as requested!
                    if (isFloatingPlayerVisible && detectedVideo != null) {
                        InAppFloatingPlayer(
                            videoInfo = detectedVideo!!,
                            isDesktopPiP = false,
                            currentTabIndex = currentTabIndex,
                            onReturnToOriginTab = { originIdx ->
                                viewModel.selectTab(originIdx)
                            },
                            onClose = { currentPos ->
                                viewModel.closeFloatingPlayer(currentPos)
                            },
                            onEnterGlobalPiP = {
                                triggerGlobalFloatingOrPiP(detectedVideo!!)
                            },
                            onEnterFullscreen = {
                                viewModel.closeFloatingPlayer()
                                Toast.makeText(this@MainActivity, "已在当前网页切换至全屏", Toast.LENGTH_SHORT).show()
                            },
                            onDownloadVideo = { url, title ->
                                viewModel.startVideoDownload(url, title)
                            }
                        )
                    }

                    // --- BOTTOM SHEET MENU (Image 3) ---
                    if (isMenuVisible) {
                        BrowserBottomSheetMenu(
                            isNightMode = isNightMode,
                            isDesktopMode = currentTab.isDesktopMode,
                            isIncognito = currentTab.isIncognito,
                            isTranslated = currentTab.isTranslated,
                            dataSavedMb = dataSavedMb,
                            onDismiss = { viewModel.setMenuVisible(false) },
                            onOpenBookmarks = { viewModel.openBookmarks() },
                            onOpenHistory = { viewModel.openHistory() },
                            onOpenDownloads = { viewModel.openDownloads() },
                            onOpenPlugins = { viewModel.setPluginManagerVisible(true) },
                            onOpenFloatingPlayer = { triggerGlobalFloatingOrPiP(viewModel.detectedVideo.value ?: createFallbackVideoForCurrentTab(viewModel)) },
                            onBookmarkPage = {
                                viewModel.bookmarkCurrentPage()
                                Toast.makeText(this@MainActivity, "已加入书签", Toast.LENGTH_SHORT).show()
                            },
                            onToggleNightMode = { viewModel.toggleNightMode() },
                            onReload = { viewModel.reload() },
                            onToggleDesktopMode = { viewModel.toggleDesktopMode() },
                            onToggleTranslation = { viewModel.toggleTranslation() },
                            onShare = {
                                val sendIntent = Intent().apply {
                                    action = Intent.ACTION_SEND
                                    putExtra(Intent.EXTRA_TEXT, currentTab.url.ifBlank { "大象浏览器 - 极速无痕与全向悬浮窗播放器" })
                                    type = "text/plain"
                                }
                                startActivity(Intent.createChooser(sendIntent, "分享当前网页"))
                            },
                            onOpenSettings = { viewModel.setSettingsVisible(true) },
                            onExit = { finish() }
                        )
                    }

                    // --- TABS MANAGER SCREEN ---
                    AnimatedVisibility(
                        visible = isTabManagerVisible,
                        enter = fadeIn(),
                        exit = fadeOut()
                    ) {
                        TabManagerScreen(
                            tabs = tabs,
                            currentTabIndex = currentTabIndex,
                            isNightMode = isNightMode,
                            onSelectTab = { viewModel.selectTab(it) },
                            onCloseTab = { viewModel.closeTab(it) },
                            onCloseTabItem = { viewModel.closeTab(it) },
                            onNewTab = { incognito -> viewModel.addNewTab(isIncognito = incognito) },
                            onBack = { viewModel.setTabManagerVisible(false) }
                        )
                    }

                    // --- SETTINGS SCREEN (Image 4) ---
                    AnimatedVisibility(
                        visible = isSettingsVisible,
                        enter = fadeIn(),
                        exit = fadeOut()
                    ) {
                        SettingsScreen(
                            repository = viewModel.repository,
                            isNightMode = isNightMode,
                            isDesktopMode = currentTab.isDesktopMode,
                            searchEngine = searchEngine,
                            onBack = { viewModel.setSettingsVisible(false) },
                            onOpenPluginManager = { viewModel.setPluginManagerVisible(true) }
                        )
                    }

                    // --- PLUGIN MANAGER SCREEN ---
                    AnimatedVisibility(
                        visible = isPluginManagerVisible,
                        enter = fadeIn(),
                        exit = fadeOut()
                    ) {
                        PluginManagerScreen(
                            repository = viewModel.repository,
                            isNightMode = isNightMode,
                            onBack = { viewModel.setPluginManagerVisible(false) }
                        )
                    }

                    // --- HISTORY & BOOKMARKS SCREEN ---
                    AnimatedVisibility(
                        visible = isHistoryBookmarksVisible,
                        enter = fadeIn(),
                        exit = fadeOut()
                    ) {
                        HistoryBookmarksScreen(
                            repository = viewModel.repository,
                            initialTab = historyBookmarksInitialTab,
                            isNightMode = isNightMode,
                            onOpenUrl = {
                                viewModel.navigateTo(it)
                                viewModel.setHistoryBookmarksVisible(false)
                            },
                            onBack = { viewModel.setHistoryBookmarksVisible(false) }
                        )
                    }

                    // --- ASK AI CHAT DIALOG ---
                    if (isAiChatVisible) {
                        AiChatDialog(
                            currentPageTitle = currentTab.title,
                            currentPageUrl = currentTab.url,
                            onDismiss = { viewModel.setAiChatVisible(false) },
                            onSummarizeWebpage = {},
                            onTranslateWebpage = {
                                viewModel.toggleTranslation()
                            }
                        )
                    }

                    // --- DOWNLOAD MANAGER SCREEN ---
                    AnimatedVisibility(
                        visible = isDownloadManagerVisible,
                        enter = fadeIn(),
                        exit = fadeOut()
                    ) {
                        DownloadManagerScreen(
                            downloadManager = viewModel.downloadManager,
                            isNightMode = isNightMode,
                            onBack = { viewModel.setDownloadManagerVisible(false) }
                        )
                    }

                    // --- PENDING DOWNLOAD CONFIRMATION DIALOG ---
                    pendingDownload?.let { pending ->
                        AlertDialog(
                            onDismissRequest = { viewModel.dismissPendingDownload() },
                            title = { Text("下载网页文件", fontWeight = FontWeight.Bold) },
                            text = {
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text("检测到可下载文件，是否开始下载？", fontSize = 14.sp)
                                    Text("文件名: ${pending.fileName}", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                                    if (pending.contentLength > 0) {
                                        Text(
                                            "文件大小: ${com.example.model.DownloadItem.formatBytes(pending.contentLength)}",
                                            fontSize = 12.sp,
                                            color = Color(0xFF64748B)
                                        )
                                    }
                                }
                            },
                            confirmButton = {
                                Button(
                                    onClick = { viewModel.confirmPendingDownload() },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3B82F6))
                                ) {
                                    Text("开始下载")
                                }
                            },
                            dismissButton = {
                                TextButton(onClick = { viewModel.dismissPendingDownload() }) {
                                    Text("取消")
                                }
                            }
                        )
                    }

                    // --- FULL-SCREEN SEARCH OVERLAY SCREEN (Figure 2 layout) ---
                    AnimatedVisibility(
                        visible = isSearchOverlayVisible,
                        enter = fadeIn(),
                        exit = fadeOut()
                    ) {
                        SearchOverlayScreen(
                            searchEngine = searchEngine,
                            searchHistory = searchHistory,
                            isNightMode = isNightMode,
                            onSearch = { query ->
                                viewModel.repository.addSearchQuery(query)
                                viewModel.navigateTo(query)
                            },
                            onDeleteHistoryItem = { item ->
                                viewModel.repository.removeSearchQuery(item)
                            },
                            onClearAllHistory = {
                                viewModel.repository.clearSearchHistory()
                            },
                            onClose = {
                                viewModel.setSearchOverlayVisible(false)
                            }
                        )
                    }
                }
            }
        }
    }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleTabIntent(intent)
    }

    private fun handleTabIntent(intent: Intent?) {
        if (intent != null && intent.hasExtra(FloatingPlayerService.EXTRA_SELECT_TAB)) {
            val tabIndex = intent.getIntExtra(FloatingPlayerService.EXTRA_SELECT_TAB, 0)
            viewModelRef?.selectTab(tabIndex)
        }
    }

    fun createFallbackVideoForCurrentTab(
        viewModel: com.example.viewmodel.BrowserViewModel
    ): VideoMediaInfo {
        val tab = viewModel.currentTab
        return VideoMediaInfo(
            url = tab.url,
            pageUrl = tab.url,
            title = tab.title.ifBlank { "网页视频" },
            videoWidth = 16,
            videoHeight = 9,
            originTabIndex = viewModel.currentTabIndex.value
        )
    }

    override fun onResume() {
        super.onResume()

        // The floating button may have opened Android's PiP/overlay settings.
        // When the user returns, continue the original request automatically
        // instead of forcing another tap on the video player's floating button.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            FloatingVideoPlayerComponent.hasPipPermission(this)
        ) {
            FloatingVideoPlayerComponent.pendingGlobalVideo?.let { pending ->
                if (FloatingVideoPlayerComponent.hasOverlayPermission(this)) {
                    triggerGlobalFloatingOrPiP(pending)
                } else {
                    FloatingVideoPlayerComponent.requestOverlayPermission(this)
                }
            }
        }
    }

    fun triggerGlobalFloatingOrPiP(video: VideoMediaInfo) {
        // Pause the webpage immediately, before any permission/settings flow.
        // This prevents the webpage player from continuing while the global
        // floating player is being prepared.
        val vm = viewModelRef
        FloatingVideoPlayerComponent.pendingGlobalVideo = video
        vm?.activeWebView?.evaluateJavascript(
            com.example.engine.Scripts.PAUSE_WEB_VIDEOS,
            null
        )

        // UC-style global floating playback uses the WindowManager overlay service.
        // PiP permission is checked first to preserve the requested settings flow;
        // the actual cross-app window additionally requires overlay permission.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !FloatingVideoPlayerComponent.hasPipPermission(this)
        ) {
            FloatingVideoPlayerComponent.openPipSettings(this)
            return
        }

        if (!FloatingVideoPlayerComponent.hasOverlayPermission(this)) {
            FloatingVideoPlayerComponent.requestOverlayPermission(this)
            return
        }

        val streamUrl = if (
            video.url.isBlank() ||
            video.url.startsWith("blob:") ||
            !video.url.startsWith("http")
        ) {
            vm?.repository?.getDetectedStreamUrl(vm.currentTab.id)
                ?: vm?.repository?.lastDetectedStreamUrl
                ?: video.url
        } else {
            video.url
        }

        val effectiveVideo = video.copy(
            url = streamUrl,
            currentTime = video.currentTime.coerceAtLeast(0.0),
            originTabIndex = video.originTabIndex ?: vm?.currentTabIndex?.value
        )

        // Keep the real stream URL and playback position before launching
        // the overlay player.
        FloatingVideoPlayerComponent.activeVideoInfo = effectiveVideo
        FloatingVideoPlayerComponent.pendingGlobalVideo = null
        vm?.closeFloatingPlayer()

        FloatingVideoPlayerComponent.startSystemFloatingPlayer(
            context = this,
            video = effectiveVideo,
            originTabIndex = effectiveVideo.originTabIndex
        )
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        isPipModeState.value = isInPictureInPictureMode
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        // When exiting the app, continue playing seamlessly in desktop PiP floating window
        val vm = viewModelRef ?: return
        val video = vm.detectedVideo.value
        val isFloating = vm.isFloatingPlayerVisible.value
        if ((isFloating || (video != null && video.isPlaying)) && video != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && FloatingVideoPlayerComponent.hasPipPermission(this)) {
                try {
                    val pipParams = FloatingVideoPlayerComponent.buildPipParams(video)
                    enterPictureInPictureMode(pipParams)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(downloadReceiver)
        } catch (e: Exception) {}
    }

    private var currentWindowBrightness: Float = -1f

    fun adjustWindowBrightness(delta: Float): Float {
        val lp = window.attributes
        if (currentWindowBrightness < 0f) {
            currentWindowBrightness = try {
                Settings.System.getInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS) / 255f
            } catch (e: Exception) {
                0.5f
            }
        }
        currentWindowBrightness = (currentWindowBrightness + delta).coerceIn(0.05f, 1.0f)
        runOnUiThread {
            lp.screenBrightness = currentWindowBrightness
            window.attributes = lp
        }
        return currentWindowBrightness
    }

    fun adjustSystemVolume(delta: Float): Float {
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return 0.5f
        val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val curVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        val step = if (delta > 0) 1 else if (delta < 0) -1 else 0
        val newVol = (curVol + step).coerceIn(0, maxVol)
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, newVol, 0)
        return newVol.toFloat() / maxVol.toFloat().coerceAtLeast(1f)
    }
}

@Composable
fun ChromiumWebViewContainer(
    tab: com.example.model.BrowserTab,
    viewModel: com.example.viewmodel.BrowserViewModel,
    onAdjustBrightness: (Float) -> Float = { 0.5f },
    onAdjustVolume: (Float) -> Float = { 0.5f },
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val isNightMode = tab.isNightMode || viewModel.repository.isNightMode.value

    AndroidView(
        factory = { ctx ->
            // Use UI_MODE_NIGHT_NO context when night mode is off so Chromium engine reports prefers-color-scheme: light
            val webViewContext = if (isNightMode) {
                ctx
            } else {
                val config = Configuration(ctx.resources.configuration).apply {
                    uiMode = Configuration.UI_MODE_NIGHT_NO or (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv())
                }
                ctx.createConfigurationContext(config)
            }

            WebView(webViewContext).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                setBackgroundColor(if (isNightMode) 0xFF111418.toInt() else android.graphics.Color.WHITE)

                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    databaseEnabled = true
                    mediaPlaybackRequiresUserGesture = false
                    setSupportZoom(true)
                    builtInZoomControls = true
                    displayZoomControls = false
                    useWideViewPort = tab.isDesktopMode
                    loadWithOverviewMode = tab.isDesktopMode
                    userAgentString = viewModel.repository.getUserAgent(tab.isDesktopMode)
                    cacheMode = if (tab.isIncognito) WebSettings.LOAD_NO_CACHE else WebSettings.LOAD_DEFAULT
                    mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        forceDark = if (isNightMode) WebSettings.FORCE_DARK_ON else WebSettings.FORCE_DARK_OFF
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        isAlgorithmicDarkeningAllowed = isNightMode
                    }
                }

                // Attach JavaScript Bridge
                addJavascriptInterface(
                    ElephantWebBridge(
                        onVideoFound = { url, title, duration, currentTime, width, height ->
                            viewModel.onVideoFound(url, title, duration, currentTime, width, height)
                        },
                        onTranslationFinished = { success, count ->
                            // Handled in ViewModel
                        },
                        onAdjustBrightness = { delta ->
                            onAdjustBrightness(delta)
                        },
                        onAdjustVolume = { delta ->
                            onAdjustVolume(delta)
                        },
                        onOpenFloatingPlayer = { url, title, currentTime, duration, width, height ->
                            (context as? MainActivity)?.let { activity ->
                                activity.runOnUiThread {
                                    viewModel.onVideoFound(url, title, duration, currentTime, width, height)
                                    val video = viewModel.detectedVideo.value
                                        ?: activity.createFallbackVideoForCurrentTab(viewModel)
                                    activity.triggerGlobalFloatingOrPiP(video)
                                }
                            }
                        },
                        onDownloadVideo = { url, title ->
                            (context as? ComponentActivity)?.runOnUiThread {
                                viewModel.startVideoDownload(url, title)
                            }
                        },
                        onShowToast = { msg ->
                            (context as? ComponentActivity)?.runOnUiThread {
                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                            }
                        }
                    ),
                    "ElephantBridge"
                )

                webViewClient = ElephantWebViewClient(
                    tab = tab,
                    repository = viewModel.repository,
                    onPageStart = { url ->
                        viewModel.onPageStarted(url)
                    },
                    onPageFinish = { url, title ->
                        viewModel.onPageFinished(url, title)
                    },
                    onAdBlocked = {}
                )

                webChromeClient = ElephantWebChromeClient(
                    tab = tab,
                    onProgressChange = { progress ->
                        viewModel.onProgressChanged(progress)
                    },
                    onTitleChange = { title ->
                        viewModel.onTitleChanged(title)
                    },
                    onIconChange = { icon ->
                        viewModel.onIconChanged(icon)
                    },
                    onShowCustomVideo = { view, callback ->
                        viewModel.showCustomVideoView(view, callback)
                    },
                    onHideCustomVideo = {
                        viewModel.hideCustomVideoView()
                    }
                )

                setDownloadListener { url, userAgent, contentDisposition, mimetype, contentLength ->
                    viewModel.onDownloadRequested(url, userAgent, contentDisposition, mimetype, contentLength)
                }

                viewModel.activeWebView = this

                if (!tab.isAtHome) {
                    loadUrl(tab.url)
                }
            }
        },
        update = { webView ->
            viewModel.activeWebView = webView
            val currentNight = tab.isNightMode || viewModel.repository.isNightMode.value
            webView.setBackgroundColor(if (currentNight) 0xFF111418.toInt() else android.graphics.Color.WHITE)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val targetDark = if (currentNight) WebSettings.FORCE_DARK_ON else WebSettings.FORCE_DARK_OFF
                if (webView.settings.forceDark != targetDark) {
                    webView.settings.forceDark = targetDark
                }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (webView.settings.isAlgorithmicDarkeningAllowed != currentNight) {
                    webView.settings.isAlgorithmicDarkeningAllowed = currentNight
                }
            }
            // Update User Agent if Desktop mode changed
            val targetUa = viewModel.repository.getUserAgent(tab.isDesktopMode)
            if (webView.settings.userAgentString != targetUa) {
                webView.settings.userAgentString = targetUa
                webView.settings.useWideViewPort = tab.isDesktopMode
                webView.settings.loadWithOverviewMode = tab.isDesktopMode
                webView.reload()
            }
            if (!tab.isAtHome && (webView.url.isNullOrBlank() || webView.url == "about:blank")) {
                webView.loadUrl(tab.url)
            }
        },
        modifier = modifier
    )
}
