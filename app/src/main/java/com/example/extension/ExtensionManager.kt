package com.example.extension

import android.content.Context
import android.net.Uri
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.flow.MutableStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.zip.ZipInputStream

class ExtensionManager(private val context: Context) {
    private val prefs = context.getSharedPreferences("extension_runtime_v2", Context.MODE_PRIVATE)
    private val _extensions = MutableStateFlow<List<BrowserExtension>>(emptyList())
    val extensions = _extensions.asStateFlow()
    private val backgroundHosts = mutableMapOf<String, WebView>()
    private val pageWebViews = ConcurrentHashMap<String, WebView>()
    private val pageUrls = ConcurrentHashMap<String, String>()

    init { load() }

    private fun load() {
        val result = mutableListOf<BrowserExtension>()
        try {
            val a = JSONArray(prefs.getString("installed", "[]") ?: "[]")
            for (i in 0 until a.length()) {
                val o = a.getJSONObject(i)
                val dir = File(o.optString("rootPath"))
                val mf = File(dir, "manifest.json")
                if (!mf.exists()) continue
                result += BrowserExtension(
                    o.optString("id"), dir.absolutePath, ExtensionManifest.parse(mf.readText()),
                    o.optBoolean("enabled", true), o.optLong("installedTime")
                )
            }
        } catch (_: Exception) {}
        _extensions.value = result
        result.filter { it.enabled }.forEach(::startBackground)
    }

    suspend fun install(uri: Uri): Result<BrowserExtension> = runCatching {
        val input = context.contentResolver.openInputStream(uri) ?: error("无法读取扩展文件")
        installBytes(input.use { it.readBytes() })
    }

    fun installBytes(bytes: ByteArray): BrowserExtension {
        val archive = normalizeArchive(bytes)
        val temp = File(context.cacheDir, "ext_" + System.nanoTime()).apply { mkdirs() }
        try {
            unzip(archive, temp)
            val root = if (File(temp, "manifest.json").exists()) temp
            else temp.listFiles()?.firstOrNull { File(it, "manifest.json").exists() }
                ?: error("扩展包中没有 manifest.json")
            val manifest = ExtensionManifest.parse(File(root, "manifest.json").readText())
            require(manifest.manifestVersion == 2 || manifest.manifestVersion == 3) { "仅支持 Manifest V2/V3" }
            val id = stableId(manifest)
            val target = File(context.filesDir, "extensions/" + id)
            target.deleteRecursively()
            target.parentFile?.mkdirs()
            root.copyRecursively(target, true)
            val ext = BrowserExtension(id, target.absolutePath, manifest, true)
            val updated = _extensions.value.filterNot { it.id == id } + ext
            _extensions.value = updated
            persist(updated)
            startBackground(ext)
            return ext
        } finally {
            temp.deleteRecursively()
        }
    }

    fun uninstall(id: String) {
        val ext = extension(id) ?: return
        backgroundHosts.remove(id)?.destroy()
        prefs.edit().remove("storage_" + id).apply()
        File(ext.rootPath).deleteRecursively()
        val updated = _extensions.value.filterNot { it.id == id }
        _extensions.value = updated
        persist(updated)
    }

    fun setEnabled(id: String, enabled: Boolean) {
        val updated = _extensions.value.map { if (it.id == id) it.copy(enabled = enabled) else it }
        _extensions.value = updated
        if (enabled) updated.firstOrNull { it.id == id }?.let(::startBackground)
        else backgroundHosts.remove(id)?.destroy()
        persist(updated)
    }

    fun extension(id: String): BrowserExtension? = _extensions.value.firstOrNull { it.id == id }

    fun popupUrl(id: String): String? = extension(id)?.manifest?.popup?.let {
        File(extension(id)!!.rootPath, it).takeIf(File::exists)?.let { f -> "file://" + f.absolutePath }
    }

    fun optionsUrl(id: String): String? = extension(id)?.manifest?.optionsPage?.let {
        File(extension(id)!!.rootPath, it).takeIf(File::exists)?.let { f -> "file://" + f.absolutePath }
    }

    fun attachWebView(pageKey: String, webView: WebView, url: String) {
        pageWebViews[pageKey] = webView
        pageUrls[pageKey] = url
        webView.addJavascriptInterface(PageBridge(pageKey), "ElephantExtensionBridge")
    }

    fun detachWebView(pageKey: String) {
        pageWebViews.remove(pageKey)
        pageUrls.remove(pageKey)
    }

    fun injectForPage(webView: WebView, url: String, runAt: String) {
        val pageKey = webView.hashCode().toString()
        if (pageWebViews[pageKey] !== webView) attachWebView(pageKey, webView, url)
        else pageUrls[pageKey] = url
        _extensions.value.filter { it.enabled }.forEach { ext ->
            ext.manifest.contentScripts
                .filter { it.runAt == runAt && matches(it.matches + ext.manifest.hostPermissions, url) }
                .forEach { spec ->
                    spec.jsFiles.forEach { name ->
                        val file = safeChild(ext.rootPath, name) ?: return@forEach
                        if (file.exists() && file.isFile) {
                            webView.evaluateJavascript(contentBootstrap(ext, file.readText()), null)
                        }
                    }
                }
        }
    }

