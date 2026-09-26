package com.example.ui.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.LaptopMac
import androidx.compose.material.icons.filled.TabletMac
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Divider
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.BrowserRepository
import com.example.model.SearchEngines
import com.example.ui.home.SearchEngineLogo

@Composable
fun SettingsScreen(
    repository: BrowserRepository,
    isNightMode: Boolean,
    isDesktopMode: Boolean,
    searchEngine: String,
    isContentBlocking: Boolean = true,
    onBack: () -> Unit,
    onOpenPluginManager: () -> Unit,
    onBackupData: () -> Unit = {},
    onRestoreData: () -> Unit = {},
    onToggleContentBlocking: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val desktopUaType by repository.desktopUaType.collectAsState()
    val customUa by repository.customUserAgent.collectAsState()
    val autoTranslate by repository.autoTranslate.collectAsState()
    val cloudAcceleration by repository.cloudAcceleration.collectAsState()
    val autoPip by repository.autoPip.collectAsState()
    var showClearDialog by remember { mutableStateOf(false) }
    var showDataBackupDialog by remember { mutableStateOf(false) }
    var showPlayerDialog by remember { mutableStateOf(false) }
    var showTranslationDialog by remember { mutableStateOf(false) }
    var showThemeDialog by remember { mutableStateOf(false) }
    var showCloudDialog by remember { mutableStateOf(false) }
    var showSearchEngineDialog by remember { mutableStateOf(false) }
    var showUaDialog by remember { mutableStateOf(false) }
    var showUaDialog by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }

    val bg = if (isNightMode) Color(0xFF111418) else Color(0xFFFFFFFF)
    val itemBg = if (isNightMode) Color(0xFF1B2028) else Color(0xFFFFFFFF)
    val textColor = if (isNightMode) Color(0xFFE2E8F0) else Color(0xFF1E293B)
    val subTextColor = if (isNightMode) Color(0xFF94A3B8) else Color(0xFF64748B)
    val dividerColor = if (isNightMode) Color(0xFF262C36) else Color(0xFFF1F5F9)

    // Check system overlay permission
    val canDrawOverlays = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        Settings.canDrawOverlays(context)
    } else {
        true
    }


    Column(
        modifier = modifier
            .fillMaxSize()
            .background(bg)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        // --- TOP BAR WITH BACK ARROW AND TITLE "设置" (Image 2 style) ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "返回",
                    tint = textColor,
                    modifier = Modifier.size(24.dp)
                )
            }
            Text(
                text = "设置",
                fontSize = 19.sp,
                fontWeight = FontWeight.Medium,
                color = textColor,
                modifier = Modifier.padding(start = 2.dp)
            )
        }

        HorizontalDivider(color = dividerColor, thickness = 0.8.dp)

        // --- SETTINGS LIST: Exact options from Image 2 without adblock, presented with clean design ---
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
        ) {
            // 1. 插件扩展
            SettingsItem(
                title = "插件扩展",
                detail = "",
                textColor = textColor,
                subTextColor = subTextColor,
                dividerColor = dividerColor,
                onClick = onOpenPluginManager
            )

            // 2. 广告与跟踪保护
            SettingsItem(
                title = "广告与跟踪保护",
                detail = if (isContentBlocking) "已开启" else "已关闭",
                textColor = textColor,
                subTextColor = subTextColor,
                dividerColor = dividerColor,
                onClick = { onToggleContentBlocking() }
            )

            // 3. 悬浮窗与播放器
            SettingsItem(
                title = "悬浮窗与播放器",
                detail = "",
                textColor = textColor,
                subTextColor = subTextColor,
                dividerColor = dividerColor,
                onClick = {
                    showPlayerDialog = true
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !canDrawOverlays) {
                        try {
                            val intent = Intent(
                                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:${context.packageName}")
                            )
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            Toast.makeText(context, "请在系统设置中允许在其他应用上层显示", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        Toast.makeText(context, "悬浮窗与画中画播放器已就绪", Toast.LENGTH_SHORT).show()
                    }
                }
            )

            // 4. 电脑端模式：统一管理桌面网页模式与 User-Agent
            SettingsItem(
                title = "电脑端模式",
                detail = when (desktopUaType) {
                    "mac" -> "Mac"
                    "ipad" -> "iPad"
                    "android" -> "Android"
                    "custom" -> "自定义"
                    else -> "Windows"
                },
                textColor = textColor,
                subTextColor = subTextColor,
                dividerColor = dividerColor,
                onClick = { showUaDialog = true }
            )

            // 5. 搜索设置
            SettingsItem(
                title = "搜索设置",
                detail = "",
                textColor = textColor,
                subTextColor = subTextColor,
                dividerColor = dividerColor,
                onClick = { showSearchEngineDialog = true }
            )

            // 7. 网页翻译
            SettingsItem(
                title = "网页翻译",
                detail = "",
                textColor = textColor,
                subTextColor = subTextColor,
                dividerColor = dividerColor,
                onClick = { showTranslationDialog = true }
            )

            // 8. 主题
            SettingsItem(
                title = "主题",
                detail = "",
                textColor = textColor,
                subTextColor = subTextColor,
                dividerColor = dividerColor,
                onClick = { showThemeDialog = true }
            )

            // 9. 云加速
            SettingsItem(
                title = "云加速",
                detail = if (cloudAcceleration) "开启" else "关闭",
                textColor = textColor,
                subTextColor = subTextColor,
                dividerColor = dividerColor,
                onClick = { showCloudDialog = true }
            )

            // 10. 数据备份与恢复
            SettingsItem(
                title = "数据备份与恢复",
                detail = "",
                textColor = textColor,
                subTextColor = subTextColor,
                dividerColor = dividerColor,
                onClick = { showDataBackupDialog = true }
            )

            // 11. 清除记录
            SettingsItem(
                title = "清除记录",
                detail = "",
                textColor = textColor,
                subTextColor = subTextColor,
                dividerColor = dividerColor,
                onClick = { showClearDialog = true }
            )

            // 12. 关于大象
            SettingsItem(
                title = "关于大象",
                detail = "V1.0.0",
                textColor = textColor,
                subTextColor = subTextColor,
                dividerColor = dividerColor,
                onClick = { showAboutDialog = true }
            )

            Spacer(modifier = Modifier.height(40.dp))

            // Centered "恢复默认" button (Image 2 bottom)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "恢复默认",
                    fontSize = 15.sp,
                    color = subTextColor,
                    modifier = Modifier
                        .clickable {
                            repository.resetBrowserPreferences()
                            Toast.makeText(context, "已恢复为默认配置", Toast.LENGTH_SHORT).show()
                        }
                        .padding(horizontal = 24.dp, vertical = 10.dp)
                )
            }
        }
    }

    if (showDataBackupDialog) {
        AlertDialog(
            onDismissRequest = { showDataBackupDialog = false },
            title = { Text("数据备份与恢复") },
            text = {
                Text(
                    "可导出书签、历史、搜索记录、快捷网站、浏览器设置和已打开标签页的元数据。恢复会覆盖这些浏览器数据。",
                    fontSize = 13.sp,
                    color = subTextColor
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showDataBackupDialog = false
                    onBackupData()
                }) { Text("导出备份") }
            },
            dismissButton = {
                TextButton(onClick = {
                    showDataBackupDialog = false
                    onRestoreData()
                }) { Text("恢复备份") }
            }
        )
    }

    // Clear History Dialog
    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("清除记录") },
            text = { Text("可清除浏览历史和搜索记录。书签、快捷网站和浏览器设置不会受到影响。") },
            confirmButton = {
                TextButton(onClick = {
                    repository.clearAllHistory()
                    repository.clearSearchHistory()
                    showClearDialog = false
                    Toast.makeText(context, "浏览记录已清空", Toast.LENGTH_SHORT).show()
                }) {
                    Text("立即清除", color = Color.Red)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text("取消")
                }
            }
        )
    }

    if (showPlayerDialog) {
        AlertDialog(
            onDismissRequest = { showPlayerDialog = false },
            title = { Text("悬浮窗与播放器") },
            text = {
                Column {
                    SettingSwitchRow("自动进入画中画", "视频切换到后台时自动进入系统 PiP", autoPip, { repository.setAutoPip(it) }, textColor, subTextColor)
                    HorizontalDivider(color = dividerColor, thickness = 0.5.dp)
                    TextButton(onClick = {
                        showPlayerDialog = false
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !canDrawOverlays) {
                            try { context.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + context.packageName))) } catch (_: Exception) {}
                        }
                    }) { Text("打开系统悬浮窗权限") }
                    Text("播放器继续使用实际视频画面比例，并在浮窗关闭后同步恢复网页播放。", fontSize = 12.sp, color = subTextColor)
                }
            },
            confirmButton = { TextButton(onClick = { showPlayerDialog = false }) { Text("完成") } }
        )
    }

    if (showTranslationDialog) {
        AlertDialog(
            onDismissRequest = { showTranslationDialog = false },
            title = { Text("网页翻译") },
            text = {
                Column {
                    SettingSwitchRow("自动翻译", "打开网页时自动提供翻译入口", autoTranslate, { repository.setAutoTranslate(it) }, textColor, subTextColor)
                    Spacer(modifier = Modifier.height(10.dp))
                    Text("当前默认语言：简体中文", fontSize = 14.sp, color = textColor)
                    Text("翻译设置只控制浏览器行为，不改变原网页地址。", fontSize = 12.sp, color = subTextColor)
                }
            },
            confirmButton = { TextButton(onClick = { showTranslationDialog = false }) { Text("完成") } }
        )
    }

    if (showThemeDialog) {
        AlertDialog(
            onDismissRequest = { showThemeDialog = false },
            title = { Text("主题") },
            text = {
                Column {
                    listOf("白天模式" to false, "黑夜模式" to true).forEach { (label, value) ->
                        Row(Modifier.fillMaxWidth().clickable { repository.setNightMode(value); showThemeDialog = false }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = isNightMode == value, onClick = { repository.setNightMode(value); showThemeDialog = false })
                            Text(label, fontSize = 15.sp, color = textColor)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showThemeDialog = false }) { Text("完成") } }
        )
    }

    if (showCloudDialog) {
        AlertDialog(
            onDismissRequest = { showCloudDialog = false },
            title = { Text("云加速") },
            text = {
                Column {
                    SettingSwitchRow("数据节省统计", "记录浏览器已经节省的数据量", cloudAcceleration, { repository.setCloudAcceleration(it) }, textColor, subTextColor)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("已节省 " + String.format("%.2f MB", repository.dataSavedMb.value), fontSize = 15.sp, fontWeight = FontWeight.Medium, color = textColor)
                    Text("当前版本不会把未实现的网络代理能力伪装成云加速。", fontSize = 12.sp, color = subTextColor)
                }
            },
            confirmButton = { TextButton(onClick = { showCloudDialog = false }) { Text("完成") } }
        )
    }

    // Search Engine Selection Dialog
    if (showSearchEngineDialog) {
        AlertDialog(
            onDismissRequest = { showSearchEngineDialog = false },
            title = { Text("搜索设置") },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    SearchEngines.ALL.forEach { item ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    repository.setSearchEngine(item.id)
                                    showSearchEngineDialog = false
                                }
                                .padding(vertical = 10.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            SearchEngineLogo(
                                engine = item.id,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = item.name,
                                    fontSize = 15.sp,
                                    fontWeight = if (searchEngine == item.id) FontWeight.Bold else FontWeight.Normal,
                                    color = if (searchEngine == item.id) Color(0xFF2563EB) else textColor
                                )
                                Text(
                                    text = item.category,
                                    fontSize = 12.sp,
                                    color = subTextColor
                                )
                            }
                            if (searchEngine == item.id) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "已选择",
                                    tint = Color(0xFF2563EB),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {}
        )
    }

    // 电脑端模式选择：桌面模式与 User-Agent 合并为一个设置入口
    if (showUaDialog) {
        var selectedType by remember { mutableStateOf(desktopUaType) }
        var enabled by remember { mutableStateOf(isDesktopMode) }
        var customInput by remember { mutableStateOf(customUa) }

        AlertDialog(
            onDismissRequest = { showUaDialog = false },
            title = { Text("电脑端模式") },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    SettingSwitchRow(
                        title = "电脑端模式",
                        detail = "以桌面网页方式访问网站",
                        checked = enabled,
                        onCheckedChange = { enabled = it },
                        textColor = textColor,
                        subTextColor = subTextColor
                    )
                    if (enabled) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("User-Agent", fontSize = 13.sp, color = subTextColor)
                        listOf(
                            "windows" to "Windows",
                            "mac" to "Mac",
                            "ipad" to "iPad",
                            "android" to "Android",
                            "custom" to "自定义"
                        ).forEach { (typeKey, label) ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { selectedType = typeKey }
                                    .padding(vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = selectedType == typeKey,
                                    onClick = { selectedType = typeKey }
                                )
                                Text(label, fontSize = 15.sp, color = textColor, modifier = Modifier.padding(start = 4.dp))
                            }
                        }
                        if (selectedType == "custom") {
                            OutlinedTextField(
                                value = customInput,
                                onValueChange = { customInput = it },
                                label = { Text("User-Agent") },
                                placeholder = { Text("输入自定义 User-Agent") },
                                modifier = Modifier.fillMaxWidth(),
                                maxLines = 3
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    repository.setDesktopMode(enabled)
                    repository.setDesktopUaType(selectedType)
                    if (selectedType == "custom") repository.setCustomUserAgent(customInput.trim())
                    showUaDialog = false
                    Toast.makeText(
                        context,
                        if (enabled) "电脑端模式已开启" else "电脑端模式已关闭",
                        Toast.LENGTH_SHORT
                    ).show()
                }) { Text("完成") }
            },
            dismissButton = {
                TextButton(onClick = { showUaDialog = false }) { Text("取消") }
            }
        )
    }

    // About Dialog
    if (showAboutDialog) {
        AlertDialog(
            onDismissRequest = { showAboutDialog = false },
            title = { Text("关于大象") },
            text = {
                Column {
                    Text("应用名称：大象 (Elephant Browser)")
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("内核架构：原生 Chromium 内核")
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("版本号：1.0.0")
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("核心特性：")
                    Text("• 支持任意方向上下左右自由调整悬浮窗播放器")
                    Text("• 支持系统级全局悬浮画中画 (PiP)")
                    Text("• 视频画面比例自适应锁定")
                    Text("• 自定义油猴/JS插件扩展系统")
                    Text("• 网页全文翻译为简体中文")
                    Text("• 电脑端模式与手机端一键切换")
                    Text("• 白天模式与黑夜模式 (默认白天模式)")
                    Text("• 隐私无痕浏览模式")
                }
            },
            confirmButton = {
                TextButton(onClick = { showAboutDialog = false }) {
                    Text("确定")
                }
            }
        )
    }
}

@Composable
private fun SettingSwitchRow(title: String, detail: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit, textColor: Color, subTextColor: Color) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = textColor)
            Text(detail, fontSize = 12.sp, color = subTextColor)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun SettingsItem(
    title: String,
    detail: String = "",
    textColor: Color,
    subTextColor: Color,
    dividerColor: Color,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 18.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                fontSize = 16.sp,
                fontWeight = FontWeight.Normal,
                color = textColor
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (detail.isNotBlank()) {
                    Text(
                        text = detail,
                        fontSize = 14.sp,
                        color = subTextColor,
                        modifier = Modifier.padding(end = 6.dp)
                    )
                }
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = subTextColor.copy(alpha = 0.45f),
                    modifier = Modifier.size(19.dp)
                )
            }
        }
        HorizontalDivider(color = dividerColor, thickness = 0.6.dp)
    }
}
