package com.example.extension

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

class ExtensionStorageHostTest {
    @Test
    fun storage_contract_is_independent_of_extension_manager() {
        val values = mutableMapOf<String, String>()
        val host = object : ExtensionStorageHost {
            override fun get(extensionId: String, key: String?): String {
                val all = JSONObject(values[extensionId] ?: "{}")
                if (key.isNullOrBlank()) return all.toString()
                val out = JSONObject()
                if (all.has(key)) out.put(key, all.opt(key))
                return out.toString()
            }
            override fun set(extensionId: String, valuesJson: String) {
                val all = JSONObject(values[extensionId] ?: "{}")
                val input = JSONObject(valuesJson)
                input.keys().forEach { key -> all.put(key, input.opt(key)) }
                values[extensionId] = all.toString()
            }
            override fun remove(extensionId: String, key: String) {
                val all = JSONObject(values[extensionId] ?: "{}")
                all.remove(key)
                values[extensionId] = all.toString()
            }
            override fun clear(extensionId: String) { values.remove(extensionId) }
        }
        host.set("test", JSONObject().put("name", "value").toString())
        assertEquals("value", JSONObject(host.get("test", "name")).getString("name"))
        host.remove("test", "name")
        assertEquals("{}", host.get("test", "name"))
    }
}
