package com.example

import android.app.PictureInPictureParams
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
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
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.core.content.FileProvider
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import kotlinx.coroutines.launch
import com.example.data.BrowserRepository
import com.example.data.BrowserBackupManager
import com.example.engine.BrowserPerformanceManager
import com.example.engine.ElephantWebBridge
import com.example.engine.ElephantWebChromeClient
import com.example.engine.ElephantWebViewClient
import com.example.model.VideoMediaInfo
import com.example.player.VideoSourceResolver
import com.example.player.NativeVideoPlaybackManager
import com.example.player.FloatingVideoPlayerComponent
import com.example.player.InAppFloatingPlayer
import com.example.service.FloatingPlayerService
import com.example.ui.ai.AiChatDialog
import com.example.ui.ai.ElephantAiScreen
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
    // Main browser activity

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

            val restoreBackupLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.OpenDocument()
            ) { uri ->
                if (uri != null) {
                    val manager = BrowserBackupManager(this@MainActivity)
                    val json = manager.read(uri)
                    val ok = json?.let { manager.restore(viewModel.repository, it) } == true
                    json?.let { manager.extractTabs(it) }?.let { (restoredTabs, restoredIndex) ->
                        viewModel.restoreTabsFromBackup(restoredTabs, restoredIndex)
                    }
                    Toast.makeText(
                        this@MainActivity,
                        if (ok) "浏览器数据已恢复，重新打开标签页后生效" else "备份文件无效或无法读取",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
            viewModel.repository.extensionManager.setBrowserTabCreator { url, active ->
                runOnUiThread { viewModel.addNewTab(initialUrl = url) }
            }
            viewModel.repository.extensionManager.setBrowserTabController(
                creator = { extensionTabId, url, active ->
                    runOnUiThread { viewModel.addNewTabForExtension(extensionTabId, url, active) }
                },
                updater = { extensionTabId, url ->
                    runOnUiThread { viewModel.updateExtensionTab(extensionTabId, url) }
                },
                selector = { extensionTabId ->
                    runOnUiThread { viewModel.selectExtensionTab(extensionTabId) }
                }
            )

            LaunchedEffect(Unit) {
                handleTabIntent(intent)
            }

            // Native playback is an optimization/takeover layer. If Media3
            // cannot play a source (DRM auth, unsupported codec, expired URL,
            // etc.), immediately return control to the exact WebView source
            // instead of leaving the user on a black/error player.
            DisposableEffect(viewModel) {
                NativeVideoPlaybackManager.setPlaybackErrorListener {
                    runOnUiThread {
                        viewModel.onNativePlaybackError()
                    }
                }
                onDispose {
                    NativeVideoPlaybackManager.setPlaybackErrorListener(null)
                }
            }

            val isNightMode by viewModel.repository.isNightMode.collectAsState()
            val isDesktopMode by viewModel.repository.isDesktopMode.collectAsState()
            val isIncognito by viewModel.repository.isIncognito.collectAsState()
            val searchEngine by viewModel.repository.searchEngine.collectAsState()
            val quickSites by viewModel.repository.quickSites.collectAsState()
            val bookmarks by viewModel.repository.bookmarks.collectAsState()
            val dataSavedMb by viewModel.repository.dataSavedMb.collectAsState()
            val isContentBlocking by viewModel.repository.isContentBlocking.collectAsState()

            val tabs by viewModel.tabs.collectAsState()
            val currentTabIndex by viewModel.currentTabIndex.collectAsState()
            val currentTab = tabs.getOrNull(currentTabIndex) ?: tabs.firstOrNull() ?: com.example.model.BrowserTab()

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
            var aiPageContext by remember { mutableStateOf("") }
            var aiPageTitle by remember { mutableStateOf("") }
            // Fullscreen video lock: blocks accidental touches on the fullscreen video
            // while leaving a lock/unlock control on the left edge of the screen.
            var nativePlayerFullscreen by remember { mutableStateOf(false) }

            var orientationBeforeNativeFullscreen by remember {
                mutableStateOf(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED)
            }
            DisposableEffect(nativePlayerFullscreen) {
                val activity = this@MainActivity
                val insetsController = WindowCompat.getInsetsController(
                    activity.window,
                    activity.window.decorView
                )
                if (nativePlayerFullscreen) {
                    orientationBeforeNativeFullscreen = activity.requestedOrientation
                    activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR
                    insetsController.hide(WindowInsetsCompat.Type.systemBars())
                    insetsController.systemBarsBehavior =
                        androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                } else {
                    activity.requestedOrientation = orientationBeforeNativeFullscreen
                    insetsController.show(WindowInsetsCompat.Type.systemBars())
                }
                onDispose { }
            }

            fun openAiWithCurrentPage() {
                aiPageTitle = currentTab.title
                aiPageContext = ""
                viewModel.activeWebView?.evaluateJavascript(
                    "(function(){return document.body ? document.body.innerText : '';})()"
                ) { raw ->
                    aiPageContext = (raw ?: "")
                        .removePrefix("\"").removeSuffix("\"")
                        .replace("\\n", "\n")
                        .replace("\\\"", "\"")
                        .take(12000)
                    viewModel.setAiChatVisible(true)
                } ?: viewModel.setAiChatVisible(true)
            }

            // Back Press Handling
            BackHandler(enabled = true) {
                when {
                    isSearchOverlayVisible -> viewModel.setSearchOverlayVisible(false)
                    isAiChatVisible -> viewModel.setAiChatVisible(false)
                    customVideoView != null -> viewModel.hideCustomVideoView()
                    nativePlayerFullscreen -> nativePlayerFullscreen = false
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
                                onToggleDesktopMode = { viewModel.toggleDesktopMode() },
                                repository = viewModel.repository
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
                                    onOpenAi = { openAiWithCurrentPage() },
                                    onOpenHistory = { viewModel.openHistory() },
                                    onOpenBookmarks = { viewModel.openBookmarks() }
                                )
                            } else {
                                // When the native in-app player is open, hide the webpage
                                // underneath it. This prevents the webpage's own video title
                                // and controls from showing through the native player and
                                // creating a duplicated/ghosted title.
                                if (!isFloatingPlayerVisible) {
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
                            }

                            // Fullscreen Web Video (HTML5 Custom View)
                            if (customVideoView != null) {
                                Box(modifier = Modifier.fillMaxSize()) {
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
                    if ((isFloatingPlayerVisible || nativePlayerFullscreen) && detectedVideo != null) {
                        InAppFloatingPlayer(
                            videoInfo = detectedVideo!!,
                            isDesktopPiP = false,
                            isFullscreen = nativePlayerFullscreen,
                            currentTabIndex = currentTabIndex,
                            onReturnToOriginTab = { originIdx ->
                                viewModel.selectTab(originIdx)
                            },
                            onClose = { currentPos ->
                                nativePlayerFullscreen = false
                                viewModel.closeFloatingPlayer(currentPos)
                            },
                            onEnterGlobalPiP = {
                                triggerGlobalFloatingOrPiP(detectedVideo!!)
                            },
                            onEnterFullscreen = {
                                nativePlayerFullscreen = !nativePlayerFullscreen
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
                            onRestoreClosedTab = { viewModel.restoreRecentlyClosedTab() },
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
                            isContentBlocking = isContentBlocking,
                            onBack = { viewModel.setSettingsVisible(false) },
                            onOpenPluginManager = { viewModel.setPluginManagerVisible(true) },
                            onToggleContentBlocking = { viewModel.repository.setContentBlocking(!viewModel.repository.isContentBlocking.value) },
                            onBackupData = {
                                try {
                                    val manager = BrowserBackupManager(this@MainActivity)
                                    val file = java.io.File(cacheDir, "elephant-browser-backup-" + System.currentTimeMillis() + ".json")
                                    file.writeText(manager.buildBackup(viewModel.repository, tabs, currentTabIndex), Charsets.UTF_8)
                                    val uri = FileProvider.getUriForFile(
                                        this@MainActivity,
                                        packageName + ".fileprovider",
                                        file
                                    )
                                    startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                                        type = "application/json"
                                        putExtra(Intent.EXTRA_STREAM, uri)
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }, "导出浏览器备份"))
                                } catch (e: Exception) {
                                    Toast.makeText(this@MainActivity, "导出备份失败", Toast.LENGTH_SHORT).show()
                                }
                            },
                            onRestoreData = {
                                restoreBackupLauncher.launch(arrayOf("application/json", "text/plain", "*/*"))
                            }
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
                            onBack = { viewModel.setPluginManagerVisible(false) },
                            onOpenChromeWebStore = {
                                viewModel.addNewTab(initialUrl = "https://chromewebstore.google.com/")
                                viewModel.setPluginManagerVisible(false)
                            }
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

                    // --- ASK AI FULL SCREEN ---
                    AnimatedVisibility(
                        visible = isAiChatVisible,
                        enter = fadeIn(),
                        exit = fadeOut()
                    ) {
                        ElephantAiScreen(
                            isNightMode = isNightMode,
                            pageTitle = aiPageTitle,
                            pageContext = aiPageContext,
                            onBack = { viewModel.setAiChatVisible(false) }
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

    override fun onPause() {
        if (activeInstance === this) activeInstance = null
        super.onPause()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleTabIntent(intent)

        if (intent.getBooleanExtra(FloatingPlayerService.EXTRA_RESUME_WEB_VIDEO, false)) {
            val position = intent.getDoubleExtra(FloatingPlayerService.EXTRA_VIDEO_POSITION_SECONDS, 0.0)
            val shouldPlay = intent.getBooleanExtra(
                FloatingPlayerService.EXTRA_VIDEO_SHOULD_PLAY,
                com.example.player.VideoPlaybackSessionManager.current()?.isPlaying ?: true
            )
            // Queue the resume against the source tab. The WebView registers
            // itself when Compose has actually attached it, so this no longer
            // depends on an arbitrary delay or the previously active tab.
            viewModelRef?.let { vm ->
                val tabIndex = intent.getIntExtra(FloatingPlayerService.EXTRA_SELECT_TAB, vm.currentTabIndex.value)
                val targetTab = vm.tabs.value.getOrNull(tabIndex)
                val pageUrl = targetTab?.url.orEmpty()
                val tabId = intent.getStringExtra(FloatingPlayerService.EXTRA_ORIGIN_TAB_ID) ?: targetTab?.id
                vm.queueWebVideoResume(tabIndex, tabId, pageUrl, position, shouldPlay)
            }
        }
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
        // A fallback video must only use the stream discovered for this tab.
        // Never fall back to the repository-wide lastDetectedStreamUrl, which
        // can belong to another tab and cause cross-tab playback leakage.
        val detectedUrl = viewModel.repository.getDetectedStreamUrlForTab(tab.id)
            ?.trim()
            ?.takeIf { it.startsWith("http://", true) || it.startsWith("https://", true) }
            ?: ""
        return VideoMediaInfo(
            url = detectedUrl,
            pageUrl = tab.url,
            title = tab.title.ifBlank { "网页视频" },
            videoWidth = 16,
            videoHeight = 9,
            originTabIndex = viewModel.currentTabIndex.value,
            originTabId = tab.id
        )
    }

    override fun onResume() {
        super.onResume()
        activeInstance = this

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
        val vm = viewModelRef
        val candidateUrl = when {
            video.url.startsWith("http://", true) || video.url.startsWith("https://", true) ->
                video.url.trim()
            vm != null -> vm.repository.getDetectedStreamUrlForTab(vm.currentTab.id)
                ?.trim()
                ?.takeIf { it.startsWith("http://", true) || it.startsWith("https://", true) }
                ?: ""
            else -> ""
        }

        val effectiveVideo = video.copy(
            url = candidateUrl,
            currentTime = video.currentTime.coerceAtLeast(0.0),
            originTabIndex = video.originTabIndex ?: vm?.currentTabIndex?.value,
            originTabId = video.originTabId ?: vm?.currentTab?.id
        )

        if (!VideoSourceResolver.canUseNativePlayer(effectiveVideo)) {
            FloatingVideoPlayerComponent.pendingGlobalVideo = null
            vm?.activeWebView?.evaluateJavascript(
                com.example.engine.Scripts.RESUME_WEB_VIDEO_AT(
                    video.currentTime.coerceAtLeast(0.0),
                    video.isPlaying
                ),
                null
            )
            Toast.makeText(
                this,
                "当前视频没有可用的原生播放地址，继续使用网页播放器",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        // Permissions are checked before locking the WebView. If Android sends
        // the user to settings, the original HTML5 video remains untouched until
        // the request can actually be handed to the native player.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !FloatingVideoPlayerComponent.hasPipPermission(this)
        ) {
            FloatingVideoPlayerComponent.pendingGlobalVideo = effectiveVideo
            FloatingVideoPlayerComponent.openPipSettings(this)
            return
        }

        if (!FloatingVideoPlayerComponent.hasOverlayPermission(this)) {
            FloatingVideoPlayerComponent.pendingGlobalVideo = effectiveVideo
            FloatingVideoPlayerComponent.requestOverlayPermission(this)
            return
        }

        // LOCK_WEB_VIDEOS snapshots the exact HTML5 position/play state and then
        // pauses the WebView. Only after that callback do we start Media3.
        FloatingVideoPlayerComponent.pendingGlobalVideo = effectiveVideo
        val webView = vm?.activeWebView
        if (webView != null) {
            webView.evaluateJavascript(
                com.example.engine.Scripts.LOCK_WEB_VIDEOS
            ) { _ ->
                // LOCK_WEB_VIDEOS snapshots the real HTML5 position into the
                // ViewModel before pausing. Re-read it here so Media3 does not
                // start from the older 250ms monitor sample.
                val latest = vm?.detectedVideo?.value
                    ?.takeIf { detected ->
                        detected.originTabId.isNullOrBlank() ||
                            detected.originTabId == vm.currentTab.id
                    }
                    ?: effectiveVideo
                continueGlobalFloatingHandoff(latest)
            }
        } else {
            continueGlobalFloatingHandoff(effectiveVideo)
        }
    }

    private fun continueGlobalFloatingHandoff(effectiveVideo: VideoMediaInfo) {
        val vm = viewModelRef
        if (!VideoSourceResolver.canUseNativePlayer(effectiveVideo)) return

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

        FloatingVideoPlayerComponent.activeVideoInfo = effectiveVideo
        FloatingVideoPlayerComponent.pendingGlobalVideo = null
        vm?.hideFloatingPlayerForExternalPlayback()

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
        if ((isFloating || (video != null && video.isPlaying)) && video != null && (isFloating || vm.repository.autoPip.value)) {
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

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        viewModelRef?.let { vm ->
            BrowserPerformanceManager.onAppTrimMemory(level, listOfNotNull(vm.activeWebView))
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
    companion object {
        private var activeInstance: MainActivity? = null

        @JvmStatic
        fun shouldResumeFloatingVideo(originTabIndex: Int, originTabId: String?, sourcePageUrl: String): Boolean {
            val activity = activeInstance ?: return false
            val vm = activity.viewModelRef ?: return false
            val current = vm.currentTab
            // A stable tab ID is authoritative. Do not fall back to the old
            // numeric index when an ID is present: tab close/reorder can shift
            // indexes and would otherwise resume the wrong WebView.
            return if (!originTabId.isNullOrBlank()) {
                current.id == originTabId
            } else {
                vm.currentTabIndex.value == originTabIndex ||
                    (sourcePageUrl.isNotBlank() && current.url == sourcePageUrl)
            }
        }

        @JvmStatic
        fun resumeWebVideoFromFloatingClose(
            originTabId: String?,
            originTabIndex: Int,
            positionSeconds: Double,
            shouldPlay: Boolean
        ): Boolean {
            val activity = activeInstance ?: return false
            val vm = activity.viewModelRef ?: return false
            val targetIndex = if (originTabIndex >= 0) {
                originTabIndex
            } else {
                vm.currentTabIndex.value
            }
            val targetTab = vm.tabs.value.getOrNull(targetIndex) ?: return false
            val targetTabId = originTabId ?: targetTab.id

            // The browser Activity is already alive behind the overlay. Queue the
            // WebView resume directly instead of starting/recreating MainActivity.
            vm.queueWebVideoResume(
                targetIndex,
                targetTabId,
                targetTab.url,
                positionSeconds.coerceAtLeast(0.0),
                shouldPlay
            )
            vm.selectTab(targetIndex)
            return true
        }

        @JvmStatic
        fun unlockFloatingSourceTab(originTabId: String?, originTabIndex: Int = -1) {
            val activity = activeInstance ?: return
            activity.viewModelRef?.let { vm ->
                if (!originTabId.isNullOrBlank()) vm.unlockWebVideoForTab(originTabId)
                else if (originTabIndex >= 0) vm.unlockWebVideoForTabIndex(originTabIndex)
            }
        }
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

                BrowserPerformanceManager.configure(this, tab.isIncognito, tab.isDesktopMode, isNightMode)

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
                        onVideoPlaybackState = { currentTime, isPlaying ->
                            viewModel.onWebVideoPlaybackState(currentTime, isPlaying)
                        },
                        onDrmDetected = { licenseUri, scheme, headers ->
                            (context as? ComponentActivity)?.runOnUiThread {
                                viewModel.onDrmLicenseDetected(licenseUri, scheme, headers)
                            }
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

                // Attach the new extension runtime bridge to every real browser WebView.
                viewModel.repository.extensionManager.attachWebView(tab.id, this, tab.url)

                webViewClient = ElephantWebViewClient(
                    tab = tab,
                    repository = viewModel.repository,
                    onPageStart = { url ->
                        viewModel.onPageStarted(url)
                    },
                    onPageFinish = { url, title ->
                        viewModel.onPageFinished(url, title)
                    },
                    onExtensionDownload = { url ->
                        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
                            viewModel.repository.extensionManager.installUrl(url)
                                .onSuccess {
                                    Toast.makeText(context, "已安装扩展：" + it.name, Toast.LENGTH_SHORT).show()
                                }
                                .onFailure {
                                    Toast.makeText(context, "扩展安装失败：" + (it.message ?: "扩展包无效"), Toast.LENGTH_LONG).show()
                                }
                        }
                    },
                    onUserScriptDownload = { url ->
                        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
                            viewModel.repository.userScriptManager.installUrl(url)
                                .onSuccess {
                                    Toast.makeText(context, "已安装脚本：" + it.name, Toast.LENGTH_SHORT).show()
                                }
                                .onFailure {
                                    Toast.makeText(context, "脚本安装失败：" + (it.message ?: "脚本无效"), Toast.LENGTH_LONG).show()
                                }
                        }
                    },
                    onDrmLicenseRequest = { licenseUri, scheme, headers ->
                        // shouldInterceptRequest runs off the UI thread.
                        (context as? ComponentActivity)?.runOnUiThread {
                            viewModel.onDrmLicenseDetected(licenseUri, scheme, headers)
                        }
                    }
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

                // One and only WebView download entry point. The previous implementation
                // registered a second listener later in the same WebView setup, which silently
                // replaced the real downloader listener. As a result ordinary downloads worked
                // only as a pending-dialog path, while direct downloads could appear to do nothing.
                setDownloadListener { url, userAgent, contentDisposition, mimeType, contentLength ->
                    val safeUrl = url?.trim().orEmpty()
                    if (safeUrl.isBlank()) {
                        Toast.makeText(context, "无效的下载地址", Toast.LENGTH_SHORT).show()
                    } else if (
                        mimeType.equals("application/x-chrome-extension", true) ||
                        safeUrl.contains("/service/update2/crx", true) ||
                        safeUrl.substringBefore("?").endsWith(".crx", true)
                    ) {
                        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
                            viewModel.repository.extensionManager.installUrl(safeUrl)
                                .onSuccess { Toast.makeText(context, "已安装扩展：" + it.name, Toast.LENGTH_SHORT).show() }
                                .onFailure { Toast.makeText(context, "扩展安装失败：" + (it.message ?: "扩展包无效"), Toast.LENGTH_LONG).show() }
                        }
                    } else if (safeUrl.startsWith("blob:", true) || safeUrl.startsWith("data:", true)) {
                        Toast.makeText(context, "当前网页视频使用浏览器内部数据流，正在尝试使用已捕获的视频地址", Toast.LENGTH_SHORT).show()
                        viewModel.onDownloadRequested(
                            url = safeUrl,
                            userAgent = userAgent,
                            contentDisposition = contentDisposition,
                            mimetype = mimeType,
                            contentLength = contentLength
                        )
                    } else {
                        val fileName = android.webkit.URLUtil.guessFileName(
                            safeUrl,
                            contentDisposition,
                            mimeType
                        )
                        viewModel.downloadManager.enqueueDownload(
                            url = safeUrl,
                            suggestedFileName = fileName,
                            mimeType = mimeType,
                            contentLength = contentLength,
                            referer = url?.let { pageUrl -> this.url ?: pageUrl },
                            userAgent = userAgent
                        )
                        Toast.makeText(context, "已开始下载：$fileName", Toast.LENGTH_SHORT).show()
                    }
                }

                viewModel.registerTabWebView(tab.id, this)

                if (!tab.isAtHome) {
                    loadUrl(tab.url)
                }
            }
        },
        update = { webView ->
            viewModel.registerTabWebView(tab.id, webView)
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
