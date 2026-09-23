package com.example.ui.ai

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Backward compatibility wrapper for AI chat entry points.
 * Delegates to the unified and coordinated ElephantAiScreen.
 */
@Composable
fun AiChatDialog(
    currentPageTitle: String = "",
    currentPageUrl: String = "",
    onDismiss: () -> Unit,
    onSummarizeWebpage: () -> Unit = {},
    onTranslateWebpage: () -> Unit = {},
    isNightMode: Boolean = false
) {
    ElephantAiScreen(
        isNightMode = isNightMode,
        onBack = onDismiss
    )
}
