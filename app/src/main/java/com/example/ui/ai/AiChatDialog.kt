package com.example.ui.ai

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class ChatMessage(val sender: String, val message: String)

@Composable
fun AiChatDialog(
    currentPageTitle: String,
    currentPageUrl: String,
    onDismiss: () -> Unit,
    onSummarizeWebpage: () -> Unit,
    onTranslateWebpage: () -> Unit
) {
    val messages = remember {
        mutableStateListOf(
            ChatMessage("ai", "你好！我是大象智能助手。关于当前网页内容，你有什么想了解的吗？我可以帮你提取摘要、翻译全文或回答问题。")
        )
    }
    var input by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = Color(0xFF6366F1),
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("问AI · 网页助手", fontSize = 17.sp, fontWeight = FontWeight.Bold)
                }
                IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) {
                    Icon(imageVector = Icons.Default.Close, contentDescription = "关闭", modifier = Modifier.size(18.dp))
                }
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Quick Action Chips
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFFEEF2FF),
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                messages.add(ChatMessage("user", "请帮我总结当前网页的重点内容"))
                                messages.add(ChatMessage("ai", "【网页重点总结】\n网页标题：$currentPageTitle\n链接：$currentPageUrl\n\n1. 本页主要呈现了核心业务内容与媒体资源；\n2. 包含视频媒体内容，支持使用大象悬浮播放器进行无缝播放；\n3. 支持一键翻译为简体中文阅读。"))
                                onSummarizeWebpage()
                            }
                    ) {
                        Text(
                            text = "💡 总结网页",
                            color = Color(0xFF4F46E5),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFFF0FDF4),
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                messages.add(ChatMessage("user", "翻译当前网页"))
                                messages.add(ChatMessage("ai", "已为您启动网页全文翻译系统，正在将页面外语内容替换为简体中文！"))
                                onTranslateWebpage()
                            }
                    ) {
                        Text(
                            text = "🌐 翻译全文",
                            color = Color(0xFF16A34A),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }

                // Chat Log
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(messages) { msg ->
                        val isAi = msg.sender == "ai"
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = if (isAi) Arrangement.Start else Arrangement.End
                        ) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (isAi) Color(0xFFF1F5F9) else Color(0xFF6366F1),
                                modifier = Modifier.fillMaxWidth(0.85f)
                            ) {
                                Text(
                                    text = msg.message,
                                    fontSize = 13.sp,
                                    color = if (isAi) Color(0xFF1E293B) else Color.White,
                                    modifier = Modifier.padding(10.dp),
                                    lineHeight = 18.sp
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Input Box
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = input,
                        onValueChange = { input = it },
                        placeholder = { Text("向AI提问...", fontSize = 13.sp) },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    IconButton(
                        onClick = {
                            if (input.isNotBlank()) {
                                val userQuery = input
                                messages.add(ChatMessage("user", userQuery))
                                input = ""
                                val reply = when {
                                    userQuery.contains("悬浮") || userQuery.contains("播放") ->
                                        "大象浏览器支持内置悬浮窗播放器，可上下左右任意拖拽调整窗口大小，也可以点击画中画按钮开启系统级全局悬浮！"
                                    userQuery.contains("插件") ->
                                        "大象内置了油猴/JavaScript自定义插件系统，您可以在设置或菜单的【插件扩展】中自由添加脚本。"
                                    userQuery.contains("翻译") ->
                                        "大象支持网页全文翻译，点击菜单或顶部栏的【网页翻译】即可将整个页面自动转换为中文。"
                                    else ->
                                        "为您查询到关于“$userQuery”的相关内容。大象浏览器拥有极速Chromium内核，并支持无痕保护。"
                                }
                                messages.add(ChatMessage("ai", reply))
                            }
                        }
                    ) {
                        Icon(imageVector = Icons.Default.Send, contentDescription = "发送", tint = Color(0xFF6366F1))
                    }
                }
            }
        },
        confirmButton = {}
    )
}
