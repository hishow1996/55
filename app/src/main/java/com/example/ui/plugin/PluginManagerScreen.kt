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
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Settings
import androidx.compose.foundation.Image
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
    var userScripts by remember { mutableStateOf(repository.userScriptManager.all()) }
    var showMenu by remember { mutableStateOf(false) }
    var showInstallMenu by remember { mutableStateOf(false) }
    var showUrlDialog by remember { mutableStateOf(false) }
    var extensionUrl by remember { mutableStateOf("") }
    var showScripts by remember { mutableStateOf(false) }
    var showScriptDialog by remember { mutableStateOf(false) }
    var scriptUrl by remember { mutableStateOf("") }
    var aboutExtensionId by remember { mutableStateOf<String?>(null) }
    var popupExtensionId by remember { mutableStateOf<String?>(null) }
    var optionsExtensionId by remember { mutableStateOf<String?>(null) }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            repository.extensionManager.install(uri)
                .onSuccess { Toast.makeText(context, "已安装：" + it.name, Toast.LENGTH_SHORT).show() }
                .onFailure { Toast.makeText(context, "安装失败：" + (it.message ?: "扩展包无效"), Toast.LENGTH_LONG).show() }
        }
    }
    val scriptLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            repository.userScriptManager.install(uri)
                .onSuccess {
                    userScripts = repository.userScriptManager.all()
                    Toast.makeText(context, "已安装脚本：" + it.name, Toast.LENGTH_SHORT).show()
                }
                .onFailure { Toast.makeText(context, "脚本安装失败：" + (it.message ?: "脚本无效"), Toast.LENGTH_LONG).show() }
        }
    }

    val background = if (isNightMode) Color(0xFF0F1115) else Color(0xFFF7F8FA)
    val cardColor = if (isNightMode) Color(0xFF191C22) else Color.White
    val secondary = if (isNightMode) Color(0xFF9AA1AD) else Color(0xFF667085)

    if (showScripts) {
        UserScriptsScreen(
            scripts = userScripts,
            isNightMode = isNightMode,
            onBack = { showScripts = false },
            onAdd = { showScriptDialog = true },
            onToggle = {
                repository.userScriptManager.setEnabled(it.first, it.second)
                userScripts = repository.userScriptManager.all()
            },
            onDelete = {
                repository.userScriptManager.uninstall(it)
                userScripts = repository.userScriptManager.all()
            }
        )
        if (showScriptDialog) {
            UserScriptInstallDialog(
                value = scriptUrl,
                secondary = secondary,
                onValueChange = { scriptUrl = it },
                onDismiss = { showScriptDialog = false },
                onInstall = {
                    val url = scriptUrl.trim()
                    showScriptDialog = false
                    scope.launch {
                        repository.userScriptManager.installUrl(url)
                            .onSuccess {
                                userScripts = repository.userScriptManager.all()
                                Toast.makeText(context, "已安装脚本：" + it.name, Toast.LENGTH_SHORT).show()
                            }
                            .onFailure { Toast.makeText(context, "脚本安装失败：" + (it.message ?: "脚本无效"), Toast.LENGTH_LONG).show() }
                    }
                },
                onPickFile = { scriptLauncher.launch(arrayOf("text/javascript", "application/javascript", "*/*")) }
            )
        }
        return
    }

    Column(
        modifier = modifier.fillMaxSize()
            .background(background)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
            }
            Column(Modifier.weight(1f).padding(start = 2.dp)) {
                Text("扩展", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Text(
                    if (extensions.isEmpty()) "还没有安装扩展" else extensions.size.toString() + " 个已安装扩展",
                    style = MaterialTheme.typography.bodySmall,
                    color = secondary
                )
            }
            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(Icons.Default.MoreVert, "更多")
                }
                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                    DropdownMenuItem(
                        text = { Text("Chrome 网上应用店") },
                        leadingIcon = { Icon(Icons.Default.OpenInNew, null) },
                        onClick = { showMenu = false; onOpenChromeWebStore() }
                    )
                    DropdownMenuItem(
                        text = { Text("用户脚本") },
                        leadingIcon = { Icon(Icons.Default.Extension, null) },
                        onClick = { showMenu = false; showScripts = true }
                    )
                }
            }
        }

        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                SectionHeader("已安装", extensions.size, secondary)
            }

            if (extensions.isEmpty()) {
                item {
                    EmptyExtensionsCard(cardColor, secondary, onOpenChromeWebStore, onInstall = { showInstallMenu = true })
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
                Spacer(Modifier.height(8.dp))
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    color = cardColor,
                    onClick = { showScripts = true }
                ) {
                    Row(Modifier.padding(horizontal = 16.dp, vertical = 15.dp), verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            Modifier.size(42.dp),
                            shape = RoundedCornerShape(13.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text("JS", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSecondaryContainer)
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("用户脚本", fontWeight = FontWeight.SemiBold)
                            Text(
                                if (userScripts.isEmpty()) "尚未安装脚本" else userScripts.size.toString() + " 个已安装脚本",
                                style = MaterialTheme.typography.bodySmall,
                                color = secondary
                            )
                        }
                        Text("›", style = MaterialTheme.typography.headlineSmall, color = secondary)
                    }
                }
            }

            item {
                Box {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        onClick = { showInstallMenu = true }
                    ) {
                        Row(
                            Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Extension, null, Modifier.size(22.dp))
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text("添加扩展", fontWeight = FontWeight.SemiBold)
                                Text("从 Chrome 商店、文件或链接安装", style = MaterialTheme.typography.bodySmall)
                            }
                            Text("＋", style = MaterialTheme.typography.titleLarge)
                        }
                    }
                    DropdownMenu(expanded = showInstallMenu, onDismissRequest = { showInstallMenu = false }) {
                        DropdownMenuItem(
                            text = { Text("Chrome 网上应用店") },
                            onClick = { showInstallMenu = false; onOpenChromeWebStore() }
                        )
                        DropdownMenuItem(
                            text = { Text("从文件安装") },
                            onClick = { showInstallMenu = false; launcher.launch(arrayOf("application/zip", "application/x-chrome-extension", "application/octet-stream", "*/*")) }
                        )
                        DropdownMenuItem(
                            text = { Text("从链接安装") },
                            onClick = { showInstallMenu = false; showUrlDialog = true }
                        )
                    }
                }
            }
        }
    }

    if (showUrlDialog) {
        AlertDialog(
            onDismissRequest = { showUrlDialog = false },
            title = { Text("从链接安装") },
            text = {
                androidx.compose.material3.OutlinedTextField(
                    value = extensionUrl,
                    onValueChange = { extensionUrl = it },
                    singleLine = true,
                    placeholder = { Text("扩展下载地址") }
                )
            },
            confirmButton = {
                TextButton(enabled = extensionUrl.isNotBlank(), onClick = {
                    val url = extensionUrl.trim()
                    showUrlDialog = false
                    scope.launch {
                        repository.extensionManager.installUrl(url)
                            .onSuccess { Toast.makeText(context, "已安装：" + it.name, Toast.LENGTH_SHORT).show() }
                            .onFailure { Toast.makeText(context, "安装失败：" + (it.message ?: "扩展地址无效"), Toast.LENGTH_LONG).show() }
                    }
                }) { Text("安装") }
            },
            dismissButton = { TextButton(onClick = { showUrlDialog = false }) { Text("取消") } }
        )
    }

    popupExtensionId?.let { id ->
        ExtensionPageDialog(repository, id, "", { repository.extensionManager.popupUrl(it) }) { popupExtensionId = null }
    }
    optionsExtensionId?.let { id ->
        ExtensionPageDialog(repository, id, " 设置", { repository.extensionManager.optionsUrl(it) }) { optionsExtensionId = null }
    }
    aboutExtensionId?.let { id ->
        repository.extensionManager.extension(id)?.let { ext ->
            ExtensionDetailsDialog(
                ext = ext,
                iconPath = repository.extensionManager.iconFile(id)?.absolutePath,
                isNightMode = isNightMode,
                onDismiss = { aboutExtensionId = null }
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
    var menu by remember { mutableStateOf(false) }
    Surface(shape = RoundedCornerShape(18.dp), color = cardColor) {
        Column(Modifier.fillMaxWidth().padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ExtensionIcon(iconPath, name, 52.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(name, fontWeight = FontWeight.SemiBold, maxLines = 1)
                    Text(
                        "v$version · Manifest V$manifestVersion",
                        style = MaterialTheme.typography.bodySmall,
                        color = secondary
                    )
                }
                Switch(checked = enabled, onCheckedChange = onToggle)
                Box {
                    IconButton(onClick = { menu = true }) {
                        Icon(Icons.Default.MoreVert, "更多")
                    }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        if (hasPopup) DropdownMenuItem(
                            text = { Text("打开扩展") },
                            leadingIcon = { Icon(Icons.Default.Extension, null) },
                            onClick = { menu = false; onPopup() }
                        )
                        if (hasOptions) DropdownMenuItem(
                            text = { Text("扩展设置") },
                            leadingIcon = { Icon(Icons.Default.Settings, null) },
                            onClick = { menu = false; onOptions() }
                        )
                        DropdownMenuItem(
                            text = { Text("扩展信息") },
                            leadingIcon = { Icon(Icons.Default.Info, null) },
                            onClick = { menu = false; onAbout() }
                        )
                        DropdownMenuItem(
                            text = { Text("卸载") },
                            leadingIcon = { Icon(Icons.Default.DeleteOutline, null) },
                            onClick = { menu = false; onUninstall() }
                        )
                    }
                }
            }
            if (description.isNotBlank()) {
                Spacer(Modifier.height(10.dp))
                Text(description, style = MaterialTheme.typography.bodySmall, color = secondary, maxLines = 2)
            }
        }
    }
}

