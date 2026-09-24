# 55 → Kiwi/Chromium Native Feature Port Manifest

This manifest is the cutover checklist for repository 55. The final browser must run from the Kiwi/Chromium Android shell and use Chromium/Blink services instead of Android System WebView.

## Runtime boundary

| 55 feature | Native target | Final status |
|---|---|---|
| Tabs / tab lifecycle | `chrome/android` TabModel / TabModelSelector | pending source port |
| Address/search bar | Chromium Android toolbar / omnibox | pending source port |
| Bottom toolbar | Chromium Android toolbar layout/resources | pending source port |
| Desktop User-Agent | Chromium network/content settings | pending source port |
| Night mode | Chromium/Kiwi Android theme/content implementation | pending source port |
| Downloads | Chromium DownloadManager / download UI | pending source port |
| History | Chromium HistoryService | pending source port |
| Bookmarks | Chromium BookmarkModel | pending source port |
| Search overlay | Chromium omnibox/search UI | pending source port |
| Home page | Chromium NTP/start surface | pending source port |
| Settings | Chromium Android settings/preferences | pending source port |
| Translation | Chromium translate stack | pending source port |
| HTML5 media | Blink/Chromium media pipeline | pending source port |
| Android PiP | Chromium media/PiP lifecycle | pending source port |
| Floating video | Chromium tab/media + Android overlay/PiP lifecycle | pending source port |
| File chooser/upload | Chromium Android content/file chooser | pending source port |
| Permissions | Chromium Android permissions/content settings | pending source port |
| Cookies/storage | Chromium Profile/Storage services | pending source port |
| AI UI/network state | 55 feature UI, Chromium-compatible Android integration | pending source port |
| Theme/UI | Chromium Android resources/theme | pending source port |
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
