package com.example.ui.history

import android.widget.Toast
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.BrowserRepository
import com.example.model.BookmarkItem
import com.example.model.HistoryItem
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class HistoryDateGroup(
    val dateKey: String,
    val title: String,
    val subtitle: String,
    val items: List<HistoryItem>
)

@Composable
fun HistoryBookmarksScreen(
    repository: BrowserRepository,
    initialTab: Int = 0, // 0 = Bookmarks, 1 = History
    isNightMode: Boolean,
    onOpenUrl: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var selectedTab by remember(initialTab) { mutableIntStateOf(initialTab) }
    var searchQuery by remember { mutableStateOf("") }
    var showClearAllHistoryDialog by remember { mutableStateOf(false) }
    var groupToDelete by remember { mutableStateOf<HistoryDateGroup?>(null) }

    val bookmarks by repository.bookmarks.collectAsState()
    val history by repository.history.collectAsState()

    val bg = if (isNightMode) Color(0xFF111418) else Color(0xFFFFFFFF)
    val cardBg = if (isNightMode) Color(0xFF1A1F26) else Color(0xFFF8FAFC)
    val headerBg = if (isNightMode) Color(0xFF222832) else Color(0xFFEFF6FF)
    val textColor = if (isNightMode) Color(0xFFE2E8F0) else Color(0xFF1E293B)
    val subTextColor = if (isNightMode) Color(0xFF94A3B8) else Color(0xFF64748B)
    val accentBlue = Color(0xFF2563EB)
    val accentAmber = Color(0xFFF59E0B)
    val dividerColor = if (isNightMode) Color(0xFF262C36) else Color(0xFFE2E8F0)

    // Calculate grouped history
    val groupedHistory = remember(history, searchQuery) {
        groupHistoryItems(history, searchQuery)
    }
    val totalFilteredHistoryCount = remember(groupedHistory) {
        groupedHistory.sumOf { it.items.size }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(bg)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        // --- TOP ACTION BAR ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "返回",
                    tint = textColor
                )
            }
            Text(
                text = if (selectedTab == 0) "书签收藏" else "浏览历史",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = textColor,
                modifier = Modifier.weight(1f)
            )

            // Clear All History Button in History Tab
            if (selectedTab == 1 && history.isNotEmpty()) {
                TextButton(
                    onClick = { showClearAllHistoryDialog = true }
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteSweep,
                        contentDescription = "清空历史",
                        tint = Color(0xFFEF4444),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "清空",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFFEF4444)
                    )
                }
            }
        }

        // --- TABS (书签收藏 / 浏览历史) ---
        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = bg,
            contentColor = accentBlue,
            indicator = { tabPositions ->
                TabRowDefaults.SecondaryIndicator(
                    modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                    color = accentBlue
                )
            }
        ) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = {
                    Text(
                        text = "书签收藏 (${bookmarks.size})",
                        fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Normal,
                        color = if (selectedTab == 0) accentBlue else subTextColor
                    )
                }
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = {
                    Text(
                        text = "浏览历史 (${history.size})",
                        fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Normal,
                        color = if (selectedTab == 1) accentBlue else subTextColor
                    )
                }
            )
        }

        // --- SEARCH BAR WITH INSTANT FILTERING & CLEAR BUTTON ---
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = {
                Text(
                    text = if (selectedTab == 0) "搜索书签标题或网址..." else "搜索历史记录或网址...",
                    fontSize = 14.sp,
                    color = subTextColor
                )
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "搜索",
                    tint = subTextColor
                )
            },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { searchQuery = "" }) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "清除搜索",
                            tint = subTextColor,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = cardBg,
                unfocusedContainerColor = cardBg,
                focusedBorderColor = accentBlue,
                unfocusedBorderColor = dividerColor,
                focusedTextColor = textColor,
                unfocusedTextColor = textColor
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        )

        // Search match summary banner when searching
        if (searchQuery.isNotBlank()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = if (selectedTab == 1) "找到 $totalFilteredHistoryCount 条相关历史" else "找到匹配书签",
                    fontSize = 12.sp,
                    color = accentBlue,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "清除条件",
                    fontSize = 12.sp,
                    color = subTextColor,
                    modifier = Modifier.clickable { searchQuery = "" }
                )
            }
        }

        HorizontalDivider(color = dividerColor)

        // --- TAB 0: BOOKMARKS TAB ---
        if (selectedTab == 0) {
            val filteredBookmarks = remember(bookmarks, searchQuery) {
                if (searchQuery.isBlank()) bookmarks
                else bookmarks.filter {
                    it.title.contains(searchQuery, ignoreCase = true) ||
                    it.url.contains(searchQuery, ignoreCase = true)
                }
            }

            if (filteredBookmarks.isEmpty()) {
                EmptyStateView(
                    message = if (searchQuery.isBlank()) "暂无书签收藏" else "未搜索到与「$searchQuery」匹配的书签",
                    subMessage = if (searchQuery.isBlank()) "浏览网页时，点击菜单中的「加入书签」即可收藏" else "请尝试其他关键词",
                    isNightMode = isNightMode
                )
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(filteredBookmarks, key = { it.id }) { item ->
                        BookmarkRow(
                            item = item,
                            textColor = textColor,
                            subTextColor = subTextColor,
                            isNightMode = isNightMode,
                            onClick = { onOpenUrl(item.url) },
                            onDelete = {
                                repository.deleteBookmark(item.id)
                                Toast.makeText(context, "已删除书签", Toast.LENGTH_SHORT).show()
                            }
                        )
                        HorizontalDivider(color = if (isNightMode) Color(0xFF1E232B) else Color(0xFFF1F5F9))
                    }
                }
            }
        } else {
            // --- TAB 1: DATE-GROUPED HISTORY TAB ---
            if (groupedHistory.isEmpty()) {
                EmptyStateView(
                    message = if (searchQuery.isBlank()) "暂无浏览历史" else "未搜索到与「$searchQuery」匹配的历史",
                    subMessage = if (searchQuery.isBlank()) "您浏览的网页会按日期自动保存在这里" else "请检查关键词或清除搜索条件",
                    isNightMode = isNightMode
                )
            } else {
                val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }

                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    groupedHistory.forEach { group ->
                        // Date Group Header
                        item(key = "header_${group.dateKey}") {
                            HistoryDateHeader(
                                group = group,
                                headerBg = headerBg,
                                textColor = textColor,
                                subTextColor = subTextColor,
                                isNightMode = isNightMode,
                                onDeleteGroup = { groupToDelete = group }
                            )
                        }

                        // History Items in this Date Group
                        items(group.items, key = { it.id }) { item ->
                            HistoryItemRow(
                                item = item,
                                timeFormat = timeFormat,
                                textColor = textColor,
                                subTextColor = subTextColor,
                                isNightMode = isNightMode,
                                onClick = { onOpenUrl(item.url) },
                                onDelete = {
                                    repository.removeHistory(item.id)
                                    Toast.makeText(context, "已删除历史记录", Toast.LENGTH_SHORT).show()
                                }
                            )
                            HorizontalDivider(color = if (isNightMode) Color(0xFF1E232B) else Color(0xFFF1F5F9))
                        }
                    }

                    item {
                        Spacer(modifier = Modifier.height(24.dp))
                    }
                }
            }
        }
    }

    // --- DIALOG: CLEAR ALL HISTORY CONFIRMATION ---
    if (showClearAllHistoryDialog) {
        AlertDialog(
            onDismissRequest = { showClearAllHistoryDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.DeleteSweep,
                        contentDescription = null,
                        tint = Color(0xFFEF4444)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("清空全部历史记录", fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Text("确定要清空所有的浏览历史记录吗？\n清空后将无法恢复已访问的历史网页。")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        repository.clearAllHistory()
                        showClearAllHistoryDialog = false
                        Toast.makeText(context, "浏览历史已全部清空", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Text("全部清空", color = Color(0xFFEF4444), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearAllHistoryDialog = false }) {
                    Text("取消")
                }
            }
        )
    }

    // --- DIALOG: CLEAR SPECIFIC DATE GROUP ---
    groupToDelete?.let { group ->
        AlertDialog(
            onDismissRequest = { groupToDelete = null },
            title = {
                Text("删除该日历史记录", fontWeight = FontWeight.Bold)
            },
            text = {
                Text("确定要删除「${group.title}${if (group.subtitle.isNotEmpty()) " (${group.subtitle})" else ""}」的全部 ${group.items.size} 条历史记录吗？")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val ids = group.items.map { it.id }
                        repository.removeHistoryList(ids)
                        groupToDelete = null
                        Toast.makeText(context, "已删除「${group.title}」的历史记录", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Text("删除", color = Color(0xFFEF4444), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { groupToDelete = null }) {
                    Text("取消")
                }
            }
        )
    }
}

/**
 * Group history items by calendar date in descending order
 */
private fun groupHistoryItems(
    items: List<HistoryItem>,
    searchQuery: String
): List<HistoryDateGroup> {
    val filtered = if (searchQuery.isBlank()) {
        items
    } else {
        items.filter {
            it.title.contains(searchQuery, ignoreCase = true) ||
            it.url.contains(searchQuery, ignoreCase = true)
        }
    }
    val sorted = filtered.sortedByDescending { it.visitTime }

    val nowCal = Calendar.getInstance()
    val currentYear = nowCal.get(Calendar.YEAR)
    val currentDay = nowCal.get(Calendar.DAY_OF_YEAR)

    val dayKeyFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    val monthDayFormat = SimpleDateFormat("M月d日", Locale.getDefault())
    val fullDateFormat = SimpleDateFormat("yyyy年M月d日", Locale.getDefault())
    val weekDayFormat = SimpleDateFormat("EEEE", Locale.CHINESE)

    val map = LinkedHashMap<String, MutableList<HistoryItem>>()
    for (item in sorted) {
        val key = dayKeyFormat.format(Date(item.visitTime))
        map.getOrPut(key) { mutableListOf() }.add(item)
    }

    val cal = Calendar.getInstance()
    return map.map { (key, groupItems) ->
        val firstTime = groupItems.first().visitTime
        cal.timeInMillis = firstTime
        val year = cal.get(Calendar.YEAR)
        val day = cal.get(Calendar.DAY_OF_YEAR)

        val (title, subtitle) = if (year == currentYear) {
            when (currentDay - day) {
                0 -> "今天" to monthDayFormat.format(Date(firstTime))
                1 -> "昨天" to monthDayFormat.format(Date(firstTime))
                2 -> "前天" to monthDayFormat.format(Date(firstTime))
                else -> "${monthDayFormat.format(Date(firstTime))} · ${weekDayFormat.format(Date(firstTime))}" to ""
            }
        } else {
            fullDateFormat.format(Date(firstTime)) to ""
        }

        HistoryDateGroup(
            dateKey = key,
            title = title,
            subtitle = subtitle,
            items = groupItems
        )
    }
}

@Composable
private fun HistoryDateHeader(
    group: HistoryDateGroup,
    headerBg: Color,
    textColor: Color,
    subTextColor: Color,
    isNightMode: Boolean,
    onDeleteGroup: () -> Unit
) {
    Surface(
        color = headerBg,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.CalendarToday,
                    contentDescription = null,
                    tint = if (isNightMode) Color(0xFF60A5FA) else Color(0xFF2563EB),
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = group.title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = textColor
                )
                if (group.subtitle.isNotEmpty()) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = group.subtitle,
                        fontSize = 12.sp,
                        color = subTextColor
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                // Count Badge
                Surface(
                    shape = CircleShape,
                    color = if (isNightMode) Color(0xFF334155) else Color(0xFFDBEAFE),
                    modifier = Modifier.padding(horizontal = 2.dp)
                ) {
                    Text(
                        text = "${group.items.size}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isNightMode) Color(0xFF93C5FD) else Color(0xFF1D4ED8),
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 1.dp)
                    )
                }
            }

            // Delete day group button
            IconButton(
                onClick = onDeleteGroup,
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.DeleteOutline,
                    contentDescription = "删除该日记录",
                    tint = subTextColor.copy(alpha = 0.7f),
                    modifier = Modifier.size(17.dp)
                )
            }
        }
    }
}

