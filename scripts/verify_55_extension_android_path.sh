#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
KIWI="$ROOT/third_party/kiwi/src.next"

fail() { echo "ERROR: $*" >&2; exit 1; }

# The Extensions runtime itself lives under extensions/. Android-specific
# integration varies between Chromium/Kiwi revisions, so do not hard-code a
# directory that only exists in one revision.
required=(
  "$KIWI/extensions/browser/extension_service.cc"
  "$KIWI/extensions/browser/extension_system.cc"
  "$KIWI/extensions/browser/extension_registrar.cc"
  "$KIWI/extensions/browser/extension_install_prompt.cc"
  "$KIWI/extensions/browser/webstore_inline_installer.cc"
  "$KIWI/chrome/android"
  "$KIWI/chrome/browser/resources/extensions"
)

for path in "${required[@]}"; do
  test -e "$path" || fail "Native extension integration point is missing: $path"
done

# Chromium revisions place Android extension integration in different source
# directories. Find the actual revision's Android-facing extension sources
# instead of assuming a single historical path.
android_extension_files="$(
  find "$KIWI/chrome/android" "$KIWI/chrome/browser" -type f \
    \( -name '*Extension*.java' -o -name '*Extension*.cc' -o -name '*Extension*.h' \) \
    2>/dev/null | head -n 20
)"
test -n "$android_extension_files" ||
  fail "No Android-facing native extension source was found in the Chromium tree."

# The native management page must be backed by Chromium resources.
test -f "$KIWI/chrome/browser/resources/extensions/extensions.html" ||
  fail "Native Extensions WebUI resources are missing."
find "$KIWI/chrome/browser/resources/extensions" -type f | grep -Eq '/(manager|item_list|detail_view)\.(ts|html)$' ||
  fail "Native Extensions WebUI manager resources are incomplete."

# The install path must remain Chromium-native; reject the old repo-55 shim.
if grep -RqsE 'KiwiExtensionApi|ExtensionManager|PluginManagerScreen|ElephantWebBridge|ElephantWebView' \
    "$KIWI/extensions" "$KIWI/chrome/android" "$KIWI/chrome/browser" 2>/dev/null; then
  fail "Legacy repo-55 extension runtime detected in Chromium source."
fi

echo "Native Android extension install/action path verified."
