package com.example.extension

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class BrowserExtensionTabHostTest {
    @Test
    fun createAndQuery_areIndependentFromExtensionManager() {
        val host = BrowserExtensionTabHost()
        val tab = host.create("https://example.com", true)
        assertEquals("https://example.com", tab.url)
        assertEquals(1, host.query("""{"active":true}""").length())
    }

    @Test
    fun bindAndSnapshot_keepBrowserTabIdentity() {
        val host = BrowserExtensionTabHost()
        host.bind("page", 42, "https://example.com")
        assertEquals(42, host.query("""{"url":"https://example.com"}""").getJSONObject(0).getInt("id"))
    }
}
