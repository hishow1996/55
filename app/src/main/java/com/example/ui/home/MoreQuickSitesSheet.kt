package com.example.ui.home

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Language
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.model.BookmarkItem
import com.example.model.QuickSite

data class RecommendedSite(
    val title: String,
    val url: String,
    val category: String,
    val bgColor: Long,
    val iconName: String = ""
)

private fun resolveSiteIconRes(url: String, title: String, iconName: String = ""): Int? {
    val lowerUrl = url.lowercase()
    val lowerTitle = title.lowercase()
    val lowerIcon = iconName.lowercase()
    return when {
        lowerIcon == "bilibili" || lowerUrl.contains("bilibili.com") || lowerTitle.contains("哔哩") -> R.drawable.ic_engine_bilibili
        lowerIcon == "baidu" || lowerUrl.contains("baidu.com") || lowerTitle.contains("百度") -> R.drawable.ic_engine_baidu
        lowerIcon == "google" || lowerUrl.contains("google.com") || lowerTitle.contains("google") -> R.drawable.ic_engine_google
        lowerIcon == "youtube" || lowerUrl.contains("youtube.com") || lowerTitle.contains("youtube") -> R.drawable.ic_site_youtube
        lowerIcon == "zhihu" || lowerUrl.contains("zhihu.com") || lowerTitle.contains("知乎") -> R.drawable.ic_engine_zhihu
        lowerIcon == "weibo" || lowerUrl.contains("weibo.cn") || lowerUrl.contains("weibo.com") || lowerTitle.contains("微博") -> R.drawable.ic_site_weibo
        lowerIcon == "toutiao" || lowerUrl.contains("toutiao.com") || lowerTitle.contains("头条") -> R.drawable.ic_engine_toutiao
        lowerIcon == "bing" || lowerUrl.contains("bing.com") || lowerTitle.contains("必应") -> R.drawable.ic_engine_bing
        lowerIcon == "iqiyi" || lowerUrl.contains("iqiyi.com") || lowerTitle.contains("爱奇艺") -> R.drawable.ic_site_iqiyi
        lowerIcon == "tencent" || lowerUrl.contains("v.qq.com") || lowerTitle.contains("腾讯视频") -> R.drawable.ic_site_tencent
        lowerIcon == "github" || lowerUrl.contains("github.com") || lowerTitle.contains("github") -> R.drawable.ic_site_github
        lowerIcon == "tiktok" || lowerUrl.contains("tiktok.com") || lowerUrl.contains("douyin.com") || lowerTitle.contains("抖音") -> R.drawable.ic_site_tiktok
        lowerIcon == "instagram" || lowerUrl.contains("instagram.com") || lowerTitle.contains("instagram") -> R.drawable.ic_site_instagram
        lowerIcon == "yfsp" || lowerUrl.contains("yfsp.tv") || lowerTitle.contains("壹帆") -> R.drawable.ic_site_yfsp
        lowerIcon == "quark" || lowerUrl.contains("quark.sm.cn") || lowerTitle.contains("夸克") -> R.drawable.ic_engine_quark
        lowerIcon == "360" || lowerUrl.contains("so.com") || lowerTitle.contains("360") -> R.drawable.ic_engine_360
        lowerIcon == "sogou" || lowerUrl.contains("sogou.com") || lowerTitle.contains("搜狗") -> R.drawable.ic_engine_sogou
        else -> null
    }
}

