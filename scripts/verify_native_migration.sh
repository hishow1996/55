#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
KIWI="$ROOT/third_party/kiwi/src.next"

"$ROOT/scripts/prepare_kiwi_chromium.sh"

test -d "$KIWI/chrome"
test -d "$KIWI/extensions"

# This is deliberately a source-tree migration gate, not a WebView compatibility shim.
# The 55 Android UI must be ported into Chromium's chrome/android stack before the
# legacy Gradle/WebView application is removed.
required=(
  "chrome/android/java/src/org/chromium/chrome/browser/ChromeTabbedActivity.java"
  "chrome/android/java/res"
  "chrome/android/BUILD.gn"
)
for p in "${required[@]}"; do
  if [ ! -e "$KIWI/$p" ]; then
    echo "Missing Chromium integration point: $p" >&2
    exit 2
  fi
done

echo "Kiwi native Chromium source is ready for 55 feature integration."
echo "Legacy WebView extension runtime must not be used by the final APK."
