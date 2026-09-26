package com.example.data

import android.content.Context
import android.content.SharedPreferences
import com.example.model.BookmarkItem
import com.example.model.HistoryItem
import com.example.extension.ExtensionManager
import com.example.model.QuickSite
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

class BrowserRepository(private val context: Context) {
    /** New Chromium-style extension runtime. The legacy script plugin list is no longer used by the browser runtime. */
    val extensionManager: ExtensionManager = ExtensionManager(context)
    private val prefs: SharedPreferences = context.getSharedPreferences("elephant_browser_prefs", Context.MODE_PRIVATE)

    // Flow for Night Mode (Default FALSE = Day Mode)
    private val _isNightMode = MutableStateFlow(prefs.getBoolean(KEY_NIGHT_MODE, false))
    val isNightMode: StateFlow<Boolean> = _isNightMode.asStateFlow()

    // Flow for Desktop Mode
    private val _isDesktopMode = MutableStateFlow(prefs.getBoolean(KEY_DESKTOP_MODE, false))
    val isDesktopMode: StateFlow<Boolean> = _isDesktopMode.asStateFlow()

    // Flow for Desktop UA profile: "windows", "mac", "ipad", "custom"
    private val _desktopUaType = MutableStateFlow(prefs.getString(KEY_DESKTOP_UA_TYPE, "windows") ?: "windows")
    val desktopUaType: StateFlow<String> = _desktopUaType.asStateFlow()

    // Flow for custom user agent string
    private val _customUserAgent = MutableStateFlow(prefs.getString(KEY_CUSTOM_UA, "") ?: "")
    val customUserAgent: StateFlow<String> = _customUserAgent.asStateFlow()

    // Content blocking (ads + common trackers)
    private val _isContentBlocking = MutableStateFlow(prefs.getBoolean(KEY_CONTENT_BLOCKING, true))
    val isContentBlocking: StateFlow<Boolean> = _isContentBlocking.asStateFlow()

    fun setContentBlocking(enabled: Boolean) {
        _isContentBlocking.value = enabled
        prefs.edit().putBoolean(KEY_CONTENT_BLOCKING, enabled).apply()
    }

    // Settings: translation, cloud acceleration, and automatic PiP preferences.
    private val _autoTranslate = MutableStateFlow(prefs.getBoolean(KEY_AUTO_TRANSLATE, false))
    val autoTranslate: StateFlow<Boolean> = _autoTranslate.asStateFlow()

    private val _cloudAcceleration = MutableStateFlow(prefs.getBoolean(KEY_CLOUD_ACCELERATION, false))
    val cloudAcceleration: StateFlow<Boolean> = _cloudAcceleration.asStateFlow()

    private val _autoPip = MutableStateFlow(prefs.getBoolean(KEY_AUTO_PIP, false))
    val autoPip: StateFlow<Boolean> = _autoPip.asStateFlow()

    fun setAutoTranslate(enabled: Boolean) {
        _autoTranslate.value = enabled
        prefs.edit().putBoolean(KEY_AUTO_TRANSLATE, enabled).apply()
    }

    fun setCloudAcceleration(enabled: Boolean) {
        _cloudAcceleration.value = enabled
        prefs.edit().putBoolean(KEY_CLOUD_ACCELERATION, enabled).apply()
    }

    fun setAutoPip(enabled: Boolean) {
        _autoPip.value = enabled
        prefs.edit().putBoolean(KEY_AUTO_PIP, enabled).apply()
    }

    fun resetBrowserPreferences() {
        setNightMode(false)
        setDesktopMode(false)
        setDesktopUaType("windows")
        setCustomUserAgent("")
        setContentBlocking(true)
        setSearchEngine("google")
        setAutoTranslate(false)
        setCloudAcceleration(false)
        setAutoPip(false)
    }

    // Flow for Incognito Mode
    private val _isIncognito = MutableStateFlow(false)
    val isIncognito: StateFlow<Boolean> = _isIncognito.asStateFlow()

