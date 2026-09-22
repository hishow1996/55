package com.example.ui.search

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.engine.SearchSuggestionManager
import com.example.model.SearchEngines
import com.example.ui.home.SearchEngineLogo
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SearchOverlayScreen(
    searchEngine: String,
    searchHistory: List<String>,
    isNightMode: Boolean,
    onSearch: (String) -> Unit,
    onDeleteHistoryItem: (String) -> Unit,
    onClearAllHistory: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    var query by remember { mutableStateOf("") }
    var suggestions by remember { mutableStateOf<List<String>>(emptyList()) }
    var itemToDelete by remember { mutableStateOf<String?>(null) }
    var showClearAllConfirm by remember { mutableStateOf(false) }

    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val coroutineScope = rememberCoroutineScope()
    var searchJob by remember { mutableStateOf<Job?>(null) }

    val engineInfo = SearchEngines.getById(searchEngine)
    val placeholderText = "在 ${engineInfo.shortName} 中搜索或输入网址"

    val bgColor = if (isNightMode) Color(0xFF121212) else Color(0xFFFFFFFF)
    val cardBg = if (isNightMode) Color(0xFF1E1E1E) else Color(0xFFF1F3F4)
    val textColor = if (isNightMode) Color(0xFFE8EAED) else Color(0xFF202124)
    val subTextColor = if (isNightMode) Color(0xFF9AA0A6) else Color(0xFF5F6368)
    val dividerColor = if (isNightMode) Color(0xFF2D2F31) else Color(0xFFE8EAED)

    // Hardware/System back handler
    BackHandler {
        onClose()
    }

    // Auto-focus and open keyboard on enter
    LaunchedEffect(Unit) {
        delay(100)
        focusRequester.requestFocus()
        keyboardController?.show()
    }

    // Dynamic search suggestions while typing (边输入边联想)
    LaunchedEffect(query) {
        searchJob?.cancel()
        val trimmed = query.trim()
        if (trimmed.isEmpty()) {
            suggestions = emptyList()
        } else {
            searchJob = coroutineScope.launch {
                delay(120) // Snappy debounce
                suggestions = SearchSuggestionManager.fetchSuggestions(
                    query = trimmed,
                    searchEngine = searchEngine,
                    localHistory = searchHistory
                )
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(bgColor)
            .statusBarsPadding()
            .imePadding()
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // --- TOP SEARCH BAR (FIGURE 2 DESIGN WITHOUT RIGHT BUTTONS) ---
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Back Arrow Button
                IconButton(
                    onClick = onClose,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "返回",
                        tint = textColor,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Spacer(modifier = Modifier.width(4.dp))

                // Search Box Container
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = cardBg,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Left Search Engine Logo (e.g. Google G logo from Figure 2)
                        SearchEngineLogo(
                            engine = searchEngine,
                            modifier = Modifier.size(22.dp)
                        )

                        Spacer(modifier = Modifier.width(10.dp))

                        // Text Input Field
                        Box(modifier = Modifier.weight(1f)) {
                            if (query.isEmpty()) {
                                Text(
                                    text = placeholderText,
                                    color = subTextColor,
                                    fontSize = 15.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            BasicTextField(
                                value = query,
                                onValueChange = { query = it },
                                singleLine = true,
                                textStyle = TextStyle(
                                    color = textColor,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Normal
                                ),
                                cursorBrush = SolidColor(if (isNightMode) Color(0xFF8AB4F8) else Color(0xFF1A73E8)),
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                keyboardActions = KeyboardActions(
                                    onSearch = {
                                        val trimmed = query.trim()
                                        if (trimmed.isNotEmpty()) {
                                            keyboardController?.hide()
                                            onSearch(trimmed)
                                        }
                                    }
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .focusRequester(focusRequester)
                            )
                        }

                        // Clear input button (ONLY show 'X' when text entered, NO mic/lens buttons as requested)
                        if (query.isNotEmpty()) {
                            IconButton(
                                onClick = { query = "" },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "清除内容",
                                    tint = subTextColor,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }

            HorizontalDivider(color = dividerColor, thickness = 0.5.dp)

            // --- SUGGESTIONS OR SEARCH HISTORY LIST ---
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                if (query.isNotBlank()) {
                    // Real-time Autocomplete / Search Suggestions (边输入边联想)
                    items(suggestions, key = { "sug_$it" }) { suggestion ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    keyboardController?.hide()
                                    onSearch(suggestion)
                                }
                                .padding(horizontal = 16.dp, vertical = 13.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Left: Search icon
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = "联想搜索",
                                tint = subTextColor,
                                modifier = Modifier.size(20.dp)
                            )

                            Spacer(modifier = Modifier.width(16.dp))

                            // Middle: Suggestion text
                            Text(
                                text = suggestion,
                                fontSize = 15.sp,
                                color = textColor,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )

                            // Right: ↖ North-West arrow to copy to search box
                            IconButton(
                                onClick = { query = suggestion },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_arrow_northwest),
                                    contentDescription = "填入搜索框",
                                    tint = subTextColor,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                } else {
                    // Search History (搜索历史，图二模式，长按删除单个，底部全部清除)
                    if (searchHistory.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 48.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "暂无搜索历史",
                                    color = subTextColor.copy(alpha = 0.7f),
                                    fontSize = 14.sp
                                )
                            }
                        }
                    } else {
                        items(searchHistory, key = { "hist_$it" }) { historyItem ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .combinedClickable(
                                        onClick = {
                                            keyboardController?.hide()
                                            onSearch(historyItem)
                                        },
                                        onLongClick = {
                                            itemToDelete = historyItem
                                        }
                                    )
                                    .padding(horizontal = 16.dp, vertical = 13.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Left: History clock icon matching Figure 2
                                Icon(
                                    imageVector = Icons.Default.History,
                                    contentDescription = "搜索历史",
                                    tint = subTextColor,
                                    modifier = Modifier.size(20.dp)
                                )

                                Spacer(modifier = Modifier.width(16.dp))

                                // Middle: History item keyword
                                Text(
                                    text = historyItem,
                                    fontSize = 15.sp,
                                    color = textColor,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )

                                // Right: ↖ North-West arrow matching Figure 2
                                IconButton(
                                    onClick = { query = historyItem },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.ic_arrow_northwest),
                                        contentDescription = "填入搜索框",
                                        tint = subTextColor,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }

                        // Bottom Action: "清除搜索历史记录"
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 20.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                TextButton(
                                    onClick = { showClearAllConfirm = true }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.DeleteOutline,
                                        contentDescription = "清除搜索历史记录",
                                        tint = subTextColor,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "清除搜索历史记录",
                                        color = subTextColor,
                                        fontSize = 14.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Dialog for deleting a single history item (on long press)
    if (itemToDelete != null) {
        AlertDialog(
            onDismissRequest = { itemToDelete = null },
            title = { Text(text = "删除搜索历史") },
            text = { Text(text = "是否删除“${itemToDelete}”？") },
            confirmButton = {
                TextButton(
                    onClick = {
                        itemToDelete?.let { onDeleteHistoryItem(it) }
                        itemToDelete = null
                    }
                ) {
                    Text(text = "删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { itemToDelete = null }) {
                    Text(text = "取消")
                }
            }
        )
    }

    // Dialog for clearing all history
    if (showClearAllConfirm) {
        AlertDialog(
            onDismissRequest = { showClearAllConfirm = false },
            title = { Text(text = "清除全部搜索历史") },
            text = { Text(text = "确定清空所有的搜索历史记录吗？") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onClearAllHistory()
                        showClearAllConfirm = false
                    }
                ) {
                    Text(text = "全部清除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearAllConfirm = false }) {
                    Text(text = "取消")
                }
            }
        )
    }
}
