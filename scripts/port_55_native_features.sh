#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
KIWI="$ROOT/third_party/kiwi/src.next"
JAVA_ROOT="$KIWI/chrome/android/java/src/org/chromium/chrome/browser"
JAVA_LIST="$KIWI/chrome/android/java_sources.gni"
TAB_ACTIVITY="$JAVA_ROOT/ChromeTabbedActivity.java"

test -d "$KIWI/chrome/android" || { echo "Kiwi Chromium source is not prepared: $KIWI" >&2; exit 2; }
mkdir -p "$JAVA_ROOT"

cp "$ROOT/patches/55/Elephant55NativeSettings.java" "$JAVA_ROOT/Elephant55NativeSettings.java"
cp "$ROOT/patches/55/Elephant55NativeFeatureController.java" "$JAVA_ROOT/Elephant55NativeFeatureController.java"

python3 - "$JAVA_LIST" <<'PY'
from pathlib import Path
import sys

p = Path(sys.argv[1])
s = p.read_text()
entries = [
    '    "java/src/org/chromium/chrome/browser/Elephant55NativeSettings.java",',
    '    "java/src/org/chromium/chrome/browser/Elephant55NativeFeatureController.java",',
]
for entry in entries:
    if entry in s:
        continue
    marker = 'chrome_java_sources += public_autofill_assistant_java_sources'
    if marker not in s:
        marker = 'if (enable_vr) {'
    if marker not in s:
        raise SystemExit("Cannot find stable chrome_java_sources insertion marker")
    block = "chrome_java_sources += [\n" + entry + "\n]\n\n"
    s = s.replace(marker, block + marker, 1)
p.write_text(s)
PY

python3 - "$TAB_ACTIVITY" <<'PY'
from pathlib import Path
import re
import sys

p = Path(sys.argv[1])
s = p.read_text()

settings_import = "import org.chromium.chrome.browser.Elephant55NativeSettings;"
controller_import = "import org.chromium.chrome.browser.Elephant55NativeFeatureController;"
anchor = "import org.chromium.chrome.browser.IntentHandler.IntentHandlerDelegate;"
if settings_import not in s:
    if anchor not in s:
        raise SystemExit("Cannot find ChromeTabbedActivity import anchor")
    s = s.replace(anchor, settings_import + "\n" + controller_import + "\n" + anchor, 1)

settings_call = "\n".join([
    "        Elephant55NativeSettings.ensureDefaults(this);",
    "        Elephant55NativeSettings.applyKiwiUiDefaults(this);",
])
if settings_call not in s:
    pattern = r"(\s*super\.onCreate\(savedInstanceState\);)"
    m = re.search(pattern, s)
    if not m:
        pattern = r"(\s*super\.onCreate\([^\n]*\);)"
        m = re.search(pattern, s)
    if not m:
        raise SystemExit("Cannot find ChromeTabbedActivity onCreate lifecycle anchor")
    s = s[:m.end()] + "\n\n" + settings_call + s[m.end():]

controller_call = "        Elephant55NativeFeatureController.applyToTab(this, getActivityTab());"
if controller_call not in s:
    pattern = r"(\s*super\.onResume\(\);)"
    m = re.search(pattern, s)
    if not m:
        raise SystemExit("Cannot find ChromeTabbedActivity onResume lifecycle anchor")
    s = s[:m.end()] + "\n" + controller_call + s[m.end():]

p.write_text(s)
PY

grep -Fqx '    "java/src/org/chromium/chrome/browser/Elephant55NativeSettings.java",' "$JAVA_LIST"
grep -Fqx '    "java/src/org/chromium/chrome/browser/Elephant55NativeFeatureController.java",' "$JAVA_LIST"
grep -Fq "Elephant55NativeSettings.ensureDefaults(this);" "$TAB_ACTIVITY"
grep -Fq "Elephant55NativeSettings.applyKiwiUiDefaults(this);" "$TAB_ACTIVITY"
grep -Fq "Elephant55NativeFeatureController.applyToTab(this, getActivityTab());" "$TAB_ACTIVITY"

echo "55 native Chromium settings, desktop-UA integration, and lifecycle wiring installed."
