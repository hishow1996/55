#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
KIWI="$ROOT/third_party/kiwi/src.next"
DEPOT_TOOLS="${DEPOT_TOOLS_DIR:-$ROOT/.depot_tools}"

fail() { echo "ERROR: $*" >&2; exit 1; }
need_cmd() { command -v "$1" >/dev/null 2>&1 || fail "Required command not found: $1"; }

# Local Chromium build preflight. Fail before GN/Ninja when the checkout or toolchain is incomplete.
need_cmd git
need_cmd python3
need_cmd bash

test -d "$KIWI" || fail "Kiwi source tree is missing: $KIWI"
if [ ! -f "$KIWI/CHROMIUM_VERSION" ] && [ ! -f "$KIWI/VERSION" ]; then
  fail "Kiwi Chromium version metadata is missing (expected CHROMIUM_VERSION or VERSION)"
fi
test -f "$KIWI/DEPS" || fail "Chromium DEPS file is missing"
test -d "$KIWI/chrome/android" || fail "Chromium Android source is missing"
test -d "$KIWI/extensions" || fail "Chromium Extensions source is missing"

if [ ! -x "$DEPOT_TOOLS/gclient" ] && ! command -v gclient >/dev/null 2>&1; then
  fail "depot_tools/gclient is missing. Set DEPOT_TOOLS_DIR or install depot_tools."
fi

if [ -d "$DEPOT_TOOLS" ]; then
  export PATH="$DEPOT_TOOLS:$PATH"
fi

need_cmd gclient
need_cmd gn
need_cmd autoninja
need_cmd java
need_cmd javac

JAVA_VERSION="$(java -version 2>&1 | head -n 1 || true)"
JAVAC_VERSION="$(javac -version 2>&1 | head -n 1 || true)"

if [ -f "$KIWI/KIWI_VERSION" ]; then
  echo "Kiwi version metadata:"
  sed -n "1,6p" "$KIWI/KIWI_VERSION"
fi
if [ -f "$KIWI/VERSION" ]; then
  echo "Chromium version metadata:"
  sed -n "1,6p" "$KIWI/VERSION"
fi

test -f "$KIWI/extensions/browser/extension_service.cc" || fail "Chromium ExtensionService is missing"
test -f "$KIWI/extensions/browser/extension_registrar.cc" || fail "Chromium ExtensionRegistrar is missing"
test -f "$KIWI/extensions/browser/extension_system.cc" || fail "Chromium ExtensionSystem is missing"
test -f "$KIWI/chrome/browser/resources/extensions/extensions.html" || fail "Chromium Extensions WebUI is missing"

echo "Local Chromium preflight passed."
echo "java: $JAVA_VERSION"
echo "javac: $JAVAC_VERSION"
echo "gn: $(gn --version)"
echo "ninja: $(ninja --version 2>/dev/null || true)"
