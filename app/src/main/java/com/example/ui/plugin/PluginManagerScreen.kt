package com.example.ui.plugin

import android.graphics.BitmapFactory
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Settings
import androidx.compose.foundation.Image
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.dp
import com.example.data.BrowserRepository
import kotlinx.coroutines.launch

@Composable
fun PluginManagerScreen(
    repository: BrowserRepository,
    isNightMode: Boolean,
    onBack: () -> Unit,
    onOpenChromeWebStore: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val extensions by repository.extensionManager.extensions.collectAsState()
    var popupExtensionId by remember { mutableStateOf<String?>(null) }
    var optionsExtensionId by remember { mutableStateOf<String?>(null) }
    var aboutExtensionId by remember { mutableStateOf<String?>(null) }
    var showUrlDialog by remember { mutableStateOf(false) }
    var extensionUrl by remember { mutableStateOf("") }
    var showUserScriptDialog by remember { mutableStateOf(false) }
    var userScriptUrl by remember { mutableStateOf("") }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            repository.extensionManager.install(uri)
                .onSuccess { Toast.makeText(context, "已安装：" + it.name, Toast.LENGTH_SHORT).show() }
                .onFailure { Toast.makeText(context, "安装失败：" + (it.message ?: "扩展包无效"), Toast.LENGTH_LONG).show() }
        }
    }

    val background = if (isNightMode) Color(0xFF101318) else Color(0xFFF6F7F9)
    val cardColor = if (isNightMode) Color(0xFF1B2028) else Color.White
    val secondary = if (isNightMode) Color(0xFF9AA4B2) else Color(0xFF667085)

    Column(
        modifier = modifier.fillMaxSize().background(background).statusBarsPadding().navigationBarsPadding()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
            }
            Column(Modifier.weight(1f)) {
                Text("扩展", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Text(extensions.size.toString() + " 个已安装扩展", style = MaterialTheme.typography.bodySmall, color = secondary)
            }
            TextButton(onClick = onOpenChromeWebStore) {
                Text("Chrome 商店")
            }
            TextButton(onClick = { showUrlDialog = true }) {
                Icon(Icons.Default.OpenInNew, null, Modifier.size(17.dp))
                Spacer(Modifier.width(6.dp))
                Text("链接安装")
            }
            TextButton(onClick = { showUserScriptDialog = true }) {
                Text("用户脚本")
            }
            TextButton(onClick = {
                launcher.launch(arrayOf("application/zip", "application/x-chrome-extension", "application/octet-stream", "*/*"))
            }) {
                Icon(Icons.Default.FileOpen, null, Modifier.size(17.dp))
                Spacer(Modifier.width(6.dp))
                Text("导入")
            }
        }

        Divider()

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                Row(
                    Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("已安装", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.width(8.dp))
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = if (isNightMode) Color(0xFF2A3039) else Color(0xFFE9EEF5)
                    ) {
                        Text(extensions.size.toString(), Modifier.padding(horizontal = 9.dp, vertical = 3.dp), color = secondary)
                    }
                }
            }

            if (extensions.isEmpty()) {
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = cardColor), shape = RoundedCornerShape(14.dp)) {
                        Column(
                            Modifier.fillMaxWidth().padding(vertical = 42.dp, horizontal = 24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(Icons.Default.Extension, null, Modifier.size(54.dp), tint = secondary)
                            Spacer(Modifier.height(12.dp))
                            Text("暂无扩展", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.height(5.dp))
                            Text("点击右上角“导入”安装 CRX 或 ZIP 扩展。", color = secondary)
                        }
                    }
                }
            } else {
                items(extensions, key = { it.id }) { ext ->
                    ExtensionCard(
                        name = ext.name,
                        iconPath = repository.extensionManager.iconFile(ext.id)?.absolutePath,
                        version = ext.version,
                        description = ext.manifest.description,
                        manifestVersion = ext.manifest.manifestVersion,
                        enabled = ext.enabled,
                        hasPopup = ext.manifest.popup != null,
                        hasOptions = ext.manifest.optionsPage != null,
                        cardColor = cardColor,
                        secondary = secondary,
                        onToggle = { repository.extensionManager.setEnabled(ext.id, it) },
                        onPopup = { popupExtensionId = ext.id },
                        onOptions = { optionsExtensionId = ext.id },
                        onAbout = { aboutExtensionId = ext.id },
                        onUninstall = {
                            repository.extensionManager.uninstall(ext.id)
                            Toast.makeText(context, "已卸载 " + ext.name, Toast.LENGTH_SHORT).show()
                        }
                    )
                }
            }

            item {
                Card(colors = CardDefaults.cardColors(containerColor = cardColor), shape = RoundedCornerShape(14.dp)) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.Top) {
                        Icon(Icons.Default.Info, null, Modifier.size(20.dp), tint = secondary)
                        Spacer(Modifier.width(10.dp))
                        Text(
                            "扩展运行在浏览器 WebView 兼容层中。依赖 Chrome 专有 API、原生服务或桌面 UI 的扩展可能需要进一步适配。",
                            style = MaterialTheme.typography.bodySmall,
                            color = secondary
                        )
                    }
                }
            }
        }
    }

    if (showUserScriptDialog) {
        AlertDialog(
            onDismissRequest = { showUserScriptDialog = false },
            title = { Text("安装用户脚本") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("支持 Greasy Fork 等站点提供的 .user.js 脚本。安装后只会在脚本声明的匹配网址执行。", color = secondary)
                    androidx.compose.material3.OutlinedTextField(
                        value = userScriptUrl,
                        onValueChange = { userScriptUrl = it },
                        singleLine = true,
                        placeholder = { Text("https://…/script.user.js") }
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = userScriptUrl.isNotBlank(),
                    onClick = {
                        val url = userScriptUrl.trim()
                        showUserScriptDialog = false
                        scope.launch {
                            repository.userScriptManager.installUrl(url)
                                .onSuccess { Toast.makeText(context, "已安装脚本：" + it.name, Toast.LENGTH_SHORT).show() }
                                .onFailure { Toast.makeText(context, "脚本安装失败：" + (it.message ?: "脚本无效"), Toast.LENGTH_LONG).show() }
                        }
                    }
                ) { Text("安装") }
            },
            dismissButton = { TextButton(onClick = { showUserScriptDialog = false }) { Text("取消") } }
        )
    }

    if (showUrlDialog) {
        AlertDialog(
            onDismissRequest = { showUrlDialog = false },
            title = { Text("安装扩展") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("粘贴 CRX/ZIP 扩展的直接下载地址。Chrome 网上应用店触发的 CRX 下载也会自动交给扩展安装器。", color = secondary)
                    androidx.compose.material3.OutlinedTextField(
                        value = extensionUrl,
                        onValueChange = { extensionUrl = it },
                        singleLine = true,
                        placeholder = { Text("https://…/extension.crx") }
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = extensionUrl.isNotBlank(),
                    onClick = {
                        val url = extensionUrl.trim()
                        showUrlDialog = false
                        scope.launch {
                            repository.extensionManager.installUrl(url)
                                .onSuccess { Toast.makeText(context, "已安装：" + it.name, Toast.LENGTH_SHORT).show() }
                                .onFailure { Toast.makeText(context, "安装失败：" + (it.message ?: "扩展地址无效"), Toast.LENGTH_LONG).show() }
                        }
                    }
                ) { Text("安装") }
            },
            dismissButton = { TextButton(onClick = { showUrlDialog = false }) { Text("取消") } }
        )
    }

    ExtensionPageDialog(repository, popupExtensionId, "", { repository.extensionManager.popupUrl(it) }) {
        popupExtensionId = null
    }
    ExtensionPageDialog(repository, optionsExtensionId, " 设置", { repository.extensionManager.optionsUrl(it) }) {
        optionsExtensionId = null
    }

    aboutExtensionId?.let { id ->
        val ext = repository.extensionManager.extension(id)
        if (ext != null) {
            AlertDialog(
                onDismissRequest = { aboutExtensionId = null },
                title = { Text(ext.name) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("版本：" + ext.version)
                        Text("Manifest：V" + ext.manifest.manifestVersion)
                        if (ext.manifest.description.isNotBlank()) Text(ext.manifest.description)
                        Text(
                            "权限：" + (ext.manifest.permissions + ext.manifest.hostPermissions)
                                .distinct().joinToString(", ").ifBlank { "无特殊权限" }
                        )
                    }
                },
                confirmButton = { TextButton(onClick = { aboutExtensionId = null }) { Text("完成") } }
            )
        }
    }
}

