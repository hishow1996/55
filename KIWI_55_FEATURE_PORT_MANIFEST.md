# 55 → Kiwi/Chromium Native Feature Port Manifest

This manifest is the cutover checklist for repository 55. The final browser must run from the Kiwi/Chromium Android shell and use Chromium/Blink services instead of Android System WebView.

## Runtime boundary

| 55 feature | Native target | Final status |
|---|---|---|
| Tabs / tab lifecycle | `chrome/android` TabModel / TabModelSelector | provided by Kiwi/Chromium native runtime |
| Address/search bar | Chromium Android toolbar / omnibox | provided by Kiwi/Chromium native runtime |
| Bottom toolbar | Chromium Android toolbar layout/resources | provided by Kiwi/Chromium native runtime |
| Desktop User-Agent | Chromium network/content settings | provided by Kiwi/Chromium native runtime |
| Night mode | Chromium/Kiwi Android theme/content implementation | provided by Kiwi/Chromium native runtime |
| Downloads | Chromium DownloadManager / download UI | provided by Kiwi/Chromium native runtime |
| History | Chromium HistoryService | provided by Kiwi/Chromium native runtime |
| Bookmarks | Chromium BookmarkModel | provided by Kiwi/Chromium native runtime |
| Search overlay | Chromium omnibox/search UI | provided by Kiwi/Chromium native runtime |
| Home page | Chromium NTP/start surface | provided by Kiwi/Chromium native runtime |
| Settings | Chromium Android settings/preferences | provided by Kiwi/Chromium native runtime |
| Translation | Chromium translate stack | provided by Kiwi/Chromium native runtime |
| HTML5 media | Blink/Chromium media pipeline | provided by Kiwi/Chromium native runtime |
| Android PiP | Chromium media/PiP lifecycle | provided by Kiwi/Chromium native runtime |
| Floating video | Chromium media + Android Picture-in-Picture lifecycle | provided by Kiwi/Chromium native runtime |
| File chooser/upload | Chromium Android content/file chooser | provided by Kiwi/Chromium native runtime |
| Permissions | Chromium Android permissions/content settings | provided by Kiwi/Chromium native runtime |
| Cookies/storage | Chromium Profile/Storage services | provided by Kiwi/Chromium native runtime |
| AI UI/network state | Native Android feature surface; no WebView dependency | native overlay hook; provider configuration remains user-supplied |
| Theme/UI | Chromium Android resources/theme | provided by Kiwi/Chromium native runtime |
| Extensions | Chromium native Extensions runtime | integrated as architecture requirement |

## Forbidden final-runtime dependencies

The final browser must not depend on:

- `android.webkit.WebView`
- `WebViewClient` / `WebChromeClient` as the browsing engine
- the deleted `com.example.extension` runtime
- JavaScript `chrome.*` compatibility bridges used to simulate extensions
- the deleted PluginManager screen/runtime

## Local cutover sequence

1. Initialize `third_party/kiwi/src.next` with `scripts/prepare_kiwi_chromium.sh`.
2. Apply the 55 Chromium feature port.
3. Run `scripts/verify_55_native_cutover.sh`.
4. Run `scripts/build_local_chromium.sh`.
5. The expected local artifact is `ElephantBrowser.apk` at repository root.

GitHub Actions are intentionally not part of this workflow.
