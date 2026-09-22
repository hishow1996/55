package com.example.ui.browser

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun BottomNavBar(
    canGoBack: Boolean,
    canGoForward: Boolean,
    tabCount: Int,
    isIncognito: Boolean,
    isNightMode: Boolean,
    onBack: () -> Unit,
    onForward: () -> Unit,
    onMenu: () -> Unit,
    onTabs: () -> Unit,
    onHome: () -> Unit,
    modifier: Modifier = Modifier
) {
    val barBg = if (isIncognito) {
        Color(0xFF161324)
    } else if (isNightMode) {
        Color(0xFF13171D)
    } else {
        Color(0xFFFFFFFF)
    }

    val iconTint = if (isIncognito) Color(0xFFC4B5FD) else if (isNightMode) Color(0xFFE2E8F0) else Color(0xFF334155)

    Surface(
        color = barBg,
        shadowElevation = 8.dp,
        modifier = modifier
            .fillMaxWidth()
            .border(
                0.5.dp,
                if (isNightMode) Color(0xFF1E293B) else Color(0xFFE2E8F0)
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .height(52.dp)
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 1. Back Button
            IconButton(
                onClick = onBack,
                modifier = Modifier.size(44.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "后退",
                    tint = iconTint,
                    modifier = Modifier.size(22.dp)
                )
            }

            // 2. Forward Button
            IconButton(
                onClick = onForward,
                modifier = Modifier.size(44.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = "前进",
                    tint = iconTint,
                    modifier = Modifier.size(22.dp)
                )
            }

            // 3. Menu Button (three horizontal lines)
            IconButton(
                onClick = onMenu,
                modifier = Modifier.size(44.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Menu,
                    contentDescription = "菜单",
                    tint = iconTint,
                    modifier = Modifier.size(22.dp)
                )
            }

            // 4. Tab Switcher Button (Rounded square with count [N])
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onTabs),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(22.dp)
                        .border(
                            2.dp,
                            iconTint,
                            RoundedCornerShape(6.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = tabCount.toString(),
                        color = iconTint,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // 5. Home Button
            IconButton(
                onClick = onHome,
                modifier = Modifier.size(44.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Home,
                    contentDescription = "主页",
                    tint = iconTint,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}
