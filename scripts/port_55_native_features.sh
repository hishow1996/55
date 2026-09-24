#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
KIWI="$ROOT/third_party/kiwi/src.next"
JAVA_ROOT="$KIWI/chrome/android/java/src/org/chromium/chrome/browser"
JAVA_LIST="$KIWI/chrome/android/java_sources.gni"

test -d "$KIWI/chrome/android" || { echo "Kiwi Chromium source is not prepared: $KIWI" >&2; exit 2; }
mkdir -p "$JAVA_ROOT"

cat > "$JAVA_ROOT/Elephant55NativeSettings.java" <<'JAVA'
package org.chromium.chrome.browser;

import android.content.Context;
import android.content.SharedPreferences;

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

JAVA

ENTRY='    "java/src/org/chromium/chrome/browser/Elephant55NativeSettings.java",'
if ! grep -Fqx "$ENTRY" "$JAVA_LIST"; then
  python3 - "$JAVA_LIST" <<'PY'
from pathlib import Path
p = Path(__import__("sys").argv[1])
s = p.read_text()
entry = '    "java/src/org/chromium/chrome/browser/Elephant55NativeSettings.java",'
markers = [
    'chrome_java_sources += public_autofill_assistant_java_sources',
    'if (enable_vr) {',
]
if entry not in s:
    replaced = False
    for marker in markers:
        if marker in s:
            s = s.replace(marker, 'chrome_java_sources += [\\n' + entry + '\\n]\\n\\n' + marker, 1)
            replaced = True
            break
    if not replaced:
        raise SystemExit('Cannot find a stable chrome_java_sources.gni insertion marker')
p.write_text(s)
PY
fi

grep -Fqx "$ENTRY" "$JAVA_LIST"
echo "55 native settings bridge installed in Chromium source."
