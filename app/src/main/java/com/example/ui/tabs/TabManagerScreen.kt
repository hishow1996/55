package com.example.ui.tabs

import android.widget.Toast
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.BrowserTab
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun TabManagerScreen(
    tabs: List<BrowserTab>,
    currentTabIndex: Int,
    isNightMode: Boolean,
    onSelectTab: (Int) -> Unit,
    onCloseTab: (Int) -> Unit,
    onCloseTabItem: ((BrowserTab) -> Unit)? = null,
    onNewTab: (Boolean) -> Unit,
    onRestoreClosedTab: (() -> Unit)? = null,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bg = if (isNightMode) Color(0xFF111418) else Color(0xFFF1F5F9)
    val cardBg = if (isNightMode) Color(0xFF1E232B) else Color(0xFFFFFFFF)
    val textColor = if (isNightMode) Color(0xFFE2E8F0) else Color(0xFF1E293B)
    val subTextColor = if (isNightMode) Color(0xFF94A3B8) else Color(0xFF64748B)
    var searchText by remember { mutableStateOf("") }
    val filteredTabs = tabs.filter { tab ->
        searchText.isBlank() || tab.title.contains(searchText, true) || tab.url.contains(searchText, true)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(bg)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        // Header
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
                    tint = textColor
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "标签页 (${tabs.size})",
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold,
                    color = textColor
                )
                Text(
                    text = "向右滑动或点击 ✕ 即可关闭标签页",
                    fontSize = 11.sp,
                    color = subTextColor
                )
            }
            IconButton(onClick = { onNewTab(false) }) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "新建标签页",
                    tint = Color(0xFF2563EB)
                )
            }
        }

        HorizontalDivider(color = if (isNightMode) Color(0xFF262C36) else Color(0xFFE2E8F0))

        OutlinedTextField(
            value = searchText,
            onValueChange = { searchText = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            singleLine = true,
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            placeholder = { Text("搜索标签页") },
            shape = RoundedCornerShape(12.dp)
        )

        if (onRestoreClosedTab != null) {
            Text(
                text = "恢复最近关闭的标签",
                color = Color(0xFF2563EB),
                fontSize = 13.sp,
                modifier = Modifier
                    .padding(horizontal = 18.dp, vertical = 2.dp)
                    .clickable { onRestoreClosedTab() }
            )
        }

        // Tab Cards Grid with Swipe-To-Dismiss Support
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier
                .weight(1f)
                .padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            itemsIndexed(filteredTabs, key = { _, tab -> tab.id }) { _, tab ->
                val index = tabs.indexOfFirst { it.id == tab.id }
                val isCurrent = index == currentTabIndex

                SwipeableTabCard(
                    tab = tab,
                    index = index,
                    isCurrent = isCurrent,
                    cardBg = cardBg,
                    textColor = textColor,
                    subTextColor = subTextColor,
                    isNightMode = isNightMode,
                    onSelect = { onSelectTab(index) },
                    onClose = {
                        if (onCloseTabItem != null) {
                            onCloseTabItem(tab)
                        } else {
                            onCloseTab(index)
                        }
                    }
                )
            }
        }

        // Bottom Action Row (新建普通标签页 + 新建无痕标签页)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = { onNewTab(false) },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB))
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(6.dp))
                Text("新建标签页")
            }

            OutlinedButton(
                onClick = { onNewTab(true) },
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.Default.Security,
                    contentDescription = null,
                    tint = Color(0xFF8B5CF6)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("无痕标签", color = Color(0xFF8B5CF6))
            }
        }
    }
}

/**
 * Tab card supporting right-swipe to delete and top-right close button
 */
