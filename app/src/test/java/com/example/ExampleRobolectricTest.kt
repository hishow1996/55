package com.example

import android.content.Context
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import com.example.model.QuickSite
import com.example.ui.home.HomeScreen
import com.example.ui.theme.MyApplicationTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @get:Rule
  val composeTestRule = createComposeRule()

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("大象", appName)
  }

  @Test
  fun `render HomeScreen`() {
    composeTestRule.setContent {
      MyApplicationTheme {
        HomeScreen(
          searchEngine = "google",
          isIncognito = false,
          isNightMode = false,
          quickSites = listOf(
            QuickSite("GitHub", "https://github.com", "github", 0xFF181717),
            QuickSite("TikTok", "https://www.tiktok.com", "tiktok", 0xFF010101),
            QuickSite("YouTube", "https://m.youtube.com", "youtube", 0xFFFF0000),
            QuickSite("爱壹帆", "https://m.yfsp.tv/list", "yfsp", 0xFFFF6200),
            QuickSite("Instagram", "https://www.instagram.com", "instagram", 0xFFE1306C),
            QuickSite("更多", "action://more", "more", 0xFF6366F1)
          ),
          bookmarks = emptyList(),
          onSearch = {},
          onSelectEngine = {},
          onOpenDownloads = {},
          onOpenAi = {},
          onOpenHistory = {},
          onOpenBookmarks = {}
        )
      }
    }
  }
}

