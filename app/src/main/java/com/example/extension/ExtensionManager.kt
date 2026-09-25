package com.example.extension

import android.content.Context
import android.net.Uri
import android.webkit.JavascriptInterface
import android.webkit.WebView
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.zip.ZipInputStream

class ExtensionManager(
    private val context: Context,
    private val runtime: ExtensionRuntimeBackend = WebViewExtensionRuntime(context),
    private val permissionPolicy: ExtensionPermissionPolicy = ManifestExtensionPermissionPolicy()
) {
    private val prefs = context.getSharedPreferences("extension_runtime_v2", Context.MODE_PRIVATE)
    private val pageRuntime: ExtensionPageRuntime = runtime.createPageRuntime()
    private val _extensions = MutableStateFlow<List<BrowserExtension>>(emptyList())
    val extensions = _extensions.asStateFlow()
    private val tabHost = BrowserExtensionTabHost()
    private val lifecycleHost = RuntimeExtensionLifecycleHost(runtime) { BackgroundBridge(it.id) }
    private val eventHost: ExtensionEventHost = WebViewExtensionEventHost(
        backgroundHosts = { lifecycleHost.backgroundHosts() },
        pageHosts = { tabHost.allPageHosts() }
    )

    fun setBrowserTabCreator(creator: ((String, Boolean) -> Unit)?) {
        tabHost.setLegacyCreateTab(creator)
    }

    fun setBrowserTabController(creator: ((Int, String, Boolean) -> Unit)?, updater: ((Int, String?) -> Unit)?, selector: ((Int) -> Unit)?) {
        tabHost.setLegacyCallbacks(creator, updater, selector)
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
        result.filter { it.enabled }.forEach { lifecycleHost.start(it) }
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
            lifecycleHost.start(ext)
            return ext
        } finally {
            temp.deleteRecursively()
        }
    }

    fun uninstall(id: String) {
        val ext = extension(id) ?: return
        lifecycleHost.stop(id)
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
        if (enabled) updated.firstOrNull { it.id == id }?.let { lifecycleHost.start(it) }
        else lifecycleHost.stop(id)
        persist(updated)
    }

    fun extension(id: String): BrowserExtension? = _extensions.value.firstOrNull { it.id == id }

    fun hasPermission(id: String, permission: String): Boolean {
        val e = extension(id) ?: return false
        return permissionPolicy.hasPermission(e, permission)
    }

    fun canAccessUrl(id: String, url: String): Boolean {
        val e = extension(id) ?: return false
        return permissionPolicy.canAccessUrl(e, url)
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
        return true
    }

    fun popupUrl(id: String): String? {
        val ext = extension(id) ?: return null
        val path = ext.manifest.popup ?: return null
        val file = safeChild(ext.rootPath, path) ?: return null
        return file.takeIf { it.exists() && it.isFile }?.let { runtime.resourceUrl(ext, path) }
    }

    fun optionsUrl(id: String): String? {
        val ext = extension(id) ?: return null
        val path = ext.manifest.optionsPage ?: return null
        val file = safeChild(ext.rootPath, path) ?: return null
        return file.takeIf { it.exists() && it.isFile }?.let { runtime.resourceUrl(ext, path) }
    }

    fun runtimeDescriptor(): ExtensionRuntimeDescriptor = ExtensionRuntimeDescriptor(
        kind = runtime.kind,
        supportsNativeChromiumApis = runtime.supportsNativeChromiumApis,
        supportsExtensionScheme = runtime.supportsExtensionScheme,
        supportsManifestV2 = true,
        supportsManifestV3 = true
    )

    fun updatePageState(pageKey: String, url: String, active: Boolean = true) {
        tabHost.updatePage(pageKey, url, active)
    }

    fun updatePageTitle(pageKey: String, title: String) { tabHost.updateTitle(pageKey, title) }

    fun setPageActive(pageKey: String) { tabHost.updatePage(pageKey, tabHost.url(pageKey), true) }

    fun bindBrowserTab(extensionTabId: Int, pageKey: String) {
        tabHost.bindReplacingLegacy(pageKey, extensionTabId)
        // Bind before Compose creates the WebView. attachWebView keeps this ID.
    }

    fun attachWebView(pageKey: String, webView: WebView, url: String) {
        val host = pageRuntime.attach(pageKey, webView, url, PageBridge(pageKey))
        tabHost.attachPage(pageKey, host, url)
    }

    fun detachWebView(pageKey: String) {
        tabHost.detachPage(pageKey)
    }

    fun injectForPage(webView: WebView, url: String, runAt: String) {
        val pageKey = webView.hashCode().toString()
        if (tabHost.pageHostMatches(pageKey, webView).not()) attachWebView(pageKey, webView, url)
        else updatePageState(pageKey, url)
        _extensions.value.filter { it.enabled }.forEach { ext ->
            ext.manifest.contentScripts
                .filter { it.runAt == runAt && matches(it.matches, url) && (ext.manifest.hostPermissions.isEmpty() || matches(ext.manifest.hostPermissions, url)) }
                .forEach { spec ->
                    spec.jsFiles.forEach { name ->
                        val file = safeChild(ext.rootPath, name) ?: return@forEach
                        if (file.exists() && file.isFile) {
                            tabHost.pageHost(pageKey)?.let { host ->
                                pageRuntime.inject(host, contentBootstrap(ext, file.readText()))
                            }
                        }
                    }
                }
        }
    }

    private fun contentBootstrap(ext: BrowserExtension, script: String): String {
        val key = "__elephant_ext_" + ext.id.replace("-", "_")
        val code = JSONObject.quote(script)
        val id = JSONObject.quote(ext.id)
        val root = JSONObject.quote(runtime.resourceUrl(ext, ""))
        return "(function(){if(window['$key'])return;window['$key']=1;" +
            KiwiExtensionApi.content(id, root) +
            "(0,eval)($code);})()"
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
        @JavascriptInterface fun tabsSendMessage(extensionId: String, tabId: Int, message: String): String =
            if (tabHost.sendMessage(tabId, extensionId, message)) JSONObject.NULL.toString() else JSONObject.NULL.toString()
        @JavascriptInterface fun tabsCreate(extensionId: String, propertiesJson: String): String {
            val p = try { JSONObject(propertiesJson) } catch (_: Exception) { JSONObject() }
            val snapshot = tabHost.create(p.optString("url", "about:blank"), p.optBoolean("active", true))
            eventHost.dispatch(extensionId, ExtensionBrowserEvent.TabsCreated(snapshot))
            return snapshot.toJson().toString()
        }

        @JavascriptInterface fun tabsUpdate(extensionId: String, tabId: Int, propertiesJson: String): String {
            val p = try { JSONObject(propertiesJson) } catch (_: Exception) { JSONObject() }
            val snapshot = tabHost.update(tabId, p.optString("url").takeIf { it.isNotBlank() }, if (p.has("active")) p.optBoolean("active") else null)
            if (snapshot != null) eventHost.dispatch(extensionId, ExtensionBrowserEvent.TabsUpdated(snapshot))
            return snapshot?.toJson()?.toString() ?: JSONObject.NULL.toString()
        }

        @JavascriptInterface fun tabsQuery(queryJson: String): String = tabHost.query(queryJson).toString()

        @JavascriptInterface fun tabsRemove(extensionId: String, tabId: Int): String =
            tabHost.remove(tabId).toString()

        @JavascriptInterface fun windowsGetCurrent(extensionId: String): String =
            JSONObject().apply { put("id", 1); put("focused", true); put("type", "normal") }.toString()

        @JavascriptInterface fun actionSetBadgeText(extensionId: String, text: String) {
            prefs.edit().putString("badge_text_" + extensionId, text).apply()
        }
        @JavascriptInterface fun actionGetBadgeText(extensionId: String): String =
            prefs.getString("badge_text_" + extensionId, "") ?: ""
        @JavascriptInterface fun actionSetBadgeBackgroundColor(extensionId: String, colorJson: String) {
            prefs.edit().putString("badge_color_" + extensionId, colorJson).apply()
        }
        @JavascriptInterface fun notificationsCreate(extensionId: String, notificationId: String, optionsJson: String): String =
            if (notificationId.isNotBlank()) notificationId else "notification-" + System.nanoTime()
        @JavascriptInterface fun notificationsClear(extensionId: String, notificationId: String): Boolean = true

        @JavascriptInterface fun contextMenuCreate(extensionId: String, itemJson: String): Int {
            val next = prefs.getInt("context_menu_next_" + extensionId, 1)
            prefs.edit().putInt("context_menu_next_" + extensionId, next + 1).apply()
            return next
        }
        @JavascriptInterface fun contextMenuRemove(extensionId: String, itemId: String) {}
        @JavascriptInterface fun contextMenuRemoveAll(extensionId: String) {}
        
        @JavascriptInterface fun executeScript(extensionId: String, optionsJson: String): String {
            if (!hasPermission(extensionId, "scripting") && !hasPermission(extensionId, "activeTab")) return "[]"
            val o = try { JSONObject(optionsJson) } catch (_: Exception) { JSONObject() }
            val js = o.optString("code").ifBlank {
                o.optString("func").takeIf { it.isNotBlank() }?.let { "($it)()" } ?: ""
            }
            if (js.isBlank()) return "[]"
            val target = o.optJSONObject("target")
            val tabId = target?.optInt("tabId", -1) ?: -1
            val targets = tabHost.executeScriptTargets(tabId)
            var count = 0
            targets.forEach { entry ->
                val pageUrl = tabHost.pageUrl(entry.first)
                val host = entry.second
                val hosts = extension(extensionId)?.manifest?.hostPermissions.orEmpty()
                if (hosts.isNotEmpty() && !matches(hosts, pageUrl) && !hasPermission(extensionId, "activeTab")) return@forEach
                count++
                host.post { host.evaluateJavascript(js) }
            }
            return if (count == 0) "[]" else "[{}]"
        }
    }

    private fun deliverToBackground(extensionId: String, message: String) {
        if (!lifecycleHost.isRunning(extensionId)) return
        eventHost.dispatch(
            extensionId,
            ExtensionBrowserEvent.RuntimeMessage(
                message = message,
                senderId = extensionId,
                destination = ExtensionBrowserEvent.MessageDestination.BACKGROUND
            )
        )
    }

    private fun deliverToPages(extensionId: String, message: String) {
        eventHost.dispatch(
            extensionId,
            ExtensionBrowserEvent.RuntimeMessage(
                message = message,
                senderId = extensionId,
                destination = ExtensionBrowserEvent.MessageDestination.PAGES
            )
        )
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
