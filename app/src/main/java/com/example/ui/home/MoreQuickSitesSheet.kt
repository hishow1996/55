package com.example.ui.home

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
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
import com.example.model.BookmarkItem
import com.example.model.QuickSite

data class RecommendedSite(
    val title: String,
    val url: String,
    val category: String,
    val bgColor: Long
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun MoreQuickSitesSheet(
    quickSites: List<QuickSite>,
    bookmarks: List<BookmarkItem>,
    isNightMode: Boolean,
    onAddQuickSite: (title: String, url: String, bgColor: Long) -> Boolean,
    onRemoveQuickSite: (url: String) -> Unit,
    onAddBookmarkToQuickSites: (bookmark: BookmarkItem) -> Boolean,
    onOpenUrl: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = LocalContext.current

    var selectedTab by remember { mutableIntStateOf(0) }
    var showAddCustomDialog by remember { mutableStateOf(false) }

    val bgColor = if (isNightMode) Color(0xFF161A22) else Color(0xFFFFFFFF)
    val cardBg = if (isNightMode) Color(0xFF212631) else Color(0xFFF8FAFC)
    val textColor = if (isNightMode) Color(0xFFE2E8F0) else Color(0xFF0F172A)
    val subTextColor = if (isNightMode) Color(0xFF94A3B8) else Color(0xFF64748B)
    val accentBlue = Color(0xFF2563EB)
    val dividerColor = if (isNightMode) Color(0xFF2D3544) else Color(0xFFE2E8F0)

    val recommendedSites = remember {
        listOf(
            // 影音娱乐
            RecommendedSite("哔哩哔哩", "https://m.bilibili.com", "影音", 0xFFFB7299),
            RecommendedSite("腾讯视频", "https://m.v.qq.com", "影音", 0xFFFF6C00),
            RecommendedSite("爱奇艺", "https://m.iqiyi.com", "影音", 0xFF00C752),
            RecommendedSite("优酷视频", "https://m.youku.com", "影音", 0xFF00A0E9),
            RecommendedSite("抖音", "https://www.douyin.com", "影音", 0xFF161823),
            RecommendedSite("YouTube", "https://m.youtube.com", "影音", 0xFFFF0000),
            // 社交资讯
            RecommendedSite("微博", "https://m.weibo.cn", "社交", 0xFFE6162D),
            RecommendedSite("知乎", "https://www.zhihu.com", "社交", 0xFF0084FF),
            RecommendedSite("豆瓣", "https://m.douban.com", "社交", 0xFF00B51D),
            RecommendedSite("少数派", "https://sspai.com", "资讯", 0xFFD71920),
            RecommendedSite("今日头条", "https://m.toutiao.com", "资讯", 0xFFED4040),
            RecommendedSite("小红书", "https://www.xiaohongshu.com", "社交", 0xFFFF2442),
            // 综合与学习
            RecommendedSite("百度", "https://www.baidu.com", "综合", 0xFF2932E1),
            RecommendedSite("Google", "https://www.google.com", "综合", 0xFF4285F4),
            RecommendedSite("必应", "https://www.bing.com", "综合", 0xFF008373),
            RecommendedSite("GitHub", "https://github.com", "开发", 0xFF24292E),
            RecommendedSite("掘金", "https://juejin.cn", "开发", 0xFF1E80FF),
            RecommendedSite("V2EX", "https://www.v2ex.com", "开发", 0xFF333344),
            RecommendedSite("微信读书", "https://weread.qq.com", "学习", 0xFF3370FF),
            // 生活购物
            RecommendedSite("京东", "https://m.jd.com", "生活", 0xFFE1251B),
            RecommendedSite("淘宝", "https://m.taobao.com", "生活", 0xFFFF5000),
            RecommendedSite("拼多多", "https://mobile.yangkeduo.com", "生活", 0xFFE02E24)
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = bgColor,
        dragHandle = null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.9f)
        ) {
            // Header Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "主页导航网址管理",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = textColor
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "管理主页快捷图标，或从书签与热门网站一键添加",
                        fontSize = 12.sp,
                        color = subTextColor
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = { showAddCustomDialog = true },
                        modifier = Modifier
                            .size(36.dp)
                            .background(
                                color = if (isNightMode) Color(0xFF1E293B) else Color(0xFFEFF6FF),
                                shape = CircleShape
                            )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "添加自定义网址",
                            tint = accentBlue,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "关闭",
                            tint = subTextColor,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }

            // Tabs
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = bgColor,
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
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Bookmark,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "从书签添加 (${bookmarks.size})",
                                fontSize = 13.sp,
                                fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Language,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            val activeCount = quickSites.count { it.url != "action://more" && it.title != "更多" }
                            Text(
                                text = "当前主页 ($activeCount)",
                                fontSize = 13.sp,
                                fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                )
                Tab(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Explore,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "热门发现",
                                fontSize = 13.sp,
                                fontWeight = if (selectedTab == 2) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                )
            }

            HorizontalDivider(color = dividerColor)

            // Tab Content
            when (selectedTab) {
                // Tab 0: 从书签直接添加到主页导航
                0 -> {
                    if (bookmarks.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Surface(
                                    shape = CircleShape,
                                    color = if (isNightMode) Color(0xFF262C36) else Color(0xFFE0F2FE),
                                    modifier = Modifier.size(64.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Default.BookmarkAdd,
                                            contentDescription = null,
                                            tint = Color(0xFF0284C7),
                                            modifier = Modifier.size(32.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = "暂无书签网站",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = textColor
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "在浏览网页时，点击底部菜单「添加书签」，即可在此处一键将收藏的网站加入主页导航！",
                                    fontSize = 13.sp,
                                    color = subTextColor,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                    lineHeight = 18.sp
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                OutlinedButton(
                                    onClick = { showAddCustomDialog = true },
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("手动添加主页导航网址")
                                }
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            item {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "点击按钮即可将书签一键添加到主页快捷导航",
                                        fontSize = 12.sp,
                                        color = subTextColor
                                    )
                                }
                            }

                            items(bookmarks, key = { it.id }) { bookmark ->
                                val cleanUrl = bookmark.url
                                    .removePrefix("https://")
                                    .removePrefix("http://")
                                    .removePrefix("www.")
                                val isAdded = quickSites.any { it.url.equals(bookmark.url, ignoreCase = true) }

                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = cardBg,
                                    border = androidx.compose.foundation.BorderStroke(1.dp, dividerColor),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Surface(
                                            shape = RoundedCornerShape(10.dp),
                                            color = Color(0xFF0284C7),
                                            modifier = Modifier.size(40.dp)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Text(
                                                    text = bookmark.title.take(1).ifBlank { "书" },
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 18.sp,
                                                    color = Color.White
                                                )
                                            }
                                        }

                                        Spacer(modifier = Modifier.width(12.dp))

                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = bookmark.title.ifBlank { cleanUrl },
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.SemiBold,
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

                                        Spacer(modifier = Modifier.width(8.dp))

                                        if (isAdded) {
                                            Surface(
                                                shape = RoundedCornerShape(8.dp),
                                                color = if (isNightMode) Color(0xFF1E293B) else Color(0xFFE2E8F0),
                                                modifier = Modifier.padding(horizontal = 4.dp)
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Check,
                                                        contentDescription = null,
                                                        tint = Color(0xFF16A34A),
                                                        modifier = Modifier.size(14.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text(
                                                        text = "已在主页",
                                                        fontSize = 12.sp,
                                                        color = if (isNightMode) Color(0xFF94A3B8) else Color(0xFF64748B),
                                                        fontWeight = FontWeight.Medium
                                                    )
                                                }
                                            }
                                        } else {
                                            Button(
                                                onClick = {
                                                    val success = onAddBookmarkToQuickSites(bookmark)
                                                    if (success) {
                                                        Toast.makeText(context, "已添加到主页导航", Toast.LENGTH_SHORT).show()
                                                    }
                                                },
                                                shape = RoundedCornerShape(10.dp),
                                                colors = ButtonDefaults.buttonColors(containerColor = accentBlue),
                                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                                modifier = Modifier.height(34.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Add,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(14.dp)
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(
                                                    text = "添加到主页",
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Tab 1: 当前主页已有导航管理
                1 -> {
                    val activeSites = quickSites.filter { it.url != "action://more" && it.title != "更多" }
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        item {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "主页快捷方式可点击直接打开，或点击删除移除",
                                    fontSize = 12.sp,
                                    color = subTextColor
                                )
                                TextButton(
                                    onClick = { showAddCustomDialog = true },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(2.dp))
                                    Text("新建网址", fontSize = 12.sp)
                                }
                            }
                        }

                        items(activeSites, key = { it.url }) { site ->
                            val cleanUrl = site.url
                                .removePrefix("https://")
                                .removePrefix("http://")
                                .removePrefix("www.")

                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = cardBg,
                                border = androidx.compose.foundation.BorderStroke(1.dp, dividerColor),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        onOpenUrl(site.url)
                                        onDismiss()
                                    }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = Color(site.bgColor),
                                        modifier = Modifier.size(40.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text(
                                                text = site.title.take(1),
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 18.sp,
                                                color = if (site.bgColor == 0xFFFFFFFF) Color(0xFFEA4335) else Color.White
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.width(12.dp))

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = site.title,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.SemiBold,
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
                                        onClick = {
                                            onRemoveQuickSite(site.url)
                                            Toast.makeText(context, "已从主页移除", Toast.LENGTH_SHORT).show()
                                        },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "移除",
                                            tint = subTextColor.copy(alpha = 0.6f),
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Tab 2: 热门精选发现
                2 -> {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        item {
                            Text(
                                text = "精选各类别常用主流网站，点击即可一键添加到主页",
                                fontSize = 12.sp,
                                color = subTextColor,
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                        }

                        items(recommendedSites, key = { it.url }) { item ->
                            val isAdded = quickSites.any { it.url.equals(item.url, ignoreCase = true) }
                            val cleanUrl = item.url
                                .removePrefix("https://")
                                .removePrefix("http://")
                                .removePrefix("www.")

                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = cardBg,
                                border = androidx.compose.foundation.BorderStroke(1.dp, dividerColor),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = Color(item.bgColor),
                                        modifier = Modifier.size(40.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text(
                                                text = item.title.take(1),
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 18.sp,
                                                color = Color.White
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.width(12.dp))

                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = item.title,
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = textColor
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Surface(
                                                shape = RoundedCornerShape(4.dp),
                                                color = if (isNightMode) Color(0xFF1E293B) else Color(0xFFE2E8F0)
                                            ) {
                                                Text(
                                                    text = item.category,
                                                    fontSize = 10.sp,
                                                    color = subTextColor,
                                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = cleanUrl,
                                            fontSize = 12.sp,
                                            color = subTextColor,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(8.dp))

                                    if (isAdded) {
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = if (isNightMode) Color(0xFF1E293B) else Color(0xFFE2E8F0),
                                            modifier = Modifier.padding(horizontal = 4.dp)
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Check,
                                                    contentDescription = null,
                                                    tint = Color(0xFF16A34A),
                                                    modifier = Modifier.size(14.dp)
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(
                                                    text = "已添加",
                                                    fontSize = 12.sp,
                                                    color = if (isNightMode) Color(0xFF94A3B8) else Color(0xFF64748B),
                                                    fontWeight = FontWeight.Medium
                                                )
                                            }
                                        }
                                    } else {
                                        Button(
                                            onClick = {
                                                val success = onAddQuickSite(item.title, item.url, item.bgColor)
                                                if (success) {
                                                    Toast.makeText(context, "已添加到主页导航", Toast.LENGTH_SHORT).show()
                                                }
                                            },
                                            shape = RoundedCornerShape(10.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = accentBlue),
                                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                            modifier = Modifier.height(34.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Add,
                                                contentDescription = null,
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = "添加",
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Dialog: 手动添加自定义导航
    if (showAddCustomDialog) {
        var inputTitle by remember { mutableStateOf("") }
        var inputUrl by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showAddCustomDialog = false },
            title = {
                Text(text = "添加自定义主页导航", fontWeight = FontWeight.Bold)
            },
            text = {
                Column {
                    Text(
                        text = "输入网站名称与网址，添加后将立即显示在主页导航栏中",
                        fontSize = 13.sp,
                        color = subTextColor
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value = inputTitle,
                        onValueChange = { inputTitle = it },
                        label = { Text("网站名称") },
                        placeholder = { Text("例如：少数派") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = inputUrl,
                        onValueChange = { inputUrl = it },
                        label = { Text("网站网址") },
                        placeholder = { Text("例如：sspai.com") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val trimmedUrl = inputUrl.trim()
                        if (trimmedUrl.isBlank()) {
                            Toast.makeText(context, "请输入网址", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        val formattedUrl = if (!trimmedUrl.startsWith("http://") && !trimmedUrl.startsWith("https://")) {
                            "https://$trimmedUrl"
                        } else {
                            trimmedUrl
                        }
                        val finalTitle = inputTitle.trim().ifBlank {
                            trimmedUrl.removePrefix("https://").removePrefix("http://").removePrefix("www.")
                        }
                        val added = onAddQuickSite(finalTitle, formattedUrl, 0xFF3B82F6)
                        if (added) {
                            Toast.makeText(context, "已成功添加到主页导航", Toast.LENGTH_SHORT).show()
                            showAddCustomDialog = false
                        } else {
                            Toast.makeText(context, "该网址已在主页中", Toast.LENGTH_SHORT).show()
                        }
                    },
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("确认添加")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddCustomDialog = false }) {
                    Text("取消")
                }
            }
        )
    }
}
