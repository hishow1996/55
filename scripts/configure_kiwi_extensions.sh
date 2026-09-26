#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
KIWI="$ROOT/third_party/kiwi/src.next"
OUT_DIR="${1:-$KIWI/out/android_arm64}"
ARGS="$OUT_DIR/args.gn"

test -d "$KIWI" || { echo "Kiwi source tree is missing: $KIWI" >&2; exit 2; }
mkdir -p "$OUT_DIR"

# Native extension runtime is a hard build requirement for repo 55.
# Do not replace this with a WebView/JavaScript compatibility layer.
python3 - "$ARGS" <<'PY'
from pathlib import Path
import sys

p = Path(sys.argv[1])
text = p.read_text() if p.exists() else ""

required = {
    "target_os": 'target_os = "android"',
    "target_cpu": 'target_cpu = "arm64"',
    "is_debug": "is_debug = false",
    "is_component_build": "is_component_build = false",
    "is_official_build": "is_official_build = false",
    "enable_extensions": "enable_extensions = true",
    "enable_plugins": "enable_plugins = true",
}

lines = [line for line in text.splitlines() if line.strip()]
for key, line in required.items():
    prefix = key + " "
    lines = [existing for existing in lines if not existing.startswith(prefix)]
    lines.append(line)

p.write_text("\n".join(lines) + "\n")
PY

grep -Fqx 'enable_extensions = true' "$ARGS"
grep -Fqx 'enable_plugins = true' "$ARGS"

# Verify that the actual Chromium extension runtime, not the old 55 plugin
# shim, is present before GN generation.
test -f "$KIWI/extensions/browser/extension_service.cc"
test -f "$KIWI/extensions/browser/extension_registrar.cc"
test -f "$KIWI/extensions/browser/extension_system.cc"

echo "Native extension build configuration ready: $ARGS"
