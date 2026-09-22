package com.example.ui.browser

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DesktopMac
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.BrowserTab
import com.example.model.VideoMediaInfo

@Composable
fun BrowserTopBar(
    tab: BrowserTab,
    detectedVideo: VideoMediaInfo?,
    translationStatus: String?,
    isNightMode: Boolean,
    onNavigate: (String) -> Unit,
    onReload: () -> Unit,
    onStop: () -> Unit,
    onToggleTranslation: () -> Unit,
    onDismissTranslation: () -> Unit,
    onOpenFloatingPlayer: () -> Unit,
    onToggleDesktopMode: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var isEditing by remember { mutableStateOf(false) }
    var inputUrl by remember(tab.url) { mutableStateOf(tab.url) }
    val focusManager = LocalFocusManager.current

    val barBg = if (tab.isIncognito) {
        Color(0xFF1E1B2E) // Incognito stealth purple/dark
    } else if (isNightMode) {
        Color(0xFF161A20)
    } else {
        Color(0xFFFFFFFF)
    }

    val cardBg = if (tab.isIncognito) {
        Color(0xFF2D2845)
    } else if (isNightMode) {
        Color(0xFF232832)
    } else {
        Color(0xFFF1F5F9)
    }

    val textColor = if (tab.isIncognito || isNightMode) Color(0xFFF1F5F9) else Color(0xFF1E293B)
    val subTextColor = if (tab.isIncognito || isNightMode) Color(0xFF94A3B8) else Color(0xFF64748B)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(barBg)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // URL Capsule
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = cardBg,
                modifier = Modifier
                    .weight(1f)
                    .height(42.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 10.dp)
                ) {
                    // Security / Incognito Icon
                    Icon(
                        imageVector = if (tab.isIncognito) Icons.Default.Security else Icons.Default.Lock,
                        contentDescription = "安全状态",
                        tint = if (tab.isIncognito) Color(0xFFA855F7) else Color(0xFF10B981),
                        modifier = Modifier.size(16.dp)
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    if (isEditing) {
                        TextField(
                            value = inputUrl,
                            onValueChange = { inputUrl = it },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(
                                onSearch = {
                                    isEditing = false
                                    focusManager.clearFocus()
                                    val trimmed = inputUrl.trim()
                                    if (trimmed.isNotEmpty()) {
                                        onNavigate(trimmed)
                                    }
                                },
                                onGo = {
                                    isEditing = false
                                    focusManager.clearFocus()
                                    val trimmed = inputUrl.trim()
                                    if (trimmed.isNotEmpty()) {
                                        onNavigate(trimmed)
                                    }
                                }
                            ),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                disabledContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent
                            ),
                            modifier = Modifier.weight(1f)
                        )

                        if (inputUrl.isNotEmpty()) {
                            IconButton(
                                onClick = { inputUrl = "" },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "清空输入",
                                    tint = subTextColor,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }

                        IconButton(
                            onClick = {
                                isEditing = false
                                focusManager.clearFocus()
                                val trimmed = inputUrl.trim()
                                if (trimmed.isNotEmpty()) {
                                    onNavigate(trimmed)
                                }
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = "前往",
                                tint = Color(0xFF3B82F6),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    } else {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .clickable {
                                    inputUrl = tab.url
                                    isEditing = true
                                }
                        ) {
                            Text(
                                text = tab.title.ifBlank { tab.url },
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = textColor,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (tab.url.isNotBlank()) {
                                Text(
                                    text = tab.url.removePrefix("https://").removePrefix("http://"),
                                    fontSize = 10.sp,
                                    color = subTextColor,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        // Refresh or Stop button
                        IconButton(
                            onClick = {
                                if (tab.isLoading) onStop() else onReload()
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = if (tab.isLoading) Icons.Default.Close else Icons.Default.Refresh,
                                contentDescription = if (tab.isLoading) "停止" else "刷新",
                                tint = subTextColor,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }

        // Animated Web Loading Progress Bar
        if (tab.isLoading && tab.progress in 1..99) {
            LinearProgressIndicator(
                progress = { tab.progress / 100f },
                color = if (tab.isIncognito) Color(0xFFA855F7) else Color(0xFF3B82F6),
                trackColor = Color.Transparent,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp)
            )
        }

        // Webpage Full Translation Status Banner
        AnimatedVisibility(
            visible = translationStatus != null,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFFE0F2FE))
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Translate,
                        contentDescription = "翻译",
                        tint = Color(0xFF0284C7),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = translationStatus ?: "",
                        fontSize = 12.sp,
                        color = Color(0xFF0369A1),
                        fontWeight = FontWeight.Medium
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (tab.isTranslated) "显示原文" else "再次翻译",
                        color = Color(0xFF0284C7),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .clickable(onClick = onToggleTranslation)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "关闭翻译栏",
                        tint = Color(0xFF0284C7),
                        modifier = Modifier
                            .size(16.dp)
                            .clickable(onClick = onDismissTranslation)
                    )
                }
            }
        }
    }
}
