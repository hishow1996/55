#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
KIWI="$ROOT/third_party/kiwi/src.next"

"$ROOT/scripts/prepare_kiwi_chromium.sh"

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

# Keep the old Gradle/WebView application as source material until each feature has a
# native Chromium implementation. Never copy its WebView runtime into the final browser.
if grep -RqsE 'android\.webkit\.(WebView|WebChromeClient|WebViewClient)'   "$ROOT/app/src/main/java" 2>/dev/null; then
  echo "ERROR: 55 source still contains WebView feature code; native Chromium cutover is incomplete." >&2
  exit 3
fi

# The extension runtime is an explicit hard requirement.
"$ROOT/scripts/verify_kiwi_extension_runtime.sh"
"$ROOT/scripts/verify_55_native_cutover.sh"

echo "55 Chromium feature overlay prerequisites verified."
echo "Use Kiwi/Chromium native implementations for tabs, downloads, history, bookmarks,"
echo "translation, media/PiP, UA, settings and UI; do not reintroduce WebView bridges."
