#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
KIWI="$ROOT/third_party/kiwi/src.next"
OUTPUT_NAME="${CHROMIUM_APK_NAME:-ElephantBrowser.apk}"
DEPOT_TOOLS="${DEPOT_TOOLS_DIR:-$ROOT/.depot_tools}"

"$ROOT/scripts/prepare_kiwi_chromium.sh"

if [ ! -d "$KIWI/chrome" ] || [ ! -d "$KIWI/extensions" ]; then
  echo "Kiwi Chromium source is incomplete: chrome/extensions directories are required." >&2
  exit 1
fi

export PATH="$DEPOT_TOOLS:$PATH"
cd "$KIWI"

bash "$ROOT/scripts/preflight_local_chromium.sh"

mkdir -p out/android_arm64

# Install and wire the repo-55 native overlay before GN generation so the
# generated Chromium Java targets include the injected native sources.
"$ROOT/scripts/port_55_native_features.sh"
"$ROOT/scripts/prepare_55_chromium_overlay.sh"
"$ROOT/scripts/verify_55_native_java_api.sh"
"$ROOT/scripts/configure_kiwi_extensions.sh" "$KIWI/out/android_arm64"

gn gen out/android_arm64
GN_ARGS="$(gn args out/android_arm64 --list 2>/dev/null || true)"
echo "$GN_ARGS" | grep -Eq "enable_extensions[[:space:]]*=.*true" || { echo "ERROR: Chromium native Extension Runtime is not enabled." >&2; exit 4; }

"$ROOT/scripts/audit_55_native_features.sh"
"$ROOT/scripts/verify_55_native_cutover.sh"
"$ROOT/scripts/validate_55_native_migration.sh"
"$ROOT/scripts/verify_native_extension_lifecycle.sh"
bash "$ROOT/scripts/verify_kiwi_extension_ui.sh"
bash "$ROOT/scripts/verify_55_extension_android_path.sh"

autoninja -C out/android_arm64 chrome_public_apk

APK="$KIWI/out/android_arm64/apks/ChromePublic.apk"
if [ ! -f "$APK" ]; then
  echo "Build completed without the expected APK: $APK" >&2
  exit 1
fi

printf "\nAPK: %s\n" "$APK"
cp -f "$APK" "$ROOT/$OUTPUT_NAME"
printf "Local APK copy: %s\n" "$ROOT/$OUTPUT_NAME"
