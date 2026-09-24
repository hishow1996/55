#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
KIWI="$ROOT/third_party/kiwi/src.next"
DEPOT_TOOLS="${DEPOT_TOOLS_DIR:-$ROOT/.depot_tools}"

if [ ! -d "$KIWI/.git" ]; then
  git -C "$ROOT" submodule update --init --recursive third_party/kiwi/src.next
fi
if [ ! -d "$DEPOT_TOOLS/.git" ]; then
  git clone https://chromium.googlesource.com/chromium/tools/depot_tools.git "$DEPOT_TOOLS"
fi
export PATH="$DEPOT_TOOLS:$PATH"
cd "$KIWI"
gclient sync
mkdir -p out/android_arm64
if [ ! -f out/android_arm64/args.gn ]; then
  cat > out/android_arm64/args.gn <<'EOF'
target_os = "android"
target_cpu = "arm64"
is_debug = true
enable_extensions = true
is_component_build = false
EOF
fi
gn gen out/android_arm64
ninja -C out/android_arm64 chrome_public_apk
printf '\nAPK: %s/out/android_arm64/apks/ChromePublic.apk\n' "$KIWI"