@Composable
private fun ExtensionPageDialog(
    repository: BrowserRepository,
    extensionId: String,
    titleSuffix: String,
    urlProvider: (String) -> String?,
    onDismiss: () -> Unit
) {
    val url = remember(extensionId) { urlProvider(extensionId) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text((repository.extensionManager.extension(extensionId)?.name ?: "扩展") + titleSuffix) },
        text = {
            if (url == null) {
                Text("此扩展页面不可用。")
            } else {
                AndroidView(
                    modifier = Modifier.fillMaxWidth().height(420.dp),
                    factory = { context ->
                        WebView(context).apply {
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            webViewClient = WebViewClient()
                            repository.extensionManager.prepareExtensionPage(
                                this, extensionId, "plugin-dialog"
                            )
                            loadUrl(url)
                        }
                    }
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("关闭") } }
    )
}

@Composable
private fun ExtensionDetailsDialog(
    ext: com.example.extension.BrowserExtension,
    iconPath: String?,
    isNightMode: Boolean,
    onDismiss: () -> Unit
) {
    val secondary = if (isNightMode) Color(0xFF9AA1AD) else Color(0xFF667085)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ExtensionIcon(iconPath, ext.name, 52.dp)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(ext.name, fontWeight = FontWeight.SemiBold, maxLines = 1)
                    Text("v" + ext.version, style = MaterialTheme.typography.bodySmall, color = secondary)
                }
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                if (ext.manifest.description.isNotBlank()) {
                    Text(ext.manifest.description, color = secondary)
                }
                DetailRow("Manifest", "V" + ext.manifest.manifestVersion)
                val permissions = (ext.manifest.permissions + ext.manifest.hostPermissions).distinct()
                DetailRow("权限", permissions.joinToString(", ").ifBlank { "无特殊权限" })
                DetailRow("内容脚本", ext.manifest.contentScripts.size.toString())
                if (ext.manifest.popup != null) DetailRow("弹窗", "支持")
                if (ext.manifest.optionsPage != null) DetailRow("设置页", "支持")
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("完成") }
        }
    )
}

