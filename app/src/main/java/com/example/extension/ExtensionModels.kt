package com.example.extension

import org.json.JSONObject
import java.util.UUID

data class ExtensionManifest(
    val manifestVersion: Int,
    val name: String,
    val version: String,
    val description: String = "",
    val permissions: List<String> = emptyList(),
    val hostPermissions: List<String> = emptyList(),
    val contentScripts: List<ContentScriptSpec> = emptyList(),
    val backgroundScripts: List<String> = emptyList(),
    val serviceWorker: String? = null,
    val popup: String? = null,
    val optionsPage: String? = null,
    val actionTitle: String? = null,
    val iconPath: String? = null,
    val key: String? = null,
    val webAccessibleResources: List<String> = emptyList()
) {
    companion object {
        fun parse(raw: String): ExtensionManifest {
            val o = JSONObject(raw)
            val mv = o.optInt("manifest_version", 2)
            val action = o.optJSONObject("action") ?: o.optJSONObject("browser_action")
            val bg = o.optJSONObject("background")
            val scripts = mutableListOf<String>()
            bg?.optJSONArray("scripts")?.let { a -> for (i in 0 until a.length()) scripts += a.optString(i) }
            val cs = mutableListOf<ContentScriptSpec>()
            o.optJSONArray("content_scripts")?.let { a ->
                for (i in 0 until a.length()) {
                    val item = a.optJSONObject(i) ?: continue
                    val matches = mutableListOf<String>()
                    item.optJSONArray("matches")?.let { m -> for (j in 0 until m.length()) matches += m.optString(j) }
                    val js = mutableListOf<String>()
                    item.optJSONArray("js")?.let { j -> for (k in 0 until j.length()) js += j.optString(k) }
                    cs += ContentScriptSpec(matches, js, item.optString("run_at", "document_idle"))
                }
            }
            val perms = mutableListOf<String>()
            o.optJSONArray("permissions")?.let { a -> for (i in 0 until a.length()) perms += a.optString(i) }
            val hosts = mutableListOf<String>()
            o.optJSONArray("host_permissions")?.let { a -> for (i in 0 until a.length()) hosts += a.optString(i) }
            if (mv == 2) hosts += perms.filter { it.contains("://") || it == "<all_urls>" }
            val war = mutableListOf<String>()
            o.optJSONArray("web_accessible_resources")?.let { a ->
                for (i in 0 until a.length()) {
                    val item = a.optString(i, "")
                    if (item.isNotBlank()) war += item
                }
            }
            o.optJSONArray("web_accessible_resources")?.let { a ->
                // MV3 may use objects: {resources:[...], matches:[...]}
                for (i in 0 until a.length()) {
                    val item = a.optJSONObject(i) ?: continue
                    item.optJSONArray("resources")?.let { resources ->
                        for (j in 0 until resources.length()) {
                            val value = resources.optString(j, "")
                            if (value.isNotBlank()) war += value
                        }
                    }
                }
            }
            val icon = o.optJSONObject("icons")?.let { icons ->
                var found: String? = null
                for (size in listOf("128", "96", "64", "48", "32", "16")) {
                    val value = icons.optString(size, "")
                    if (value.isNotBlank()) { found = value; break }
                }
                found
            }
            return ExtensionManifest(
                mv, o.optString("name", "未命名扩展"), o.optString("version", "1.0"),
                o.optString("description", ""), perms.distinct(), hosts.distinct(), cs,
                scripts, bg?.optString("service_worker")?.takeIf { it.isNotBlank() },
                action?.optString("default_popup")?.takeIf { it.isNotBlank() },
                o.optString("options_page").takeIf { it.isNotBlank() },
                action?.optString("default_title")?.takeIf { it.isNotBlank() }, icon,
                o.optString("key").takeIf { it.isNotBlank() },
                war.distinct()
            )
        }
    }
}

data class ContentScriptSpec(val matches: List<String>, val jsFiles: List<String>, val runAt: String)

data class BrowserExtension(
    val id: String = UUID.randomUUID().toString(),
    val rootPath: String,
    val manifest: ExtensionManifest,
    val enabled: Boolean = true,
    val installedTime: Long = System.currentTimeMillis()
) {
    val name get() = manifest.name
    val version get() = manifest.version
}
