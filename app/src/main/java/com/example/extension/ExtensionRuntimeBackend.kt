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
    val supportsExtensionScheme: Boolean

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
    val supportsExtensionScheme: Boolean,
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
    override val supportsExtensionScheme = false

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
        supportsExtensionScheme = supportsExtensionScheme,
        supportsManifestV2 = true,
        supportsManifestV3 = true
    )
}

/**
 * WebView compatibility runtime. Background WebViews are created here rather
 * than inside ExtensionManager, leaving the lifecycle boundary replaceable by
 * a Chromium-native runtime later.
 */
class WebViewExtensionRuntime(
    private val context: android.content.Context
) : ExtensionRuntimeBackend {
    override val kind = ExtensionRuntimeBackend.Kind.WEBVIEW_COMPATIBILITY
    override val supportsNativeChromiumApis = false
    override val supportsExtensionScheme = false

    override fun resourceUrl(extension: BrowserExtension, relativePath: String): String {
        val root = java.io.File(extension.rootPath).canonicalFile
        val target = java.io.File(root, relativePath).canonicalFile
        require(target.path == root.path || target.path.startsWith(root.path + java.io.File.separator)) {
            "非法扩展资源路径"
        }
        return "file://" + target.absolutePath
    }

    fun startBackground(
        extension: BrowserExtension,
        bridge: Any
    ): ExtensionBackgroundHost? {
        val worker = extension.manifest.serviceWorker
            ?: extension.manifest.backgroundScripts.firstOrNull()
            ?: return null
        val root = java.io.File(extension.rootPath).canonicalFile
        val file = java.io.File(root, worker).canonicalFile
        if (!file.exists() || !file.isFile ||
            !(file.path == root.path || file.path.startsWith(root.path + java.io.File.separator))) return null

        val host = createWebViewBackgroundHost(context)
        host.addJavascriptInterface(bridge, "ElephantExtensionBridge")
        val polyfill = KiwiExtensionApi.background(
            org.json.JSONObject.quote(extension.id),
            org.json.JSONObject.quote("file://" + extension.rootPath + "/")
        )
        host.loadDataWithBaseURL(
            resourceUrl(extension, ""),
            "<html><script>" + polyfill + file.readText() + "</script></html>"
        )
        return host
    }

    companion object {
        val descriptor = ExtensionRuntimeDescriptor(
            kind = ExtensionRuntimeBackend.Kind.WEBVIEW_COMPATIBILITY,
            supportsNativeChromiumApis = false,
            supportsExtensionScheme = false,
            supportsManifestV2 = true,
            supportsManifestV3 = true
        )
    }
}