    // Flow for Search Engine (default: Baidu / Google)
    private val _searchEngine = MutableStateFlow(prefs.getString(KEY_SEARCH_ENGINE, "google") ?: "google")
    val searchEngine: StateFlow<String> = _searchEngine.asStateFlow()

    // Saved data stats (MB)
    private val _dataSavedMb = MutableStateFlow(prefs.getFloat(KEY_DATA_SAVED, 27.04f))
    val dataSavedMb: StateFlow<Float> = _dataSavedMb.asStateFlow()

    // Bookmarks list
    private val _bookmarks = MutableStateFlow<List<BookmarkItem>>(emptyList())
    val bookmarks: StateFlow<List<BookmarkItem>> = _bookmarks.asStateFlow()

    // History list
    private val _history = MutableStateFlow<List<HistoryItem>>(emptyList())
    val history: StateFlow<List<HistoryItem>> = _history.asStateFlow()

    // Search History list (Figure 2 search history)
    private val _searchHistory = MutableStateFlow<List<String>>(emptyList())
    val searchHistory: StateFlow<List<String>> = _searchHistory.asStateFlow()

    // Quick sites
    private val _quickSites = MutableStateFlow<List<QuickSite>>(emptyList())
    val quickSites: StateFlow<List<QuickSite>> = _quickSites.asStateFlow()

    // Sniffed media stream URLs for seamless floating player playback
    private val detectedStreamUrls = java.util.concurrent.ConcurrentHashMap<String, String>()
    @Volatile
    var lastDetectedStreamUrl: String? = null
        private set

    fun setDetectedStreamUrl(tabId: String, url: String) {
        if (url.isNotBlank()) {
            detectedStreamUrls[tabId] = url
            lastDetectedStreamUrl = url
        }
    }

    fun getDetectedStreamUrl(tabId: String): String? = detectedStreamUrls[tabId] ?: lastDetectedStreamUrl

    /** Returns only the stream discovered for this exact tab, never another tab's last URL. */
    fun getDetectedStreamUrlForTab(tabId: String): String? = detectedStreamUrls[tabId]

    fun clearDetectedStreamUrl(tabId: String) {
        detectedStreamUrls.remove(tabId)
    }

    init {
        loadBookmarks()
        loadHistory()
        loadSearchHistory()
        loadQuickSites()
    }

    fun setNightMode(enabled: Boolean) {
        _isNightMode.value = enabled
        prefs.edit().putBoolean(KEY_NIGHT_MODE, enabled).apply()
    }

    fun setDesktopMode(enabled: Boolean) {
        _isDesktopMode.value = enabled
        prefs.edit().putBoolean(KEY_DESKTOP_MODE, enabled).apply()
    }

    fun setDesktopUaType(type: String) {
        _desktopUaType.value = type
        prefs.edit().putString(KEY_DESKTOP_UA_TYPE, type).apply()
    }

    fun setCustomUserAgent(ua: String) {
        _customUserAgent.value = ua
        prefs.edit().putString(KEY_CUSTOM_UA, ua).apply()
    }

    fun getDesktopUserAgent(): String {
        return when (_desktopUaType.value) {
            "mac" -> DESKTOP_MAC_UA
            "ipad" -> DESKTOP_IPAD_UA
            "android" -> MOBILE_USER_AGENT
            "custom" -> _customUserAgent.value.ifBlank { DESKTOP_WINDOWS_UA }
            else -> DESKTOP_WINDOWS_UA
        }
    }

    fun getUserAgent(isDesktop: Boolean): String {
        return if (isDesktop) getDesktopUserAgent() else MOBILE_USER_AGENT
    }

