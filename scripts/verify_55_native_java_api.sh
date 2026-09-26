#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
KIWI="$ROOT/third_party/kiwi/src.next"
TAB_UTILS="$KIWI/chrome/android/java/src/org/chromium/chrome/browser/tab/TabUtils.java"
CONTROLLER="$ROOT/patches/55/Elephant55NativeFeatureController.java"

fail() { echo "ERROR: $*" >&2; exit 1; }

test -f "$TAB_UTILS" || fail "Chromium TabUtils.java is missing"
test -f "$CONTROLLER" || fail "55 native feature controller is missing"

# Keep the overlay aligned with the actual Chromium API in the Kiwi source
# tree. This prevents a stale helper signature from reaching javac.
grep -Eq 'switchUserAgent[[:space:]]*\(' "$TAB_UTILS" ||
  fail "Chromium TabUtils.switchUserAgent API is missing; update the 55 overlay before building"

grep -Fq 'TabUtils.switchUserAgent(' "$CONTROLLER" ||
  fail "55 native feature controller does not use Chromium TabUtils"

if grep -Fq 'forcedByUser' "$CONTROLLER"; then
  fail "55 controller appears to use a stale 4-argument switchUserAgent call"
fi

if grep -Eq 'android\.webkit\.(WebView|WebChromeClient|WebViewClient)' "$CONTROLLER"; then
  fail "55 native feature controller contains a WebView dependency"
fi

echo "55 native Java API contract passed."
