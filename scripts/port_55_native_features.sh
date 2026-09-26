#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
KIWI="$ROOT/third_party/kiwi/src.next"
JAVA_ROOT="$KIWI/chrome/android/java/src/org/chromium/chrome/browser"
JAVA_LIST="$KIWI/chrome/android/java_sources.gni"
TAB_ACTIVITY="$JAVA_ROOT/ChromeTabbedActivity.java"

test -d "$KIWI/chrome/android" || { echo "Kiwi Chromium source is not prepared: $KIWI" >&2; exit 2; }
mkdir -p "$JAVA_ROOT"

# Install the repo-55 native preference/controller classes into the Chromium
# Android source tree. These are the only repo-55 runtime classes copied into
# the final Chromium APK; the old Gradle/WebView runtime is never packaged.
# The top-level java_sources.gni is the Kiwi integration point that appends
# additional sources to chrome_java_sources.
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
        raise SystemExit("Cannot find stable chrome_java_sources.gni insertion marker")
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

settings_call = "        Elephant55NativeSettings.ensureDefaults(this);
        Elephant55NativeSettings.applyKiwiUiDefaults();"
if settings_call not in s:
    compositor = "        super.initializeCompositor();"
    if compositor in s:
        s = s.replace(compositor, compositor + "\n\n" + settings_call, 1)
    else:
        raise SystemExit("Cannot find initializeCompositor anchor")

controller_call = "        Elephant55NativeFeatureController.applyToTab(this, getActivityTab());"
if controller_call not in s:
    # Prefer the native lifecycle hook. Fall back to onResume if this Kiwi
    # revision does not expose onResumeWithNative.
    pattern = r"(protected void onResumeWithNative\(\)\s*\{\s*\n)(\s*super\.onResumeWithNative\(\);)"
    m = re.search(pattern, s)
    if m:
        s = s[:m.end()] + "\n" + controller_call + s[m.end():]
    else:
        pattern = r"(protected void onResume\(\)\s*\{\s*\n)(\s*super\.onResume\(\);)"
        m = re.search(pattern, s)
        if m:
            s = s[:m.end()] + "\n" + controller_call + s[m.end():]
        else:
            raise SystemExit("Cannot find a Chromium resume lifecycle hook")

p.write_text(s)
PY

grep -Fqx '    "java/src/org/chromium/chrome/browser/Elephant55NativeSettings.java",' "$JAVA_LIST"
grep -Fqx '    "java/src/org/chromium/chrome/browser/Elephant55NativeFeatureController.java",' "$JAVA_LIST"
grep -Fq "Elephant55NativeSettings.ensureDefaults(this);" "$TAB_ACTIVITY"
grep -Fq "Elephant55NativeFeatureController.applyToTab(this, getActivityTab());" "$TAB_ACTIVITY"

echo "55 native Chromium settings, desktop-UA integration, and lifecycle wiring installed."
