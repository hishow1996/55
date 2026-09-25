package com.example.extension

import android.content.Context
import org.json.JSONObject

interface ExtensionStorageHost {
    fun get(extensionId: String, key: String?): String
    fun set(extensionId: String, valuesJson: String)
    fun remove(extensionId: String, key: String)
    fun clear(extensionId: String)
}

class SharedPreferencesExtensionStorageHost(
    context: Context
) : ExtensionStorageHost {
    private val prefs = context.getSharedPreferences("extension_runtime_v2", Context.MODE_PRIVATE)

    override fun get(extensionId: String, key: String?): String {
        val all = try {
            JSONObject(prefs.getString("storage_$extensionId", "{}") ?: "{}")
        } catch (_: Exception) {
            JSONObject()
        }
        if (key.isNullOrBlank()) return all.toString()
        val out = JSONObject()
        if (all.has(key)) out.put(key, all.opt(key))
        return out.toString()
    }

    override fun set(extensionId: String, valuesJson: String) {
        val all = try {
            JSONObject(prefs.getString("storage_$extensionId", "{}") ?: "{}")
        } catch (_: Exception) {
            JSONObject()
        }
        val values = try { JSONObject(valuesJson) } catch (_: Exception) { JSONObject() }
        val keys = values.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            all.put(key, values.opt(key))
        }
        prefs.edit().putString("storage_$extensionId", all.toString()).apply()
    }

    override fun remove(extensionId: String, key: String) {
        val all = try {
            JSONObject(prefs.getString("storage_$extensionId", "{}") ?: "{}")
        } catch (_: Exception) {
            JSONObject()
        }
        all.remove(key)
        prefs.edit().putString("storage_$extensionId", all.toString()).apply()
    }

    override fun clear(extensionId: String) {
        prefs.edit().putString("storage_$extensionId", "{}").apply()
    }
}
