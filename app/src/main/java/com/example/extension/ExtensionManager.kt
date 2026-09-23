package com.example.extension

import android.content.Context
import android.net.Uri
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    private val pageActive = ConcurrentHashMap<String, Boolean>()
    private val pageTitles = ConcurrentHashMap<String, String>()
    private val pageTabIds = ConcurrentHashMap<String, Int>()
    private var nextTabId = 1
    private var browserTabCreator: ((String, Boolean) -> Unit)? = null
    private var browserTabController: ((Int, String, Boolean) -> Unit)? = null
    private var browserTabUpdater: ((Int, String?) -> Unit)? = null
    private var browserTabSelector: ((Int) -> Unit)? = null

    fun setBrowserTabCreator(creator: ((String, Boolean) -> Unit)?) { browserTabCreator = creator }

    fun setBrowserTabController(creator: ((Int, String, Boolean) -> Unit)?, updater: ((Int, String?) -> Unit)?, selector: ((Int) -> Unit)?) {
        browserTabController = creator
        browserTabUpdater = updater
        browserTabSelector = selector
    }

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

    fun isPinned(id: String): Boolean =
        prefs.getStringSet("pinned_extensions", emptySet())?.contains(id) == true

    fun setPinned(id: String, pinned: Boolean) {
        val set = prefs.getStringSet("pinned_extensions", emptySet())?.toMutableSet() ?: mutableSetOf()
        if (pinned) set.add(id) else set.remove(id)
        prefs.edit().putStringSet("pinned_extensions", set).apply()
    }

    fun setEnabled(id: String, enabled: Boolean) {
        val updated = _extensions.value.map { if (it.id == id) it.copy(enabled = enabled) else it }
        _extensions.value = updated
        if (enabled) updated.firstOrNull { it.id == id }?.let(::startBackground)
        else backgroundHosts.remove(id)?.destroy()
        persist(updated)
    }

    fun extension(id: String): BrowserExtension? = _extensions.value.firstOrNull { it.id == id }

    fun hasPermission(id: String, permission: String): Boolean {
        val e = extension(id) ?: return false
        return permission in e.manifest.permissions || permission in e.manifest.hostPermissions || (permission == "activeTab" && e.enabled)
    }

    fun getManifestJson(id: String): String {
        val e = extension(id) ?: return "{}"
        return JSONObject().apply {
            put("manifest_version", e.manifest.manifestVersion); put("name", e.name); put("version", e.version)
            put("description", e.manifest.description); put("permissions", JSONArray(e.manifest.permissions))
            put("host_permissions", JSONArray(e.manifest.hostPermissions))
        }.toString()
    }

    fun iconFile(id: String): File? {
        val ext = extension(id) ?: return null
        val path = ext.manifest.iconPath ?: return null
        return safeChild(ext.rootPath, path)?.takeIf { it.exists() && it.isFile }
    }

    fun prepareExtensionPage(webView: WebView, extensionId: String, pageKey: String = "extension-page"): Boolean {
        val ext = extension(extensionId) ?: return false
        if (!ext.enabled) return false
        attachWebView("$pageKey:$extensionId", webView, "extension://$extensionId")
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        return true
    }

    fun popupUrl(id: String): String? = extension(id)?.manifest?.popup?.let {
        File(extension(id)!!.rootPath, it).takeIf(File::exists)?.let { f -> "file://" + f.absolutePath }
    }

    fun optionsUrl(id: String): String? = extension(id)?.manifest?.optionsPage?.let {
        File(extension(id)!!.rootPath, it).takeIf(File::exists)?.let { f -> "file://" + f.absolutePath }
    }

    fun updatePageState(pageKey: String, url: String, active: Boolean = true) {
        pageUrls[pageKey] = url
        pageActive[pageKey] = active
        if (active) setPageActive(pageKey)
    }

    fun updatePageTitle(pageKey: String, title: String) { pageTitles[pageKey] = title }

    fun setPageActive(pageKey: String) {
        pageActive.keys.forEach { pageActive[it] = it == pageKey }
    }

    fun bindBrowserTab(extensionTabId: Int, pageKey: String) {
        pageTabIds.entries.filter { it.value == extensionTabId && it.key != pageKey && it.key.startsWith("extension-tab-") }
            .forEach { entry ->
                pageTabIds.remove(entry.key)
                pageUrls.remove(entry.key)
                pageTitles.remove(entry.key)
                pageActive.remove(entry.key)
            }
        // Bind before Compose creates the WebView. attachWebView keeps this ID.
        pageTabIds[pageKey] = extensionTabId
    }

    fun attachWebView(pageKey: String, webView: WebView, url: String) {
        pageWebViews[pageKey] = webView
        pageUrls[pageKey] = url
        pageActive[pageKey] = true
        pageTitles.putIfAbsent(pageKey, "")
        pageTabIds.putIfAbsent(pageKey, nextTabId++)
        webView.addJavascriptInterface(PageBridge(pageKey), "ElephantExtensionBridge")
    }

    fun detachWebView(pageKey: String) {
        pageWebViews.remove(pageKey)
        pageUrls.remove(pageKey)
        pageActive.remove(pageKey)
        pageTitles.remove(pageKey)
        pageTabIds.remove(pageKey)
    }

    fun injectForPage(webView: WebView, url: String, runAt: String) {
        val pageKey = webView.hashCode().toString()
        if (pageWebViews[pageKey] !== webView) attachWebView(pageKey, webView, url)
        else updatePageState(pageKey, url)
        _extensions.value.filter { it.enabled }.forEach { ext ->
            ext.manifest.contentScripts
                .filter { it.runAt == runAt && matches(it.matches, url) && (ext.manifest.hostPermissions.isEmpty() || matches(ext.manifest.hostPermissions, url)) }
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
            "chrome.runtime=chrome.runtime||{id:$id,getURL:function(p){return $root+p;},sendMessage:function(m,c){try{var r=window.ElephantExtensionBridge&&window.ElephantExtensionBridge.sendMessage($id,JSON.stringify(m));if(c)c(r?JSON.parse(r):null)}catch(e){}},onMessage:{addListener:function(fn){window.__elephantRuntimeOnMessage=fn}}};" +
            "chrome.storage=chrome.storage||{};chrome.storage.local=chrome.storage.local||{get:function(k,c){try{var r=window.ElephantExtensionBridge.storageGet($id,typeof k==='string'?k:null);if(c)c(r?JSON.parse(r):{})}catch(e){if(c)c({})}},set:function(v,c){try{window.ElephantExtensionBridge.storageSet($id,JSON.stringify(v));if(c)c()}catch(e){if(c)c()}},remove:function(k,c){try{window.ElephantExtensionBridge.storageRemove($id,k);if(c)c()}catch(e){if(c)c()}},clear:function(c){try{window.ElephantExtensionBridge.storageClear($id);if(c)c()}catch(e){if(c)c()}}};" +
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
        val polyfill = "(function(){window.chrome={runtime:{id:$id,getURL:function(p){return $root+p;},getManifest:function(){return JSON.parse(ElephantExtensionBridge.getManifest($id));},sendMessage:function(m,c){var r=ElephantExtensionBridge.sendMessage($id,JSON.stringify(m));if(c)c(r?JSON.parse(r):null)},onMessage:{addListener:function(fn){window.__elephantOnMessage=fn}}},storage:{local:{get:function(k,c){var r=ElephantExtensionBridge.storageGet($id,typeof k==='string'?k:null);if(c)c(r?JSON.parse(r):{})},set:function(v,c){ElephantExtensionBridge.storageSet($id,JSON.stringify(v));if(c)c()},remove:function(k,c){ElephantExtensionBridge.storageRemove($id,k);if(c)c()},clear:function(c){ElephantExtensionBridge.storageClear($id);if(c)c()}}},tabs:{query:function(q,c){var r=ElephantExtensionBridge.tabsQuery(JSON.stringify(q||{}));if(c)c(r?JSON.parse(r):[])},sendMessage:function(tabId,m,c){var r=ElephantExtensionBridge.tabsSendMessage($id,tabId,JSON.stringify(m));if(c)c(r?JSON.parse(r):null)},update:function(tabId,p,c){var r=ElephantExtensionBridge.tabsUpdate($id,tabId,JSON.stringify(p||{}));if(c)c(r?JSON.parse(r):null)},create:function(p,c){var r=ElephantExtensionBridge.tabsCreate($id,JSON.stringify(p||{}));if(c)c(r?JSON.parse(r):null)}},scripting:{executeScript:function(o,c){var r=ElephantExtensionBridge.executeScript($id,JSON.stringify(o||{}));if(c)c(r?JSON.parse(r):[])}}};})();"
        wv.loadDataWithBaseURL("file://" + ext.rootPath + "/", "<html><script>" + polyfill + file.readText() + "</script></html>", "text/html", "UTF-8", null)
        backgroundHosts[ext.id] = wv
    }

    private inner class PageBridge(private val pageKey: String) {
        @JavascriptInterface fun getManifest(extensionId: String): String = getManifestJson(extensionId)
        @JavascriptInterface fun storageGet(extensionId: String, key: String?): String = storageGetJson(extensionId, key)
        @JavascriptInterface fun storageSet(extensionId: String, valuesJson: String) { storageSetJson(extensionId, valuesJson) }
        @JavascriptInterface fun storageRemove(extensionId: String, key: String) { storageRemoveJson(extensionId, key) }
        @JavascriptInterface fun storageClear(extensionId: String) { storageClearJson(extensionId) }
        @JavascriptInterface fun sendMessage(extensionId: String, message: String): String {
            deliverToBackground(extensionId, message)
            return JSONObject.NULL.toString()
        }
    }

    private inner class BackgroundBridge(private val id: String) {
        @JavascriptInterface fun getManifest(extensionId: String): String = getManifestJson(extensionId)
        @JavascriptInterface fun storageGet(extensionId: String, key: String?): String = storageGetJson(extensionId, key)
        @JavascriptInterface fun storageSet(extensionId: String, valuesJson: String) { storageSetJson(extensionId, valuesJson) }
        @JavascriptInterface fun storageRemove(extensionId: String, key: String) { storageRemoveJson(extensionId, key) }
        @JavascriptInterface fun storageClear(extensionId: String) { storageClearJson(extensionId) }
        @JavascriptInterface fun sendMessage(extensionId: String, message: String): String {
            deliverToPages(extensionId, message)
            return JSONObject.NULL.toString()
        }
        @JavascriptInterface fun tabsSendMessage(extensionId: String, tabId: Int, message: String): String {
            val target = pageWebViews.entries.firstOrNull { pageTabIds[it.key] == tabId }?.value
            if (target == null) return JSONObject.NULL.toString()
            val payload = JSONObject.quote(message)
            val idJson = JSONObject.quote(extensionId)
            target.post { target.evaluateJavascript("if(window.__elephantRuntimeOnMessage)window.__elephantRuntimeOnMessage(JSON.parse($payload),{id:$idJson},function(){});", null) }
            return JSONObject.NULL.toString()
        }
        @JavascriptInterface fun tabsCreate(extensionId: String, propertiesJson: String): String {
            val p = try { JSONObject(propertiesJson) } catch (_: Exception) { JSONObject() }
            val url = p.optString("url", "about:blank")
            val active = p.optBoolean("active", true)
            val newKey = "extension-tab-" + System.nanoTime()
            val id = nextTabId++
            pageTabIds[newKey] = id
            pageUrls[newKey] = url
            pageTitles[newKey] = ""
            pageActive[newKey] = active
            if (active) pageActive.keys.filter { it != newKey }.forEach { pageActive[it] = false }
            browserTabController?.invoke(id, url, active) ?: browserTabCreator?.invoke(url, active)
            return JSONObject().apply { put("id", id); put("url", url); put("active", active); put("status", "loading"); put("title", "") }.toString()
        }
        @JavascriptInterface fun tabsUpdate(extensionId: String, tabId: Int, propertiesJson: String): String {
            val key = pageTabIds.entries.firstOrNull { it.value == tabId }?.key ?: return JSONObject.NULL.toString()
            val p = try { JSONObject(propertiesJson) } catch (_: Exception) { JSONObject() }
            val targetUrl = p.optString("url").takeIf { it.isNotBlank() }
            if (targetUrl != null) {
                pageUrls[key] = targetUrl
                browserTabUpdater?.invoke(tabId, targetUrl)
                pageWebViews[key]?.post { it.loadUrl(targetUrl) }
            }
            if (p.has("active") && p.optBoolean("active")) {
                setPageActive(key)
                browserTabSelector?.invoke(tabId)
            }
            val obj = JSONObject().apply {
                put("id", tabId)
                put("url", pageUrls[key] ?: "")
                put("active", pageActive[key] ?: false)
                put("status", "loading")
                put("title", pageTitles[key] ?: "")
            }
            return obj.toString()
        }

        @JavascriptInterface fun tabsQuery(queryJson: String): String {
            val q = try { JSONObject(queryJson) } catch (_: Exception) { JSONObject() }
            val result = JSONArray()
            pageUrls.forEach { (key, url) ->
                val obj = JSONObject().apply {
                    put("id", pageTabIds[key] ?: 0)
                    put("url", url)
                    put("active", pageActive[key] ?: true)
                    put("status", "complete")
                    put("title", pageTitles[key] ?: "")
                }
                val urlMatch = q.optString("url", "").let { it.isBlank() || matches(listOf(it), url) }
                if ((!q.has("active") || q.optBoolean("active") == obj.optBoolean("active")) && urlMatch) result.put(obj)
            }
            return result.toString()
        }
        @JavascriptInterface fun executeScript(extensionId: String, optionsJson: String): String {
            if (!hasPermission(extensionId, "scripting") && !hasPermission(extensionId, "activeTab")) return "[]"
            val o = try { JSONObject(optionsJson) } catch (_: Exception) { JSONObject() }
            val js = o.optString("code").ifBlank {
                o.optString("func").takeIf { it.isNotBlank() }?.let { "($it)()" } ?: ""
            }
            if (js.isBlank()) return "[]"
            val target = o.optJSONObject("target")
            val tabId = target?.optInt("tabId", -1) ?: -1
            val targets = if (tabId > 0) {
                pageWebViews.entries.filter { pageTabIds[it.key] == tabId }
            } else {
                pageWebViews.entries.toList()
            }
            var count = 0
            targets.forEach { entry ->
                val pageUrl = pageUrls[entry.key] ?: return@forEach
                val hosts = extension(extensionId)?.manifest?.hostPermissions.orEmpty()
                if (hosts.isNotEmpty() && !matches(hosts, pageUrl) && !hasPermission(extensionId, "activeTab")) return@forEach
                count++
                entry.value.post { entry.value.evaluateJavascript(js, null) }
            }
            return if (count == 0) "[]" else "[{}]"
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
        val id = JSONObject.quote(extensionId)
        pageWebViews.values.distinct().forEach { wv ->
            wv.post {
                wv.evaluateJavascript(
                    "window.dispatchEvent(new CustomEvent('elephant-extension-message',{detail:JSON.parse($payload)}));if(window.__elephantRuntimeOnMessage)window.__elephantRuntimeOnMessage(JSON.parse($payload),{id:$id},function(){});",
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

    private fun storageRemoveJson(extensionId: String, key: String) {
        val all = try { JSONObject(prefs.getString("storage_" + extensionId, "{}") ?: "{}") } catch (_: Exception) { JSONObject() }
        all.remove(key)
        prefs.edit().putString("storage_" + extensionId, all.toString()).apply()
    }

    private fun storageClearJson(extensionId: String) {
        prefs.edit().putString("storage_" + extensionId, "{}").apply()
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

    private fun stableId(manifest: ExtensionManifest): String {
        val publicKey = manifest.key?.let { try { android.util.Base64.decode(it, android.util.Base64.DEFAULT) } catch (_: Exception) { null } }
        val digest = MessageDigest.getInstance("SHA-256").digest(publicKey ?: (manifest.name + "|" + manifest.version).toByteArray(Charsets.UTF_8))
        if (publicKey != null) {
            return digest.take(16).joinToString("") { byte ->
                val value = byte.toInt() and 0xFF
                (('a'.code + ((value ushr 4) and 15)).toChar().toString() + ('a'.code + (value and 15)).toChar())
            }
        }
        return digest.joinToString("") { "%02x".format(it) }.take(32)
    }

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
            val uri = try { Uri.parse(url) } catch (_: Exception) { return false }
            val scheme = uri.scheme?.lowercase() ?: return false
            val host = uri.host?.lowercase() ?: ""
            val path = uri.path ?: "/"
            return patterns.any { raw ->
                val p = raw.trim()
                if (p == "<all_urls>") return@any scheme == "http" || scheme == "https" || scheme == "file"
                if (p == "*") return@any scheme == "http" || scheme == "https"
                val parts = p.split("://", limit = 2)
                if (parts.size != 2) return@any false
                val ps = parts[0].lowercase()
                val rest = parts[1]
                val slash = rest.indexOf('/')
                val ph = if (slash >= 0) rest.substring(0, slash).lowercase() else rest.lowercase()
                val pp = if (slash >= 0) rest.substring(slash) else "/*"
                if (ps != "*" && ps != scheme) return@any false
                val hostOk = if (scheme == "file") true else when {
                    ph == "*" -> true
                    ph.startsWith("*.") -> host == ph.substring(2) || host.endsWith("." + ph.substring(2))
                    else -> host == ph
                }
                if (!hostOk) return@any false
                val regexPath = "^" + pp.split("*").joinToString(".*") { Regex.escape(it) } + "$"
                Regex(regexPath).matches(path)
            }
        }
    }
