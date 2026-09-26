package com.example.extension

import android.content.Context
import android.net.Uri
import android.webkit.WebView
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class UserScript(val id: String, val name: String, val matches: List<String>, val code: String, val enabled: Boolean = true)

class UserScriptManager(private val context: Context) {
    private val root = File(context.filesDir, "userscripts")
    private val prefs = context.getSharedPreferences("userscript_manager", Context.MODE_PRIVATE)
    init { root.mkdirs() }

    fun install(uri: Uri): Result<UserScript> = runCatching {
        val text = context.contentResolver.openInputStream(uri)?.use { it.bufferedReader().readText() } ?: error("无法读取用户脚本")
        installText(text)
    }

    suspend fun installUrl(url: String): Result<UserScript> = withContext(Dispatchers.IO) { runCatching {
        require(url.startsWith("http://", true) || url.startsWith("https://", true)) { "用户脚本地址无效" }
        val c = java.net.URL(url).openConnection() as java.net.HttpURLConnection
        c.instanceFollowRedirects = true; c.connectTimeout = 15000; c.readTimeout = 30000
        c.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android) AppleWebKit/537.36 Chrome/140 Mobile Safari/537.36")
        c.connect()
        require(c.responseCode in 200..299) { "下载用户脚本失败：HTTP " + c.responseCode }
        installText(c.inputStream.use { it.bufferedReader().readText() })
    } }

    fun installText(text: String): UserScript {
        val meta = Regex("""//\s*==UserScript==([\s\S]*?)//\s*==/UserScript==""").find(text)?.groupValues?.get(1)
            ?: error("不是有效的用户脚本")
        fun values(key: String) = Regex("""//\s*@""" + key + """\s+(.+)""").findAll(meta).map { it.groupValues[1].trim() }.toList()
        val name = values("name").firstOrNull()?.takeIf { it.isNotBlank() } ?: "未命名脚本"
        val matches = values("match").ifEmpty { values("include") }
        require(matches.isNotEmpty()) { "用户脚本没有 @match/@include" }
        val code = text.substringAfter("// ==/UserScript==").trim()
        val id = java.security.MessageDigest.getInstance("SHA-256").digest((name + "|" + code).toByteArray())
            .joinToString("") { "%02x".format(it) }.take(32)
        File(root, "$id.user.js").writeText(code)
        prefs.edit().putString("meta_$id", org.json.JSONObject().apply {
            put("id", id); put("name", name); put("matches", org.json.JSONArray(matches)); put("enabled", true)
        }.toString()).apply()
        return UserScript(id, name, matches, code, true)
    }

    fun injectForPage(webView: WebView, url: String) {
        all().filter { it.enabled && ExtensionManager.matches(it.matches, url) }.forEach { script ->
            val js = org.json.JSONObject.quote(script.code)
            webView.evaluateJavascript("(function(){try{(0,eval)(" + js + ")}catch(e){}})();", null)
        }
    }

    fun all(): List<UserScript> = prefs.all.entries.mapNotNull { entry ->
        if (!entry.key.startsWith("meta_")) return@mapNotNull null
        try {
            val o = org.json.JSONObject(entry.value as String)
            UserScript(o.getString("id"), o.getString("name"),
                (0 until o.getJSONArray("matches").length()).map { o.getJSONArray("matches").getString(it) },
                File(root, o.getString("id") + ".user.js").takeIf { it.exists() }?.readText() ?: "",
                o.optBoolean("enabled", true))
        } catch (_: Exception) { null }
    }

    fun setEnabled(id: String, enabled: Boolean) {
        val raw = prefs.getString("meta_$id", null) ?: return
        prefs.edit().putString("meta_$id", org.json.JSONObject(raw).put("enabled", enabled).toString()).apply()
    }
    fun uninstall(id: String) {
        File(root, "$id.user.js").delete(); prefs.edit().remove("meta_$id").apply()
    }
}
