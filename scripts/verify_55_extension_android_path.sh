#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
KIWI="$ROOT/third_party/kiwi/src.next"

fail() { echo "ERROR: $*" >&2; exit 1; }

# Android extension support is not just the core ExtensionService. The APK
# must also contain the Android bridges used by extension actions/popups and
# the native install path. These checks keep a partially enabled desktop-only
# runtime from reaching the local build.
required=(
  "$KIWI/chrome/browser/extensions/extension_service.cc"
  "$KIWI/chrome/browser/extensions/extension_system.cc"
  "$KIWI/chrome/browser/extensions/extension_registrar.cc"
  "$KIWI/chrome/browser/ui/android/extensions"
  "$KIWI/chrome/browser/ui/android/toolbar/extensions"
  "$KIWI/chrome/browser/extensions/webstore_inline_installer.cc"
  "$KIWI/chrome/browser/extensions/extension_install_prompt.cc"
)

for path in "${required[@]}"; do
  test -e "$path" || fail "Android extension integration point is missing: $path"
done

# Verify that Android extension actions have a native bridge and that the
# Extensions WebUI is part of the Chromium resource tree.
find "$KIWI/chrome/browser/ui/android/extensions" -type f \( -name '*.cc' -o -name '*.h' -o -name '*.java' \) | grep -q . ||
  fail "Android extension action bridge sources are missing."

grep -RqsE 'ExtensionActionsBridge|extension_actions_bridge' "$KIWI/chrome/browser/ui/android/extensions" "$KIWI/chrome/browser/ui/android/toolbar/extensions" ||
  fail "Android extension action bridge is not wired."

grep -RqsE 'extensions.html|ExtensionsUI|ExtensionService' "$KIWI/chrome/browser/resources/extensions" "$KIWI/chrome/browser/ui/webui" ||
  fail "Native Extensions WebUI is not wired."

# The install path must remain Chromium-native; reject the old repo-55 shim.
if grep -RqsE 'KiwiExtensionApi|ExtensionManager|PluginManagerScreen|ElephantWebBridge|ElephantWebView' "$KIWI/chrome/browser/extensions" "$KIWI/chrome/browser/ui/android/extensions" 2>/dev/null; then
  fail "Legacy repo-55 extension runtime detected in Chromium extension code."
fi

echo "Native Android extension install/action path verified."
