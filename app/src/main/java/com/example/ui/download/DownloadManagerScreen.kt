package com.example.ui.download

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.download.ElephantDownloadManager
import com.example.model.DownloadItem
import com.example.model.DownloadStatus
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadManagerScreen(
    downloadManager: ElephantDownloadManager,
    isNightMode: Boolean,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val downloads by downloadManager.downloads.collectAsState()

    var selectedTabIndex by remember { mutableIntStateOf(0) } // 0: 全部, 1: 下载中, 2: 已完成
    var showNewDownloadDialog by remember { mutableStateOf(false) }
    var itemToDelete by remember { mutableStateOf<DownloadItem?>(null) }

    val bg = if (isNightMode) Color(0xFF0F172A) else Color(0xFFF8FAFC)
    val cardBg = if (isNightMode) Color(0xFF1E293B) else Color.White
    val textPrimary = if (isNightMode) Color(0xFFF1F5F9) else Color(0xFF0F172A)
    val textSecondary = if (isNightMode) Color(0xFF94A3B8) else Color(0xFF64748B)

    val activeCount = downloads.count { it.status == DownloadStatus.DOWNLOADING || it.status == DownloadStatus.PENDING || it.status == DownloadStatus.PAUSED }
    val completedCount = downloads.count { it.status == DownloadStatus.COMPLETED }
    val totalSpeed = downloads.filter { it.status == DownloadStatus.DOWNLOADING }.sumOf { it.speedBytesPerSec }

    val filteredList = when (selectedTabIndex) {
        1 -> downloads.filter { it.status == DownloadStatus.DOWNLOADING || it.status == DownloadStatus.PENDING || it.status == DownloadStatus.PAUSED }
        2 -> downloads.filter { it.status == DownloadStatus.COMPLETED }
        else -> downloads
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "下载管理",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = textPrimary
                        )
                        if (activeCount > 0) {
                            Text(
                                text = "正在下载 $activeCount 个任务 · ${DownloadItem.formatBytes(totalSpeed)}/s",
                                fontSize = 11.sp,
                                color = Color(0xFF38BDF8)
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回",
                            tint = textPrimary
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { showNewDownloadDialog = true }) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "新建下载任务",
                            tint = Color(0xFF3B82F6)
                        )
                    }
                    if (completedCount > 0) {
                        IconButton(onClick = { downloadManager.clearCompleted() }) {
                            Icon(
                                imageVector = Icons.Default.DeleteSweep,
                                contentDescription = "清空已完成",
                                tint = textSecondary
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = if (isNightMode) Color(0xFF1E293B) else Color.White
                )
            )
        },
        containerColor = bg,
        modifier = modifier.statusBarsPadding()
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Filter Tabs
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = selectedTabIndex == 0,
                    onClick = { selectedTabIndex = 0 },
                    label = { Text("全部 (${downloads.size})") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color(0xFF3B82F6),
                        selectedLabelColor = Color.White
                    )
                )
                FilterChip(
                    selected = selectedTabIndex == 1,
                    onClick = { selectedTabIndex = 1 },
                    label = { Text("下载中 ($activeCount)") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color(0xFF3B82F6),
                        selectedLabelColor = Color.White
                    )
                )
                FilterChip(
                    selected = selectedTabIndex == 2,
                    onClick = { selectedTabIndex = 2 },
                    label = { Text("已完成 ($completedCount)") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color(0xFF3B82F6),
                        selectedLabelColor = Color.White
                    )
                )
            }

            // Download List or Empty State
            if (filteredList.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = if (isNightMode) Color(0xFF1E293B) else Color(0xFFE2E8F0),
                            modifier = Modifier.size(80.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.CloudDownload,
                                    contentDescription = null,
                                    tint = Color(0xFF3B82F6),
                                    modifier = Modifier.size(40.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = if (selectedTabIndex == 1) "暂无正在下载的任务" else "暂无下载记录",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                            color = textPrimary
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "可在网页中点击文件或图片链接开始下载，也可以点击右上角 '+' 手动添加",
                            fontSize = 12.sp,
                            color = textSecondary,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 20.dp)
                        )
                        Spacer(modifier = Modifier.height(20.dp))
                        Button(
                            onClick = {
                                // Add a fast sample download for immediate user testing
                                downloadManager.enqueueDownload(
                                    url = "https://raw.githubusercontent.com/google/material-design-icons/master/png/action/visibility/materialicons/48dp/2x/baseline_visibility_black_48dp.png",
                                    suggestedFileName = "elephant_sample_icon.png",
                                    mimeType = "image/png"
                                )
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3B82F6))
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("添加快速测试下载")
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(filteredList, key = { it.id }) { item ->
                        DownloadItemCard(
                            item = item,
                            isNightMode = isNightMode,
                            cardBg = cardBg,
                            textPrimary = textPrimary,
                            textSecondary = textSecondary,
                            onOpen = { downloadManager.openDownloadedFile(context, item) },
                            onShare = { downloadManager.shareDownloadedFile(context, item) },
                            onPause = { downloadManager.pauseDownload(item.id) },
                            onResume = { downloadManager.resumeDownload(item.id) },
                            onDelete = { itemToDelete = item }
                        )
                    }
                }
            }
        }
    }

    // Delete Confirmation Dialog
    itemToDelete?.let { item ->
        AlertDialog(
            onDismissRequest = { itemToDelete = null },
            title = { Text("删除下载任务") },
            text = { Text("是否确定从下载列表中移除并删除本地文件 \"${item.fileName}\"？") },
            confirmButton = {
                TextButton(
                    onClick = {
                        downloadManager.deleteDownload(item.id, deleteFile = true)
                        itemToDelete = null
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFEF4444))
                ) {
                    Text("删除文件")
                }
            },
            dismissButton = {
                TextButton(onClick = { itemToDelete = null }) {
                    Text("取消")
                }
            }
        )
    }

    // New Download Dialog
    if (showNewDownloadDialog) {
        NewDownloadDialog(
            isNightMode = isNightMode,
            onDismiss = { showNewDownloadDialog = false },
            onConfirm = { url, fileName ->
                downloadManager.enqueueDownload(url = url, suggestedFileName = fileName.ifBlank { null })
                showNewDownloadDialog = false
            }
        )
    }
}

