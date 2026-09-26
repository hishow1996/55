package com.example.extension

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ExtensionTabHostTest {
    @Test
    fun snapshot_serializes_standard_tab_fields() {
        val snapshot = TabSnapshot(7, "https://example.com", true, "complete", "Example")
        val json = snapshot.toJson()
        assertEquals(7, json.getInt("id"))
        assertEquals("https://example.com", json.getString("url"))
        assertTrue(json.getBoolean("active"))
        assertEquals("complete", json.getString("status"))
        assertEquals("Example", json.getString("title"))
    }

    @Test
    fun host_contract_can_be_implemented_without_webview() {
        val host = object : ExtensionTabHost {
            override fun create(url: String, active: Boolean) = TabSnapshot(1, url, active)
            override fun update(tabId: Int, url: String?, active: Boolean?) =
                TabSnapshot(tabId, url ?: "about:blank", active ?: false)
            override fun query(queryJson: String) = JSONArray().put(JSONObject().put("id", 1))
            override fun remove(tabId: Int) = tabId == 1
            override fun select(tabId: Int) = tabId == 1
            override fun sendMessage(tabId: Int, extensionId: String, message: String) = tabId == 1
        }

        assertEquals("https://example.com", host.create("https://example.com", true).url)
        assertTrue(host.remove(1))
        assertTrue(host.select(1))
        assertTrue(host.sendMessage(1, "ext", "hello"))
        assertEquals(1, host.query("{}").length())
    }
}
