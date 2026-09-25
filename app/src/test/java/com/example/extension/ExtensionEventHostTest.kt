package com.example.extension

import org.junit.Assert.assertEquals
import org.junit.Test

class ExtensionEventHostTest {
    @Test
    fun runtimeMessage_keepsDestinationAndSender() {
        val event = ExtensionBrowserEvent.RuntimeMessage(
            message = """{"hello":"world"}""",
            senderId = "ext.test",
            destination = ExtensionBrowserEvent.MessageDestination.BACKGROUND
        )
        assertEquals("ext.test", event.senderId)
        assertEquals(
            ExtensionBrowserEvent.MessageDestination.BACKGROUND,
            event.destination
        )
    }

    @Test
    fun tabEvents_areIndependentOfWebView() {
        val event = ExtensionBrowserEvent.TabsCreated(
            TabSnapshot(7, "https://example.com", true)
        )
        assertEquals(7, event.tab.id)
        assertEquals("https://example.com", event.tab.url)
    }
}
