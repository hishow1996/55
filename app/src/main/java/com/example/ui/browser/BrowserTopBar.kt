package com.example.ui.browser

import android.graphics.BitmapFactory
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
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
    repository: BrowserRepository? = null,
    modifier: Modifier = Modifier
) {
    var isEditing by remember { mutableStateOf(false) }
    var textFieldValue by remember(tab.url) {
        mutableStateOf(TextFieldValue(text = tab.url, selection = TextRange(0, tab.url.length)))
    }
    val focusManager = LocalFocusManager.current
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    BackHandler(enabled = isEditing) {
        isEditing = false
        focusManager.clearFocus()
    }

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
                shape = RoundedCornerShape(22.dp),
                color = cardBg,
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp)
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
                        BasicTextField(
                            value = textFieldValue,
                            onValueChange = { textFieldValue = it },
                            singleLine = true,
                            textStyle = TextStyle(
                                fontSize = 14.sp,
                                color = textColor,
                                fontWeight = FontWeight.Normal
                            ),
                            cursorBrush = SolidColor(if (tab.isIncognito) Color(0xFFA855F7) else Color(0xFF3B82F6)),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                            keyboardActions = KeyboardActions(
                                onSearch = {
                                    isEditing = false
                                    focusManager.clearFocus()
                                    val trimmed = textFieldValue.text.trim()
                                    if (trimmed.isNotEmpty()) {
                                        onNavigate(trimmed)
                                    }
                                },
                                onGo = {
                                    isEditing = false
                                    focusManager.clearFocus()
                                    val trimmed = textFieldValue.text.trim()
                                    if (trimmed.isNotEmpty()) {
                                        onNavigate(trimmed)
                                    }
                                }
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 4.dp),
                            decorationBox = { innerTextField ->
                                Box(contentAlignment = Alignment.CenterStart) {
                                    if (textFieldValue.text.isEmpty()) {
                                        Text(
                                            text = "搜索或输入网址",
                                            fontSize = 14.sp,
                                            color = subTextColor
                                        )
                                    }
                                    innerTextField()
                                }
                            }
                        )

                        if (textFieldValue.text.isNotEmpty()) {
                            // Copy URL button
                            IconButton(
                                onClick = {
                                    clipboardManager.setText(AnnotatedString(textFieldValue.text))
                                    Toast.makeText(context, "网址已复制到剪贴板", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.size(30.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ContentCopy,
                                    contentDescription = "复制网址",
                                    tint = subTextColor,
                                    modifier = Modifier.size(16.dp)
                                )
                            }

                            // Clear button
                            IconButton(
                                onClick = { textFieldValue = TextFieldValue("") },
                                modifier = Modifier.size(30.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "清空输入",
                                    tint = subTextColor,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }

                        // Go / Navigate button
                        IconButton(
                            onClick = {
                                isEditing = false
                                focusManager.clearFocus()
                                val trimmed = textFieldValue.text.trim()
                                if (trimmed.isNotEmpty()) {
                                    onNavigate(trimmed)
                                }
                            },
                            modifier = Modifier.size(30.dp)
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
                                    textFieldValue = TextFieldValue(
                                        text = tab.url,
                                        selection = TextRange(0, tab.url.length)
                                    )
                                    isEditing = true
                                },
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = tab.title.ifBlank { tab.url.ifBlank { "搜索或输入网址" } },
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = textColor,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (tab.url.isNotBlank()) {
                                Text(
                                    text = tab.url.removePrefix("https://").removePrefix("http://"),
                                    fontSize = 11.sp,
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

            repository?.let { repo ->
                val extensions by repo.extensionManager.extensions.collectAsState()
                var extensionMenu by remember { mutableStateOf(false) }
                var popupId by remember { mutableStateOf<String?>(null) }
                Box {
                    IconButton(onClick = { extensionMenu = true }) {
                        Icon(Icons.Default.Extension, contentDescription = "扩展", tint = textColor)
                    }
                    DropdownMenu(expanded = extensionMenu, onDismissRequest = { extensionMenu = false }) {
                        Text("扩展", modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp), fontWeight = FontWeight.SemiBold)
                        val enabled = extensions.filter { it.enabled }.sortedWith(compareByDescending<com.example.extension.BrowserExtension> { repo.extensionManager.isPinned(it.id) }.thenBy { it.name.lowercase() })
                        if (enabled.isEmpty()) {
                            Text("暂无已启用扩展", modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp), color = subTextColor)
                        } else {
                            enabled.forEach { ext ->
                                DropdownMenuItem(
                                    text = { Text(ext.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                    leadingIcon = { Icon(Icons.Default.Extension, null) },
                                    onClick = { extensionMenu = false; if (ext.manifest.popup != null) popupId = ext.id }
                                )
                            }
                        }
                    }
                    popupId?.let { id ->
                        val url = repo.extensionManager.popupUrl(id)
                        val ext = repo.extensionManager.extension(id)
                        if (url != null && ext != null) {
                            AlertDialog(
                                onDismissRequest = { popupId = null },
                                title = { Text(ext.name) },
                                text = {
                                    androidx.compose.ui.viewinterop.AndroidView(
                                        factory = { ctx ->
                                            android.webkit.WebView(ctx).apply {
                                                settings.javaScriptEnabled = true
                                                settings.domStorageEnabled = true
                                                settings.allowFileAccess = true
                                                settings.allowContentAccess = true
                                                webViewClient = android.webkit.WebViewClient()
                                                repo.extensionManager.prepareExtensionPage(this, id, "popup")
                                                loadUrl(url)
                                            }
                                        },
                                        modifier = Modifier.fillMaxWidth().height(430.dp)
                                    )
                                },
                                confirmButton = { androidx.compose.material3.TextButton(onClick = { popupId = null }) { Text("关闭") } }
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