@Composable
private fun SwipeableTabCard(
    tab: BrowserTab,
    index: Int,
    isCurrent: Boolean,
    cardBg: Color,
    textColor: Color,
    subTextColor: Color,
    isNightMode: Boolean,
    onSelect: () -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val density = LocalDensity.current

    val offsetX = remember { Animatable(0f) }
    val dismissThresholdPx = with(density) { 70.dp.toPx() }
    val dismissTargetPx = with(density) { 260.dp.toPx() }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(160.dp)
    ) {
        // --- 1. DELETE BACKGROUND INDICATOR (REVEALED ON SWIPE RIGHT) ---
        val swipeProgress = (offsetX.value / dismissThresholdPx).coerceIn(0f, 1f)
        val deleteBgColor = if (isNightMode) {
            Color(0xFF451A1A).copy(alpha = 0.4f + swipeProgress * 0.6f)
        } else {
            Color(0xFFFEE2E2).copy(alpha = 0.4f + swipeProgress * 0.6f)
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(16.dp))
                .background(deleteBgColor),
            contentAlignment = Alignment.CenterStart
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .padding(start = 14.dp)
                    .graphicsLayer {
                        alpha = (swipeProgress * 1.2f).coerceIn(0.2f, 1f)
                        scaleX = 0.85f + swipeProgress * 0.15f
                        scaleY = 0.85f + swipeProgress * 0.15f
                    }
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "删除标签页",
                    tint = Color(0xFFEF4444),
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "关闭",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFEF4444)
                )
            }
        }

        // --- 2. FOREGROUND TAB CARD ---
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = cardBg,
            shadowElevation = if (isCurrent) 6.dp else 2.dp,
            modifier = Modifier
                .fillMaxSize()
                .offset { IntOffset(offsetX.value.roundToInt(), 0) }
                .graphicsLayer {
                    // Slight fade as it is swiped away
                    alpha = (1f - (offsetX.value / dismissTargetPx).coerceIn(0f, 0.75f))
                }
                .clip(RoundedCornerShape(16.dp))
                .border(
                    2.dp,
                    if (isCurrent) Color(0xFF3B82F6) else Color.Transparent,
                    RoundedCornerShape(16.dp)
                )
                .clickable {
                    if (offsetX.value < 10f) {
                        onSelect()
                    }
                }
                .draggable(
                    state = rememberDraggableState { delta ->
                        coroutineScope.launch {
                            // Only allow swiping right (delta > 0 or moving back to 0)
                            val newOffset = (offsetX.value + delta).coerceAtLeast(0f)
                            offsetX.snapTo(newOffset)
                        }
                    },
                    orientation = Orientation.Horizontal,
                    onDragStopped = { velocity ->
                        coroutineScope.launch {
                            if (offsetX.value > dismissThresholdPx || velocity > 700f) {
                                // Animate off to the right and dismiss
                                offsetX.animateTo(
                                    targetValue = dismissTargetPx,
                                    animationSpec = tween(durationMillis = 180)
                                )
                                onClose()
                            } else {
                                // Snap back to starting position
                                offsetX.animateTo(
                                    targetValue = 0f,
                                    animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
                                )
                            }
                        }
                    }
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(10.dp)
            ) {
                // Card Header (Icon + Title + Close Button)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = if (tab.isIncognito) Icons.Default.Security else Icons.Default.Language,
                            contentDescription = null,
                            tint = if (tab.isIncognito) Color(0xFFA855F7) else Color(0xFF3B82F6),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = tab.title.ifBlank { "新标签页" },
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = textColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // ✕ Close Button
                    IconButton(
                        onClick = {
                            coroutineScope.launch {
                                // Quick slide-out animation before calling onClose
                                offsetX.animateTo(
                                    targetValue = dismissTargetPx,
                                    animationSpec = tween(durationMillis = 150)
                                )
                                onClose()
                            }
                        },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "关闭标签页",
                            tint = subTextColor,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Preview Box
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isNightMode) Color(0xFF14181E) else Color(0xFFF8FAFC)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(8.dp)
                    ) {
                        if (tab.isIncognito) {
                            Text(
                                text = "无痕浏览中",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color(0xFFA855F7)
                            )
                            Text(
                                text = "不记录历史与缓存",
                                fontSize = 10.sp,
                                color = subTextColor
                            )
                        } else if (!tab.isAtHome) {
                            Text(
                                text = tab.url,
                                fontSize = 11.sp,
                                color = subTextColor,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis
                            )
                        } else {
                            Text(
                                text = "大象首页",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = subTextColor
                            )
                        }
                    }
                }
            }
        }
    }
}