@Composable
private fun ExtensionCard(
    name: String,
    iconPath: String?,
    version: String,
    description: String,
    manifestVersion: Int,
    enabled: Boolean,
    hasPopup: Boolean,
    hasOptions: Boolean,
    cardColor: Color,
    secondary: Color,
    onToggle: (Boolean) -> Unit,
    onPopup: () -> Unit,
    onOptions: () -> Unit,
    onAbout: () -> Unit,
    onUninstall: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Card(colors = CardDefaults.cardColors(containerColor = cardColor), shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    modifier = Modifier.size(44.dp),
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer
                ) {
                    val bitmap = remember(iconPath) {
                        iconPath?.let { BitmapFactory.decodeFile(it) }
                    }
                    if (bitmap != null) {
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = name,
                            modifier = Modifier.fillMaxSize().padding(5.dp)
                        )
                    } else {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Default.Extension,
                                null,
                                Modifier.size(28.dp),
                                tint = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1)
                    Spacer(Modifier.height(2.dp))
                    Text("v" + version + "  ·  Manifest V" + manifestVersion, style = MaterialTheme.typography.bodySmall, color = secondary)
                }
                Switch(checked = enabled, onCheckedChange = onToggle)
                Box {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(Icons.Default.MoreVert, "更多")
                    }
                    DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                        if (hasPopup) DropdownMenuItem(
                            text = { Text("打开扩展弹窗") },
                            leadingIcon = { Icon(Icons.Default.OpenInNew, null) },
                            onClick = { menuExpanded = false; onPopup() }
                        )
                        if (hasOptions) DropdownMenuItem(
                            text = { Text("扩展设置") },
                            leadingIcon = { Icon(Icons.Default.Settings, null) },
                            onClick = { menuExpanded = false; onOptions() }
                        )
                        DropdownMenuItem(
                            text = { Text("扩展详情") },
                            leadingIcon = { Icon(Icons.Default.Info, null) },
                            onClick = { menuExpanded = false; onAbout() }
                        )
                        DropdownMenuItem(
                            text = { Text("卸载") },
                            leadingIcon = { Icon(Icons.Default.DeleteOutline, null) },
                            onClick = { menuExpanded = false; onUninstall() }
                        )
                    }
                }
            }
            if (description.isNotBlank()) {
                Spacer(Modifier.height(10.dp))
                Text(description, style = MaterialTheme.typography.bodySmall, color = secondary, maxLines = 3)
            }
            if (hasPopup || hasOptions) {
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    if (hasPopup) TextButton(onClick = onPopup) { Text("弹窗") }
                    if (hasOptions) TextButton(onClick = onOptions) { Text("设置") }
                }
            }
        }
    }
}

@Composable
private fun ExtensionPageDialog(
    repository: BrowserRepository,
    extensionId: String?,
    titleSuffix: String,
    urlProvider: (String) -> String?,
    onDismiss: () -> Unit
) {
    extensionId?.let { id ->
        val url = urlProvider(id)
        val ext = repository.extensionManager.extension(id)
        if (url != null && ext != null) {
            AlertDialog(
                onDismissRequest = onDismiss,
                title = { Text(ext.name + titleSuffix) },
                text = {
                    AndroidView(
                        factory = { ctx ->
                            WebView(ctx).apply {
                                settings.javaScriptEnabled = true
                                settings.domStorageEnabled = true
                                settings.allowFileAccess = true
                                settings.allowContentAccess = true
                                webViewClient = WebViewClient()
                                loadUrl(url)
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(440.dp)
                    )
                },
                confirmButton = { TextButton(onClick = onDismiss) { Text("关闭") } }
            )
        }
    }
}
