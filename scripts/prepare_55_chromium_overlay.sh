#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
KIWI="$ROOT/third_party/kiwi/src.next"

required=(
  "$KIWI/chrome/android/java/src/org/chromium/chrome/browser"
  "$KIWI/chrome/android/java/res_chromium"
  "$KIWI/chrome/android/BUILD.gn"
  "$KIWI/extensions/browser/extension_service.cc"
  "$KIWI/components/download"
  "$KIWI/components/history"
  "$KIWI/components/bookmarks"
)

for path in "${required[@]}"; do
  if [ ! -e "$path" ]; then
    echo "Missing required Chromium integration point: $path" >&2
    exit 2
  fi
done

# app/ is retained as source material for feature parity only. The final APK is
# built from Kiwi Chromium; only the native overlay is allowed into its source tree.
if grep -RqsE 'android\.webkit\.(WebView|WebChromeClient|WebViewClient)|ElephantWebBridge|ElephantWebViewClient|ElephantWebChromeClient|com\.example\.extension|ExtensionManager|KiwiExtensionApi|BrowserExtension|PluginManagerScreen' "$KIWI/chrome" 2>/dev/null; then
  echo "ERROR: legacy WebView/plugin runtime was copied into Chromium source." >&2
  exit 3
fi

"$ROOT/scripts/verify_kiwi_extension_runtime.sh"
"$ROOT/scripts/verify_55_native_cutover.sh"

echo "55 Chromium feature overlay prerequisites verified."
echo "Use Kiwi/Chromium native implementations for tabs, downloads, history, bookmarks,"
echo "translation, media/PiP, UA, settings and UI; do not reintroduce WebView bridges."
