package com.example.extension

import org.json.JSONArray
import org.json.JSONObject

/**
 * Browser tab/window boundary exposed to extension APIs.
 *
 * This is deliberately independent of Android WebView. A Chromium-native
 * implementation can map these operations to Chromium's tab/window model.
 */
interface ExtensionTabHost {
    fun create(url: String, active: Boolean): TabSnapshot
    fun update(tabId: Int, url: String?, active: Boolean?): TabSnapshot?
    fun query(queryJson: String): JSONArray
    fun remove(tabId: Int): Boolean
    fun select(tabId: Int): Boolean
    fun sendMessage(tabId: Int, extensionId: String, message: String): Boolean
}

data class TabSnapshot(
    val id: Int,
    val url: String,
    val active: Boolean,
    val status: String = "loading",
    val title: String = ""
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("url", url)
        put("active", active)
        put("status", status)
        put("title", title)
    }
}
