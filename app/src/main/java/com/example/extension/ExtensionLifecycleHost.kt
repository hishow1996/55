package com.example.extension

/**
 * Owns extension background execution lifetime.
 *
 * The manager only asks the lifecycle host to start/stop an extension. The
 * concrete host can therefore be replaced by Chromium's service-worker /
 * ExtensionService lifecycle later.
 */
interface ExtensionLifecycleHost {
    fun start(extension: BrowserExtension): Boolean
    fun stop(extensionId: String): Boolean
    fun isRunning(extensionId: String): Boolean
}

class RuntimeExtensionLifecycleHost(
    private val runtime: ExtensionRuntimeBackend,
    private val bridgeFactory: (BrowserExtension) -> Any
) : ExtensionLifecycleHost {
    private val hosts = mutableMapOf<String, ExtensionBackgroundHost>()

    fun backgroundHosts(): Map<String, ExtensionBackgroundHost> = hosts

    override fun start(extension: BrowserExtension): Boolean {
        if (!extension.enabled || hosts.containsKey(extension.id)) return false
        val host = runtime.createBackgroundHost(extension, bridgeFactory(extension)) ?: return false
        hosts[extension.id] = host
        return true
    }

    override fun stop(extensionId: String): Boolean =
        hosts.remove(extensionId)?.let { it.destroy(); true } ?: false

    override fun isRunning(extensionId: String): Boolean = hosts.containsKey(extensionId)
}

