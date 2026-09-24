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

mkdir -p out/android_arm64
cat > out/android_arm64/args.gn <<'EOF'
target_os = "android"
target_cpu = "arm64"
is_debug = true
enable_extensions = true
is_component_build = false
EOF

gn gen out/android_arm64
ninja -C out/android_arm64 chrome_public_apk

APK="$KIWI/out/android_arm64/apks/ChromePublic.apk"
if [ ! -f "$APK" ]; then
  echo "Build completed without the expected APK: $APK" >&2
  exit 1
fi

printf "\nAPK: %s\n" "$APK"
cp -f "$APK" "$ROOT/$OUTPUT_NAME"
printf "Local APK copy: %s\\n" "$ROOT/$OUTPUT_NAME"
