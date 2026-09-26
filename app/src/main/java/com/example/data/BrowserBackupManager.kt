package com.example.data

import android.content.Context
import android.net.Uri
import com.example.model.BrowserTab
import org.json.JSONObject

/**
 * Portable JSON backup for browser-owned data.
 *
 * The backup intentionally contains settings/bookmarks/history/search history,
 * quick sites and open-tab metadata. WebView cookies/cache are left to Android's
 * own storage/backup system and are never copied into a user-visible export.
 */
class BrowserBackupManager(private val context: Context) {
    fun buildBackup(repository: BrowserRepository, tabs: List<BrowserTab>, currentIndex: Int): String {
        val root = JSONObject()
        root.put("format", "elephant-browser-backup")
        root.put("version", 1)
        root.put("createdAt", System.currentTimeMillis())

        root.put("settings", JSONObject().apply {
            put("nightMode", repository.isNightMode.value)
            put("desktopMode", repository.isDesktopMode.value)
            put("desktopUaType", repository.desktopUaType.value)
            put("customUserAgent", repository.customUserAgent.value)
            put("searchEngine", repository.searchEngine.value)
        })

        root.put("bookmarks", JSONArrayCompat.fromBookmarks(repository.bookmarks.value))
        root.put("history", JSONArrayCompat.fromHistory(repository.history.value))
        root.put("searchHistory", org.json.JSONArray(repository.searchHistory.value))
        root.put("quickSites", JSONArrayCompat.fromQuickSites(repository.quickSites.value))

        root.put("tabs", org.json.JSONArray().apply {
            tabs.filterNot { it.isIncognito }.take(24).forEach { tab ->
                put(JSONObject().apply {
                    put("url", tab.url)
                    put("title", tab.title)
                    put("isDesktopMode", tab.isDesktopMode)
                    put("isNightMode", tab.isNightMode)
                })
            }
        })
        root.put("currentTabIndex", currentIndex)
        return root.toString(2)
    }

    fun extractTabs(json: String): Pair<List<BrowserTab>, Int>? {
        return try {
            val root = JSONObject(json)
            if (root.optString("format") != "elephant-browser-backup") return null
            val array = root.optJSONArray("tabs") ?: return null
            val tabs = buildList {
                for (i in 0 until array.length()) {
                    val o = array.optJSONObject(i) ?: continue
                    val url = o.optString("url")
                    add(BrowserTab(
                        url = url,
                        title = o.optString("title").ifBlank { "恢复的标签页" },
                        isDesktopMode = o.optBoolean("isDesktopMode", false),
                        isNightMode = o.optBoolean("isNightMode", false)
                    ))
                }
            }.ifEmpty { listOf(BrowserTab()) }
            tabs to root.optInt("currentTabIndex", 0).coerceIn(0, tabs.lastIndex)
        } catch (_: Exception) {
            null
        }
    }

    fun write(uri: Uri, json: String): Boolean = try {
        context.contentResolver.openOutputStream(uri)?.use {
            it.write(json.toByteArray(Charsets.UTF_8))
        } != null
    } catch (_: Exception) {
        false
    }

    fun read(uri: Uri): String? = try {
        context.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
    } catch (_: Exception) {
        null
    }

    /**
     * Restore only data represented by the public BrowserRepository API.
     * Existing user data is replaced, not merged, to keep the result deterministic.
     */
    fun restore(repository: BrowserRepository, json: String): Boolean {
        return try {
            val root = JSONObject(json)
            if (root.optString("format") != "elephant-browser-backup") return false
            val settings = root.optJSONObject("settings")
            settings?.let {
                repository.setNightMode(it.optBoolean("nightMode", false))
                repository.setDesktopMode(it.optBoolean("desktopMode", false))
                repository.setDesktopUaType(it.optString("desktopUaType", "windows"))
                repository.setCustomUserAgent(it.optString("customUserAgent", ""))
                repository.setSearchEngine(it.optString("searchEngine", "google"))
            }
            repository.importBookmarks(root.optJSONArray("bookmarks"))
            repository.importHistory(root.optJSONArray("history"))
            repository.importSearchHistory(root.optJSONArray("searchHistory"))
            repository.importQuickSites(root.optJSONArray("quickSites"))
            true
        } catch (_: Exception) {
            false
        }
    }

    private object JSONArrayCompat {
        fun fromBookmarks(items: List<com.example.model.BookmarkItem>) =
            org.json.JSONArray().apply {
                items.forEach { put(JSONObject().apply {
                    put("id", it.id); put("title", it.title); put("url", it.url); put("createTime", it.createTime)
                }) }
            }

        fun fromHistory(items: List<com.example.model.HistoryItem>) =
            org.json.JSONArray().apply {
                items.forEach { put(JSONObject().apply {
                    put("id", it.id); put("title", it.title); put("url", it.url); put("visitTime", it.visitTime)
                }) }
            }

        fun fromQuickSites(items: List<com.example.model.QuickSite>) =
            org.json.JSONArray().apply {
                items.forEach { put(JSONObject().apply {
                    put("title", it.title); put("url", it.url); put("iconName", it.iconName)
                    put("bgColor", it.bgColor); put("isCustom", it.isCustom)
                }) }
            }
    }
}
