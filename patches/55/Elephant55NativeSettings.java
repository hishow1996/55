package org.chromium.chrome.browser;

import android.content.Context;
import android.content.SharedPreferences;

import org.chromium.base.ContextUtils;

/**
 * Native configuration bridge for the Elephant Browser (repo 55) feature port.
 *
 * This class deliberately lives inside Chromium's Android source tree. It is
 * not a WebView bridge and it does not implement a second extension/plugin
 * runtime. The Chromium tab/profile/extension services remain authoritative.
 */
public final class Elephant55NativeSettings {
    private static final String PREFS = "elephant_55_native";
    private static final String KEY_BOTTOM_TOOLBAR = "bottom_toolbar";
    private static final String KEY_DESKTOP_MODE = "desktop_mode";
    private static final String KEY_NIGHT_MODE = "night_mode";
    private static final String KEY_PIP = "global_pip";
    private static final String KEY_AI = "ai_enabled";

    private Elephant55NativeSettings() {}

    public static void applyKiwiUiDefaults(Context context) {
        boolean desktopMode = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getBoolean(KEY_DESKTOP_MODE, false);
        ContextUtils.getAppSharedPreferences().edit()
                .putBoolean("desktop_mode", desktopMode)
                .apply();
    }

    public static void ensureDefaults(Context context) {
        SharedPreferences p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        if (!p.contains(KEY_BOTTOM_TOOLBAR)
                || !p.contains(KEY_DESKTOP_MODE)
                || !p.contains(KEY_NIGHT_MODE)
                || !p.contains(KEY_PIP)
                || !p.contains(KEY_AI)) {
            p.edit()
                    .putBoolean(KEY_BOTTOM_TOOLBAR, true)
                    .putBoolean(KEY_DESKTOP_MODE, false)
                    .putBoolean(KEY_NIGHT_MODE, false)
                    .putBoolean(KEY_PIP, true)
                    .putBoolean(KEY_AI, true)
                    .apply();
        }
    }

    public static boolean isBottomToolbarEnabled(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getBoolean(KEY_BOTTOM_TOOLBAR, true);
    }

    public static boolean isDesktopMode(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getBoolean(KEY_DESKTOP_MODE, false);
    }

    public static boolean isNightMode(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getBoolean(KEY_NIGHT_MODE, false);
    }

    public static boolean isGlobalPipEnabled(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getBoolean(KEY_PIP, true);
    }

    public static boolean isAiEnabled(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getBoolean(KEY_AI, true);
    }
}
