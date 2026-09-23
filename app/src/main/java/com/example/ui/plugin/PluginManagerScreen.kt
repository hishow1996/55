package com.example.ui.plugin

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.TextButton
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.platform.LocalContext
import com.example.data.BrowserRepository
import kotlinx.coroutines.launch

@Composable
fun PluginManagerScreen(
    repository: BrowserRepository,
    isNightMode: Boolean,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val extensions by repository.extensionManager.extensions.collectAsState()
    var popupExtensionId by remember { mutableStateOf<String?>(null) }
    var optionsExtensionId by remember { mutableStateOf<String?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            repository.extensionManager.install(uri)
                .onSuccess { Toast.makeText(context, "扩展已安装：" + it.name, Toast.LENGTH_SHORT).show() }
                .onFailure { Toast.makeText(context, "安装失败：" + (it.message ?: "未知错误"), Toast.LENGTH_LONG).show() }
        }
    }

    Column(
        modifier = modifier.fillMaxSize()
            .background(if (isNightMode) MaterialTheme.colorScheme.background else MaterialTheme.colorScheme.surface)
            .statusBarsPadding().navigationBarsPadding()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
            }
            Column(Modifier.weight(1f)) {
                Text("浏览器扩展", style = MaterialTheme.typography.titleLarge)
                Text("Chrome / Edge Manifest V2 / V3", style = MaterialTheme.typography.bodySmall)
            }
            Button(onClick = { launcher.launch(arrayOf("application/zip", "application/x-chrome-extension", "application/octet-stream", "*/*")) }) {
                Icon(Icons.Default.FileOpen, null)
                Spacer(Modifier.width(6.dp))
                Text("导入")
            }
        }

        if (extensions.isEmpty()) {
            Column(
                Modifier.fillMaxSize().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(Icons.Default.Extension, null, Modifier.size(52.dp))
                Spacer(Modifier.size(12.dp))
                Text("还没有安装扩展")
                Spacer(Modifier.size(8.dp))
                Text("支持 CRX、ZIP 扩展包以及 Manifest V2/V3")
            }
        } else {
            LazyColumn(
                Modifier.fillMaxSize().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(extensions, key = { it.id }) { ext ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Extension, null, Modifier.size(38.dp))
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(ext.name, style = MaterialTheme.typography.titleMedium)
                                    Text("v" + ext.version + " · Manifest V" + ext.manifest.manifestVersion, style = MaterialTheme.typography.bodySmall)
                                }
                                Switch(
                                    checked = ext.enabled,
                                    onCheckedChange = { repository.extensionManager.setEnabled(ext.id, it) }
                                )
                            }
                            if (ext.manifest.description.isNotBlank()) {
                                Spacer(Modifier.size(6.dp))
                                Text(ext.manifest.description, style = MaterialTheme.typography.bodyMedium)
                            }
                            Spacer(Modifier.size(6.dp))
                            Text(
                                "权限：" + (ext.manifest.permissions + ext.manifest.hostPermissions).distinct().joinToString(", ").ifBlank { "无特殊权限" },
                                style = MaterialTheme.typography.bodySmall
                            )
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                ext.manifest.popup?.let {
                                    TextButton(onClick = { popupExtensionId = ext.id }) { Text("打开弹窗") }
                                }
                                ext.manifest.optionsPage?.let {
                                    TextButton(onClick = { optionsExtensionId = ext.id }) { Text("设置") }
                                }
                                IconButton(onClick = {
                                    repository.extensionManager.uninstall(ext.id)
                                    Toast.makeText(context, "扩展已卸载", Toast.LENGTH_SHORT).show()
                                }) {
                                    Icon(Icons.Default.Delete, "卸载")
                                }
                            }
                        }
                    }
                }
            }
        }

        popupExtensionId?.let { id ->
            val url = repository.extensionManager.popupUrl(id)
            if (url != null) {
                AlertDialog(
                    onDismissRequest = { popupExtensionId = null },
                    title = { Text(repository.extensionManager.extension(id)?.name ?: "扩展") },
                    text = {
                        AndroidView(
                            factory = { ctx ->
                                android.webkit.WebView(ctx).apply {
                                    settings.javaScriptEnabled = true
                                    settings.domStorageEnabled = true
                                    webViewClient = android.webkit.WebViewClient()
                                    loadUrl(url)
                                }
                            },
                            modifier = Modifier.fillMaxWidth().size(420.dp)
                        )
                    },
                    confirmButton = { TextButton(onClick = { popupExtensionId = null }) { Text("关闭") } }
                )
            }
        }

        optionsExtensionId?.let { id ->
            val url = repository.extensionManager.optionsUrl(id)
            if (url != null) {
                AlertDialog(
                    onDismissRequest = { optionsExtensionId = null },
                    title = { Text((repository.extensionManager.extension(id)?.name ?: "扩展") + " 设置") },
                    text = {
                        AndroidView(
                            factory = { ctx ->
                                android.webkit.WebView(ctx).apply {
                                    settings.javaScriptEnabled = true
                                    settings.domStorageEnabled = true
                                    webViewClient = android.webkit.WebViewClient()
                                    loadUrl(url)
                                }
                            },
                            modifier = Modifier.fillMaxWidth().size(420.dp)
                        )
                    },
                    confirmButton = { TextButton(onClick = { optionsExtensionId = null }) { Text("关闭") } }
                )
            }
        }
    }
}
