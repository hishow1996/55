#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
KIWI="$ROOT/third_party/kiwi/src.next"

fail() { echo "ERROR: $*" >&2; exit 1; }

test -d "$KIWI/chrome" || fail "Kiwi Chromium source is not prepared"
test -f "$KIWI/chrome/android/java/src/org/chromium/chrome/browser/ChromeTabbedActivity.java" || fail "ChromeTabbedActivity missing"
test -f "$KIWI/chrome/android/java/src/org/chromium/chrome/browser/tab/TabUtils.java" || fail "TabUtils missing"
if ! find "$KIWI/chrome/android/java/src" "$KIWI/chrome/android/java" -type f \
    \( -name '*PictureInPicture*.java' -o -name '*PictureInPicture*.kt' \) -print -quit | grep -q .; then
  fail "Chromium PiP implementation is missing"
fi
test -d "$KIWI/components/history" || fail "Chromium history service missing"
test -d "$KIWI/components/bookmarks" || fail "Chromium bookmarks service missing"
test -f "$KIWI/extensions/browser/extension_service.cc" || fail "Chromium extension runtime missing"

JAVA_LIST="$KIWI/chrome/android/java_sources.gni"
grep -Fq '"java/src/org/chromium/chrome/browser/Elephant55NativeSettings.java",' "$JAVA_LIST" || fail "55 settings source not registered"
grep -Fq '"java/src/org/chromium/chrome/browser/Elephant55NativeFeatureController.java",' "$JAVA_LIST" || fail "55 feature controller source not registered"

grep -Fq 'Elephant55NativeSettings.ensureDefaults(this);' "$KIWI/chrome/android/java/src/org/chromium/chrome/browser/ChromeTabbedActivity.java" || fail "55 settings lifecycle hook missing"
grep -Fq 'Elephant55NativeSettings.applyKiwiUiDefaults();' "$KIWI/chrome/android/java/src/org/chromium/chrome/browser/ChromeTabbedActivity.java" || fail "55 Kiwi UI defaults hook missing"
grep -Fq 'Elephant55NativeSettings.ensureDefaults(this);' "$KIWI/chrome/android/java/src/org/chromium/chrome/browser/ChromeTabbedActivity.java" || fail "55 preference initialization hook missing"
grep -Fq 'Elephant55NativeFeatureController.applyToTab(this, getActivityTab());' "$KIWI/chrome/android/java/src/org/chromium/chrome/browser/ChromeTabbedActivity.java" || fail "55 native tab controller hook missing"

if grep -RqsE 'android\.webkit\.(WebView|WebChromeClient|WebViewClient)|com\.example\.extension|ExtensionManager|KiwiExtensionApi|PluginManagerScreen' "$KIWI/chrome"; then
  fail "legacy WebView/custom extension code was copied into Chromium runtime"
fi

echo "55 native migration contract passed."
