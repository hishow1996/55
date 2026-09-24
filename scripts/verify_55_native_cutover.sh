#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
KIWI="$ROOT/third_party/kiwi/src.next"

"$ROOT/scripts/prepare_kiwi_chromium.sh"

required=(
  "$KIWI/chrome/android/java/src/org/chromium/chrome/browser/ChromeTabbedActivity.java"
  "$KIWI/chrome/android/java/res_chromium"
  "$KIWI/chrome/android/BUILD.gn"
  "$KIWI/extensions/browser/extension_service.cc"
  "$KIWI/extensions/browser/extension_registrar.cc"
  "$KIWI/components/download"
  "$KIWI/components/history"
  "$KIWI/components/bookmarks"
)

for path in "${required[@]}"; do
  if [ ! -e "$path" ]; then
    echo "Missing native Chromium integration point: $path" >&2
    exit 2
  fi
done

legacy_found=0
if grep -RqsE 'android\.webkit\.(WebView|WebChromeClient|WebViewClient)' "$ROOT/app/src/main/java" 2>/dev/null; then
  echo "ERROR: Android System WebView is still present in 55 runtime code." >&2
  legacy_found=1
fi

for path in   "$ROOT/app/src/main/java/com/example/extension"   "$ROOT/app/src/main/java/com/example/ui/plugin/PluginManagerScreen.kt"; do
  if [ -e "$path" ]; then
    echo "ERROR: deleted custom extension/plugin runtime returned: $path" >&2
    legacy_found=1
  fi
done

if grep -RqsE 'ElephantWebBridge|ElephantWebViewClient|ElephantWebChromeClient|registerTabWebView|activeWebView|tabWebViews'   "$ROOT/app/src/main/java" 2>/dev/null; then
  echo "ERROR: 55 WebView browser runtime references are still present." >&2
  legacy_found=1
fi

if [ "$legacy_found" -ne 0 ]; then
  echo "Native cutover is not complete; do not treat the Gradle/WebView app as the final browser." >&2
  exit 3
fi

echo "55 native Chromium cutover verification passed."
