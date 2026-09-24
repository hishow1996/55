#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
APP="$ROOT/app/src/main/java"

echo "=== 55 native cutover audit ==="

check_absent() {
  local label="$1"
  local pattern="$2"
  if grep -RqsE "$pattern" "$APP" 2>/dev/null; then
    echo "BLOCKED: $label"
    grep -RIlE "$pattern" "$APP" 2>/dev/null | sed "s#^$ROOT/##"
  else
    echo "OK: $label"
  fi
}

check_absent "Android WebView runtime" 'android\.webkit\.(WebView|WebChromeClient|WebViewClient)'
check_absent "WebView browser bridge" 'ElephantWebBridge|ElephantWebViewClient|ElephantWebChromeClient'
check_absent "WebView tab registry" 'registerTabWebView|activeWebView|tabWebViews'
check_absent "custom extension runtime" 'com\.example\.extension|ExtensionManager|KiwiExtensionApi|BrowserExtension'
check_absent "custom plugin manager" 'PluginManagerScreen|onOpenPlugins|onOpenPluginManager'

echo
echo "Required Chromium native integration points:"
KIWI="$ROOT/third_party/kiwi/src.next"
for path in \
  "$KIWI/chrome/android/java/src/org/chromium/chrome/browser/ChromeTabbedActivity.java" \
  "$KIWI/chrome/android/java/src/org/chromium/chrome/browser/tab/TabImpl.java" \
  "$KIWI/chrome/android/java/src/org/chromium/chrome/browser/tab/TabUtils.java" \
  "$KIWI/chrome/android/java/src/org/chromium/chrome/browser/download" \
  "$KIWI/components/history" \
  "$KIWI/components/bookmarks" \
  "$KIWI/extensions/browser/extension_service.cc"; do
  if [ -e "$path" ]; then
    echo "OK: ${path#$KIWI/}"
  else
    echo "MISSING: ${path#$KIWI/}"
    exit 2
  fi
done

echo
echo "Native cutover audit finished."
