#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
APP="$ROOT/app/src/main/java"
KIWI="$ROOT/third_party/kiwi/src.next"

echo "=== 55 native cutover audit ==="

check_absent() {
  local label="$1"
  local pattern="$2"
  local root="${3:-$APP}"
  if grep -RqsE "$pattern" "$root" 2>/dev/null; then
    echo "BLOCKED: $label"
    grep -RIlE "$pattern" "$root" 2>/dev/null | sed "s#^$ROOT/##"
  else
    echo "OK: $label"
  fi
}

check_absent "WebView runtime copied into Chromium" 'android\.webkit\.(WebView|WebChromeClient|WebViewClient)' "$KIWI/chrome"
check_absent "WebView bridge copied into Chromium" 'ElephantWebBridge|ElephantWebViewClient|ElephantWebChromeClient' "$KIWI/chrome"
check_absent "custom extension runtime copied into Chromium" 'com\.example\.extension|ExtensionManager|KiwiExtensionApi|BrowserExtension' "$KIWI/chrome"
check_absent "custom plugin manager copied into Chromium" 'PluginManagerScreen|onOpenPlugins|onOpenPluginManager' "$KIWI/chrome"

echo
echo "Required Chromium native integration points:"
for path in   "$KIWI/chrome/android/java/src/org/chromium/chrome/browser/ChromeTabbedActivity.java"   "$KIWI/chrome/android/java/src/org/chromium/chrome/browser/tab/TabImpl.java"   "$KIWI/chrome/android/java/src/org/chromium/chrome/browser/tab/TabUtils.java"   "$KIWI/chrome/android/java/src/org/chromium/chrome/browser/download"   "$KIWI/components/history"   "$KIWI/components/bookmarks"   "$KIWI/extensions/browser/extension_service.cc"; do
  if [ -e "$path" ]; then
    echo "OK: ${path#$KIWI/}"
  else
    echo "MISSING: ${path#$KIWI/}"
    exit 2
  fi
done

echo
echo "Native cutover audit finished."
