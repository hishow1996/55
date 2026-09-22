package com.example.data

import android.content.Context
import android.content.SharedPreferences
import com.example.model.BookmarkItem
import com.example.model.HistoryItem
import com.example.model.PluginItem
import com.example.model.QuickSite
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

class BrowserRepository(private val context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("elephant_browser_prefs", Context.MODE_PRIVATE)

    // Flow for Night Mode (Default FALSE = Day Mode)
    private val _isNightMode = MutableStateFlow(prefs.getBoolean(KEY_NIGHT_MODE, false))
    val isNightMode: StateFlow<Boolean> = _isNightMode.asStateFlow()

    // Flow for Desktop Mode
    private val _isDesktopMode = MutableStateFlow(prefs.getBoolean(KEY_DESKTOP_MODE, false))
    val isDesktopMode: StateFlow<Boolean> = _isDesktopMode.asStateFlow()

    // Flow for Incognito Mode
    private val _isIncognito = MutableStateFlow(false)
    val isIncognito: StateFlow<Boolean> = _isIncognito.asStateFlow()

    // Flow for Search Engine (default: Baidu / Google)
    private val _searchEngine = MutableStateFlow(prefs.getString(KEY_SEARCH_ENGINE, "google") ?: "google")
    val searchEngine: StateFlow<String> = _searchEngine.asStateFlow()

    // Flow for Adblock
    private val _isAdBlockEnabled = MutableStateFlow(prefs.getBoolean(KEY_ADBLOCK, true))
    val isAdBlockEnabled: StateFlow<Boolean> = _isAdBlockEnabled.asStateFlow()

    // Saved data stats (MB)
    private val _dataSavedMb = MutableStateFlow(prefs.getFloat(KEY_DATA_SAVED, 27.04f))
    val dataSavedMb: StateFlow<Float> = _dataSavedMb.asStateFlow()

    // Bookmarks list
    private val _bookmarks = MutableStateFlow<List<BookmarkItem>>(emptyList())
    val bookmarks: StateFlow<List<BookmarkItem>> = _bookmarks.asStateFlow()

    // History list
    private val _history = MutableStateFlow<List<HistoryItem>>(emptyList())
    val history: StateFlow<List<HistoryItem>> = _history.asStateFlow()

    // Plugins list
    private val _plugins = MutableStateFlow<List<PluginItem>>(emptyList())
    val plugins: StateFlow<List<PluginItem>> = _plugins.asStateFlow()

    // Quick sites
    private val _quickSites = MutableStateFlow<List<QuickSite>>(getQuickSites())
    val quickSites: StateFlow<List<QuickSite>> = _quickSites.asStateFlow()

    init {
        loadBookmarks()
        loadHistory()
        loadPlugins()
    }

    fun setNightMode(enabled: Boolean) {
        _isNightMode.value = enabled
        prefs.edit().putBoolean(KEY_NIGHT_MODE, enabled).apply()
    }

    fun setDesktopMode(enabled: Boolean) {
        _isDesktopMode.value = enabled
        prefs.edit().putBoolean(KEY_DESKTOP_MODE, enabled).apply()
    }

    fun setIncognito(enabled: Boolean) {
        _isIncognito.value = enabled
    }

    fun setSearchEngine(engine: String) {
        _searchEngine.value = engine
        prefs.edit().putString(KEY_SEARCH_ENGINE, engine).apply()
    }

    fun setAdBlockEnabled(enabled: Boolean) {
        _isAdBlockEnabled.value = enabled
        prefs.edit().putBoolean(KEY_ADBLOCK, enabled).apply()
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

    // Plugins Operations
    private fun loadPlugins() {
        val raw = prefs.getString(KEY_PLUGINS, null)
        val defaultList = DefaultPlugins.getBuiltInPlugins()
        if (raw == null) {
            _plugins.value = defaultList
            savePlugins(defaultList)
        } else {
            try {
                val array = JSONArray(raw)
                val list = mutableListOf<PluginItem>()
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    list.add(
                        PluginItem(
                            id = obj.optString("id"),
                            name = obj.optString("name"),
                            description = obj.optString("description"),
                            author = obj.optString("author", "大象开发者"),
                            version = obj.optString("version", "1.0"),
                            isEnabled = obj.optBoolean("isEnabled", true),
                            matchPattern = obj.optString("matchPattern", "*"),
                            runAt = obj.optString("runAt", "document_end"),
                            scriptCode = obj.optString("scriptCode"),
                            isBuiltIn = obj.optBoolean("isBuiltIn", false)
                        )
                    )
                }
                // Ensure built-in plugins exist
                defaultList.forEach { def ->
                    if (list.none { it.id == def.id }) {
                        list.add(def)
                    }
                }
                _plugins.value = list
            } catch (e: Exception) {
                _plugins.value = defaultList
            }
        }
    }

    fun togglePlugin(id: String, enabled: Boolean) {
        val updated = _plugins.value.map {
            if (it.id == id) it.copy(isEnabled = enabled) else it
        }
        _plugins.value = updated
        savePlugins(updated)
    }

    fun addCustomPlugin(plugin: PluginItem) {
        val list = _plugins.value.toMutableList()
        list.removeAll { it.id == plugin.id }
        list.add(0, plugin)
        _plugins.value = list
        savePlugins(list)
    }

    fun deletePlugin(id: String) {
        val list = _plugins.value.filterNot { it.id == id && !it.isBuiltIn }
        _plugins.value = list
        savePlugins(list)
    }

    private fun savePlugins(list: List<PluginItem>) {
        val array = JSONArray()
        list.forEach { item ->
            val obj = JSONObject()
            obj.put("id", item.id)
            obj.put("name", item.name)
            obj.put("description", item.description)
            obj.put("author", item.author)
            obj.put("version", item.version)
            obj.put("isEnabled", item.isEnabled)
            obj.put("matchPattern", item.matchPattern)
            obj.put("runAt", item.runAt)
            obj.put("scriptCode", item.scriptCode)
            obj.put("isBuiltIn", item.isBuiltIn)
            array.put(obj)
        }
        prefs.edit().putString(KEY_PLUGINS, array.toString()).apply()
    }

    fun getQuickSites(): List<QuickSite> = listOf(
        QuickSite("Google", "https://www.google.com", "google", 0xFFFFFFFF),
        QuickSite("百度", "https://www.baidu.com", "baidu", 0xFF2932E1),
        QuickSite("哔哩哔哩", "https://m.bilibili.com", "bilibili", 0xFFFB7299),
        QuickSite("腾讯视频", "https://m.v.qq.com", "video", 0xFFFF6C00),
        QuickSite("爱奇艺", "https://m.iqiyi.com", "iqiyi", 0xFF00C752),
        QuickSite("YouTube", "https://m.youtube.com", "youtube", 0xFFFF0000),
        QuickSite("知乎", "https://www.zhihu.com", "zhihu", 0xFF0084FF),
        QuickSite("GitHub", "https://github.com", "github", 0xFF24292E),
        QuickSite("微博", "https://m.weibo.cn", "weibo", 0xFFE6162D),
        QuickSite("更多", "https://hao.qq.com", "more", 0xFF6366F1)
    )

    fun getSearchUrl(query: String): String {
        val q = query.trim()
        if (q.startsWith("http://") || q.startsWith("https://")) {
            return q
        }
        if (q.contains(".") && !q.contains(" ") && (q.endsWith(".com") || q.endsWith(".cn") || q.endsWith(".org") || q.endsWith(".net") || q.endsWith(".io") || q.endsWith(".tv") || q.endsWith(".cc") || q.endsWith(".top"))) {
            return "https://$q"
        }
        val encoded = java.net.URLEncoder.encode(q, "UTF-8")
        return when (_searchEngine.value) {
            "baidu" -> "https://www.baidu.com/s?wd=$encoded"
            "bing" -> "https://www.bing.com/search?q=$encoded"
            "360" -> "https://www.so.com/s?q=$encoded"
            "sogou" -> "https://www.sogou.com/web?query=$encoded"
            else -> "https://www.google.com/search?q=$encoded"
        }
    }

    companion object {
        private const val KEY_NIGHT_MODE = "pref_night_mode"
        private const val KEY_DESKTOP_MODE = "pref_desktop_mode"
        private const val KEY_SEARCH_ENGINE = "pref_search_engine"
        private const val KEY_ADBLOCK = "pref_adblock"
        private const val KEY_DATA_SAVED = "pref_data_saved"
        private const val KEY_BOOKMARKS = "pref_bookmarks"
        private const val KEY_HISTORY = "pref_history"
        private const val KEY_PLUGINS = "pref_plugins"

        val DESKTOP_USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36"
        val MOBILE_USER_AGENT = "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36 Elephant/1.0"
    }
}
