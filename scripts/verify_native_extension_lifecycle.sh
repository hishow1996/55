#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
KIWI="$ROOT/third_party/kiwi/src.next"

fail() { echo "ERROR: $*" >&2; exit 1; }

test -d "$KIWI/extensions" || fail "Chromium extensions tree is missing"

# These are the native lifecycle boundaries repo 55 relies on. Do not replace
# any of them with Kotlin state holders or WebView JavaScript bridges.
required=(
  "$KIWI/extensions/browser/extension_service.cc"
  "$KIWI/extensions/browser/extension_system.cc"
  "$KIWI/extensions/browser/extension_registrar.cc"
  "$KIWI/extensions/browser/extension_registry.cc"
  "$KIWI/extensions/browser/extension_prefs.cc"
  "$KIWI/extensions/browser/extension_host.cc"
  "$KIWI/extensions/browser/extension_host_delegate.cc"
  "$KIWI/extensions/common/extension.cc"
  "$KIWI/extensions/common/manifest.cc"
  "$KIWI/extensions/browser/extension_install_prompt.cc"
)

for path in "${required[@]}"; do
  test -f "$path" || fail "Missing native extension lifecycle component: $path"
done

# Content scripts, background/service-worker execution and messaging must stay
# inside Chromium's extension process/runtime.
find "$KIWI/extensions" -type f \(   -name '*content_script*.cc' -o   -name '*service_worker*.cc' -o   -name '*message_service*.cc' \) -print -quit | grep -q . || fail "Native extension execution/messaging components missing"

# Native management UI is required for the final browser. Different Chromium
# revisions use different resource layouts, so accept either known layout.
if ! { test -d "$KIWI/chrome/browser/resources/extensions" ||        test -d "$KIWI/chrome/browser/resources/extension"; }; then
  fail "Chromium extension management resources missing"
fi

# No custom repo-55 extension runtime may be present in the Chromium tree.
if grep -RqsE 'com\.example\.extension|ExtensionManager|KiwiExtensionApi|PluginManagerScreen|ElephantWebBridge'     "$KIWI/chrome" "$KIWI/extensions" 2>/dev/null; then
  fail "Legacy repo-55 extension runtime detected in Chromium source"
fi

echo "Native extension lifecycle contract passed."
