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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Divider
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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

@Composable
fun SettingsScreen(
    repository: BrowserRepository,
    isNightMode: Boolean,
    isDesktopMode: Boolean,
    searchEngine: String,
    onBack: () -> Unit,
    onOpenPluginManager: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var showClearDialog by remember { mutableStateOf(false) }
    var showSearchEngineDialog by remember { mutableStateOf(false) }
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
        // --- TOP BAR WITH BACK ARROW AND TITLE "设置" (Image 4) ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 10.dp),
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
                fontWeight = FontWeight.Bold,
                color = textColor,
                modifier = Modifier.padding(start = 4.dp)
            )
        }

        HorizontalDivider(color = dividerColor, thickness = 1.dp)

        // --- SETTINGS LIST (Image 4 Style) ---
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
        ) {
            SettingsItem(
                title = "广告设定与过滤",
                detail = if (repository.isAdBlockEnabled.value) "已开启强力拦截" else "已关闭",
                textColor = textColor,
                subTextColor = subTextColor,
                dividerColor = dividerColor,
                onClick = {
                    val newState = !repository.isAdBlockEnabled.value
                    repository.setAdBlockEnabled(newState)
                    Toast.makeText(context, if (newState) "广告拦截已开启" else "广告拦截已关闭", Toast.LENGTH_SHORT).show()
                }
            )

            SettingsItem(
                title = "插件扩展系统",
                detail = "${repository.plugins.value.count { it.isEnabled }} 个已启用",
                textColor = textColor,
                subTextColor = subTextColor,
                dividerColor = dividerColor,
                onClick = onOpenPluginManager
            )

            SettingsItem(
                title = "悬浮窗与播放器权限",
                detail = if (canDrawOverlays) "全局悬浮已授权" else "点击授权系统悬浮窗",
                textColor = textColor,
                subTextColor = if (canDrawOverlays) Color(0xFF10B981) else Color(0xFFEF4444),
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
                        Toast.makeText(context, "悬浮窗与画中画功能已就绪", Toast.LENGTH_SHORT).show()
                    }
                }
            )

            SettingsItem(
                title = "浏览设置",
                detail = if (isDesktopMode) "默认电脑端模式" else "默认移动端模式",
                textColor = textColor,
                subTextColor = subTextColor,
                dividerColor = dividerColor,
                onClick = {
                    repository.setDesktopMode(!isDesktopMode)
                    Toast.makeText(context, "已切换为${if (!isDesktopMode) "电脑端" else "移动端"}模式", Toast.LENGTH_SHORT).show()
                }
            )

            SettingsItem(
                title = "搜索引擎",
                detail = when (searchEngine) {
                    "baidu" -> "百度"
                    "bing" -> "微软必应"
                    "360" -> "360 搜索"
                    "sogou" -> "搜狗搜索"
                    else -> "谷歌 Google"
                },
                textColor = textColor,
                subTextColor = subTextColor,
                dividerColor = dividerColor,
                onClick = { showSearchEngineDialog = true }
            )

            SettingsItem(
                title = "网页全文翻译",
                detail = "目标语言：简体中文 (智能DOM翻译)",
                textColor = textColor,
                subTextColor = subTextColor,
                dividerColor = dividerColor,
                onClick = {
                    Toast.makeText(context, "在网页菜单中可一键翻译全文", Toast.LENGTH_SHORT).show()
                }
            )

            SettingsItem(
                title = "主题与色彩",
                detail = if (isNightMode) "黑夜模式" else "白天模式 (默认)",
                textColor = textColor,
                subTextColor = subTextColor,
                dividerColor = dividerColor,
                onClick = {
                    repository.setNightMode(!isNightMode)
                }
            )

            SettingsItem(
                title = "数据节省与云加速",
                detail = "开启 · ${String.format("%.2f MB", repository.dataSavedMb.value)}",
                textColor = textColor,
                subTextColor = Color(0xFF0284C7),
                dividerColor = dividerColor,
                onClick = {
                    Toast.makeText(context, "数据节省与智能广告压缩正在运行", Toast.LENGTH_SHORT).show()
                }
            )

            SettingsItem(
                title = "清除记录与缓存",
                detail = "历史记录、缓存及Cookie",
                textColor = textColor,
                subTextColor = subTextColor,
                dividerColor = dividerColor,
                onClick = { showClearDialog = true }
            )

            SettingsItem(
                title = "关于大象",
                detail = "V1.0.0 (Chromium 内核)",
                textColor = textColor,
                subTextColor = subTextColor,
                dividerColor = dividerColor,
                onClick = { showAboutDialog = true }
            )

            Spacer(modifier = Modifier.height(28.dp))

            // Centered "恢复默认" button (Image 4 bottom)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "恢复默认",
                    fontSize = 14.sp,
                    color = subTextColor,
                    modifier = Modifier
                        .clickable {
                            repository.setNightMode(false) // Default Day mode
                            repository.setDesktopMode(false)
                            repository.setSearchEngine("google")
                            repository.setAdBlockEnabled(true)
                            Toast.makeText(context, "已恢复为默认配置", Toast.LENGTH_SHORT).show()
                        }
                        .padding(horizontal = 24.dp, vertical = 8.dp)
                )
            }
        }
    }

    // Clear History Dialog
    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("清除浏览记录") },
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
            title = { Text("选择默认搜索引擎") },
            text = {
                Column {
                    listOf(
                        "google" to "谷歌 (Google)",
                        "baidu" to "百度 (Baidu)",
                        "bing" to "微软必应 (Bing)",
                        "360" to "360 搜索",
                        "sogou" to "搜狗搜索"
                    ).forEach { (key, label) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    repository.setSearchEngine(key)
                                    showSearchEngineDialog = false
                                }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = label,
                                fontSize = 15.sp,
                                fontWeight = if (searchEngine == key) FontWeight.Bold else FontWeight.Normal,
                                color = if (searchEngine == key) Color(0xFF2563EB) else textColor
                            )
                        }
                    }
                }
            },
            confirmButton = {}
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
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                fontSize = 15.sp,
                fontWeight = FontWeight.Normal,
                color = textColor
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (detail.isNotBlank()) {
                    Text(
                        text = detail,
                        fontSize = 13.sp,
                        color = subTextColor,
                        modifier = Modifier.padding(end = 6.dp)
                    )
                }
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = subTextColor.copy(alpha = 0.6f),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        HorizontalDivider(color = dividerColor, thickness = 0.8.dp)
    }
}