@Composable
private fun DetailRow(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun ExtensionIcon(iconPath: String?, name: String, size: androidx.compose.ui.unit.Dp) {
    val bitmap = remember(iconPath) { iconPath?.let { BitmapFactory.decodeFile(it) } }
    Surface(
        modifier = Modifier.size(size),
        shape = RoundedCornerShape(size * 0.26f),
        color = MaterialTheme.colorScheme.secondaryContainer
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = name,
                modifier = Modifier.fillMaxSize().padding(size * 0.12f)
            )
        } else {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Extension, null, Modifier.size(size * 0.55f))
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String, count: Int, secondary: Color) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 2.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.width(8.dp))
        Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
            Text(count.toString(), Modifier.padding(horizontal = 8.dp, vertical = 3.dp), style = MaterialTheme.typography.labelSmall, color = secondary)
        }
    }
}

@Composable
private fun EmptyExtensionsCard(cardColor: Color, secondary: Color, onStore: () -> Unit, onInstall: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = cardColor), shape = RoundedCornerShape(20.dp)) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 38.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(Icons.Default.Extension, null, Modifier.size(46.dp), tint = secondary)
            Spacer(Modifier.height(12.dp))
            Text("还没有扩展", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(5.dp))
            Text("安装扩展来增强浏览器功能", color = secondary)
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onStore) { Text("Chrome 商店") }
                TextButton(onClick = onInstall) { Text("选择文件") }
            }
        }
    }
}

