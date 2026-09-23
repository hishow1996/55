package com.example.extension

import android.content.Context
import org.json.JSONObject
import java.io.File

class ExtensionStore(private val context: Context) {
    fun readFile(extension: BrowserExtension, path: String): String? {
        val root = File(extension.rootPath).canonicalFile
        val file = File(root, path).canonicalFile
        if (!file.path.startsWith(root.path + File.separator)) return null
        return file.takeIf { it.isFile }?.readText()
    }
    fun manifest(extension: BrowserExtension): JSONObject? =
        readFile(extension, "manifest.json")?.let(::JSONObject)
}