    private fun contentBootstrap(ext: BrowserExtension, script: String): String {
        val key = "__elephant_ext_" + ext.id.replace("-", "_")
        val code = JSONObject.quote(script)
        val id = JSONObject.quote(ext.id)
        val root = JSONObject.quote("file://" + ext.rootPath + "/")
        return "(function(){if(window['$key'])return;window['$key']=1;window.chrome=window.chrome||{};" +
            "chrome.runtime=chrome.runtime||{id:$id,getURL:function(p){return $root+p;},sendMessage:function(m,c){try{var r=window.ElephantExtensionBridge&&window.ElephantExtensionBridge.sendMessage($id,JSON.stringify(m));if(c)c(r?JSON.parse(r):null)}catch(e){}}};" +
            "chrome.storage=chrome.storage||{};chrome.storage.local=chrome.storage.local||{get:function(k,c){try{var r=window.ElephantExtensionBridge.storageGet($id,typeof k==='string'?k:null);if(c)c(r?JSON.parse(r):{})}catch(e){if(c)c({})}},set:function(v,c){try{window.ElephantExtensionBridge.storageSet($id,JSON.stringify(v));if(c)c()}catch(e){if(c)c()}}};" +
            "(0,eval)($code);})()"
    }

    private fun startBackground(ext: BrowserExtension) {
        if (!ext.enabled || backgroundHosts.containsKey(ext.id)) return
        val worker = ext.manifest.serviceWorker ?: ext.manifest.backgroundScripts.firstOrNull() ?: return
        val file = safeChild(ext.rootPath, worker) ?: return
        if (!file.exists() || !file.isFile) return
        val wv = WebView(context)
        wv.settings.javaScriptEnabled = true
        wv.settings.domStorageEnabled = true
        wv.webViewClient = WebViewClient()
        wv.addJavascriptInterface(BackgroundBridge(ext.id), "ElephantExtensionBridge")
        val id = JSONObject.quote(ext.id)
        val root = JSONObject.quote("file://" + ext.rootPath + "/")
        val polyfill = "(function(){window.chrome={runtime:{id:$id,getURL:function(p){return $root+p;},sendMessage:function(m,c){var r=ElephantExtensionBridge.sendMessage($id,JSON.stringify(m));if(c)c(r?JSON.parse(r):null)},onMessage:{addListener:function(fn){window.__elephantOnMessage=fn}}},storage:{local:{get:function(k,c){var r=ElephantExtensionBridge.storageGet($id,typeof k==='string'?k:null);if(c)c(r?JSON.parse(r):{})},set:function(v,c){ElephantExtensionBridge.storageSet($id,JSON.stringify(v));if(c)c()}}},tabs:{query:function(q,c){var r=ElephantExtensionBridge.tabsQuery(JSON.stringify(q||{}));if(c)c(r?JSON.parse(r):[])}},scripting:{executeScript:function(o,c){var r=ElephantExtensionBridge.executeScript($id,JSON.stringify(o||{}));if(c)c(r?JSON.parse(r):[])}}};})();"
        wv.loadDataWithBaseURL("file://" + ext.rootPath + "/", "<html><script>" + polyfill + file.readText() + "</script></html>", "text/html", "UTF-8", null)
        backgroundHosts[ext.id] = wv
    }

    private inner class PageBridge(private val pageKey: String) {
        @JavascriptInterface fun storageGet(extensionId: String, key: String?): String = storageGetJson(extensionId, key)
        @JavascriptInterface fun storageSet(extensionId: String, valuesJson: String) { storageSetJson(extensionId, valuesJson) }
        @JavascriptInterface fun sendMessage(extensionId: String, message: String): String {
            deliverToBackground(extensionId, message)
            return JSONObject.NULL.toString()
        }
    }

    private inner class BackgroundBridge(private val id: String) {
        @JavascriptInterface fun storageGet(extensionId: String, key: String?): String = storageGetJson(extensionId, key)
        @JavascriptInterface fun storageSet(extensionId: String, valuesJson: String) { storageSetJson(extensionId, valuesJson) }
        @JavascriptInterface fun sendMessage(extensionId: String, message: String): String {
            deliverToPages(extensionId, message)
            return JSONObject.NULL.toString()
        }
        @JavascriptInterface fun tabsQuery(queryJson: String): String {
            val q = try { JSONObject(queryJson) } catch (_: Exception) { JSONObject() }
            val result = JSONArray()
            pageUrls.forEach { (key, url) ->
                val obj = JSONObject().apply {
                    put("id", key.hashCode())
                    put("url", url)
                    put("active", true)
                    put("status", "complete")
                }
                if (!q.has("active") || q.optBoolean("active") == obj.optBoolean("active")) result.put(obj)
            }
            return result.toString()
        }
        @JavascriptInterface fun executeScript(extensionId: String, optionsJson: String): String {
            val o = try { JSONObject(optionsJson) } catch (_: Exception) { JSONObject() }
            val js = o.optString("code").ifBlank { o.optString("func").takeIf { it.isNotBlank() }?.let { "($it)()" } ?: "" }
            if (js.isBlank()) return "[]"
            pageWebViews.values.distinct().forEach { it.post { it.evaluateJavascript(js, null) } }
            return "[{}]"
        }
    }

