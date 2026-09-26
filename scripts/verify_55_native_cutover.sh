#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
KIWI="$ROOT/third_party/kiwi/src.next"

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
if grep -RqsE 'android\.webkit\.(WebView|WebChromeClient|WebViewClient)|ElephantWebBridge|ElephantWebViewClient|ElephantWebChromeClient|com\.example\.extension|ExtensionManager|KiwiExtensionApi|BrowserExtension|PluginManagerScreen|onOpenPlugins|onOpenPluginManager' "$KIWI/chrome" 2>/dev/null; then
  echo "ERROR: legacy WebView/plugin runtime was copied into Chromium source." >&2
  legacy_found=1
fi

if [ "$legacy_found" -ne 0 ]; then
  echo "Native cutover is not complete." >&2
  exit 3
fi

echo "55 native Chromium cutover verification passed."
