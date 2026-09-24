#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
KIWI_DIR="$ROOT/third_party/kiwi/src.next"
DEPOT_TOOLS_DIR="${DEPOT_TOOLS_DIR:-$ROOT/.depot_tools}"
KIWI_REPO="https://github.com/kiwibrowser/src.next.git"

mkdir -p "$ROOT/third_party/kiwi"

# gclient operates from the directory containing .gclient. Keep that metadata
# outside the Chromium source so the source remains a clean git submodule.
GCLIENT_ROOT="$ROOT/third_party/kiwi"
if [ ! -f "$GCLIENT_ROOT/.gclient" ]; then
  cat > "$GCLIENT_ROOT/.gclient" <<EOF
solutions = [
  {
    "name": "src.next",
    "url": "$KIWI_REPO",
    "managed": False,
    "custom_deps": {},
    "custom_vars": {},
  },
]
EOF
fi

# Prefer the repository gitlink for reproducible local builds.
if git -C "$ROOT" submodule status -- third_party/kiwi/src.next >/dev/null 2>&1; then
  git -C "$ROOT" submodule update --init --recursive -- third_party/kiwi/src.next
fi

if [ ! -d "$DEPOT_TOOLS_DIR/.git" ]; then
  git clone https://chromium.googlesource.com/chromium/tools/depot_tools.git "$DEPOT_TOOLS_DIR"
fi
export PATH="$DEPOT_TOOLS_DIR:$PATH"

# Fallback only when the checkout was exported without its gitlink.
if [ ! -d "$KIWI_DIR/.git" ]; then
  if [ -e "$KIWI_DIR" ] && [ "$(find "$KIWI_DIR" -mindepth 1 -maxdepth 1 | head -n 1)" != "" ]; then
    echo "Kiwi source directory exists but is not a git checkout/submodule: $KIWI_DIR" >&2
    exit 5
  fi
  git clone --branch kiwi --depth 1 "$KIWI_REPO" "$KIWI_DIR"
fi

cd "$GCLIENT_ROOT"
gclient sync --nohooks
cd "$KIWI_DIR"
gclient runhooks

test -f extensions/browser/extension_registrar.cc
test -f extensions/browser/extension_system.cc
test -f extensions/browser/extension_service.cc
test -f chrome/android/java/src/org/chromium/chrome/browser/ChromeTabbedActivity.java

echo "Kiwi/Chromium source prepared and native extension runtime verified."
echo "Next step: scripts/build_local_chromium.sh"
