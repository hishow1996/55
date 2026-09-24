# 55 → Kiwi Chromium feature migration map

This branch uses Kiwi/Chromium as the browser engine and Android browser shell. The old
Android System WebView application is retained only as the source of feature behavior while
each feature is migrated into the Chromium Android stack.

Reference baseline: https://github.com/kiwibrowser/src.next

## Migration rules

1. Do not recreate a WebView browser inside Chromium.
2. Reuse Chromium/Chrome Android primitives wherever they already implement the feature.
3. Port 55-specific behavior into Chromium Java/C++/resources/BUILD.gn integration points.
4. Do not restore `com.example.extension`, `ExtensionManager`, or a JavaScript `chrome.*` shim.
5. Do not use Gradle dependencies as a substitute for Chromium native functionality.
6. The final APK is built by GN/Ninja, not the old `:app` Gradle application.

## 55 feature inventory

| 55 feature | Chromium/Kiwi integration direction |
|---|---|
| Tabs / tab switching | Chrome Android tab model, TabModelSelector, tab switcher |
| Address/search bar | Chrome Android toolbar/location bar; preserve 55 bottom-toolbar behavior where required |
| Desktop User-Agent | Chromium Android network request/user-agent override path |
| Night mode | Kiwi-style Chromium Android night-mode implementation |
| Downloads | Chromium DownloadManager / Android download UI and native download pipeline |
| History | Chromium HistoryService / Android history UI |
| Bookmarks | Chromium BookmarkModel / Android bookmark UI |
| Search overlay | Chromium Android omnibox/search UI integration |
| Home page | Chrome Android NTP/start surface, with 55-specific content layered on top |
| Settings | Chrome Android Settings + 55-specific preferences |
| Translation | Chromium translate stack and Android translate UI |
| Video playback | Chromium/Blink media pipeline; no WebView Media3 bridge |
| Picture-in-picture | Android Chrome media/PiP integration |
| Floating player | Chromium tab/media integration plus Android PiP/overlay lifecycle |
| File upload/download | Chromium native content/file chooser and download pipeline |
| Permissions | Chromium Android permission/content-settings systems |
| Cookies/storage | Chromium profile/storage services |
| AI features | 55 UI retained, networking/state moved into Chromium-compatible Android components |
| Theme/UI | Chromium Android resources/theme system |
| Plugin/extension system | **Chromium native Extensions runtime only** |

## Kiwi-derived UI/branding conventions

The old Kiwi source documents browser branding in
`chrome/android/java/res_chromium/values/channel_constants.xml`, Android mipmap
resources and the Android manifest. The migration therefore treats Chromium's Android
resources as the authoritative branding layer rather than the old Gradle manifest.

## Build contract

A successful local build must:

- prepare the Kiwi Chromium source;
- enable Chromium native extensions;
- verify no legacy 55 WebView extension runtime exists;
- apply the 55 Chromium overlay;
- generate GN files;
- build `chrome_public_apk`;
- copy the resulting APK to `ElephantBrowser.apk`.

The overlay script is deliberately fail-fast: if a required Chromium integration point is
missing, it stops instead of silently producing a partial browser.