    fun convertToDesktopUrl(url: String): String {
        if (url.isBlank()) return url
        return url
            .replace("://m.bilibili.com", "://www.bilibili.com")
            .replace("://m.baidu.com", "://www.baidu.com")
            .replace("://m.weibo.cn", "://weibo.com")
            .replace("://m.youtube.com", "://www.youtube.com")
            .replace("://m.zhihu.com", "://www.zhihu.com")
            .replace("://mobile.twitter.com", "://twitter.com")
            .replace("://mobile.x.com", "://x.com")
    }

    fun setIncognito(enabled: Boolean) {
        _isIncognito.value = enabled
    }

    fun setSearchEngine(engine: String) {
        _searchEngine.value = engine
        prefs.edit().putString(KEY_SEARCH_ENGINE, engine).apply()
    }

    fun addSavedData(mb: Float) {
        val newVal = _dataSavedMb.value + mb
        _dataSavedMb.value = newVal
        prefs.edit().putFloat(KEY_DATA_SAVED, newVal).apply()
    }

    // Bookmarks Operations
    private fun loadBookmarks() {
        val raw = prefs.getString(KEY_BOOKMARKS, null)
        if (raw == null) {
            // Default initial bookmarks
            val defaults = listOf(
                BookmarkItem(title = "百度一下", url = "https://www.baidu.com"),
                BookmarkItem(title = "哔哩哔哩", url = "https://m.bilibili.com"),
                BookmarkItem(title = "知乎", url = "https://www.zhihu.com"),
                BookmarkItem(title = "GitHub", url = "https://github.com")
            )
            _bookmarks.value = defaults
            saveBookmarks(defaults)
        } else {
            try {
                val array = JSONArray(raw)
                val list = mutableListOf<BookmarkItem>()
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    list.add(
                        BookmarkItem(
                            id = obj.optString("id"),
                            title = obj.optString("title"),
                            url = obj.optString("url"),
                            createTime = obj.optLong("createTime")
                        )
                    )
                }
                _bookmarks.value = list
            } catch (e: Exception) {
                _bookmarks.value = emptyList()
            }
        }
    }

    fun addBookmark(title: String, url: String) {
        val current = _bookmarks.value.toMutableList()
        current.removeAll { it.url == url }
        current.add(0, BookmarkItem(title = title.ifBlank { url }, url = url))
        _bookmarks.value = current
        saveBookmarks(current)
    }

    fun removeBookmark(id: String) {
        val current = _bookmarks.value.toMutableList()
        current.removeAll { it.id == id }
        _bookmarks.value = current
        saveBookmarks(current)
    }

    fun deleteBookmark(id: String) = removeBookmark(id)

    private fun saveBookmarks(list: List<BookmarkItem>) {
        val array = JSONArray()
        list.forEach { item ->
            val obj = JSONObject()
            obj.put("id", item.id)
            obj.put("title", item.title)
            obj.put("url", item.url)
            obj.put("createTime", item.createTime)
            array.put(obj)
        }
        prefs.edit().putString(KEY_BOOKMARKS, array.toString()).apply()
    }

    // History Operations
    private fun loadHistory() {
        val raw = prefs.getString(KEY_HISTORY, null)
        if (raw != null) {
            try {
                val array = JSONArray(raw)
                val list = mutableListOf<HistoryItem>()
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    list.add(
                        HistoryItem(
                            id = obj.optString("id"),
                            title = obj.optString("title"),
                            url = obj.optString("url"),
                            visitTime = obj.optLong("visitTime")
                        )
                    )
                }
                _history.value = list
            } catch (e: Exception) {
                _history.value = emptyList()
            }
        } else {
            // Initial realistic history items across different dates (today, yesterday, earlier)
            val now = System.currentTimeMillis()
            val oneHour = 3600 * 1000L
            val oneDay = 24 * 3600 * 1000L
            val initial = listOf(
                // Today
                HistoryItem(title = "哔哩哔哩 (゜-゜)つロ 干杯~-bilibili", url = "https://m.bilibili.com", visitTime = now - (25 * 60 * 1000L)),
                HistoryItem(title = "百度一下，你就知道", url = "https://m.baidu.com", visitTime = now - (2 * oneHour)),
                HistoryItem(title = "GitHub: Let's build from here", url = "https://github.com", visitTime = now - (4 * oneHour)),
                // Yesterday
                HistoryItem(title = "知乎 - 有问题，就会有答案", url = "https://www.zhihu.com", visitTime = now - oneDay - (1 * oneHour)),
                HistoryItem(title = "YouTube", url = "https://m.youtube.com", visitTime = now - oneDay - (3 * oneHour)),
                HistoryItem(title = "爱奇艺 - 在线视频媒体平台", url = "https://m.iqiyi.com", visitTime = now - oneDay - (6 * oneHour)),
                // 2 Days Ago
                HistoryItem(title = "腾讯视频 - 中国领先在线视频", url = "https://m.v.qq.com", visitTime = now - (2 * oneDay) - (2 * oneHour)),
                HistoryItem(title = "微博 - 随时随地发现新鲜事", url = "https://m.weibo.cn", visitTime = now - (2 * oneDay) - (5 * oneHour)),
                // 4 Days Ago
                HistoryItem(title = "阮一峰的网络日志", url = "https://www.ruanyifeng.com/blog/", visitTime = now - (4 * oneDay))
            )
            _history.value = initial
            saveHistory(initial)
        }
    }

    fun addHistory(title: String, url: String) {
        if (_isIncognito.value) return // In privacy mode, do NOT write history
        if (url.startsWith("about:") || url.isBlank()) return

        val current = _history.value.toMutableList()
        current.removeAll { it.url == url }
        current.add(0, HistoryItem(title = title.ifBlank { url }, url = url, visitTime = System.currentTimeMillis()))
        if (current.size > 300) {
            current.removeAt(current.lastIndex)
        }
        _history.value = current
        saveHistory(current)
    }

    fun removeHistory(id: String) {
        val current = _history.value.toMutableList()
        current.removeAll { it.id == id }
        _history.value = current
        saveHistory(current)
    }

    fun removeHistoryList(ids: Collection<String>) {
        val current = _history.value.toMutableList()
        current.removeAll { ids.contains(it.id) }
        _history.value = current
        saveHistory(current)
    }

    fun clearAllHistory() {
        _history.value = emptyList()
        prefs.edit().remove(KEY_HISTORY).apply()
    }

    private fun saveHistory(list: List<HistoryItem>) {
        val array = JSONArray()
        list.forEach { item ->
            val obj = JSONObject()
            obj.put("id", item.id)
            obj.put("title", item.title)
            obj.put("url", item.url)
            obj.put("visitTime", item.visitTime)
            array.put(obj)
        }
        prefs.edit().putString(KEY_HISTORY, array.toString()).apply()
    }

    // Search History Operations (matching Figure 2, starting empty)
    private fun loadSearchHistory() {
        val raw = prefs.getString(KEY_SEARCH_HISTORY, null)
        if (raw != null) {
            try {
                val array = JSONArray(raw)
                val list = mutableListOf<String>()
                val sampleKeywords = setOf(
                    "google ai studio",
                    "github",
                    "大象粗线条极简轮廓画像拱桥",
                    "3D游戏动漫风格自然素材",
                    "动漫风格资源包免费下载",
                    "动漫风格资源包",
                    "卡车3D模型"
                )
                for (i in 0 until array.length()) {
                    val q = array.getString(i)
                    if (q.isNotBlank() && !sampleKeywords.contains(q)) {
                        list.add(q)
                    }
                }
                _searchHistory.value = list
                saveSearchHistory(list)
            } catch (e: Exception) {
                _searchHistory.value = emptyList()
            }
        } else {
            // Start empty as requested: do not add image history items
            _searchHistory.value = emptyList()
            saveSearchHistory(emptyList())
        }
    }

    fun addSearchQuery(query: String) {
        val clean = query.trim()
        if (clean.isBlank()) return
        val current = _searchHistory.value.toMutableList()
        current.removeAll { it.equals(clean, ignoreCase = true) }
        current.add(0, clean)
        if (current.size > 50) {
            current.removeAt(current.lastIndex)
        }
        _searchHistory.value = current
        saveSearchHistory(current)
    }

    fun removeSearchQuery(query: String) {
        val current = _searchHistory.value.toMutableList()
        current.removeAll { it.equals(query, ignoreCase = true) }
        _searchHistory.value = current
        saveSearchHistory(current)
    }

    fun clearSearchHistory() {
        _searchHistory.value = emptyList()
        saveSearchHistory(emptyList())
    }

    private fun saveSearchHistory(list: List<String>) {
        val array = JSONArray()
        list.forEach { array.put(it) }
        prefs.edit().putString(KEY_SEARCH_HISTORY, array.toString()).apply()
    }

    /** Import methods used by the portable browser backup format. */
    fun importBookmarks(array: JSONArray?) {
        if (array == null) return
        val list = mutableListOf<BookmarkItem>()
        for (i in 0 until array.length()) {
            val o = array.optJSONObject(i) ?: continue
            val url = o.optString("url").trim()
            if (url.isBlank()) continue
            list.add(BookmarkItem(
                id = o.optString("id").ifBlank { java.util.UUID.randomUUID().toString() },
                title = o.optString("title").ifBlank { url },
                url = url,
                createTime = o.optLong("createTime", System.currentTimeMillis())
            ))
        }
        _bookmarks.value = list.distinctBy { it.url }
        saveBookmarks(_bookmarks.value)
    }

    fun importHistory(array: JSONArray?) {
        if (array == null) return
        val list = mutableListOf<HistoryItem>()
        for (i in 0 until array.length()) {
            val o = array.optJSONObject(i) ?: continue
            val url = o.optString("url").trim()
            if (url.isBlank()) continue
            list.add(HistoryItem(
                id = o.optString("id").ifBlank { java.util.UUID.randomUUID().toString() },
                title = o.optString("title").ifBlank { url },
                url = url,
                visitTime = o.optLong("visitTime", System.currentTimeMillis())
            ))
        }
        _history.value = list.sortedByDescending { it.visitTime }.take(300)
        saveHistory(_history.value)
    }

    fun importSearchHistory(array: JSONArray?) {
        if (array == null) return
        val list = buildList {
            for (i in 0 until array.length()) {
                val value = array.optString(i).trim()
                if (value.isNotBlank() && !contains(value)) add(value)
            }
        }.take(50)
        _searchHistory.value = list
        saveSearchHistory(list)
    }

    fun importQuickSites(array: JSONArray?) {
        if (array == null) return
        val list = mutableListOf<QuickSite>()
        for (i in 0 until array.length()) {
            val o = array.optJSONObject(i) ?: continue
            val url = o.optString("url").trim()
            if (url.isBlank()) continue
            list.add(QuickSite(
                title = o.optString("title").ifBlank { url },
                url = url,
                iconName = o.optString("iconName"),
                bgColor = o.optLong("bgColor", 0xFFF1F5F9),
                isCustom = o.optBoolean("isCustom", false)
            ))
        }
        _quickSites.value = list
        saveQuickSites(list)
    }

    // Extension operations are owned by ExtensionManager.
    private fun loadQuickSites() {
        val sitesVersion = prefs.getInt("quick_sites_version_v8", 0)
        if (sitesVersion < 8) {
            val defaults = getDefaultQuickSites()
            _quickSites.value = defaults
            saveQuickSites(defaults)
            prefs.edit().putInt("quick_sites_version_v8", 8).apply()
            return
        }
        val raw = prefs.getString(KEY_QUICK_SITES, null)
        if (raw == null) {
            val defaults = getDefaultQuickSites()
            _quickSites.value = defaults
            saveQuickSites(defaults)
        } else {
            try {
                val array = JSONArray(raw)
                val list = mutableListOf<QuickSite>()
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    list.add(
                        QuickSite(
                            title = obj.optString("title"),
                            url = obj.optString("url"),
                            iconName = obj.optString("iconName"),
                            bgColor = obj.optLong("bgColor", 0xFFF1F5F9),
                            isCustom = obj.optBoolean("isCustom", false)
                        )
                    )
                }
                if (list.none { it.url.contains("yfsp.tv", ignoreCase = true) }) {
                    val moreIdx = list.indexOfFirst { it.url == "action://more" || it.title == "更多" }
                    val yfspSite = QuickSite("爱壹帆", "https://m.yfsp.tv/list", "yfsp", 0xFFFF6200)
                    if (moreIdx != -1) {
                        list.add(moreIdx, yfspSite)
                    } else {
                        list.add(yfspSite)
                    }
                }
                if (list.none { it.url == "action://more" || it.title == "更多" }) {
                    list.add(QuickSite("更多", "action://more", "more", 0xFF6366F1))
                }
                _quickSites.value = list
                saveQuickSites(list)
            } catch (e: Exception) {
                _quickSites.value = getDefaultQuickSites()
            }
        }
    }

    private fun saveQuickSites(list: List<QuickSite>) {
        val array = JSONArray()
        list.forEach { item ->
            val obj = JSONObject()
            obj.put("title", item.title)
            obj.put("url", item.url)
            obj.put("iconName", item.iconName)
            obj.put("bgColor", item.bgColor)
            obj.put("isCustom", item.isCustom)
            array.put(obj)
        }
        prefs.edit().putString(KEY_QUICK_SITES, array.toString()).apply()
    }

    fun getDefaultQuickSites(): List<QuickSite> = listOf(
        QuickSite("GitHub", "https://github.com", "github", 0xFF181717),
        QuickSite("TikTok", "https://www.tiktok.com", "tiktok", 0xFF010101),
        QuickSite("YouTube", "https://m.youtube.com", "youtube", 0xFFFF0000),
        QuickSite("爱壹帆", "https://m.yfsp.tv/list", "yfsp", 0xFFFF6200),
        QuickSite("Instagram", "https://www.instagram.com", "instagram", 0xFFE1306C),
        QuickSite("更多", "action://more", "more", 0xFF6366F1)
    )

    fun getQuickSites(): List<QuickSite> = _quickSites.value

    fun addQuickSite(title: String, url: String, iconName: String = "", bgColor: Long = 0xFF3B82F6): Boolean {
        val cleanUrl = url.trim()
        if (cleanUrl.isBlank() || cleanUrl == "action://more") return false
        val current = _quickSites.value.toMutableList()
        if (current.any { it.url.equals(cleanUrl, ignoreCase = true) }) {
            return false
        }
        val newSite = QuickSite(
            title = title.trim().ifBlank { cleanUrl },
            url = cleanUrl,
            iconName = iconName.ifBlank { "custom" },
            bgColor = bgColor,
            isCustom = true
        )
        val moreIndex = current.indexOfFirst { it.url == "action://more" || it.title == "更多" }
        if (moreIndex != -1) {
            current.add(moreIndex, newSite)
        } else {
            current.add(newSite)
            current.add(QuickSite("更多", "action://more", "more", 0xFF6366F1))
        }
        _quickSites.value = current
        saveQuickSites(current)
        return true
    }

    fun removeQuickSite(url: String) {
        if (url == "action://more") return
        val current = _quickSites.value.toMutableList()
        current.removeAll { it.url == url }
        if (current.none { it.url == "action://more" || it.title == "更多" }) {
            current.add(QuickSite("更多", "action://more", "more", 0xFF6366F1))
        }
        _quickSites.value = current
        saveQuickSites(current)
    }

    fun isQuickSite(url: String): Boolean {
        val clean = url.trim()
        return _quickSites.value.any { it.url.equals(clean, ignoreCase = true) }
    }

    fun addBookmarkToQuickSites(bookmark: BookmarkItem): Boolean {
        return addQuickSite(bookmark.title, bookmark.url, "bookmark", 0xFF0284C7)
    }

    fun getSearchUrl(query: String): String {
        val q = query.trim()
        if (q.isBlank()) return ""
        if (q.startsWith("http://", ignoreCase = true) || q.startsWith("https://", ignoreCase = true)) {
            return q
        }
        // Match common web addresses, domains or IP addresses (e.g. baidu.com, bilibili.com/video, 192.168.1.1)
        val domainRegex = "^(www\\.)?[-a-zA-Z0-9@:%._+~#=]{1,256}\\.[a-zA-Z0-9()]{2,6}\\b([-a-zA-Z0-9()@:%_+.~#?&/=]*)$".toRegex()
        if (!q.contains(" ") && (domainRegex.matches(q) || q.startsWith("www.") || q.contains(".com") || q.contains(".cn") || q.contains(".org") || q.contains(".net") || q.contains(".tv") || q.contains(".io"))) {
            return "https://$q"
        }
        val encoded = try {
            java.net.URLEncoder.encode(q, "UTF-8")
        } catch (e: Exception) {
            q
        }
        return when (_searchEngine.value) {
            "baidu" -> "https://www.baidu.com/s?wd=$encoded"
            "bing" -> "https://www.bing.com/search?q=$encoded"
            "360" -> "https://www.so.com/s?q=$encoded"
            "sogou" -> "https://www.sogou.com/web?query=$encoded"
            "quark" -> "https://quark.sm.cn/s?q=$encoded"
            "toutiao" -> "https://so.toutiao.com/search?keyword=$encoded"
            "duckduckgo" -> "https://duckduckgo.com/?q=$encoded"
            "zhihu" -> "https://www.zhihu.com/search?type=content&q=$encoded"
            "bilibili" -> "https://search.bilibili.com/all?keyword=$encoded"
            "github" -> "https://github.com/search?q=$encoded"
            "yandex" -> "https://yandex.com/search/?text=$encoded"
            else -> "https://www.google.com/search?q=$encoded&cs=0"
        }
    }

    companion object {
        private const val KEY_NIGHT_MODE = "pref_night_mode"
        private const val KEY_CONTENT_BLOCKING = "pref_content_blocking"
        private const val KEY_DESKTOP_MODE = "pref_desktop_mode"
        private const val KEY_DESKTOP_UA_TYPE = "pref_desktop_ua_type"
        private const val KEY_CUSTOM_UA = "pref_custom_ua"
        private const val KEY_SEARCH_ENGINE = "pref_search_engine"
        private const val KEY_DATA_SAVED = "pref_data_saved"
        private const val KEY_AUTO_TRANSLATE = "pref_auto_translate"
        private const val KEY_CLOUD_ACCELERATION = "pref_cloud_acceleration"
        private const val KEY_AUTO_PIP = "pref_auto_pip"
        private const val KEY_BOOKMARKS = "pref_bookmarks"
        private const val KEY_HISTORY = "pref_history"
        private const val KEY_SEARCH_HISTORY = "pref_search_history"
        private const val KEY_QUICK_SITES = "pref_quick_sites"

        // Desktop User Agents
        const val DESKTOP_WINDOWS_UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36"
        const val DESKTOP_MAC_UA = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36"
        const val DESKTOP_IPAD_UA = "Mozilla/5.0 (iPad; CPU OS 17_5 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.5 Mobile/15E148 Safari/604.1"

        const val DESKTOP_USER_AGENT = DESKTOP_WINDOWS_UA
        const val MOBILE_USER_AGENT = "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36 Elephant/1.0"
    }
}