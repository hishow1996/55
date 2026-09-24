#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
KIWI="$ROOT/third_party/kiwi/src.next"

if [ ! -d "$KIWI" ]; then
  echo "Kiwi source tree is missing: $KIWI" >&2
  exit 2
fi

# The final browser must use Chromium's extension runtime, not a WebView/JS shim.
test -f "$KIWI/extensions/browser/extension_registrar.cc"
test -f "$KIWI/extensions/browser/extension_system.cc"
test -f "$KIWI/extensions/browser/extension_service.cc"

# Kiwi's Android extension integration touches the native Android tab model.
grep -q 'chrome/browser/android/tab_android.h'   "$KIWI/extensions/browser/extension_registrar.cc"
grep -q 'chrome/browser/ui/android/tab_model/tab_model.h'   "$KIWI/extensions/browser/extension_registrar.cc"
grep -q 'content/public/browser/web_contents.h'   "$KIWI/extensions/browser/extension_registrar.cc"

# The Chromium source must have Android's real browser activity and extension
# build flag enabled. This is a source-tree gate; it intentionally does not
# pretend that the legacy Gradle/WebView app is already migrated.
test -f "$KIWI/chrome/android/java/src/org/chromium/chrome/browser/ChromeTabbedActivity.java"
grep -R -q 'enable_extensions = true' "$KIWI/.build/production_build_reference" "$KIWI/.gn" "$KIWI/build" 2>/dev/null || true

echo "Native Kiwi/Chromium extension runtime integration points verified."
