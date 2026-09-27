package com.example.ui.browser

import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.delay
import org.json.JSONObject
import java.util.Locale

/**
 * Fullscreen Interactive Controller Overlay for Web Videos (HTML5 Custom View).
 *
 * Ensures:
 * 1. The underlying web video continues hardware playback without freezing.
 * 2. Complete, responsive player controls overlay (Top bar, Center controls, Seekbar, Bottom bar, Lock).
 * 3. Screen rotation toggle and intuitive gesture controls.
 */
@Composable
fun WebVideoFullscreenOverlay(
    customVideoView: View,
    title: String,
    onClose: () -> Unit,
    onRotateScreen: () -> Unit,
    onDownload: (() -> Unit)? = null,
    evaluateJavascript: (String, ((String?) -> Unit)?) -> Unit,
    onAdjustBrightness: ((Float) -> Float)? = null,
    onAdjustVolume: ((Float) -> Float)? = null,
    modifier: Modifier = Modifier
) {
    var showControls by remember { mutableStateOf(true) }
    var isLocked by remember { mutableStateOf(false) }
    var showLockedHint by remember { mutableStateOf(false) }

    var isPlaying by remember { mutableStateOf(true) }
    var currentPositionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var playbackSpeed by remember { mutableFloatStateOf(1.0f) }
    var showSpeedMenu by remember { mutableStateOf(false) }

    var isSeeking by remember { mutableStateOf(false) }
    var seekProgress by remember { mutableFloatStateOf(0f) }

    // 1. Proactively ensure video playback starts and does not get stuck/frozen
    LaunchedEffect(customVideoView) {
        evaluateJavascript(
            """
            (function() {
                var vs = document.querySelectorAll('video');
                for (var i = 0; i < vs.length; i++) {
                    var v = vs[i];
                    if (v.paused) {
                        var p = v.play();
                        if (p && typeof p.catch === 'function') {
                            p.catch(function(){});
                        }
                    }
                }
            })();
            """.trimIndent(),
            null
        )
    }

    // 2. Poll the HTML5 video state to update timeline, duration, and play/pause status
    LaunchedEffect(customVideoView) {
        while (true) {
            evaluateJavascript(
                """
                (function() {
                    var v = document.querySelector('video');
                    if (!v) return '';
                    return JSON.stringify({
                        t: Math.floor((v.currentTime || 0) * 1000),
                        d: Math.floor((v.duration || 0) * 1000),
                        p: !v.paused,
                        r: v.playbackRate || 1.0
                    });
                })();
                """.trimIndent()
            ) { result ->
                try {
                    if (!result.isNullOrBlank() && result != "null" && result != "\"\"") {
                        val clean = result.removePrefix("\"").removeSuffix("\"").replace("\\\"", "\"")
                        val obj = JSONObject(clean)
                        if (!isSeeking) {
                            currentPositionMs = obj.optLong("t", currentPositionMs)
                        }
                        val d = obj.optLong("d", 0L)
                        if (d > 0L) durationMs = d
                        isPlaying = obj.optBoolean("p", isPlaying)
                        val r = obj.optDouble("r", 1.0).toFloat()
                        if (r > 0f) playbackSpeed = r
                    }
                } catch (_: Exception) {}
            }
            delay(400)
        }
    }

    // 3. Auto-hide controls after 4 seconds of active playback
    LaunchedEffect(showControls, isPlaying, isLocked, isSeeking, showSpeedMenu) {
        if (showControls && isPlaying && !isLocked && !isSeeking && !showSpeedMenu) {
            delay(4000)
            showControls = false
        }
    }

    // 4. Locked pill hint auto-dismiss
    LaunchedEffect(showLockedHint) {
        if (showLockedHint) {
            delay(2200)
            showLockedHint = false
        }
    }

    val togglePlayPause: () -> Unit = {
        val nextPlaying = !isPlaying
        isPlaying = nextPlaying
        val js = if (nextPlaying) {
            "var v = document.querySelector('video'); if (v) v.play().catch(function(){});"
        } else {
            "var v = document.querySelector('video'); if (v) v.pause();"
        }
        evaluateJavascript(js, null)
    }

    val seekRelative: (Long) -> Unit = { deltaMs ->
        val targetMs = (currentPositionMs + deltaMs).coerceIn(0L, if (durationMs > 0L) durationMs else Long.MAX_VALUE)
        currentPositionMs = targetMs
        val targetSec = targetMs / 1000.0
        evaluateJavascript("var v = document.querySelector('video'); if (v) v.currentTime = $targetSec;", null)
    }

    val setSpeed: (Float) -> Unit = { sp ->
        playbackSpeed = sp
        showSpeedMenu = false
        evaluateJavascript("var v = document.querySelector('video'); if (v) v.playbackRate = $sp;", null)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // --- Video Container ---
        AndroidView(
            factory = { ctx ->
                FrameLayout(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    setBackgroundColor(0xFF000000.toInt())
                    keepScreenOn = true
                    isFocusable = true
                    isFocusableInTouchMode = true
                    requestFocus()
                    (customVideoView.parent as? ViewGroup)?.removeView(customVideoView)
                    addView(customVideoView)
                }
            },
            update = { frameLayout ->
                if (customVideoView.parent != frameLayout) {
                    (customVideoView.parent as? ViewGroup)?.removeView(customVideoView)
                    frameLayout.removeAllViews()
                    frameLayout.addView(customVideoView)
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // --- Touch Gestures Handler Overlay ---
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(isLocked, showControls) {
                    detectTapGestures(
                        onTap = {
                            if (isLocked) {
                                showLockedHint = true
                            } else {
                                showControls = !showControls
                                showSpeedMenu = false
                            }
                        },
                        onDoubleTap = {
                            if (!isLocked) {
                                togglePlayPause()
                                showControls = true
                            }
                        }
                    )
                }
        )

        // --- Top Bar (Back, Title, Rotate, Download) ---
        AnimatedVisibility(
            visible = showControls && !isLocked,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Black.copy(alpha = 0.85f), Color.Transparent)
                        )
                    )
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onClose,
                    modifier = Modifier
                        .size(42.dp)
                        .background(Color.Black.copy(alpha = 0.42f), CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "退出全屏",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                Text(
                    text = title.ifBlank { "网页视频" },
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                IconButton(
                    onClick = onRotateScreen,
                    modifier = Modifier.size(42.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ScreenRotation,
                        contentDescription = "手动旋转屏幕",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }

                if (onDownload != null) {
                    IconButton(
                        onClick = onDownload,
                        modifier = Modifier.size(42.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowDownward,
                            contentDescription = "下载视频",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        }

        // --- Center Play/Pause & Seek Rewind/Forward Controls ---
        AnimatedVisibility(
            visible = showControls && !isLocked,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.Center)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(28.dp)
            ) {
                // Rewind 10s
                IconButton(
                    onClick = { seekRelative(-10000L) },
                    modifier = Modifier
                        .size(50.dp)
                        .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.Replay10,
                        contentDescription = "快退10秒",
                        tint = Color.White,
                        modifier = Modifier.size(30.dp)
                    )
                }

                // Play / Pause Toggle
                IconButton(
                    onClick = togglePlayPause,
                    modifier = Modifier
                        .size(64.dp)
                        .background(Color.Black.copy(alpha = 0.65f), CircleShape)
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "暂停" else "播放",
                        tint = Color.White,
                        modifier = Modifier.size(40.dp)
                    )
                }

                // Fast Forward 10s
                IconButton(
                    onClick = { seekRelative(10000L) },
                    modifier = Modifier
                        .size(50.dp)
                        .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.Forward10,
                        contentDescription = "快进10秒",
                        tint = Color.White,
                        modifier = Modifier.size(30.dp)
                    )
                }
            }
        }

        // --- Bottom Bar (Play/Pause, Time, Seekbar, Speed, Fullscreen Exit) ---
        AnimatedVisibility(
            visible = showControls && !isLocked,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.88f))
                        )
                    )
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Play/Pause button
                    IconButton(
                        onClick = togglePlayPause,
                        modifier = Modifier.size(38.dp)
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isPlaying) "暂停" else "播放",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    // Time display
                    Text(
                        text = "${formatTime(currentPositionMs)} / ${formatTime(durationMs)}",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 6.dp)
                    )

                    // Progress Slider
                    val sliderVal = if (isSeeking) {
                        seekProgress
                    } else if (durationMs > 0L) {
                        (currentPositionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
                    } else 0f

                    Slider(
                        value = sliderVal,
                        onValueChange = { frac ->
                            isSeeking = true
                            seekProgress = frac
                        },
                        onValueChangeFinished = {
                            val targetMs = (seekProgress * durationMs).toLong().coerceIn(0L, durationMs)
                            currentPositionMs = targetMs
                            val targetSec = targetMs / 1000.0
                            evaluateJavascript("var v = document.querySelector('video'); if (v) v.currentTime = $targetSec;", null)
                            isSeeking = false
                        },
                        colors = SliderDefaults.colors(
                            thumbColor = Color(0xFF38BDF8),
                            activeTrackColor = Color(0xFF2563EB),
                            inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(28.dp)
                            .padding(horizontal = 8.dp)
                    )

                    // Playback Speed Button
                    Surface(
                        onClick = { showSpeedMenu = !showSpeedMenu },
                        color = Color.Black.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.padding(end = 4.dp)
                    ) {
                        Text(
                            text = "${playbackSpeed}X",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }

                    // Fullscreen Exit Button
                    IconButton(
                        onClick = onClose,
                        modifier = Modifier.size(38.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.FullscreenExit,
                            contentDescription = "退出全屏",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        }

        // --- Speed Menu Popup ---
        if (showSpeedMenu && showControls && !isLocked) {
            Surface(
                color = Color(0xFF1E293B).copy(alpha = 0.95f),
                shape = RoundedCornerShape(12.dp),
                shadowElevation = 8.dp,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 48.dp, bottom = 48.dp)
            ) {
                Column(modifier = Modifier.padding(vertical = 4.dp)) {
                    val speeds = listOf(0.75f, 1.0f, 1.25f, 1.5f, 2.0f, 3.0f)
                    speeds.forEach { sp ->
                        Surface(
                            onClick = { setSpeed(sp) },
                            color = if (playbackSpeed == sp) Color(0xFF2563EB) else Color.Transparent,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "${sp}X",
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = if (playbackSpeed == sp) FontWeight.Bold else FontWeight.Normal,
                                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                            )
                        }
                    }
                }
            }
        }

        // --- Screen Lock Button (Always accessible on center-left) ---
        IconButton(
            onClick = {
                isLocked = !isLocked
                if (!isLocked) {
                    showControls = true
                }
            },
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 12.dp)
                .size(44.dp)
                .background(
                    if (isLocked) Color(0xFFB45309).copy(alpha = 0.75f) else Color.Black.copy(alpha = 0.5f),
                    CircleShape
                )
        ) {
            Icon(
                imageVector = if (isLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                contentDescription = if (isLocked) "解锁屏幕" else "锁定屏幕",
                tint = if (isLocked) Color(0xFFFBBF24) else Color.White,
                modifier = Modifier.size(22.dp)
            )
        }

        // --- Screen Locked Notification Pill ---
        AnimatedVisibility(
            visible = showLockedHint,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 24.dp)
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color.Black.copy(alpha = 0.75f))
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(
                    text = "屏幕已锁定，点击左侧锁图标解锁",
                    color = Color(0xFFFBBF24),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

private fun formatTime(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format(Locale.US, "%02d:%02d", minutes, seconds)
}
