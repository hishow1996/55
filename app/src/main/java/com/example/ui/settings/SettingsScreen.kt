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
    onBack: () -> Unit,
    onOpenPluginManager: () -> Unit,
    onBackupData: () -> Unit = {},
    onRestoreData: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val desktopUaType by repository.desktopUaType.collectAsState()
    val customUa by repository.customUserAgent.collectAsState()
    var showClearDialog by remember { mutableStateOf(false) }
    var showSearchEngineDialog by remember { mutableStateOf(false) }
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

    var showBrowserSettingsDialog by remember { mutableStateOf(false) }

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

            // 2. 悬浮窗与播放器
            SettingsItem(
                title = "悬浮窗与播放器",
                detail = "",
                textColor = textColor,
                subTextColor = subTextColor,
                dividerColor = dividerColor,
                onClick = {
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

            // 3. 电脑端模式
            SettingsItem(
                title = "电脑端模式",
                detail = "",
                textColor = textColor,
                subTextColor = subTextColor,
                dividerColor = dividerColor,
                onClick = {
                    val newState = !isDesktopMode
                    repository.setDesktopMode(newState)
                    Toast.makeText(
                        context,
                        if (newState) "已开启电脑端模式" else "已恢复移动端模式",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            )

            // 4. 电脑版 User-Agent
            SettingsItem(
                title = "电脑版 User-Agent",
                detail = "",
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

            // 6. 网页翻译
            SettingsItem(
                title = "网页翻译",
                detail = "",
                textColor = textColor,
                subTextColor = subTextColor,
                dividerColor = dividerColor,
                onClick = {
                    Toast.makeText(context, "支持网页智能翻译，可在浏览网页时随时调用", Toast.LENGTH_SHORT).show()
                }
            )

            // 7. 主题
            SettingsItem(
                title = "主题",
                detail = "",
                textColor = textColor,
                subTextColor = subTextColor,
                dividerColor = dividerColor,
                onClick = {
                    val newMode = !isNightMode
                    repository.setNightMode(newMode)
                    Toast.makeText(
                        context,
                        if (newMode) "已切换为黑夜模式" else "已切换为白天模式",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            )

            // 8. 云加速
            SettingsItem(
                title = "云加速",
                detail = "开启",
                textColor = textColor,
                subTextColor = subTextColor,
                dividerColor = dividerColor,
                onClick = {
                    Toast.makeText(
                        context,
                        "云加速与数据节省正在运行 (${String.format("%.2f MB", repository.dataSavedMb.value)})",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            )

            // 9. 数据备份与恢复
            SettingsItem(
                title = "数据备份与恢复",
                detail = "",
                textColor = textColor,
                subTextColor = subTextColor,
                dividerColor = dividerColor,
                onClick = { showBrowserSettingsDialog = true }
            )

            // 10. 清除记录
            SettingsItem(
                title = "清除记录",
                detail = "",
                textColor = textColor,
                subTextColor = subTextColor,
                dividerColor = dividerColor,
                onClick = { showClearDialog = true }
            )

            // 11. 关于大象
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
                            repository.setNightMode(false) // Default Day mode
                            repository.setDesktopMode(false)
                            repository.setSearchEngine("google")
                            Toast.makeText(context, "已恢复为默认配置", Toast.LENGTH_SHORT).show()
                        }
                        .padding(horizontal = 24.dp, vertical = 10.dp)
                )
            }
        }
    }

    if (showBrowserSettingsDialog) {
        AlertDialog(
            onDismissRequest = { showBrowserSettingsDialog = false },
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
                    showBrowserSettingsDialog = false
                    onBackupData()
                }) { Text("导出备份") }
            },
            dismissButton = {
                TextButton(onClick = {
                    showBrowserSettingsDialog = false
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
            text = { Text("确定要清空所有浏览历史和本地缓存数据吗？此操作无法撤销。") },
            confirmButton = {
                TextButton(onClick = {
                    repository.clearAllHistory()
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

    // User-Agent Selection & Customization Dialog
    if (showUaDialog) {
        var selectedType by remember { mutableStateOf(desktopUaType) }
        var customInput by remember { mutableStateOf(customUa) }

        AlertDialog(
            onDismissRequest = { showUaDialog = false },
            title = {
                Text("电脑版 User-Agent (用户代理) 设置")
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = "切换为电脑版模式时，浏览器向网站发送的 User-Agent 标头。网站将根据此标头返回完整的电脑端桌面布局。",
                        fontSize = 12.sp,
                        color = subTextColor,
                        lineHeight = 16.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    val options = listOf(
                        Triple("windows", "Windows Chrome (推荐)", BrowserRepository.DESKTOP_WINDOWS_UA),
                        Triple("mac", "Mac Safari / Chrome (苹果电脑)", BrowserRepository.DESKTOP_MAC_UA),
                        Triple("ipad", "iPad / 平板电脑版", BrowserRepository.DESKTOP_IPAD_UA),
                        Triple("custom", "自定义 User-Agent", if (customInput.isNotBlank()) customInput else "输入自定义 UA 字符串")
                    )

                    options.forEach { (typeKey, title, sampleUa) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedType = typeKey }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selectedType == typeKey,
                                onClick = { selectedType = typeKey }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = title,
                                    fontSize = 14.sp,
                                    fontWeight = if (selectedType == typeKey) FontWeight.Bold else FontWeight.Normal,
                                    color = if (selectedType == typeKey) Color(0xFF2563EB) else textColor
                                )
                                Text(
                                    text = sampleUa,
                                    fontSize = 10.sp,
                                    color = subTextColor,
                                    maxLines = 1,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    if (selectedType == "custom") {
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = customInput,
                            onValueChange = { customInput = it },
                            label = { Text("自定义 User-Agent 字符串", fontSize = 12.sp) },
                            placeholder = { Text("例如：Mozilla/5.0 ...", fontSize = 12.sp) },
                            modifier = Modifier.fillMaxWidth(),
                            maxLines = 3
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFF2563EB).copy(alpha = 0.08f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(
                                text = "当前选定 User-Agent：",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF2563EB)
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            val activeUaString = when (selectedType) {
                                "mac" -> BrowserRepository.DESKTOP_MAC_UA
                                "ipad" -> BrowserRepository.DESKTOP_IPAD_UA
                                "custom" -> customInput.ifBlank { BrowserRepository.DESKTOP_WINDOWS_UA }
                                else -> BrowserRepository.DESKTOP_WINDOWS_UA
                            }
                            Text(
                                text = activeUaString,
                                fontSize = 10.sp,
                                color = subTextColor,
                                lineHeight = 13.sp
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    repository.setDesktopUaType(selectedType)
                    if (selectedType == "custom") {
                        repository.setCustomUserAgent(customInput.trim())
                    }
                    showUaDialog = false
                    Toast.makeText(context, "电脑版 User-Agent 已保存", Toast.LENGTH_SHORT).show()
                }) {
                    Text("保存", color = Color(0xFF2563EB), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showUaDialog = false }) {
                    Text("取消")
                }
            }
        )
    }

    // Browser Settings Dialog
    if (showBrowserSettingsDialog) {
        AlertDialog(
            onDismissRequest = { showBrowserSettingsDialog = false },
            title = { Text("浏览设置") },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                repository.setDesktopMode(!isDesktopMode)
                            }
                            .padding(vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("默认电脑版模式", fontSize = 15.sp, fontWeight = FontWeight.Medium)
                            Text(
                                if (isDesktopMode) "新建标签页默认以电脑版打开" else "新建标签页以移动端打开",
                                fontSize = 12.sp,
                                color = subTextColor
                            )
                        }
                        Switch(
                            checked = isDesktopMode,
                            onCheckedChange = { repository.setDesktopMode(it) }
                        )
                    }

                    HorizontalDivider(color = dividerColor, thickness = 0.5.dp)

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                showBrowserSettingsDialog = false
                                showUaDialog = true
                            }
                            .padding(vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("电脑版 User-Agent", fontSize = 15.sp, fontWeight = FontWeight.Medium)
                            val uaName = when (desktopUaType) {
                                "mac" -> "Mac Safari / Chrome"
                                "ipad" -> "iPad / 平板电脑"
                                "custom" -> "自定义 UA"
                                else -> "Windows Chrome"
                            }
                            Text(uaName, fontSize = 12.sp, color = Color(0xFF2563EB))
                        }
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            tint = subTextColor.copy(alpha = 0.5f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showBrowserSettingsDialog = false }) {
                    Text("完成")
                }
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
