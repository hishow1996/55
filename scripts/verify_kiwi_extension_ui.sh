#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
KIWI="$ROOT/third_party/kiwi/src.next"

fail() { echo "ERROR: $*" >&2; exit 1; }

RES="$KIWI/chrome/browser/resources/extensions"
test -d "$RES" || fail "Native Chromium extension management resources are missing."

# Kiwi's documented native extension page is kiwi://extensions. Chromium's
# current implementation can expose the same WebUI through chrome://extensions,
# so the verifier checks the actual native resource implementation rather than
# requiring a URL literal in a particular C++ file.
for path in   "$RES/extensions.html"   "$RES/manager.ts"   "$RES/item_list.ts"   "$RES/detail_view.ts"; do
  test -f "$path" || fail "Native extension-management resource missing: $path"
done

# The management page must be backed by Chromium WebUI/resources, not the old
# repo-55 Compose/WebView plugin implementation.
if grep -RqsE 'com\.example\.extension|KiwiExtensionApi|PluginManagerScreen|ExtensionManager' "$RES" 2>/dev/null; then
  fail "Legacy repo-55 extension UI was copied into Chromium."
fi

# Native extension build targets must be present.
grep -RqsE 'extensions/browser|ENABLE_EXTENSIONS|enable_extensions'   "$KIWI/chrome" "$KIWI/extensions" ||
  fail "Native extension build integration missing."

echo "Native Kiwi/Chromium extension management UI verified."
