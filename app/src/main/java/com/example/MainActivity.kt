package com.example

import android.app.PictureInPictureParams
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.BrowserRepository
import com.example.engine.ElephantWebBridge
import com.example.engine.ElephantWebChromeClient
import com.example.engine.ElephantWebViewClient
import com.example.model.VideoMediaInfo
import com.example.player.InAppFloatingPlayer
import com.example.service.FloatingPlayerService
import com.example.ui.ai.AiChatDialog
import com.example.ui.browser.BottomNavBar
import com.example.ui.browser.BrowserTopBar
import com.example.ui.history.HistoryBookmarksScreen
import com.example.ui.home.HomeScreen
import com.example.ui.menu.BrowserBottomSheetMenu
import com.example.ui.plugin.PluginManagerScreen
import com.example.ui.settings.SettingsScreen
import com.example.ui.tabs.TabManagerScreen
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {

    private var viewModelRef: com.example.viewmodel.BrowserViewModel? = null
    private var isPipModeActive = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val viewModel: com.example.viewmodel.BrowserViewModel = viewModel()
            viewModelRef = viewModel

            val isNightMode by viewModel.repository.isNightMode.collectAsState()
            val isDesktopMode by viewModel.repository.isDesktopMode.collectAsState()
            val isIncognito by viewModel.repository.isIncognito.collectAsState()
            val searchEngine by viewModel.repository.searchEngine.collectAsState()
            val quickSites by viewModel.repository.quickSites.collectAsState()
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

            // Back Press Handling
            BackHandler(enabled = true) {
                when {
                    customVideoView != null -> viewModel.hideCustomVideoView()
                    isFloatingPlayerVisible -> viewModel.closeFloatingPlayer()
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
                        if (currentTab.url.isNotBlank() && customVideoView == null) {
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
                                onOpenFloatingPlayer = { viewModel.startFloatingPlayer() }
                            )
                        }

                        // Middle View: Either HomeScreen or WebView
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                        ) {
                            if (currentTab.url.isBlank()) {
                                HomeScreen(
                                    searchEngine = searchEngine,
                                    isIncognito = currentTab.isIncognito,
                                    isNightMode = isNightMode,
                                    quickSites = quickSites,
                                    onSearch = { viewModel.navigateTo(it) },
                                    onSelectEngine = { viewModel.repository.setSearchEngine(it) },
                                    onOpenDownloads = {
                                        Toast.makeText(this@MainActivity, "暂无正在下载的文件", Toast.LENGTH_SHORT).show()
                                    },
                                    onOpenAi = { viewModel.setAiChatVisible(true) },
                                    onOpenHistory = { viewModel.openHistory() },
                                    onOpenBookmarks = { viewModel.openBookmarks() }
                                )
                            } else {
                                // Chromium Core WebView Host
                                ChromiumWebViewContainer(
                                    tab = currentTab,
                                    viewModel = viewModel,
                                    modifier = Modifier.fillMaxSize()
                                )
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
                                canGoBack = currentTab.canGoBack || currentTab.url.isNotBlank(),
                                canGoForward = currentTab.canGoForward,
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
                            onClose = { viewModel.closeFloatingPlayer() },
                            onEnterGlobalPiP = {
                                triggerGlobalFloatingOrPiP(detectedVideo!!)
                            },
                            onEnterFullscreen = {
                                viewModel.closeFloatingPlayer()
                                Toast.makeText(this@MainActivity, "已在当前网页切换至全屏", Toast.LENGTH_SHORT).show()
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
                            onOpenDownloads = {
                                Toast.makeText(this@MainActivity, "暂无正在下载的文件", Toast.LENGTH_SHORT).show()
                            },
                            onOpenPlugins = { viewModel.setPluginManagerVisible(true) },
                            onOpenFloatingPlayer = { viewModel.startFloatingPlayer() },
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
                }
            }
        }
    }

    private fun triggerGlobalFloatingOrPiP(video: VideoMediaInfo) {
        val width = if (video.videoWidth > 0) video.videoWidth else 16
        val height = if (video.videoHeight > 0) video.videoHeight else 9

        // 1. Check if can draw system overlays
        val hasOverlayPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(this)
        } else {
            true
        }

        if (hasOverlayPermission) {
            val serviceIntent = Intent(this, FloatingPlayerService::class.java).apply {
                putExtra(FloatingPlayerService.EXTRA_VIDEO_URL, video.url)
                putExtra(FloatingPlayerService.EXTRA_VIDEO_TITLE, video.title)
                putExtra(FloatingPlayerService.EXTRA_VIDEO_RATIO, width.toFloat() / height.toFloat())
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }
            Toast.makeText(this, "全局悬浮窗已在桌面开启", Toast.LENGTH_SHORT).show()
        } else {
            // Use Android Picture-in-Picture directly!
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                try {
                    val rational = Rational(width.coerceIn(1, 1000), height.coerceIn(1, 1000))
                    val pipParams = PictureInPictureParams.Builder()
                        .setAspectRatio(rational)
                        .build()
                    enterPictureInPictureMode(pipParams)
                } catch (e: Exception) {
                    Toast.makeText(this, "正在为您请求全局悬浮窗权限...", Toast.LENGTH_SHORT).show()
                    val intent = Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:$packageName")
                    )
                    startActivity(intent)
                }
            } else {
                Toast.makeText(this, "请在设置中开启悬浮窗权限", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        isPipModeActive = isInPictureInPictureMode
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        // If in-app floating player is currently active, enter PiP smoothly
        val vm = viewModelRef ?: return
        val video = vm.detectedVideo.value
        if (vm.isFloatingPlayerVisible.value && video != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val width = if (video.videoWidth > 0) video.videoWidth else 16
                val height = if (video.videoHeight > 0) video.videoHeight else 9
                val rational = Rational(width.coerceIn(1, 1000), height.coerceIn(1, 1000))
                enterPictureInPictureMode(PictureInPictureParams.Builder().setAspectRatio(rational).build())
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}

@Composable
fun ChromiumWebViewContainer(
    tab: com.example.model.BrowserTab,
    viewModel: com.example.viewmodel.BrowserViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    AndroidView(
        factory = { ctx ->
            WebView(ctx).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )

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
                    userAgentString = if (tab.isDesktopMode) BrowserRepository.DESKTOP_USER_AGENT else BrowserRepository.MOBILE_USER_AGENT
                    cacheMode = if (tab.isIncognito) WebSettings.LOAD_NO_CACHE else WebSettings.LOAD_DEFAULT
                    mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                }

                // Attach JavaScript Bridge
                addJavascriptInterface(
                    ElephantWebBridge(
                        onVideoFound = { url, title, duration, currentTime, width, height ->
                            viewModel.onVideoFound(url, title, duration, currentTime, width, height)
                        },
                        onTranslationFinished = { success, count ->
                            // Handled in ViewModel
                        }
                    ),
                    "ElephantBridge"
                )

                webViewClient = ElephantWebViewClient(
                    tab = tab,
                    repository = viewModel.repository,
                    onPageStart = { url ->
                        tab.url = url
                    },
                    onPageFinish = { url, title ->
                        tab.url = url
                        tab.title = title
                    },
                    onAdBlocked = {}
                )

                webChromeClient = ElephantWebChromeClient(
                    tab = tab,
                    onProgressChange = { progress ->
                        tab.progress = progress
                    },
                    onTitleChange = { title ->
                        tab.title = title
                    },
                    onIconChange = { icon ->
                        tab.favicon = icon
                    },
                    onShowCustomVideo = { view, callback ->
                        viewModel.showCustomVideoView(view, callback)
                    },
                    onHideCustomVideo = {
                        viewModel.hideCustomVideoView()
                    }
                )

                viewModel.activeWebView = this

                if (tab.url.isNotBlank()) {
                    loadUrl(tab.url)
                }
            }
        },
        update = { webView ->
            viewModel.activeWebView = webView
            // Update User Agent if Desktop mode changed
            val targetUa = if (tab.isDesktopMode) BrowserRepository.DESKTOP_USER_AGENT else BrowserRepository.MOBILE_USER_AGENT
            if (webView.settings.userAgentString != targetUa) {
                webView.settings.userAgentString = targetUa
                webView.settings.useWideViewPort = tab.isDesktopMode
                webView.settings.loadWithOverviewMode = tab.isDesktopMode
                webView.reload()
            }
        },
        modifier = modifier
    )
}