@Composable
private fun UserScriptsScreen(
    scripts: List<com.example.extension.UserScript>,
    isNightMode: Boolean,
    onBack: () -> Unit,
    onAdd: () -> Unit,
    onToggle: (Pair<String, Boolean>) -> Unit,
    onDelete: (String) -> Unit
) {
    val background = if (isNightMode) Color(0xFF0F1115) else Color(0xFFF7F8FA)
    val cardColor = if (isNightMode) Color(0xFF191C22) else Color.White
    val secondary = if (isNightMode) Color(0xFF9AA1AD) else Color(0xFF667085)
    Column(Modifier.fillMaxSize().background(background).statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") }
            Column(Modifier.weight(1f)) {
                Text("用户脚本", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Text(scripts.size.toString() + " 个已安装脚本", style = MaterialTheme.typography.bodySmall, color = secondary)
            }
            IconButton(onClick = onAdd) { Icon(Icons.Default.MoreVert, "添加") }
        }
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (scripts.isEmpty()) {
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = cardColor), shape = RoundedCornerShape(20.dp)) {
                        Column(Modifier.fillMaxWidth().padding(38.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("暂无用户脚本", fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.height(6.dp))
                            Text("支持 .user.js 脚本", color = secondary)
                            Spacer(Modifier.height(14.dp))
                            TextButton(onClick = onAdd) { Text("添加脚本") }
                        }
                    }
                }
            } else {
                items(scripts, key = { it.id }) { script ->
                    Surface(shape = RoundedCornerShape(18.dp), color = cardColor) {
                        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Surface(Modifier.size(42.dp), RoundedCornerShape(13.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text("JS", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSecondaryContainer)
                                }
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(script.name, fontWeight = FontWeight.SemiBold, maxLines = 1)
                                Text(script.matches.size.toString() + " 个匹配规则", style = MaterialTheme.typography.bodySmall, color = secondary)
                            }
                            Switch(checked = script.enabled, onCheckedChange = { onToggle(script.id to it) })
                            IconButton(onClick = { onDelete(script.id) }) { Icon(Icons.Default.DeleteOutline, "卸载") }
                        }
                    }
                }
            }
            item {
                Surface(
                    Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    onClick = onAdd
                ) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("＋", style = MaterialTheme.typography.titleLarge)
                        Spacer(Modifier.width(8.dp))
                        Text("添加用户脚本", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

@Composable
private fun UserScriptInstallDialog(
    value: String,
    secondary: Color,
    onValueChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onInstall: () -> Unit,
    onPickFile: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加用户脚本") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("支持 Greasy Fork 等站点提供的 .user.js 脚本。", color = secondary)
                androidx.compose.material3.OutlinedTextField(
                    value = value,
                    onValueChange = onValueChange,
                    singleLine = true,
                    placeholder = { Text("https://…/script.user.js") }
                )
                TextButton(onClick = onPickFile) { Text("从文件选择") }
            }
        },
        confirmButton = {
            TextButton(enabled = value.isNotBlank(), onClick = onInstall) { Text("安装") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