@Composable
private fun NavItemIcon(
    title: String,
    url: String,
    bgColor: Long,
    iconName: String = "",
    size: Int = 46,
    isNightMode: Boolean = false
) {
    val iconRes = resolveSiteIconRes(url, title, iconName)
    val cardSurface = if (isNightMode) Color(0xFF262E3B) else Color(0xFFF1F5F9)

    Box(
        modifier = Modifier
            .size(size.dp)
            .clip(RoundedCornerShape(13.dp))
            .background(cardSurface),
        contentAlignment = Alignment.Center
    ) {
        if (iconRes != null) {
            Image(
                painter = painterResource(id = iconRes),
                contentDescription = title,
                modifier = Modifier.size((size * 0.65f).dp)
            )
        } else {
            val effColor = if (bgColor != 0L && bgColor != 0xFFF1F5F9) {
                Color(bgColor)
            } else {
                val seed = title.hashCode()
                val palette = listOf(
                    Color(0xFF3B82F6), Color(0xFF10B981), Color(0xFFF59E0B),
                    Color(0xFFEF4444), Color(0xFF8B5CF6), Color(0xFFEC4899),
                    Color(0xFF06B6D4), Color(0xFF6366F1)
                )
                palette[kotlin.math.abs(seed) % palette.size]
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(effColor),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = title.take(1).uppercase(),
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = (size * 0.44f).sp
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
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

    // Tab 0: 精选推荐 (Recommended/Popular), Tab 1: 我的主页 (Current Home), Tab 2: 书签导入 (Bookmarks)
    var selectedTab by remember { mutableIntStateOf(0) }
    var selectedCategory by remember { mutableStateOf("全部") }
    var showAddCustomDialog by remember { mutableStateOf(false) }

    val bgColor = if (isNightMode) Color(0xFF161B22) else Color(0xFFFFFFFF)
    val cardBg = if (isNightMode) Color(0xFF212836) else Color(0xFFF8FAFC)
    val textColor = if (isNightMode) Color(0xFFF1F5F9) else Color(0xFF0F172A)
    val subTextColor = if (isNightMode) Color(0xFF94A3B8) else Color(0xFF64748B)
    val accentBlue = Color(0xFF2563EB)
    val dividerColor = if (isNightMode) Color(0xFF273142) else Color(0xFFE2E8F0)

    val recommendedSites = remember {
        listOf(
            // 影音娱乐
            RecommendedSite("哔哩哔哩", "https://m.bilibili.com", "影音", 0xFFFB7299, "bilibili"),
            RecommendedSite("YouTube", "https://m.youtube.com", "影音", 0xFFFF0000, "youtube"),
            RecommendedSite("腾讯视频", "https://m.v.qq.com", "影音", 0xFFFF6C00, "tencent"),
            RecommendedSite("爱奇艺", "https://m.iqiyi.com", "影音", 0xFF00C752, "iqiyi"),
            RecommendedSite("优酷视频", "https://m.youku.com", "影音", 0xFF00A0E9),
            RecommendedSite("抖音", "https://www.douyin.com", "影音", 0xFF161823, "tiktok"),
            RecommendedSite("壹帆视频", "https://m.yfsp.tv", "影音", 0xFFE50914, "yfsp"),
            // 社交资讯
            RecommendedSite("知乎", "https://www.zhihu.com", "社交", 0xFF0084FF, "zhihu"),
            RecommendedSite("微博", "https://m.weibo.cn", "社交", 0xFFE6162D, "weibo"),
            RecommendedSite("今日头条", "https://m.toutiao.com", "资讯", 0xFFED4040, "toutiao"),
            RecommendedSite("豆瓣", "https://m.douban.com", "社交", 0xFF00B51D),
            RecommendedSite("少数派", "https://sspai.com", "资讯", 0xFFD71920),
            RecommendedSite("小红书", "https://www.xiaohongshu.com", "社交", 0xFFFF2442),
            // 综合搜索
            RecommendedSite("百度", "https://www.baidu.com", "综合", 0xFF2932E1, "baidu"),
            RecommendedSite("Google", "https://www.google.com", "综合", 0xFF4285F4, "google"),
            RecommendedSite("必应", "https://www.bing.com", "综合", 0xFF008373, "bing"),
            RecommendedSite("夸克", "https://quark.sm.cn", "综合", 0xFF007AFF, "quark"),
            RecommendedSite("360搜索", "https://m.so.com", "综合", 0xFF19B955, "360"),
            // 开发技术
            RecommendedSite("GitHub", "https://github.com", "开发", 0xFF24292E, "github"),
            RecommendedSite("掘金", "https://juejin.cn", "开发", 0xFF1E80FF),
            RecommendedSite("V2EX", "https://www.v2ex.com", "开发", 0xFF333344),
            RecommendedSite("微信读书", "https://weread.qq.com", "学习", 0xFF3370FF),
            // 生活购物
            RecommendedSite("京东", "https://m.jd.com", "生活", 0xFFE1251B),
            RecommendedSite("淘宝", "https://m.taobao.com", "生活", 0xFFFF5000),
            RecommendedSite("拼多多", "https://mobile.yangkeduo.com", "生活", 0xFFE02E24)
        )
    }

    val categories = remember {
        listOf("全部", "影音", "社交", "综合", "资讯", "开发", "生活")
    }

    val activeSites = quickSites.filter { it.url != "action://more" && it.title != "更多" }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = bgColor,
        dragHandle = null,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f)
        ) {
            // Elegant Drag Handle
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp, bottom = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(width = 38.dp, height = 4.5.dp)
                        .clip(CircleShape)
                        .background(subTextColor.copy(alpha = 0.35f))
                )
            }

            // Clean Header Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "网址导航",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = textColor
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = when (selectedTab) {
                            0 -> "发现热门网站，点击一键添至主页"
                            1 -> "管理当前主页图标，轻按直接打开"
                            else -> "从我的书签中挑选导入主页"
                        },
                        fontSize = 12.sp,
                        color = subTextColor
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Modern "+ 自定义" Capsule Button
                    Surface(
                        onClick = { showAddCustomDialog = true },
                        shape = RoundedCornerShape(18.dp),
                        color = if (isNightMode) Color(0xFF1E293B) else Color(0xFFEFF6FF),
                        border = BorderStroke(1.dp, if (isNightMode) Color(0xFF334155) else Color(0xFFBFDBFE)),
                        modifier = Modifier.height(34.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = null,
                                tint = accentBlue,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "添加网址",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = accentBlue
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(if (isNightMode) Color(0xFF222B38) else Color(0xFFF1F5F9))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "关闭",
                            tint = subTextColor,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            // Modern Segmented Pill Control (Tabs)
            val tabs = listOf(
                "精选推荐",
                "我的主页 (${activeSites.size})",
                "书签导入 (${bookmarks.size})"
            )
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = if (isNightMode) Color(0xFF1E2533) else Color(0xFFF1F5F9),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 6.dp)
                    .height(42.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(3.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    tabs.forEachIndexed { index, title ->
                        val isSelected = selectedTab == index
                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            color = if (isSelected) {
                                if (isNightMode) Color(0xFF2E3B4E) else Color.White
                            } else Color.Transparent,
                            shadowElevation = if (isSelected) 1.5.dp else 0.dp,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(18.dp))
                                .clickable { selectedTab = index }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = title,
                                    fontSize = 12.5.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) {
                                        if (isNightMode) Color.White else Color(0xFF0F172A)
                                    } else subTextColor,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Tab Content
            when (selectedTab) {
                // Tab 0: 精选推荐 (Grid Layout with Category Filter)
                0 -> {
                    // Category Chips Bar
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(categories) { cat ->
                            val isCatSelected = selectedCategory == cat
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = if (isCatSelected) accentBlue else cardBg,
                                border = if (isCatSelected) null else BorderStroke(1.dp, dividerColor),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(16.dp))
                                    .clickable { selectedCategory = cat }
                            ) {
                                Text(
                                    text = cat,
                                    fontSize = 12.sp,
                                    fontWeight = if (isCatSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isCatSelected) Color.White else subTextColor,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }

                    val filteredSites = remember(selectedCategory) {
                        if (selectedCategory == "全部") {
                            recommendedSites
                        } else {
                            recommendedSites.filter { it.category == selectedCategory }
                        }
                    }

                    // 4-Column Icon Grid (Clean app-like layout matching Home shortcuts)
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(4),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    ) {
                        items(filteredSites, key = { it.url }) { item ->
                            val isAdded = quickSites.any { it.url.equals(item.url, ignoreCase = true) }

                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable {
                                        if (isAdded) {
                                            Toast.makeText(context, "${item.title} 已在主页快捷导航中", Toast.LENGTH_SHORT).show()
                                        } else {
                                            val ok = onAddQuickSite(item.title, item.url, item.bgColor)
                                            if (ok) {
                                                Toast.makeText(context, "已将 ${item.title} 添加到主页", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    }
                                    .padding(vertical = 4.dp)
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier.size(52.dp)
                                ) {
                                    NavItemIcon(
                                        title = item.title,
                                        url = item.url,
                                        bgColor = item.bgColor,
                                        iconName = item.iconName,
                                        size = 46,
                                        isNightMode = isNightMode
                                    )

                                    // Status Badge in Top-Right
                                    if (isAdded) {
                                        Box(
                                            modifier = Modifier
                                                .align(Alignment.TopEnd)
                                                .size(17.dp)
                                                .clip(CircleShape)
                                                .background(Color(0xFF16A34A)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = "已添加",
                                                tint = Color.White,
                                                modifier = Modifier.size(11.dp)
                                            )
                                        }
                                    } else {
                                        Box(
                                            modifier = Modifier
                                                .align(Alignment.TopEnd)
                                                .size(17.dp)
                                                .clip(CircleShape)
                                                .background(accentBlue),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Add,
                                                contentDescription = "添加",
                                                tint = Color.White,
                                                modifier = Modifier.size(11.dp)
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(6.dp))

                                Text(
                                    text = item.title,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = textColor,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                }

                // Tab 1: 我的主页导航 (4-Column Grid with quick delete badges)
                1 -> {
                    if (activeSites.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "主页暂无快捷网址",
                                    fontSize = 15.sp,
                                    color = subTextColor
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                Button(
                                    onClick = { selectedTab = 0 },
                                    colors = ButtonDefaults.buttonColors(containerColor = accentBlue),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text("去挑选热门网站")
                                }
                            }
                        }
                    } else {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 20.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "轻按直接打开，点击右上角 × 即可移除",
                                    fontSize = 11.5.sp,
                                    color = subTextColor
                                )
                            }

                            LazyVerticalGrid(
                                columns = GridCells.Fixed(4),
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(16.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                items(activeSites, key = { it.url }) { site ->
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(12.dp))
                                            .clickable {
                                                onOpenUrl(site.url)
                                                onDismiss()
                                            }
                                            .padding(vertical = 4.dp)
                                    ) {
                                        Box(
                                            contentAlignment = Alignment.Center,
                                            modifier = Modifier.size(52.dp)
                                        ) {
                                            NavItemIcon(
                                                title = site.title,
                                                url = site.url,
                                                bgColor = site.bgColor,
                                                iconName = site.iconName,
                                                size = 46,
                                                isNightMode = isNightMode
                                            )

                                            // Delete "×" badge in top-right
                                            Box(
                                                modifier = Modifier
                                                    .align(Alignment.TopEnd)
                                                    .size(20.dp)
                                                    .clip(CircleShape)
                                                    .background(Color(0xFFEF4444))
                                                    .clickable {
                                                        onRemoveQuickSite(site.url)
                                                        Toast.makeText(context, "已从主页移除", Toast.LENGTH_SHORT).show()
                                                    },
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Close,
                                                    contentDescription = "移除",
                                                    tint = Color.White,
                                                    modifier = Modifier.size(12.dp)
                                                )
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(6.dp))

                                        Text(
                                            text = site.title,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = textColor,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            textAlign = TextAlign.Center,
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }
                                }

                                // Plus Button Tile
                                item {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(12.dp))
                                            .clickable { showAddCustomDialog = true }
                                            .padding(vertical = 4.dp)
                                    ) {
                                        Box(
                                            contentAlignment = Alignment.Center,
                                            modifier = Modifier
                                                .size(46.dp)
                                                .clip(RoundedCornerShape(13.dp))
                                                .background(if (isNightMode) Color(0xFF1E2633) else Color(0xFFF1F5F9))
                                                .border(
                                                    1.dp,
                                                    if (isNightMode) Color(0xFF334155) else Color(0xFFCBD5E1),
                                                    RoundedCornerShape(13.dp)
                                                )
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Add,
                                                contentDescription = "新建网址",
                                                tint = accentBlue,
                                                modifier = Modifier.size(22.dp)
                                            )
                                        }

                                        Spacer(modifier = Modifier.height(6.dp))

                                        Text(
                                            text = "新建网址",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = accentBlue,
                                            maxLines = 1,
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Tab 2: 书签导入 (Clean list with domain, quick add pill)
                2 -> {
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
                                    color = if (isNightMode) Color(0xFF262E3B) else Color(0xFFEFF6FF),
                                    modifier = Modifier.size(60.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Default.BookmarkBorder,
                                            contentDescription = null,
                                            tint = accentBlue,
                                            modifier = Modifier.size(28.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(14.dp))
                                Text(
                                    text = "暂无书签",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = textColor
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "浏览网页时点击「添加书签」，即可在此处一键添至主页导航",
                                    fontSize = 13.sp,
                                    color = subTextColor,
                                    textAlign = TextAlign.Center,
                                    lineHeight = 18.sp
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .padding(horizontal = 16.dp, vertical = 6.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(bookmarks, key = { it.id }) { bookmark ->
                                val cleanUrl = bookmark.url
                                    .removePrefix("https://")
                                    .removePrefix("http://")
                                    .removePrefix("www.")
                                val isAdded = quickSites.any { it.url.equals(bookmark.url, ignoreCase = true) }

                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = cardBg,
                                    border = BorderStroke(1.dp, dividerColor),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 12.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        NavItemIcon(
                                            title = bookmark.title,
                                            url = bookmark.url,
                                            bgColor = 0xFF0284C7,
                                            size = 38,
                                            isNightMode = isNightMode
                                        )

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
                                                fontSize = 11.5.sp,
                                                color = subTextColor,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }

                                        Spacer(modifier = Modifier.width(8.dp))

                                        if (isAdded) {
                                            Surface(
                                                shape = RoundedCornerShape(12.dp),
                                                color = if (isNightMode) Color(0xFF1E293B) else Color(0xFFF1F5F9),
                                                modifier = Modifier.height(28.dp)
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    modifier = Modifier.padding(horizontal = 8.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Check,
                                                        contentDescription = null,
                                                        tint = Color(0xFF16A34A),
                                                        modifier = Modifier.size(13.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(3.dp))
                                                    Text(
                                                        text = "已在主页",
                                                        fontSize = 11.5.sp,
                                                        color = subTextColor,
                                                        fontWeight = FontWeight.Medium
                                                    )
                                                }
                                            }
                                        } else {
                                            Button(
                                                onClick = {
                                                    val success = onAddBookmarkToQuickSites(bookmark)
                                                    if (success) {
                                                        Toast.makeText(context, "已添加 ${bookmark.title} 到主页", Toast.LENGTH_SHORT).show()
                                                    }
                                                },
                                                shape = RoundedCornerShape(14.dp),
                                                colors = ButtonDefaults.buttonColors(containerColor = accentBlue),
                                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                                                modifier = Modifier.height(28.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Add,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(13.dp)
                                                )
                                                Spacer(modifier = Modifier.width(3.dp))
                                                Text(
                                                    text = "添加",
                                                    fontSize = 11.5.sp,
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
    }

    // Dialog: 手动添加自定义导航
    if (showAddCustomDialog) {
        var inputTitle by remember { mutableStateOf("") }
        var inputUrl by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showAddCustomDialog = false },
            title = {
                Text(text = "添加自定义主页网址", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            },
            text = {
                Column {
                    Text(
                        text = "输入网站名称与网址，添加后将立即显示在主页导航栏中",
                        fontSize = 12.5.sp,
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
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = accentBlue)
                ) {
                    Text("确认添加")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddCustomDialog = false }) {
                    Text("取消", color = subTextColor)
                }
            }
        )
    }
}
