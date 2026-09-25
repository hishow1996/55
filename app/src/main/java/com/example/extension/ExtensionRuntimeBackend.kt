package com.example.extension

/**
 * Runtime boundary for browser extensions.
 *
 * The browser UI and extension installation code must not depend on a particular
 * JavaScript execution engine. A real Chromium build can provide the native
 * backend; the current Android System WebView build uses the compatibility backend.
 */
interface ExtensionRuntimeBackend {
    val kind: Kind
    val supportsNativeChromiumApis: Boolean

    /**
     * Converts an extension-relative resource into the URL namespace exposed
     * by this runtime. The WebView backend uses file:// today; a Chromium
     * backend will return chrome-extension://<id>/... without changing UI code.
     */
    fun resourceUrl(extension: BrowserExtension, relativePath: String): String

    enum class Kind {
        WEBVIEW_COMPATIBILITY,
        CHROMIUM_NATIVE
    }
}

/**
 * Runtime descriptor exposed to the browser UI and diagnostics.
 */
data class ExtensionRuntimeDescriptor(
    val kind: ExtensionRuntimeBackend.Kind,
    val supportsNativeChromiumApis: Boolean,
    val supportsManifestV2: Boolean,
    val supportsManifestV3: Boolean
)

/**
 * Current repo-55 runtime descriptor.
 *
 * Keep this explicit instead of pretending the WebView JavaScript bridge is the
 * Chromium ExtensionService/ExtensionHost stack. That distinction is important
 * when we eventually switch the browser engine to a bundled Chromium build.
 */
object CurrentExtensionRuntime : ExtensionRuntimeBackend {
    override val kind = ExtensionRuntimeBackend.Kind.WEBVIEW_COMPATIBILITY
    override val supportsNativeChromiumApis = false

    override fun resourceUrl(extension: BrowserExtension, relativePath: String): String {
        val root = java.io.File(extension.rootPath).canonicalFile
        val target = java.io.File(root, relativePath).canonicalFile
        require(target.path == root.path || target.path.startsWith(root.path + java.io.File.separator)) {
            "非法扩展资源路径"
        }
        return "file://" + target.absolutePath
    }

    val descriptor = ExtensionRuntimeDescriptor(
        kind = kind,
        supportsNativeChromiumApis = supportsNativeChromiumApis,
        supportsManifestV2 = true,
        supportsManifestV3 = true
    )
}