@Composable
private fun HistoryItemRow(
    item: HistoryItem,
    timeFormat: SimpleDateFormat,
    textColor: Color,
    subTextColor: Color,
    isNightMode: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val cleanUrl = item.url
        .removePrefix("https://")
        .removePrefix("http://")
        .removePrefix("www.")

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Icon container
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = if (isNightMode) Color(0xFF262C36) else Color(0xFFFEF3C7),
            modifier = Modifier.size(38.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.History,
                    contentDescription = null,
                    tint = Color(0xFFD97706),
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Title and URL info
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.title.ifBlank { cleanUrl },
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = textColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.AccessTime,
                    contentDescription = null,
                    tint = subTextColor.copy(alpha = 0.8f),
                    modifier = Modifier.size(12.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = timeFormat.format(Date(item.visitTime)),
                    fontSize = 12.sp,
                    color = subTextColor,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "·  $cleanUrl",
                    fontSize = 12.sp,
                    color = subTextColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // Delete item button
        IconButton(
            onClick = onDelete,
            modifier = Modifier.size(36.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "删除记录",
                tint = subTextColor.copy(alpha = 0.5f),
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
private fun BookmarkRow(
    item: BookmarkItem,
    textColor: Color,
    subTextColor: Color,
    isNightMode: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val cleanUrl = item.url
        .removePrefix("https://")
        .removePrefix("http://")
        .removePrefix("www.")

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = if (isNightMode) Color(0xFF262C36) else Color(0xFFE0F2FE),
            modifier = Modifier.size(38.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.Bookmark,
                    contentDescription = null,
                    tint = Color(0xFF0284C7),
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.title.ifBlank { cleanUrl },
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = textColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = cleanUrl,
                fontSize = 12.sp,
                color = subTextColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        IconButton(
            onClick = onDelete,
            modifier = Modifier.size(36.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "删除书签",
                tint = subTextColor.copy(alpha = 0.5f),
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
private fun EmptyStateView(
    message: String,
    subMessage: String,
    isNightMode: Boolean
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp, vertical = 64.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Surface(
                shape = CircleShape,
                color = if (isNightMode) Color(0xFF1E232B) else Color(0xFFF1F5F9),
                modifier = Modifier.size(72.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.History,
                        contentDescription = null,
                        tint = if (isNightMode) Color(0xFF64748B) else Color(0xFF94A3B8),
                        modifier = Modifier.size(36.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = message,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = if (isNightMode) Color(0xFFE2E8F0) else Color(0xFF334155)
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = subMessage,
                fontSize = 13.sp,
                color = if (isNightMode) Color(0xFF94A3B8) else Color(0xFF64748B)
            )
        }
    }
}
