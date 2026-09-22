package com.example.ui.plugin

import android.widget.Toast
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.BrowserRepository
import com.example.model.PluginItem

@Composable
fun PluginManagerScreen(
    repository: BrowserRepository,
    isNightMode: Boolean,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val plugins = repository.plugins.value
    var showAddDialog by remember { mutableStateOf(false) }

    val bg = if (isNightMode) Color(0xFF111418) else Color(0xFFF8FAFC)
    val cardBg = if (isNightMode) Color(0xFF1E232B) else Color(0xFFFFFFFF)
    val textColor = if (isNightMode) Color(0xFFE2E8F0) else Color(0xFF1E293B)
    val subTextColor = if (isNightMode) Color(0xFF94A3B8) else Color(0xFF64748B)

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
                .padding(horizontal = 8.dp, vertical = 10.dp),
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
                text = "自定义插件与扩展",
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold,
                color = textColor,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = { showAddDialog = true }) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "新建插件",
                    tint = Color(0xFF2563EB)
                )
            }
        }

        HorizontalDivider(color = if (isNightMode) Color(0xFF262C36) else Color(0xFFE2E8F0))

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text(
                    text = "已安装插件 (${plugins.size})",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = subTextColor,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }

            items(plugins, key = { it.id }) { plugin ->
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = cardBg,
                    shadowElevation = 2.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (plugin.isBuiltIn) Color(0xFFEDE9FE) else Color(0xFFE0F2FE),
                                    modifier = Modifier.size(38.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Default.Extension,
                                            contentDescription = null,
                                            tint = if (plugin.isBuiltIn) Color(0xFF7C3AED) else Color(0xFF0284C7),
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = plugin.name,
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = textColor
                                        )
                                        if (plugin.isBuiltIn) {
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Surface(
                                                shape = RoundedCornerShape(4.dp),
                                                color = Color(0xFF7C3AED).copy(alpha = 0.15f)
                                            ) {
                                                Text(
                                                    text = "内置",
                                                    fontSize = 10.sp,
                                                    color = Color(0xFF7C3AED),
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                                )
                                            }
                                        }
                                    }
                                    Text(
                                        text = "v${plugin.version} · ${plugin.author} · ${plugin.runAt}",
                                        fontSize = 11.sp,
                                        color = subTextColor
                                    )
                                }
                            }

                            Switch(
                                checked = plugin.isEnabled,
                                onCheckedChange = { isChecked ->
                                    repository.togglePlugin(plugin.id, isChecked)
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = Color(0xFF2563EB)
                                )
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = plugin.description,
                            fontSize = 13.sp,
                            color = subTextColor,
                            lineHeight = 18.sp
                        )

                        if (!plugin.isBuiltIn) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                IconButton(
                                    onClick = {
                                        repository.deletePlugin(plugin.id)
                                        Toast.makeText(context, "已删除插件", Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "删除插件",
                                        tint = Color(0xFFEF4444),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Add Custom Plugin Dialog
    if (showAddDialog) {
        var name by remember { mutableStateOf("") }
        var description by remember { mutableStateOf("") }
        var pattern by remember { mutableStateOf("*") }
        var code by remember {
            mutableStateOf(
                """
                // 大象浏览器自定义脚本
                (function() {
                    console.log('大象自定义脚本已运行在：' + location.href);
                    // 在此处编写自定义JS逻辑，例如自动填充、修改DOM等
                })();
                """.trimIndent()
            )
        }

        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("新建自定义JS插件") },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("插件名称") },
                        placeholder = { Text("例如：自动跳过广告/暗黑模式") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it },
                        label = { Text("功能描述") },
                        placeholder = { Text("简要描述该插件的功能") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = pattern,
                        onValueChange = { pattern = it },
                        label = { Text("匹配网址规则") },
                        placeholder = { Text("* 代表所有网站") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = code,
                        onValueChange = { code = it },
                        label = { Text("JavaScript 脚本代码") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (name.isBlank() || code.isBlank()) {
                            Toast.makeText(context, "插件名称与代码不能为空", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        repository.addCustomPlugin(
                            PluginItem(
                                name = name,
                                description = description.ifBlank { "用户自定义扩展脚本" },
                                matchPattern = pattern.ifBlank { "*" },
                                scriptCode = code,
                                isBuiltIn = false
                            )
                        )
                        showAddDialog = false
                        Toast.makeText(context, "自定义插件已添加并生效", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB))
                ) {
                    Text("保存并启用")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) {
                    Text("取消")
                }
            }
        )
    }
}
