package com.example.player

import android.media.MediaPlayer
import android.net.Uri
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.VideoView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.model.VideoMediaInfo
import kotlinx.coroutines.delay
import kotlin.math.max
import kotlin.math.roundToInt

@Composable
fun InAppFloatingPlayer(
    videoInfo: VideoMediaInfo,
    onClose: () -> Unit,
    onEnterGlobalPiP: () -> Unit,
    onEnterFullscreen: () -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val context = LocalContext.current

    // Video aspect ratio calculation (matches video picture ratio)
    val baseRatio = remember(videoInfo.videoWidth, videoInfo.videoHeight) {
        if (videoInfo.videoHeight > 0 && videoInfo.videoWidth > 0) {
            videoInfo.videoWidth.toFloat() / videoInfo.videoHeight.toFloat()
        } else {
            16f / 9f
        }
    }

    // Window size state (default 260dp width, height computed from ratio)
    var windowWidthDp by remember { mutableFloatStateOf(280f) }
    var windowHeightDp by remember { mutableFloatStateOf(280f / baseRatio) }

    // Lock aspect ratio toggle: when locked, resizing keeps the exact video ratio; when unlocked, freely resize in any direction
    var lockAspectRatio by remember { mutableStateOf(false) }

    // Window position offset (pixels)
    var offsetX by remember { mutableFloatStateOf(60f) }
    var offsetY by remember { mutableFloatStateOf(200f) }

    // Player playback state
    var isPlaying by remember { mutableStateOf(videoInfo.isPlaying) }
    var currentPositionMs by remember { mutableIntStateOf((videoInfo.currentTime * 1000).toInt()) }
    var durationMs by remember { mutableIntStateOf((videoInfo.duration * 1000).toInt().coerceAtLeast(1000)) }
    var playbackSpeed by remember { mutableFloatStateOf(1.0f) }
    var showControls by remember { mutableStateOf(true) }

    // VideoView holder
    var videoViewRef by remember { mutableStateOf<VideoView?>(null) }
    var mediaPlayerRef by remember { mutableStateOf<MediaPlayer?>(null) }

    // Auto-hide controls after 4 seconds
    LaunchedEffect(showControls, isPlaying) {
        if (showControls && isPlaying) {
            delay(4000)
            showControls = false
        }
    }

    // Progress updater timer
    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            videoViewRef?.let { vv ->
                if (vv.isPlaying) {
                    currentPositionMs = vv.currentPosition
                    durationMs = max(vv.duration, durationMs)
                }
            }
            delay(500)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            videoViewRef?.stopPlayback()
        }
    }

    // Minimum and maximum size limits in dp
    val minWidthDp = 180f
    val minHeightDp = 110f
    val maxWidthDp = 420f
    val maxHeightDp = 600f

    // Root draggable container
    Box(
        modifier = modifier
            .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
            .size(windowWidthDp.dp, windowHeightDp.dp)
            .shadow(16.dp, RoundedCornerShape(16.dp))
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF0F172A))
            .border(
                1.5.dp,
                if (lockAspectRatio) Color(0xFF60A5FA) else Color(0xFF38BDF8),
                RoundedCornerShape(16.dp)
            )
            .pointerInput(Unit) {
                detectTapGestures(onTap = { showControls = !showControls })
            }
    ) {
        // --- 1. Native Video Playback Surface ---
        AndroidView(
            factory = { ctx ->
                VideoView(ctx).apply {
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    try {
                        if (videoInfo.url.isNotBlank() && (videoInfo.url.startsWith("http://") || videoInfo.url.startsWith("https://"))) {
                            setVideoURI(Uri.parse(videoInfo.url))
                            setOnPreparedListener { mp ->
                                mediaPlayerRef = mp
                                mp.isLooping = true
                                durationMs = max(mp.duration, 1000)
                                if (currentPositionMs > 0) seekTo(currentPositionMs)
                                start()
                                isPlaying = true
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                    videoViewRef = this
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // --- 2. Floating Video Player Controls Overlay ---
        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.65f))
            ) {
                // Top Header (Drag area + Actions)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.4f))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                        // Dragging top bar moves the entire floating window
                        .pointerInput(Unit) {
                            detectDragGestures { change, dragAmount ->
                                change.consume()
                                offsetX += dragAmount.x
                                offsetY += dragAmount.y
                            }
                        },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = videoInfo.title.ifBlank { "网页视频" },
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        // Aspect Ratio Mode Indicator / Lock Toggle
                        IconButton(
                            onClick = { lockAspectRatio = !lockAspectRatio },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = if (lockAspectRatio) Icons.Default.Lock else Icons.Default.LockOpen,
                                contentDescription = if (lockAspectRatio) "已锁定视频比例" else "自由缩放模式",
                                tint = if (lockAspectRatio) Color(0xFF60A5FA) else Color.White.copy(alpha = 0.7f),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Global PiP button
                        IconButton(
                            onClick = onEnterGlobalPiP,
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.PictureInPictureAlt,
                                contentDescription = "全局悬浮",
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        // Fullscreen button
                        IconButton(
                            onClick = onEnterFullscreen,
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Fullscreen,
                                contentDescription = "全屏播放",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        // Close button
                        IconButton(
                            onClick = onClose,
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "关闭悬浮窗",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                // Center Play/Pause & Skip Controls
                Row(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Rewind 10s
                    IconButton(
                        onClick = {
                            videoViewRef?.let { vv ->
                                val target = max(0, vv.currentPosition - 10000)
                                vv.seekTo(target)
                                currentPositionMs = target
                            }
                        },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Replay10,
                            contentDescription = "快退10秒",
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    // Play/Pause
                    Surface(
                        shape = CircleShape,
                        color = Color(0xFF38BDF8),
                        modifier = Modifier.size(44.dp),
                        onClick = {
                            videoViewRef?.let { vv ->
                                if (isPlaying) {
                                    vv.pause()
                                    isPlaying = false
                                } else {
                                    vv.start()
                                    isPlaying = true
                                }
                            }
                        }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (isPlaying) "暂停" else "播放",
                                tint = Color.White,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                    }

                    // Forward 10s
                    IconButton(
                        onClick = {
                            videoViewRef?.let { vv ->
                                val target = (vv.currentPosition + 10000).coerceAtMost(durationMs)
                                vv.seekTo(target)
                                currentPositionMs = target
                            }
                        },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Forward10,
                            contentDescription = "快进10秒",
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }

                // Bottom Progress Bar & Time
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.5f))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = formatTime(currentPositionMs),
                            color = Color.White.copy(alpha = 0.85f),
                            fontSize = 11.sp
                        )
                        // Speed Switcher
                        Text(
                            text = "${playbackSpeed}x",
                            color = Color(0xFF38BDF8),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color.White.copy(alpha = 0.15f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                .pointerInput(Unit) {
                                    detectTapGestures(onTap = {
                                        val speeds = listOf(0.75f, 1.0f, 1.25f, 1.5f, 2.0f)
                                        val nextIndex = (speeds.indexOf(playbackSpeed) + 1) % speeds.size
                                        playbackSpeed = speeds[nextIndex]
                                        try {
                                            mediaPlayerRef?.playbackParams = mediaPlayerRef?.playbackParams?.setSpeed(playbackSpeed) ?: return@detectTapGestures
                                        } catch (e: Exception) {}
                                    })
                                }
                        )
                        Text(
                            text = formatTime(durationMs),
                            color = Color.White.copy(alpha = 0.85f),
                            fontSize = 11.sp
                        )
                    }

                    Slider(
                        value = if (durationMs > 0) currentPositionMs.toFloat() / durationMs.toFloat() else 0f,
                        onValueChange = { frac ->
                            val target = (frac * durationMs).toInt()
                            currentPositionMs = target
                            videoViewRef?.seekTo(target)
                        },
                        colors = SliderDefaults.colors(
                            thumbColor = Color(0xFF38BDF8),
                            activeTrackColor = Color(0xFF38BDF8),
                            inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(20.dp)
                    )
                }
            }
        }

        // --- 3. Arbitrary Resizing Borders & Handles (Top, Bottom, Left, Right & Corners) ---
        // As requested: "可以任意调整悬浮窗口的大小，无论上下左右都可以"

        // TOP EDGE RESIZE HANDLE
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .height(14.dp)
                .pointerInput(lockAspectRatio, baseRatio) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        val deltaDp = with(density) { dragAmount.y.toDp().value }
                        val newHeight = (windowHeightDp - deltaDp).coerceIn(minHeightDp, maxHeightDp)
                        offsetY += with(density) { (windowHeightDp - newHeight).dp.toPx() }
                        windowHeightDp = newHeight
                        if (lockAspectRatio) {
                            windowWidthDp = (newHeight * baseRatio).coerceIn(minWidthDp, maxWidthDp)
                        }
                    }
                }
        )

        // BOTTOM EDGE RESIZE HANDLE
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(14.dp)
                .pointerInput(lockAspectRatio, baseRatio) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        val deltaDp = with(density) { dragAmount.y.toDp().value }
                        val newHeight = (windowHeightDp + deltaDp).coerceIn(minHeightDp, maxHeightDp)
                        windowHeightDp = newHeight
                        if (lockAspectRatio) {
                            windowWidthDp = (newHeight * baseRatio).coerceIn(minWidthDp, maxWidthDp)
                        }
                    }
                }
        )

        // LEFT EDGE RESIZE HANDLE
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .fillMaxHeight()
                .width(14.dp)
                .pointerInput(lockAspectRatio, baseRatio) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        val deltaDp = with(density) { dragAmount.x.toDp().value }
                        val newWidth = (windowWidthDp - deltaDp).coerceIn(minWidthDp, maxWidthDp)
                        offsetX += with(density) { (windowWidthDp - newWidth).dp.toPx() }
                        windowWidthDp = newWidth
                        if (lockAspectRatio) {
                            windowHeightDp = (newWidth / baseRatio).coerceIn(minHeightDp, maxHeightDp)
                        }
                    }
                }
        )

        // RIGHT EDGE RESIZE HANDLE
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .width(14.dp)
                .pointerInput(lockAspectRatio, baseRatio) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        val deltaDp = with(density) { dragAmount.x.toDp().value }
                        val newWidth = (windowWidthDp + deltaDp).coerceIn(minWidthDp, maxWidthDp)
                        windowWidthDp = newWidth
                        if (lockAspectRatio) {
                            windowHeightDp = (newWidth / baseRatio).coerceIn(minHeightDp, maxHeightDp)
                        }
                    }
                }
        )

        // CORNER RESIZE VISUAL ACCENTS (Bottom-Right & Top-Left)
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .size(24.dp)
                .pointerInput(lockAspectRatio, baseRatio) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        val deltaXDp = with(density) { dragAmount.x.toDp().value }
                        val deltaYDp = with(density) { dragAmount.y.toDp().value }
                        val newWidth = (windowWidthDp + deltaXDp).coerceIn(minWidthDp, maxWidthDp)
                        val newHeight = if (lockAspectRatio) {
                            (newWidth / baseRatio).coerceIn(minHeightDp, maxHeightDp)
                        } else {
                            (windowHeightDp + deltaYDp).coerceIn(minHeightDp, maxHeightDp)
                        }
                        windowWidthDp = newWidth
                        windowHeightDp = newHeight
                    }
                }
        ) {
            // Visual drag grip
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(4.dp)
                    .size(8.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color(0xFF38BDF8))
            )
        }
    }
}

private fun formatTime(ms: Int): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%02d:%02d", minutes, seconds)
}
