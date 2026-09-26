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
ExtensionsRegistry.

## Build requirements

The local build must contain:

- `enable_extensions = true`
- `enable_plugins = true`
- `extensions/browser/extension_service.cc`
- `extensions/browser/extension_registrar.cc`
- `extensions/browser/extension_system.cc`
- Chromium Android `ChromeTabbedActivity`

Use:

```
scripts/prepare_kiwi_chromium.sh
scripts/configure_kiwi_extensions.sh third_party/kiwi/src.next/out/android_arm64
scripts/port_55_native_features.sh
scripts/verify_kiwi_extension_runtime.sh
scripts/verify_55_native_cutover.sh
scripts/validate_55_native_migration.sh
scripts/build_local_chromium.sh
```

The last command performs GN generation and builds `chrome_public_apk`.

## Migration boundary

The repo-55 application module remains source material for feature parity.
It is not the runtime of the final browser APK.

No `android.webkit.WebView`, `ExtensionManager`, `KiwiExtensionApi`,
or custom JavaScript extension bridge may be copied into the Chromium runtime.
