package com.example.ui.ai

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ElephantAiScreen(
    isNightMode: Boolean,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    val messages = remember {
        mutableStateListOf(
            ChatMessageItem(
                sender = "ai",
                text = "你好！我是大象智能 AI 助手。\n\n我拥有广泛的通用专业知识，可以为你解答各类百科常识、协助撰写文章文案、翻译多国语言、编写与调试代码，或者协助你整理灵感与生活规划。\n\n你可以随时向我提问任何问题！"
            )
        )
    }

    var inputText by remember { mutableStateOf("") }
    var isGenerating by remember { mutableStateOf(false) }

    // Theme Colors
    val bg = if (isNightMode) Color(0xFF111418) else Color(0xFFF8FAFC)
    val cardBg = if (isNightMode) Color(0xFF1E232B) else Color(0xFFFFFFFF)
    val borderColor = if (isNightMode) Color(0xFF2E3540) else Color(0xFFE2E8F0)
    val textColor = if (isNightMode) Color(0xFFF1F5F9) else Color(0xFF1E293B)
    val secondaryText = if (isNightMode) Color(0xFF94A3B8) else Color(0xFF64748B)
    val primaryAccent = Color(0xFF2563EB) // Elephant Brand Blue

    fun copyToClipboard(text: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Elephant AI Reply", text)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(context, "已复制回答内容", Toast.LENGTH_SHORT).show()
    }

    fun submitQuery(query: String) {
        val trimmed = query.trim()
        if (trimmed.isBlank() || isGenerating) return

        messages.add(ChatMessageItem(sender = "user", text = trimmed))
        inputText = ""
        isGenerating = true

        coroutineScope.launch {
            // Scroll to bottom
            listState.animateScrollToItem(messages.size)

            val reply = GeminiAiService.sendMessage(
                history = messages.toList(),
                newUserPrompt = trimmed
            )

            messages.add(ChatMessageItem(sender = "ai", text = reply))
            isGenerating = false

            // Scroll to newly added answer
            listState.animateScrollToItem(messages.size)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(bg)
            .statusBarsPadding()
    ) {
        // --- TOP APP BAR ---
        Surface(
            color = cardBg,
            shadowElevation = 1.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "返回",
                        tint = textColor
                    )
                }

                Spacer(modifier = Modifier.width(4.dp))

                // Avatar and Title
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(if (isNightMode) Color(0xFF2D3748) else Color(0xFFEFF6FF)),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.ic_elephant),
                        contentDescription = "大象图标",
                        modifier = Modifier.size(22.dp)
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "大象 AI 助手",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = textColor
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color(0xFF2563EB).copy(alpha = 0.12f))
                                .padding(horizontal = 5.dp, vertical = 1.5.dp)
                        ) {
                            Text(
                                text = "Gemini 3.5",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = primaryAccent
                            )
                        }
                    }
                    Text(
                        text = "通用智能 · 独立问答与创作",
                        fontSize = 11.sp,
                        color = secondaryText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Clear Chat Action
                IconButton(
                    onClick = {
                        messages.clear()
                        messages.add(
                            ChatMessageItem(
                                sender = "ai",
                                text = "新对话已开启！有什么我可以帮你的吗？你可以向我提问任何问题。"
                            )
                        )
                        Toast.makeText(context, "已开启新对话", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteSweep,
                        contentDescription = "开启新对话",
                        tint = secondaryText
                    )
                }
            }
        }

        HorizontalDivider(color = borderColor, thickness = 0.5.dp)

        // --- CONVERSATION / CHAT BODY ---
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item { Spacer(modifier = Modifier.height(8.dp)) }

                // Initial Inspiration Topic Cards (when conversation has only initial greeting)
                if (messages.size <= 1) {
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(CircleShape)
                                    .background(if (isNightMode) Color(0xFF242C38) else Color(0xFFDBEAFE)),
                                contentAlignment = Alignment.Center
                            ) {
                                Image(
                                    painter = painterResource(id = R.drawable.ic_elephant),
                                    contentDescription = null,
                                    modifier = Modifier.size(38.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "探索大象 AI 的无限可能",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = textColor
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "点击下方推荐话题快速提问，或在底部直接输入任意问题",
                                fontSize = 12.sp,
                                color = secondaryText
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            // Inspiration Prompts Chips
                            val promptSuggestions = listOf(
                                "💡 通俗解释量子力学的核心原理",
                                "✈️ 帮我做一份3天休闲旅游攻略",
                                "💻 用 Kotlin 写一个二分查找算法",
                                "🌐 翻译“千里之行，始于足下”并解析",
                                "🥗 推荐一份科学减脂一日三餐饮食谱",
                                "🧠 为科技品牌构思5个有创意的口号"
                            )

                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Center,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                promptSuggestions.forEach { promptText ->
                                    Surface(
                                        shape = RoundedCornerShape(18.dp),
                                        color = cardBg,
                                        border = androidx.compose.foundation.BorderStroke(1.dp, borderColor),
                                        modifier = Modifier
                                            .padding(horizontal = 4.dp)
                                            .clip(RoundedCornerShape(18.dp))
                                            .clickable { submitQuery(promptText) }
                                    ) {
                                        Text(
                                            text = promptText,
                                            fontSize = 12.sp,
                                            color = textColor,
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Chat Messages
                items(messages, key = { it.id }) { msg ->
                    val isUser = msg.sender == "user"
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
                        verticalAlignment = Alignment.Top
                    ) {
                        if (!isUser) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(if (isNightMode) Color(0xFF2D3748) else Color(0xFFEFF6FF)),
                                contentAlignment = Alignment.Center
                            ) {
                                Image(
                                    painter = painterResource(id = R.drawable.ic_elephant),
                                    contentDescription = "AI",
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                        }

                        Column(
                            horizontalAlignment = if (isUser) Alignment.End else Alignment.Start,
                            modifier = Modifier.fillMaxWidth(if (isUser) 0.85f else 0.88f)
                        ) {
                            Surface(
                                shape = if (isUser) {
                                    RoundedCornerShape(18.dp, 18.dp, 4.dp, 18.dp)
                                } else {
                                    RoundedCornerShape(18.dp, 18.dp, 18.dp, 4.dp)
                                },
                                color = if (isUser) primaryAccent else cardBg,
                                border = if (!isUser) androidx.compose.foundation.BorderStroke(1.dp, borderColor) else null,
                                shadowElevation = if (isUser) 0.dp else 1.dp
                            ) {
                                Text(
                                    text = msg.text,
                                    fontSize = 14.sp,
                                    color = if (isUser) Color.White else textColor,
                                    lineHeight = 22.sp,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)
                                )
                            }

                            // Copy button for AI replies
                            if (!isUser) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .padding(top = 4.dp, start = 4.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable { copyToClipboard(msg.text) }
                                        .padding(horizontal = 6.dp, vertical = 3.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ContentCopy,
                                        contentDescription = "复制",
                                        tint = secondaryText,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "复制内容",
                                        fontSize = 11.sp,
                                        color = secondaryText
                                    )
                                }
                            }
                        }
                    }
                }

                // AI Generating/Thinking State
                if (isGenerating) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Start,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(if (isNightMode) Color(0xFF2D3748) else Color(0xFFEFF6FF)),
                                contentAlignment = Alignment.Center
                            ) {
                                Image(
                                    painter = painterResource(id = R.drawable.ic_elephant),
                                    contentDescription = "AI",
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))

                            Surface(
                                shape = RoundedCornerShape(18.dp, 18.dp, 18.dp, 4.dp),
                                color = cardBg,
                                border = androidx.compose.foundation.BorderStroke(1.dp, borderColor)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(14.dp),
                                        strokeWidth = 2.dp,
                                        color = primaryAccent
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = "大象 AI 正在思考回答...",
                                        fontSize = 13.sp,
                                        color = secondaryText
                                    )
                                }
                            }
                        }
                    }
                }

                item { Spacer(modifier = Modifier.height(12.dp)) }
            }
        }

        // --- BOTTOM INPUT AREA ---
        Surface(
            color = cardBg,
            shadowElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                HorizontalDivider(color = borderColor, thickness = 0.5.dp)

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Capsule input box matching Browser Home Search style
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .clip(RoundedCornerShape(24.dp))
                            .background(if (isNightMode) Color(0xFF262C36) else Color(0xFFF1F5F9))
                            .border(1.dp, borderColor, RoundedCornerShape(24.dp))
                            .padding(horizontal = 14.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = primaryAccent,
                                modifier = Modifier.size(18.dp)
                            )

                            Spacer(modifier = Modifier.width(8.dp))

                            Box(modifier = Modifier.weight(1f)) {
                                if (inputText.isEmpty()) {
                                    Text(
                                        text = "向大象 AI 提问任何问题...",
                                        fontSize = 14.sp,
                                        color = secondaryText,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                BasicTextField(
                                    value = inputText,
                                    onValueChange = { inputText = it },
                                    textStyle = TextStyle(
                                        color = textColor,
                                        fontSize = 14.sp
                                    ),
                                    cursorBrush = SolidColor(primaryAccent),
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }

                            if (inputText.isNotEmpty()) {
                                IconButton(
                                    onClick = { inputText = "" },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Clear,
                                        contentDescription = "清空输入",
                                        tint = secondaryText,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    // Send Button
                    val canSend = inputText.isNotBlank() && !isGenerating
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(if (canSend) primaryAccent else Color(0xFF94A3B8).copy(alpha = 0.4f))
                            .clickable(enabled = canSend) {
                                submitQuery(inputText)
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = "发送",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}
