#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
KIWI="$ROOT/third_party/kiwi/src.next"

fail() { echo "ERROR: $*" >&2; exit 1; }

test -d "$KIWI/chrome/browser/resources/extensions" ||
  fail "Native Chromium extension management resources are missing."

# Kiwi exposes its extension manager as a native WebUI. Accept the Chromium
# chrome://extensions surface as well because newer Chromium revisions rename
# the internal URL while retaining the same native Extensions WebUI.
if ! grep -RqsE 'kiwi://extensions|chrome://extensions'     "$KIWI/chrome" "$KIWI/components" "$KIWI/extensions" 2>/dev/null; then
  fail "No native extension-management URL was found."
fi

# The management page must be backed by Chromium WebUI/resources, not the old
# repo-55 Compose/WebView plugin implementation.
if grep -RqsE 'com\.example\.extension|KiwiExtensionApi|PluginManagerScreen|ExtensionManager'     "$KIWI/chrome/browser/resources/extensions" 2>/dev/null; then
  fail "Legacy repo-55 extension UI was copied into Chromium."
fi

# Native extension build targets must be present.
grep -RqsE 'extensions/browser|ENABLE_EXTENSIONS|enable_extensions'   "$KIWI/chrome" "$KIWI/extensions" || fail "Native extension build integration missing."

echo "Native Kiwi/Chromium extension management UI verified."
