package com.example.ui.menu

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Brightness4
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Opacity
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SmartDisplay
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowserBottomSheetMenu(
    isNightMode: Boolean,
    isDesktopMode: Boolean,
    isIncognito: Boolean,
    isTranslated: Boolean,
    dataSavedMb: Float,
    onDismiss: () -> Unit,
    onOpenBookmarks: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenDownloads: () -> Unit,
    onOpenPlugins: () -> Unit,
    onOpenFloatingPlayer: () -> Unit,
    onBookmarkPage: () -> Unit,
    onToggleNightMode: () -> Unit,
    onReload: () -> Unit,
    onToggleDesktopMode: () -> Unit,
    onToggleTranslation: () -> Unit,
    onShare: () -> Unit,
    onOpenSettings: () -> Unit,
    onExit: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val sheetBg = if (isNightMode) Color(0xFF1E232B) else Color(0xFFFFFFFF)
    val textColor = if (isNightMode) Color(0xFFE2E8F0) else Color(0xFF1E293B)
    val subTextColor = if (isNightMode) Color(0xFF94A3B8) else Color(0xFF64748B)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = sheetBg,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        dragHandle = null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            // --- TOP STATUS ROW (Data Saving Pill + Incognito Button) ---
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left: Data Saving Pill (Image 3 style: "27.04 MB 已节省")
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = if (isNightMode) Color(0xFF28303C) else Color(0xFFF1F5F9),
                    modifier = Modifier.padding(vertical = 4.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = Color(0xFF0EA5E9),
                            modifier = Modifier.size(24.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Opacity,
                                    contentDescription = "节省流量",
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = String.format("%.2f MB", dataSavedMb),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF0284C7)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "已节省",
                            fontSize = 11.sp,
                            color = subTextColor
                        )
                    }
                }

                // Right: Incognito Mode Button (Ghost/Stealth icon)
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = if (isIncognito) Color(0xFF6366F1) else (if (isNightMode) Color(0xFF28303C) else Color(0xFFF1F5F9)),
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .clickable(onClick = {
                            onDismiss()
                            // Handled by VM toggleIncognito
                        })
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.VisibilityOff,
                            contentDescription = "无痕模式",
                            tint = if (isIncognito) Color.White else subTextColor,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isIncognito) "无痕已开启" else "无痕模式",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = if (isIncognito) Color.White else textColor
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // --- PRIMARY CIRCULAR TOOLS ROW (Image 3: 书签, 历史, 下载, 主题/插件, 悬浮播放器) ---
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                MenuCircleButton(
                    title = "书签",
                    icon = Icons.Default.Bookmark,
                    bgColor = Color(0xFF06B6D4), // Cyan
                    textColor = textColor,
                    onClick = { onDismiss(); onOpenBookmarks() }
                )
                MenuCircleButton(
                    title = "历史",
                    icon = Icons.Default.History,
                    bgColor = Color(0xFFF59E0B), // Amber
                    textColor = textColor,
                    onClick = { onDismiss(); onOpenHistory() }
                )
                MenuCircleButton(
                    title = "下载",
                    icon = Icons.Default.Download,
                    bgColor = Color(0xFF3B82F6), // Blue
                    textColor = textColor,
                    onClick = { onDismiss(); onOpenDownloads() }
                )
                MenuCircleButton(
                    title = "插件扩展",
                    icon = Icons.Default.Extension,
                    bgColor = Color(0xFFEC4899), // Pink
                    textColor = textColor,
                    onClick = { onDismiss(); onOpenPlugins() }
                )
                MenuCircleButton(
                    title = "悬浮播放",
                    icon = Icons.Default.PictureInPictureAlt,
                    bgColor = Color(0xFF8B5CF6), // Violet
                    textColor = textColor,
                    onClick = { onDismiss(); onOpenFloatingPlayer() }
                )
            }

            Spacer(modifier = Modifier.height(22.dp))

            // --- SECONDARY UTILITY ACTIONS ROW (Image 3: 加入书签, 夜间/白天, 刷新, 电脑版, 翻译, 分享) ---
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                MenuSmallItem(
                    title = "加入书签",
                    icon = Icons.Default.StarBorder,
                    iconTint = subTextColor,
                    textColor = textColor,
                    onClick = { onDismiss(); onBookmarkPage() }
                )
                MenuSmallItem(
                    title = if (isNightMode) "日间模式" else "夜间模式",
                    icon = if (isNightMode) Icons.Default.LightMode else Icons.Default.DarkMode,
                    iconTint = if (isNightMode) Color(0xFFF59E0B) else subTextColor,
                    textColor = textColor,
                    onClick = onToggleNightMode
                )
                MenuSmallItem(
                    title = "刷新",
                    icon = Icons.Default.Refresh,
                    iconTint = subTextColor,
                    textColor = textColor,
                    onClick = { onDismiss(); onReload() }
                )
                MenuSmallItem(
                    title = if (isDesktopMode) "手机版" else "电脑版",
                    icon = if (isDesktopMode) Icons.Default.PhoneAndroid else Icons.Default.Computer,
                    iconTint = if (isDesktopMode) Color(0xFF3B82F6) else subTextColor,
                    textColor = textColor,
                    onClick = { onDismiss(); onToggleDesktopMode() }
                )
                MenuSmallItem(
                    title = if (isTranslated) "显示原文" else "网页翻译",
                    icon = Icons.Default.Translate,
                    iconTint = if (isTranslated) Color(0xFF0284C7) else subTextColor,
                    textColor = textColor,
                    onClick = { onDismiss(); onToggleTranslation() }
                )
                MenuSmallItem(
                    title = "分享",
                    icon = Icons.Default.Share,
                    iconTint = subTextColor,
                    textColor = textColor,
                    onClick = { onDismiss(); onShare() }
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Bottom Divider
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(0.8.dp)
                    .background(if (isNightMode) Color(0xFF334155) else Color(0xFFF1F5F9))
            )

            Spacer(modifier = Modifier.height(10.dp))

            // --- BOTTOM CONTROL BAR (Settings, Down Arrow / Collapse, Power / Exit) ---
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { onDismiss(); onOpenSettings() }) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "设置",
                        tint = subTextColor,
                        modifier = Modifier.size(24.dp)
                    )
                }

                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = "收起",
                        tint = subTextColor,
                        modifier = Modifier.size(28.dp)
                    )
                }

                IconButton(onClick = { onDismiss(); onExit() }) {
                    Icon(
                        imageVector = Icons.Default.PowerSettingsNew,
                        contentDescription = "退出",
                        tint = subTextColor,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun MenuCircleButton(
    title: String,
    icon: ImageVector,
    bgColor: Color,
    textColor: Color,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(4.dp)
    ) {
        Surface(
            shape = CircleShape,
            color = bgColor,
            shadowElevation = 4.dp,
            modifier = Modifier.size(46.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = title,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = textColor,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun MenuSmallItem(
    title: String,
    icon: ImageVector,
    iconTint: Color,
    textColor: Color,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(52.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = title,
            tint = iconTint,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = title,
            fontSize = 11.sp,
            color = textColor,
            textAlign = TextAlign.Center,
            maxLines = 1
        )
    }
}
