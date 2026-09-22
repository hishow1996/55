package com.example.ui.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.BrowserRepository
import com.example.model.BookmarkItem
import com.example.model.QuickSite
import com.example.model.SearchEngines

@Composable
fun SearchEngineLogo(
    engine: String,
    modifier: Modifier = Modifier
) {
    val info = SearchEngines.getById(engine)
    Icon(
        painter = painterResource(id = info.iconRes),
        contentDescription = info.shortName,
        tint = Color.Unspecified,
        modifier = modifier
    )
}

@Composable
fun HomeScreen(
    searchEngine: String,
    isIncognito: Boolean,
    isNightMode: Boolean,
    quickSites: List<QuickSite>,
    bookmarks: List<BookmarkItem> = emptyList(),
    onSearch: (String) -> Unit,
    onOpenSearch: () -> Unit = {},
    onSelectEngine: (String) -> Unit,
    onAddQuickSite: (title: String, url: String, bgColor: Long) -> Boolean = { _, _, _ -> false },
    onRemoveQuickSite: (url: String) -> Unit = {},
    onAddBookmarkToQuickSites: (bookmark: BookmarkItem) -> Boolean = { false },
    onOpenDownloads: () -> Unit,
    onOpenAi: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenBookmarks: () -> Unit,
    onOpenMoreSites: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var engineMenuExpanded by remember { mutableStateOf(false) }
    var showMoreSitesSheet by remember { mutableStateOf(false) }

    val bgColor = if (isNightMode) Color(0xFF111418) else Color(0xFFFAFBFD)
    val cardBg = if (isNightMode) Color(0xFF1E232B) else Color(0xFFFFFFFF)
    val textColor = if (isNightMode) Color(0xFFE2E8F0) else Color(0xFF1E293B)
    val subTextColor = if (isNightMode) Color(0xFF94A3B8) else Color(0xFF64748B)

    // Soft warm peach/gold wave glow at bottom matching Image 2
    val bgBrush = if (isNightMode) {
        Brush.verticalGradient(listOf(Color(0xFF111418), Color(0xFF161A20), Color(0xFF1A1F27)))
    } else {
        Brush.verticalGradient(
            0.0f to Color(0xFFFFFFFF),
            0.65f to Color(0xFFFCFDFC),
            1.0f to Color(0xFFFFF6EE)
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(bgBrush)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(36.dp))

            // Brand Header: "大象" with the cute elephant badge matching Image 1 & Image 2
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.padding(bottom = 28.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = if (isNightMode) Color(0xFF262C36) else Color(0xFFF1F5F9),
                    modifier = Modifier
                        .size(46.dp)
                        .shadow(4.dp, RoundedCornerShape(16.dp))
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Image(
                            painter = painterResource(id = R.drawable.ic_elephant),
                            contentDescription = "大象图标",
                            modifier = Modifier.size(34.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "大象",
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Bold,
                            color = textColor,
                            letterSpacing = 1.sp
                        )
                        if (isIncognito) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = Color(0xFF6366F1),
                                modifier = Modifier.padding(top = 4.dp)
                            ) {
                                Text(
                                    text = "无痕",
                                    color = Color.White,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Search Capsule Bar: Clicking enters Figure 2 search screen directly
            Surface(
                shape = RoundedCornerShape(32.dp),
                color = cardBg,
                shadowElevation = if (isNightMode) 2.dp else 4.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        1.dp,
                        if (isNightMode) Color(0xFF334155) else Color(0xFFE2E8F0),
                        RoundedCornerShape(32.dp)
                    )
                    .clip(RoundedCornerShape(32.dp))
                    .clickable { onOpenSearch() }
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)
                ) {
                    // Search Engine Picker Button (Image 2 style: ONLY icon + dropdown arrow, NO text)
                    Box {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .clickable { engineMenuExpanded = true }
                                .padding(start = 4.dp, end = 6.dp, top = 2.dp, bottom = 2.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ArrowDropDown,
                                contentDescription = "切换搜索引擎",
                                tint = subTextColor.copy(alpha = 0.8f),
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(2.dp))
                            SearchEngineLogo(
                                engine = searchEngine,
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        DropdownMenu(
                            expanded = engineMenuExpanded,
                            onDismissRequest = { engineMenuExpanded = false },
                            modifier = Modifier
                                .heightIn(max = 420.dp)
                                .widthIn(min = 210.dp)
                        ) {
                            SearchEngines.ALL.forEach { item ->
                                DropdownMenuItem(
                                    leadingIcon = { SearchEngineLogo(item.id, Modifier.size(20.dp)) },
                                    text = {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                text = item.name,
                                                fontSize = 14.sp,
                                                fontWeight = if (searchEngine == item.id) FontWeight.Bold else FontWeight.Normal,
                                                color = if (searchEngine == item.id) Color(0xFF2563EB) else textColor
                                            )
                                            if (searchEngine == item.id) {
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(
                                                    text = "✓",
                                                    fontSize = 13.sp,
                                                    color = Color(0xFF2563EB),
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    },
                                    onClick = { onSelectEngine(item.id); engineMenuExpanded = false }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Text(
                        text = "在 ${SearchEngines.getById(searchEngine).shortName} 中搜索或输入网址",
                        color = subTextColor,
                        fontSize = 15.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            // Quick 4-Action Row (Figure 4 style: 下载器, 问AI, 历史记录, 书签)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                QuickActionItem(
                    title = "下载器",
                    iconRes = R.drawable.ic_action_download_hex,
                    isNightMode = isNightMode,
                    onClick = onOpenDownloads
                )
                QuickActionItem(
                    title = "问AI",
                    iconRes = R.drawable.ic_action_ai_bubble,
                    isNightMode = isNightMode,
                    onClick = onOpenAi
                )
                QuickActionItem(
                    title = "历史记录",
                    iconRes = R.drawable.ic_action_history_clock,
                    isNightMode = isNightMode,
                    onClick = onOpenHistory
                )
                QuickActionItem(
                    title = "书签",
                    iconRes = R.drawable.ic_action_bookmark_ribbon,
                    isNightMode = isNightMode,
                    onClick = onOpenBookmarks
                )
            }

            Spacer(modifier = Modifier.height(30.dp))

            // Navigation Grid (5 columns per row, circular official logos matching Figure 3)
            val displaySites = remember(quickSites) {
                if (quickSites.any { it.url == "action://more" || it.title == "更多" || it.iconName == "more" }) {
                    quickSites
                } else {
                    quickSites + QuickSite("更多", "action://more", "more", 0xFF6366F1)
                }
            }
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                val rows = displaySites.chunked(5)
                rows.forEach { rowSites ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        for (index in 0 until 5) {
                            if (index < rowSites.size) {
                                val site = rowSites[index]
                                SiteShortcutItem(
                                    site = site,
                                    isNightMode = isNightMode,
                                    onClick = {
                                        if (site.url == "action://more" || site.title == "更多" || site.iconName == "more") {
                                            showMoreSitesSheet = true
                                            onOpenMoreSites()
                                        } else {
                                            onSearch(site.url)
                                        }
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                            } else {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }

        // More Quick Sites Bottom Sheet
        if (showMoreSitesSheet) {
            MoreQuickSitesSheet(
                quickSites = quickSites,
                bookmarks = bookmarks,
                isNightMode = isNightMode,
                onAddQuickSite = onAddQuickSite,
                onRemoveQuickSite = onRemoveQuickSite,
                onAddBookmarkToQuickSites = onAddBookmarkToQuickSites,
                onOpenUrl = { url ->
                    showMoreSitesSheet = false
                    onSearch(url)
                },
                onDismiss = { showMoreSitesSheet = false }
            )
        }
    }
}

@Composable
private fun QuickActionItem(
    title: String,
    iconRes: Int,
    isNightMode: Boolean,
    onClick: () -> Unit
) {
    val iconColor = if (isNightMode) Color(0xFFE2E8F0) else Color(0xFF262626)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 6.dp)
    ) {
        Box(
            modifier = Modifier.size(36.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(id = iconRes),
                contentDescription = title,
                tint = iconColor,
                modifier = Modifier.size(28.dp)
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = title,
            fontSize = 12.sp,
            color = if (isNightMode) Color(0xFFCBD5E1) else Color(0xFF262626),
            fontWeight = FontWeight.Normal
        )
    }
}

@Composable
fun SiteOfficialIcon(
    site: QuickSite,
    modifier: Modifier = Modifier
) {
    val iconRes = when (site.iconName.lowercase()) {
        "github" -> R.drawable.ic_site_github
        "tiktok" -> R.drawable.ic_site_tiktok
        "youtube" -> R.drawable.ic_site_youtube
        "instagram" -> R.drawable.ic_site_instagram
        "google" -> R.drawable.ic_engine_google
        "baidu" -> R.drawable.ic_engine_baidu
        "bilibili" -> R.drawable.ic_engine_bilibili
        "more" -> R.drawable.ic_site_more
        else -> when {
            site.url.contains("github.com", ignoreCase = true) || site.title.contains("github", ignoreCase = true) -> R.drawable.ic_site_github
            site.url.contains("tiktok.com", ignoreCase = true) || site.title.contains("tiktok", ignoreCase = true) -> R.drawable.ic_site_tiktok
            site.url.contains("youtube.com", ignoreCase = true) || site.title.contains("youtube", ignoreCase = true) -> R.drawable.ic_site_youtube
            site.url.contains("instagram.com", ignoreCase = true) || site.title.contains("instagram", ignoreCase = true) -> R.drawable.ic_site_instagram
            site.url.contains("google.com", ignoreCase = true) || site.title.contains("google", ignoreCase = true) -> R.drawable.ic_engine_google
            site.url.contains("bilibili.com", ignoreCase = true) || site.title.contains("哔哩", ignoreCase = true) -> R.drawable.ic_engine_bilibili
            site.url.contains("baidu.com", ignoreCase = true) || site.title.contains("百度", ignoreCase = true) -> R.drawable.ic_engine_baidu
            site.url == "action://more" || site.title == "更多" -> R.drawable.ic_site_more
            else -> null
        }
    }

    if (iconRes != null) {
        Image(
            painter = painterResource(id = iconRes),
            contentDescription = site.title,
            modifier = modifier
        )
    } else {
        Box(
            modifier = modifier
                .clip(CircleShape)
                .background(Color(site.bgColor)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = site.title.take(1).uppercase(),
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = Color.White
            )
        }
    }
}

@Composable
private fun SiteShortcutItem(
    site: QuickSite,
    isNightMode: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp)
    ) {
        Surface(
            shape = CircleShape,
            color = Color.Transparent,
            shadowElevation = 1.dp,
            modifier = Modifier.size(48.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape),
                contentAlignment = Alignment.Center
            ) {
                SiteOfficialIcon(
                    site = site,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = site.title,
            fontSize = 12.sp,
            color = if (isNightMode) Color(0xFFE2E8F0) else Color(0xFF334155),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}
