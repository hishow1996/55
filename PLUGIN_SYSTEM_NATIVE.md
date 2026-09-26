# Repo 55 native extension system

Repo 55 now treats Chromium's extension runtime as the plugin system.

## Runtime

The final Android APK is built from the Kiwi/Chromium source tree in
`third_party/kiwi/src.next`. Extension lifecycle, content scripts, background
pages/service workers, permissions, storage, messaging and extension UI are
owned by Chromium's native Extensions subsystem.

The deleted Kotlin/WebView implementation is intentionally not replaced by
another compatibility layer.

## User-facing extension management

The browser exposes Chromium/Kiwi's native extension management surface
(`chrome://extensions`) rather than the old Compose `PluginManagerScreen`.
Installed extensions are managed by Chromium's native ExtensionService and
ExtensionRegistry.

The Android extension path also includes the native extension-action/toolbar
bridge and Chromium's native install prompt/installer path. The build runs
`scripts/verify_55_extension_android_path.sh` before Ninja so a desktop-only
extension runtime cannot be mistaken for complete Android support.

## Build requirements

The local build must contain:

- `enable_extensions = true`
- `enable_plugins = true`
- `extensions/browser/extension_service.cc`
- `extensions/browser/extension_registrar.cc`
- `extensions/browser/extension_system.cc`
- Chromium Android `ChromeTabbedActivity`
- Chromium Android extension action/toolbar sources
- Chromium native extension installer/prompt sources
- Chromium Extensions WebUI resources

Use:

```
scripts/prepare_kiwi_chromium.sh
bash scripts/preflight_local_chromium.sh
scripts/configure_kiwi_extensions.sh third_party/kiwi/src.next/out/android_arm64
scripts/port_55_native_features.sh
scripts/verify_55_native_java_api.sh
scripts/verify_kiwi_extension_runtime.sh
scripts/verify_55_native_cutover.sh
scripts/validate_55_native_migration.sh
scripts/verify_55_extension_android_path.sh
scripts/build_local_chromium.sh
```

The last command performs GN generation and builds `chrome_public_apk`.

## Migration boundary

The repo-55 application module remains source material for feature parity.
It is not the runtime of the final browser APK.

No `android.webkit.WebView`, `ExtensionManager`, `KiwiExtensionApi`,
or custom JavaScript extension bridge may be copied into the Chromium runtime.

## Management contract

The management layer deliberately delegates lifecycle operations to Chromium's
native `ExtensionService`/`ExtensionRegistry` rather than maintaining a
second extension database.

For repo 55, the browser UI treats `chrome://extensions` as the source of
truth. A native Android toolbar surface may mirror ExtensionAction/toolbar
state, but it must not duplicate extension state or permissions.

Acceptance criteria:

1. An installed extension survives browser restart through Chromium profile
   storage.
2. Disable/enable changes are reflected by Chromium's ExtensionService.
3. Uninstall removes the extension from the native registry and its profile
   state.
4. Extension permissions are read from the native manifest/permission system.
5. Content scripts and background/service-worker execution remain native.
6. Android extension actions/popups use Chromium's native Android bridge.
7. Extension installation uses Chromium's native installer/prompt path.
8. No Kotlin `ExtensionManager` or JavaScript compatibility API is introduced.

## Native management surface

Kiwi's Chromium integration exposes the extension manager through the native
Extensions WebUI. The final APK therefore does not need a Compose/WebView
plugin manager. The page is backed by Chromium's extension resources and the
native ExtensionService/ExtensionRegistry lifecycle.

The local build verifier requires the Chromium extension resources and Android
extension bridge before GN/Ninja compilation proceeds. No second Kotlin/WebView
manager is introduced.

GitHub Actions are intentionally not part of the local build workflow.
