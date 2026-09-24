#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
KIWI_DIR="$ROOT/third_party/kiwi/src.next"
DEPOT_TOOLS_DIR="${DEPOT_TOOLS_DIR:-$ROOT/.depot_tools}"
KIWI_REPO="https://github.com/kiwibrowser/src.next.git"

mkdir -p "$ROOT/third_party/kiwi"

if [ ! -d "$DEPOT_TOOLS_DIR/.git" ]; then
  git clone https://chromium.googlesource.com/chromium/tools/depot_tools.git "$DEPOT_TOOLS_DIR"
fi
export PATH="$DEPOT_TOOLS_DIR:$PATH"

if [ ! -d "$KIWI_DIR/.git" ]; then
  git clone --branch kiwi --depth 1 "$KIWI_REPO" "$KIWI_DIR"
else
  git -C "$KIWI_DIR" fetch --all --tags
  git -C "$KIWI_DIR" checkout kiwi
  git -C "$KIWI_DIR" pull --ff-only
fi

cd "$KIWI_DIR"
gclient sync

# Validate the actual native Chromium extension runtime before the 55 overlay
# is considered ready for a local build.
test -f extensions/browser/extension_registrar.cc
test -f extensions/browser/extension_system.cc
test -f extensions/browser/extension_service.cc
test -f chrome/android/java/src/org/chromium/chrome/browser/ChromeTabbedActivity.java

echo "Kiwi/Chromium source prepared and native extension runtime verified."
echo "Next step: scripts/build_local_chromium.sh"
