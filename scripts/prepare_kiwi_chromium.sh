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

echo "Kiwi/Chromium source prepared at: $KIWI_DIR"
echo "Next step: scripts/build_local_chromium.sh"
