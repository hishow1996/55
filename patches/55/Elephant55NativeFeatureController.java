package org.chromium.chrome.browser;

import android.content.Context;

import androidx.annotation.Nullable;

import org.chromium.chrome.browser.tab.Tab;
import org.chromium.chrome.browser.tab.TabUtils;

/**
 * Applies repo-55 browser preferences to the real Chromium tab runtime.
 *
 * The Chromium Tab/WebContents stack remains the only page runtime. This class
 * contains no WebView and no replacement extension/plugin implementation.
 */
public final class Elephant55NativeFeatureController {
    private Elephant55NativeFeatureController() {}

    /** Apply persisted 55 settings to the currently visible Chromium tab. */
    public static void applyToTab(Context context, @Nullable Tab tab) {
        if (tab == null || tab.getWebContents() == null) return;

        // Desktop mode is implemented by Chromium's NavigationController rather
        // than by mutating an Android WebView user-agent string.
        TabUtils.switchUserAgent(
                tab,
                Elephant55NativeSettings.isDesktopMode(context),
                TabUtils.UseDesktopUserAgentCaller.OTHER);
    }
}
