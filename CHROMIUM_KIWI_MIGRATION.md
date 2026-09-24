# 55 → Kiwi/Chromium native extension runtime migration

This branch is the native-runtime migration branch for repository 55.

Target architecture:

55 browser UI/features → Chromium Android browser shell → Blink/V8 → Chromium Extensions runtime → Kiwi native integration

The existing Android System WebView + Kotlin ExtensionManager + JavaScript chrome.* compatibility layer is not the target architecture.

Source baseline: https://github.com/kiwibrowser/src.next

Kiwi Next is a full Chromium source tree and is built with Chromium GN/Ninja rather than the current Android Gradle application model.

The final integration must use Chromium's native extensions runtime. The old simulated extension runtime must not be reintroduced.

GitHub Actions are intentionally not part of the local build workflow.
