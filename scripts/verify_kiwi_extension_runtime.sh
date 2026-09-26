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

# Verify the Android browser shell and the extension build configuration.
test -f "$KIWI/chrome/android/java/src/org/chromium/chrome/browser/ChromeTabbedActivity.java"
if [ -f "$KIWI/out/android_arm64/args.gn" ]; then
  grep -Fqx 'enable_extensions = true' "$KIWI/out/android_arm64/args.gn"
  grep -Fqx 'enable_plugins = true' "$KIWI/out/android_arm64/args.gn"
fi

# Never accept the old repo-55 WebView/JavaScript compatibility runtime.
if grep -RqsE 'com\.example\.extension|ExtensionManager|KiwiExtensionApi|PluginManagerScreen' "$KIWI/chrome" "$KIWI/extensions" 2>/dev/null; then
  echo "ERROR: legacy repo-55 extension shim detected in Chromium source." >&2
  exit 3
fi

echo "Native Kiwi/Chromium extension runtime integration points verified."
