package com.example.data

import android.content.Context
import com.example.model.BrowserTab
import org.json.JSONArray
import org.json.JSONObject

/**
 * Lightweight browser-session persistence.
 *
 * WebView instances themselves are intentionally not serialized. We persist the
 * navigation/session metadata so the browser can recreate each tab after the
 * process is killed or the app is reopened.
 */
class BrowserSessionStore(context: Context) {
    private val prefs = context.getSharedPreferences("elephant_browser_session", Context.MODE_PRIVATE)

    fun saveTabs(tabs: List<BrowserTab>, currentIndex: Int) {
        val array = JSONArray()
        tabs.filterNot { it.isIncognito }.take(MAX_TABS).forEach { tab ->
            array.put(JSONObject().apply {
                put("id", tab.id)
                put("url", tab.url)
                put("title", tab.title)
                put("isDesktopMode", tab.isDesktopMode)
                put("isNightMode", tab.isNightMode)
            })
        }
        prefs.edit()
            .putString(KEY_TABS, array.toString())
            .putInt(KEY_CURRENT, currentIndex.coerceIn(0, maxOf(0, tabs.lastIndex)))
            .apply()
    }

    fun loadTabs(): Pair<List<BrowserTab>, Int> {
        val raw = prefs.getString(KEY_TABS, null) ?: return emptyList<BrowserTab>() to 0
        return try {
            val array = JSONArray(raw)
            val result = buildList {
                for (i in 0 until array.length()) {
                    val o = array.getJSONObject(i)
                    add(
                        BrowserTab(
                            id = o.optString("id").ifBlank { java.util.UUID.randomUUID().toString() },
                            url = o.optString("url"),
                            title = o.optString("title").ifBlank { "新标签页" },
                            isDesktopMode = o.optBoolean("isDesktopMode", false),
                            isNightMode = o.optBoolean("isNightMode", false)
                        )
                    )
                }
            }.ifEmpty { listOf(BrowserTab()) }
            result to prefs.getInt(KEY_CURRENT, 0).coerceIn(0, result.lastIndex)
        } catch (_: Exception) {
            emptyList<BrowserTab>() to 0
        }
    }

    fun addRecentlyClosed(tab: BrowserTab) {
        if (tab.isIncognito || tab.url.isBlank()) return
        val array = try { JSONArray(prefs.getString(KEY_CLOSED, "[]")) } catch (_: Exception) { JSONArray() }
        val item = JSONObject().apply {
            put("url", tab.url)
            put("title", tab.title)
            put("isDesktopMode", tab.isDesktopMode)
            put("isNightMode", tab.isNightMode)
        }
        val next = JSONArray()
        next.put(item)
        for (i in 0 until minOf(array.length(), MAX_CLOSED - 1)) next.put(array.getJSONObject(i))
        prefs.edit().putString(KEY_CLOSED, next.toString()).apply()
    }

    fun popRecentlyClosed(): BrowserTab? {
        val array = try { JSONArray(prefs.getString(KEY_CLOSED, "[]")) } catch (_: Exception) { JSONArray() }
        if (array.length() == 0) return null
        val o = array.getJSONObject(0)
        val next = JSONArray()
        for (i in 1 until array.length()) next.put(array.getJSONObject(i))
        prefs.edit().putString(KEY_CLOSED, next.toString()).apply()
        return BrowserTab(
            url = o.optString("url"),
            title = o.optString("title").ifBlank { "恢复的标签页" },
            isDesktopMode = o.optBoolean("isDesktopMode", false),
            isNightMode = o.optBoolean("isNightMode", false)
        )
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    companion object {
        private const val KEY_TABS = "tabs_v2"
        private const val KEY_CURRENT = "current_index_v2"
        private const val KEY_CLOSED = "recently_closed_v2"
        private const val MAX_TABS = 24
        private const val MAX_CLOSED = 20
    }
}