@Composable
fun DownloadItemCard(
    item: DownloadItem,
    isNightMode: Boolean,
    cardBg: Color,
    textPrimary: Color,
    textSecondary: Color,
    onOpen: () -> Unit,
    onShare: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = cardBg),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            // Top Row: File Icon + Name + Status Tag
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // File Type Icon
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = getFileTypeColor(item.fileName).copy(alpha = 0.15f),
                    modifier = Modifier.size(42.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = getFileTypeIcon(item.fileName),
                            contentDescription = null,
                            tint = getFileTypeColor(item.fileName),
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.fileName,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        color = textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = item.url,
                        fontSize = 11.sp,
                        color = textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Status Badge
                StatusBadge(status = item.status)
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Middle Section: Progress / Speed / Stats
            when (item.status) {
                DownloadStatus.DOWNLOADING, DownloadStatus.PAUSED, DownloadStatus.PENDING -> {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        // Progress Bar
                        LinearProgressIndicator(
                            progress = { item.progress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = if (item.status == DownloadStatus.PAUSED) Color(0xFFF59E0B) else Color(0xFF3B82F6),
                            trackColor = if (isNightMode) Color(0xFF334155) else Color(0xFFE2E8F0)
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = item.formattedProgressSize,
                                fontSize = 11.sp,
                                color = textSecondary
                            )

                            if (item.status == DownloadStatus.DOWNLOADING) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Speed,
                                        contentDescription = null,
                                        tint = Color(0xFF38BDF8),
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text(
                                        text = item.formattedSpeed,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = Color(0xFF38BDF8)
                                    )
                                }
                            } else if (item.status == DownloadStatus.PAUSED) {
                                Text(
                                    text = "已暂停",
                                    fontSize = 11.sp,
                                    color = Color(0xFFF59E0B)
                                )
                            }
                        }
                    }
                }

                DownloadStatus.COMPLETED -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${item.formattedTotalSize} · ${formatDate(item.finishTime ?: item.startTime)}",
                            fontSize = 11.sp,
                            color = textSecondary
                        )

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = Color(0xFF10B981),
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "下载完成",
                                fontSize = 11.sp,
                                color = Color(0xFF10B981),
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                DownloadStatus.FAILED -> {
                    Text(
                        text = "失败原因: ${item.errorMessage ?: "网络连接异常"}",
                        fontSize = 11.sp,
                        color = Color(0xFFEF4444)
                    )
                }

                DownloadStatus.CANCELLED -> {
                    Text(
                        text = "任务已取消",
                        fontSize = 11.sp,
                        color = textSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                when (item.status) {
                    DownloadStatus.DOWNLOADING -> {
                        OutlinedButton(
                            onClick = onPause,
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                            modifier = Modifier.height(34.dp)
                        ) {
                            Icon(Icons.Default.Pause, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("暂停", fontSize = 12.sp)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        OutlinedButton(
                            onClick = onDelete,
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                            modifier = Modifier.height(34.dp)
                        ) {
                            Icon(Icons.Default.Clear, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("取消", fontSize = 12.sp)
                        }
                    }

                    DownloadStatus.PAUSED -> {
                        Button(
                            onClick = onResume,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3B82F6)),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                            modifier = Modifier.height(34.dp)
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("继续", fontSize = 12.sp)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        OutlinedButton(
                            onClick = onDelete,
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                            modifier = Modifier.height(34.dp)
                        ) {
                            Icon(Icons.Default.DeleteOutline, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("删除", fontSize = 12.sp)
                        }
                    }

                    DownloadStatus.COMPLETED -> {
                        // Open Button (CRITICAL FEATURE)
                        Button(
                            onClick = onOpen,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
                            modifier = Modifier.height(34.dp)
                        ) {
                            Icon(Icons.Default.FileOpen, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("打开", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        OutlinedButton(
                            onClick = onShare,
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.height(34.dp)
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("分享", fontSize = 12.sp)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        IconButton(
                            onClick = onDelete,
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(Icons.Default.DeleteOutline, contentDescription = "删除记录", tint = textSecondary, modifier = Modifier.size(18.dp))
                        }
                    }

                    DownloadStatus.FAILED -> {
                        Button(
                            onClick = onResume,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                            modifier = Modifier.height(34.dp)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("重试", fontSize = 12.sp)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        OutlinedButton(
                            onClick = onDelete,
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.height(34.dp)
                        ) {
                            Icon(Icons.Default.DeleteOutline, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("删除", fontSize = 12.sp)
                        }
                    }

                    DownloadStatus.CANCELLED -> {
                        OutlinedButton(
                            onClick = onDelete,
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.height(34.dp)
                        ) {
                            Icon(Icons.Default.DeleteOutline, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("清除", fontSize = 12.sp)
                        }
                    }

                    DownloadStatus.PENDING -> {
                        OutlinedButton(
                            onClick = onDelete,
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.height(34.dp)
                        ) {
                            Text("取消", fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun StatusBadge(status: DownloadStatus) {
    val (label, bg, fg) = when (status) {
        DownloadStatus.DOWNLOADING -> Triple("下载中", Color(0xFF3B82F6).copy(alpha = 0.12f), Color(0xFF2563EB))
        DownloadStatus.PAUSED -> Triple("已暂停", Color(0xFFF59E0B).copy(alpha = 0.12f), Color(0xFFD97706))
        DownloadStatus.COMPLETED -> Triple("已完成", Color(0xFF10B981).copy(alpha = 0.12f), Color(0xFF059669))
        DownloadStatus.FAILED -> Triple("失败", Color(0xFFEF4444).copy(alpha = 0.12f), Color(0xFFDC2626))
        DownloadStatus.CANCELLED -> Triple("已取消", Color(0xFF64748B).copy(alpha = 0.12f), Color(0xFF475569))
        DownloadStatus.PENDING -> Triple("等待中", Color(0xFF8B5CF6).copy(alpha = 0.12f), Color(0xFF7C3AED))
    }

    Surface(
        shape = RoundedCornerShape(6.dp),
        color = bg
    ) {
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = fg,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

@Composable
fun NewDownloadDialog(
    isNightMode: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (url: String, fileName: String) -> Unit
) {
    var urlText by remember { mutableStateOf("") }
    var fileNameText by remember { mutableStateOf("") }
    var isError by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("新建下载任务") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("输入下载地址(URL)：", fontSize = 13.sp)
                OutlinedTextField(
                    value = urlText,
                    onValueChange = {
                        urlText = it
                        isError = false
                    },
                    placeholder = { Text("https://example.com/file.zip") },
                    isError = isError,
                    supportingText = if (isError) { { Text("下载链接不能为空") } } else null,
                    modifier = Modifier.fillMaxWidth()
                )

                Text("自定义文件名(可选)：", fontSize = 13.sp)
                OutlinedTextField(
                    value = fileNameText,
                    onValueChange = { fileNameText = it },
                    placeholder = { Text("留空自动从链接中获取") },
                    modifier = Modifier.fillMaxWidth()
                )

                // Quick test presets
                Text("或选择快速测试预设：", fontSize = 12.sp, color = Color(0xFF3B82F6))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    PresetChip("测试图片") {
                        urlText = "https://raw.githubusercontent.com/google/material-design-icons/master/png/action/visibility/materialicons/48dp/2x/baseline_visibility_black_48dp.png"
                        fileNameText = "elephant_icon.png"
                    }
                    PresetChip("测试文档") {
                        urlText = "https://www.w3.org/WAI/ER/tests/xhtml/testfiles/resources/pdf/dummy.pdf"
                        fileNameText = "sample_manual.pdf"
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (urlText.isBlank()) {
                        isError = true
                    } else {
                        onConfirm(urlText.trim(), fileNameText.trim())
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3B82F6))
            ) {
                Text("开始下载")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

@Composable
private fun PresetChip(label: String, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = Color(0xFF3B82F6).copy(alpha = 0.1f),
        modifier = Modifier.clickable { onClick() }
    ) {
        Text(
            text = label,
            fontSize = 11.sp,
            color = Color(0xFF2563EB),
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

private fun getFileTypeIcon(fileName: String): ImageVector {
    val ext = fileName.substringAfterLast('.', "").lowercase()
    return when (ext) {
        "mp4", "mkv", "webm", "avi", "mov", "flv" -> Icons.Default.VideoFile
        "mp3", "m4a", "wav", "flac", "aac", "ogg" -> Icons.Default.AudioFile
        "jpg", "jpeg", "png", "webp", "gif", "bmp", "svg" -> Icons.Default.Image
        "zip", "rar", "7z", "tar", "gz" -> Icons.Default.FolderZip
        "pdf", "doc", "docx", "txt", "xls", "xlsx", "ppt", "pptx" -> Icons.Default.Description
        else -> Icons.Default.InsertDriveFile
    }
}

private fun getFileTypeColor(fileName: String): Color {
    val ext = fileName.substringAfterLast('.', "").lowercase()
    return when (ext) {
        "apk" -> Color(0xFF10B981)
        "mp4", "mkv", "webm", "avi", "mov" -> Color(0xFF8B5CF6)
        "mp3", "m4a", "wav", "flac" -> Color(0xFFF59E0B)
        "jpg", "jpeg", "png", "webp", "gif" -> Color(0xFF06B6D4)
        "zip", "rar", "7z", "tar", "gz" -> Color(0xFF3B82F6)
        "pdf", "doc", "docx", "txt" -> Color(0xFFEC4899)
        else -> Color(0xFF64748B)
    }
}

private fun formatDate(ms: Long): String {
    val sdf = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())
    return sdf.format(Date(ms))
}
