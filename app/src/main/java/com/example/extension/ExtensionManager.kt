package com.example.extension

import android.content.Context
import android.net.Uri
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.File
import java.security.MessageDigest
import java.util.zip.ZipInputStream

class ExtensionManager(private val context: Context) {
    private val prefs = context.getSharedPreferences("extension_runtime_v1", Context.MODE_PRIVATE)
    private val _extensions = MutableStateFlow<List<BrowserExtension>>(emptyList())
    val extensions = _extensions.asStateFlow()
    private val backgroundHosts = mutableMapOf<String, WebView>()

    init { load() }

    private fun load() {
        val result = mutableListOf<BrowserExtension>()
        try {
            val a = org.json.JSONArray(prefs.getString("installed", "[]") ?: "[]")
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
        temp.deleteRecursively()
        val ext = BrowserExtension(id, target.absolutePath, manifest, true)
        val updated = _extensions.value.filterNot { it.id == id } + ext
        _extensions.value = updated
        persist(updated)
        startBackground(ext)
        return ext
    }

    fun uninstall(id: String) {
        val ext = extension(id) ?: return
        backgroundHosts.remove(id)?.destroy()
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

    fun popupUrl(id: String): String? = extension(id)?.manifest?.popup?.let { "file://" + File(extension(id)!!.rootPath, it).absolutePath }
    fun optionsUrl(id: String): String? = extension(id)?.manifest?.optionsPage?.let { "file://" + File(extension(id)!!.rootPath, it).absolutePath }

    fun injectForPage(webView: WebView, url: String, runAt: String) {
        _extensions.value.filter { it.enabled }.forEach { ext ->
            ext.manifest.contentScripts.filter { it.runAt == runAt && matches(it.matches + ext.manifest.hostPermissions, url) }
                .forEach { spec ->
                    spec.jsFiles.forEach { name ->
                        val file = File(ext.rootPath, name)
                        if (file.exists()) webView.evaluateJavascript(contentBootstrap(ext, file.readText()), null)
                    }
                }
        }
    }

    private fun contentBootstrap(ext: BrowserExtension, script: String): String {
        val key = "__elephant_ext_" + ext.id.replace("-", "_")
        val code = JSONObject.quote(script)
        val id = JSONObject.quote(ext.id)
        return "(function(){if(window['" + key + "'])return;window['" + key + "']=1;window.chrome=window.chrome||{};" +
            "chrome.runtime=chrome.runtime||{id:" + id + ",getURL:function(p){return 'file://" + ext.rootPath + "/'+p;},sendMessage:function(m){try{window.ElephantExtensionBridge&&window.ElephantExtensionBridge.message(" + id + ",JSON.stringify(m))}catch(e){}}};" +
            "chrome.storage=chrome.storage||{local:{get:function(k,c){if(c)c({});},set:function(v,c){if(c)c();}}};" +
            "(0,eval)(" + code + ");})()"
    }

    private fun startBackground(ext: BrowserExtension) {
        if (!ext.enabled || backgroundHosts.containsKey(ext.id)) return
        val worker = ext.manifest.serviceWorker ?: ext.manifest.backgroundScripts.firstOrNull() ?: return
        val file = File(ext.rootPath, worker)
        if (!file.exists()) return
        val wv = WebView(context)
        wv.settings.javaScriptEnabled = true
        wv.settings.domStorageEnabled = true
        wv.webViewClient = WebViewClient()
        wv.addJavascriptInterface(BackgroundBridge(ext.id), "ElephantExtensionBridge")
        val polyfill = "window.chrome={runtime:{id:" + JSONObject.quote(ext.id) + ",getURL:function(p){return 'file://" + ext.rootPath + "/'+p;},sendMessage:function(m,c){ElephantExtensionBridge.message(" + JSONObject.quote(ext.id) + ",JSON.stringify(m));if(c)c({});}},storage:{local:{get:function(k,c){if(c)c({});},set:function(v,c){if(c)c();}}},tabs:{query:function(q,c){if(c)c([]);}},scripting:{executeScript:function(o,c){if(c)c([]);}}};"
        wv.loadDataWithBaseURL("file://" + ext.rootPath + "/", "<html><script>" + polyfill + file.readText() + "</script></html>", "text/html", "UTF-8", null)
        backgroundHosts[ext.id] = wv
    }

    private inner class BackgroundBridge(private val id: String) {
        @JavascriptInterface fun message(extensionId: String, message: String) {
            // Browser-owned extension messages terminate at this controlled bus.
        }
    }

    private fun persist(list: List<BrowserExtension>) {
        val a = org.json.JSONArray()
        list.forEach { e -> a.put(JSONObject().apply {
            put("id", e.id); put("rootPath", e.rootPath); put("enabled", e.enabled); put("installedTime", e.installedTime)
        }) }
        prefs.edit().putString("installed", a.toString()).apply()
    }

    private fun stableId(manifest: ExtensionManifest): String =
        MessageDigest.getInstance("SHA-256").digest((manifest.name + "|" + manifest.version).toByteArray())
            .joinToString("") { "%02x".format(it) }.take(32)

    private fun normalizeArchive(bytes: ByteArray): ByteArray {
        if (bytes.size < 4 || bytes[0].toInt() != 0x43 || bytes[1].toInt() != 0x72 || bytes[2].toInt() != 0x32 || bytes[3].toInt() != 0x34) return bytes
        if (bytes.size < 16) error("CRX 文件损坏")
        return when (readIntLE(bytes, 8)) {
            2 -> bytes.copyOfRange(16 + readIntLE(bytes, 8) + readIntLE(bytes, 12), bytes.size)
            3 -> {
                val header = readIntLE(bytes, 8)
                bytes.copyOfRange(12 + header, bytes.size)
            }
            else -> error("不支持的 CRX 版本")
        }
    }

    private fun readIntLE(b: ByteArray, o: Int) = (b[o].toInt() and 255) or ((b[o+1].toInt() and 255) shl 8) or ((b[o+2].toInt() and 255) shl 16) or ((b[o+3].toInt() and 255) shl 24)

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
            if (patterns.any { it == "<all_urls>" || it == "*" }) return true
            return patterns.any { p ->
                Regex("^" + Regex.escape(p).replace("\\*", ".*") + "$").matches(url)
            }
        }
    }
}
