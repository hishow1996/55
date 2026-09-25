package com.example.extension

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

class ExtensionStorageHostTest {
    @Test
    fun storage_roundTrip_is_independent_of_extension_manager() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val host = SharedPreferencesExtensionStorageHost(context)
        val id = "storage-test-" + System.nanoTime()
        host.clear(id)
        host.set(id, JSONObject().put("name", "value").toString())
        assertEquals("value", JSONObject(host.get(id, "name")).getString("name"))
        host.remove(id, "name")
        assertEquals("{}", host.get(id, "name"))
        host.clear(id)
    }
}