    private fun deliverToBackground(extensionId: String, message: String) {
        val wv = backgroundHosts[extensionId] ?: return
        val payload = JSONObject.quote(message)
        val id = JSONObject.quote(extensionId)
        wv.post {
            wv.evaluateJavascript(
                "window.__elephantOnMessage&&window.__elephantOnMessage(JSON.parse($payload),{id:$id},function(){});",
                null
            )
        }
    }

    private fun deliverToPages(extensionId: String, message: String) {
        val payload = JSONObject.quote(message)
        pageWebViews.values.distinct().forEach { wv ->
            wv.post {
                wv.evaluateJavascript(
                    "window.dispatchEvent(new CustomEvent('elephant-extension-message',{detail:JSON.parse($payload)}));",
                    null
                )
            }
        }
    }

    private fun storageGetJson(extensionId: String, key: String?): String {
        val all = try { JSONObject(prefs.getString("storage_" + extensionId, "{}") ?: "{}") } catch (_: Exception) { JSONObject() }
        if (key.isNullOrBlank()) return all.toString()
        val out = JSONObject()
        if (all.has(key)) out.put(key, all.opt(key))
        return out.toString()
    }

    private fun storageSetJson(extensionId: String, valuesJson: String) {
        val all = try { JSONObject(prefs.getString("storage_" + extensionId, "{}") ?: "{}") } catch (_: Exception) { JSONObject() }
        val values = try { JSONObject(valuesJson) } catch (_: Exception) { JSONObject() }
        val keys = values.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            all.put(key, values.opt(key))
        }
        prefs.edit().putString("storage_" + extensionId, all.toString()).apply()
    }

    private fun persist(list: List<BrowserExtension>) {
        val a = JSONArray()
        list.forEach { e -> a.put(JSONObject().apply {
            put("id", e.id); put("rootPath", e.rootPath); put("enabled", e.enabled); put("installedTime", e.installedTime)
        }) }
        prefs.edit().putString("installed", a.toString()).apply()
    }

    private fun stableId(manifest: ExtensionManifest): String =
        MessageDigest.getInstance("SHA-256").digest((manifest.name + "|" + manifest.version).toByteArray())
            .joinToString("") { "%02x".format(it) }.take(32)

    private fun safeChild(rootPath: String, relative: String): File? {
        val root = File(rootPath).canonicalFile
        val file = File(root, relative).canonicalFile
        return if (file.path == root.path || file.path.startsWith(root.path + File.separator)) file else null
    }

    private fun normalizeArchive(bytes: ByteArray): ByteArray {
        if (bytes.size < 4 || bytes[0].toInt() != 0x43 || bytes[1].toInt() != 0x72 || bytes[2].toInt() != 0x32 || bytes[3].toInt() != 0x34) return bytes
        if (bytes.size < 16) error("CRX 文件损坏")
        return when (readIntLE(bytes, 8)) {
            2 -> {
                val publicKeyLength = readIntLE(bytes, 8)
                val signatureLength = readIntLE(bytes, 12)
                val start = 16 + publicKeyLength + signatureLength
                require(start <= bytes.size) { "CRX 文件损坏" }
                bytes.copyOfRange(start, bytes.size)
            }
            3 -> {
                val headerSize = readIntLE(bytes, 8)
                val start = 12 + headerSize
                require(start <= bytes.size) { "CRX 文件损坏" }
                bytes.copyOfRange(start, bytes.size)
            }
            else -> error("不支持的 CRX 版本")
        }
    }

    private fun readIntLE(b: ByteArray, o: Int) =
        (b[o].toInt() and 255) or ((b[o+1].toInt() and 255) shl 8) or
            ((b[o+2].toInt() and 255) shl 16) or ((b[o+3].toInt() and 255) shl 24)

    private fun unzip(bytes: ByteArray, dest: File) {
        ZipInputStream(ByteArrayInputStream(bytes)).use { zis ->
            while (true) {
                val entry = zis.nextEntry ?: break
                val out = File(dest, entry.name)
                require(out.canonicalPath.startsWith(dest.canonicalPath + File.separator)) { "非法扩展包路径" }
                if (entry.isDirectory) out.mkdirs() else {
                    out.parentFile?.mkdirs()
                    out.outputStream().use { zis.copyTo(it) }
                }
            }
        }
    }

    companion object {
        fun matches(patterns: List<String>, url: String): Boolean {
            if (patterns.isEmpty()) return false
            if (patterns.any { it == "<all_urls>" || it == "*" }) return true
            return patterns.any { p ->
                try { Regex("^" + p.split("*").joinToString(".*") { Regex.escape(it) } + "$").matches(url) }
                catch (_: Exception) { false }
            }
        }
    }
}
