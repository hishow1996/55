package com.example.extension

import android.webkit.WebView
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

/**
 * Default app adapter for ExtensionTabHost.
 *
 * It owns the browser-tab callbacks and extension-side page bookkeeping so
 * ExtensionManager does not need to know how the browser UI creates/selects
 * tabs. A Chromium-native adapter can later implement the same interface.
 */
class BrowserExtensionTabHost(
    private val createTab: ((String, Boolean) -> Unit)? = null,
    private val controlTab: ((Int, String, Boolean) -> Unit)? = null,
    private val updateTab: ((Int, String?) -> Unit)? = null,
    private val selectTab: ((Int) -> Unit)? = null
) : ExtensionTabHost {
    private val pageHosts = ConcurrentHashMap<String, ExtensionPageHost>()
    private val pageUrls = ConcurrentHashMap<String, String>()
    private val pageActive = ConcurrentHashMap<String, Boolean>()
    private val pageTitles = ConcurrentHashMap<String, String>()
    private val pageTabIds = ConcurrentHashMap<String, Int>()
    private var nextTabId = 1

    fun bind(pageKey: String, extensionTabId: Int, url: String = "") {
        pageTabIds[pageKey] = extensionTabId
        if (url.isNotBlank()) pageUrls[pageKey] = url
        pageActive.putIfAbsent(pageKey, true)
        pageTitles.putIfAbsent(pageKey, "")
    }

    fun attachPage(pageKey: String, host: ExtensionPageHost, url: String) {
        pageHosts[pageKey] = host
        pageUrls[pageKey] = url
        pageActive[pageKey] = true
        pageTitles.putIfAbsent(pageKey, "")
        pageTabIds.putIfAbsent(pageKey, nextTabId++)
    }

    fun updatePage(pageKey: String, url: String, active: Boolean = true) {
        pageUrls[pageKey] = url
        pageActive[pageKey] = active
        if (active) pageActive.keys.forEach { pageActive[it] = it == pageKey }
    }

    fun updateTitle(pageKey: String, title: String) {
        pageTitles[pageKey] = title
    }

    fun detachPage(pageKey: String) {
        pageHosts.remove(pageKey)
        pageUrls.remove(pageKey)
        pageActive.remove(pageKey)
        pageTitles.remove(pageKey)
        pageTabIds.remove(pageKey)
    }

    override fun create(url: String, active: Boolean): TabSnapshot {
        val key = "extension-tab-" + System.nanoTime()
        val id = nextTabId++
        pageTabIds[key] = id
        pageUrls[key] = url
        pageTitles[key] = ""
        pageActive[key] = active
        if (active) pageActive.keys.filter { it != key }.forEach { pageActive[it] = false }
        controlTab?.invoke(id, url, active) ?: createTab?.invoke(url, active)
        return TabSnapshot(id, url, active)
    }

    override fun update(tabId: Int, url: String?, active: Boolean?): TabSnapshot? {
        val key = pageTabIds.entries.firstOrNull { it.value == tabId }?.key ?: return null
        if (url != null) {
            pageUrls[key] = url
            updateTab?.invoke(tabId, url)
            pageHosts[key]?.loadUrl(url)
        }
        if (active == true) {
            updatePage(key, pageUrls[key] ?: "", true)
            selectTab?.invoke(tabId)
        }
        return snapshot(key)
    }

    override fun query(queryJson: String): JSONArray {
        val q = try { JSONObject(queryJson) } catch (_: Exception) { JSONObject() }
        val result = JSONArray()
        pageUrls.forEach { (key, url) ->
            val obj = snapshot(key).toJson()
            val urlMatch = q.optString("url", "").let { it.isBlank() || matchesPattern(it, url) }
            if ((!q.has("active") || q.optBoolean("active") == obj.optBoolean("active")) && urlMatch) result.put(obj)
        }
        return result
    }

    override fun remove(tabId: Int): Boolean {
        val key = pageTabIds.entries.firstOrNull { it.value == tabId }?.key ?: return false
        pageHosts.remove(key)?.destroy()
        pageTabIds.remove(key)
        pageUrls.remove(key)
        pageTitles.remove(key)
        pageActive.remove(key)
        return true
    }

    override fun select(tabId: Int): Boolean {
        val key = pageTabIds.entries.firstOrNull { it.value == tabId }?.key ?: return false
        updatePage(key, pageUrls[key] ?: "", true)
        selectTab?.invoke(tabId)
        return true
    }

    override fun sendMessage(tabId: Int, extensionId: String, message: String): Boolean {
        val target = pageHosts.entries.firstOrNull { pageTabIds[it.key] == tabId }?.value ?: return false
        val payload = JSONObject.quote(message)
        val idJson = JSONObject.quote(extensionId)
        target.post {
            target.evaluateJavascript(
                "if(window.__elephantRuntimeOnMessage)window.__elephantRuntimeOnMessage(JSON.parse($payload),{id:$idJson},function(){});"
            )
        }
        return true
    }

    private fun snapshot(key: String): TabSnapshot =
        TabSnapshot(
            id = pageTabIds[key] ?: 0,
            url = pageUrls[key] ?: "",
            active = pageActive[key] ?: false,
            title = pageTitles[key] ?: ""
        )

    private fun matchesPattern(pattern: String, url: String): Boolean {
        if (pattern == "<all_urls>") return url.startsWith("http://") || url.startsWith("https://")
        val p = pattern.replace(".", "\.").replace("*", ".*")
        return Regex("^$p$").matches(url)
    }
}
