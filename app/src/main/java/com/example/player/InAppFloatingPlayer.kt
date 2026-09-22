package com.example.player

import android.graphics.SurfaceTexture
import android.media.MediaPlayer
import android.media.PlaybackParams
import android.net.Uri
import android.os.Build
import android.view.Surface
import android.view.TextureView
import android.view.ViewGroup
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
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

/**
 * Unified Floating Video Player Component
 * Provides identical UI and behavior for:
 * 1. In-App Floating Window: Draggable, resizable (top/bottom/left/right), rounded borders.
 * 2. Desktop Picture-in-Picture (PiP): Full-bleed layout with identical controls and buttons (Figure 1 UI).
 * 3. Video Engine: Uses TextureView + MediaPlayer with HTTP headers (Referer, User-Agent)
 *    to support HLS/m3u8, mp4, and live streaming smoothly without SurfaceView stuttering.
 * 4. Progress Sync: Resumes from webpage video currentTime and reports back to web video on close.
 */
@Composable
fun InAppFloatingPlayer(
    videoInfo: VideoMediaInfo,
    onClose: (currentPositionSeconds: Double) -> Unit,
    onEnterGlobalPiP: () -> Unit,
    onEnterFullscreen: () -> Unit,
    modifier: Modifier = Modifier,
    isDesktopPiP: Boolean = false,
    currentTabIndex: Int = 0,
    onReturnToOriginTab: ((Int) -> Unit)? = null,
    onDownloadVideo: ((url: String, title: String) -> Unit)? = null
) {
    val density = LocalDensity.current
    val context = LocalContext.current

    // Video aspect ratio calculation
    val baseRatio = remember(videoInfo.videoWidth, videoInfo.videoHeight) {
        if (videoInfo.videoHeight > 0 && videoInfo.videoWidth > 0) {
            (videoInfo.videoWidth.toFloat() / videoInfo.videoHeight.toFloat()).coerceIn(0.5f, 3.0f)
        } else {
            16f / 9f
        }
    }

    val displayMetrics = context.resources.displayMetrics
    val screenWidth = displayMetrics.widthPixels.toFloat()
    val screenHeight = displayMetrics.heightPixels.toFloat()
    val screenWidthDp = with(density) { screenWidth.toDp().value }
    val screenHeightDp = with(density) { screenHeight.toDp().value }

    // Floating window width can NEVER exceed the screen width
    val maxWidthDp = screenWidthDp
    val maxHeightDp = screenHeightDp * 0.85f

    val minWidthDp = 160f.coerceAtMost(screenWidthDp * 0.5f)
    val minHeightDp = 90f.coerceAtMost(screenHeightDp * 0.4f)

    val initialWidthDp = remember(screenWidthDp) {
        (screenWidthDp * 0.75f).coerceIn(minWidthDp, maxWidthDp)
    }
    val initialHeightDp = remember(initialWidthDp, baseRatio) {
        (initialWidthDp / baseRatio).coerceIn(minHeightDp, maxHeightDp)
    }

    // In-app window size states
    var windowWidthDp by remember { mutableFloatStateOf(initialWidthDp) }
    var windowHeightDp by remember { mutableFloatStateOf(initialHeightDp) }

    // Lock aspect ratio during resizing
    var lockAspectRatio by remember { mutableStateOf(false) }

    // Floating window position offset (centered horizontally initially, strictly inside screen)
    var offsetX by remember {
        val initWidthPx = with(density) { initialWidthDp.dp.toPx() }
        mutableFloatStateOf(((screenWidth - initWidthPx) / 2f).coerceAtLeast(0f))
    }
    var offsetY by remember {
        mutableFloatStateOf((120f * density.density).coerceIn(0f, (screenHeight - 200f).coerceAtLeast(0f)))
    }

    // Playback state
    var isPlaying by remember { mutableStateOf(true) }
    var currentPositionMs by remember { mutableIntStateOf((videoInfo.currentTime * 1000).toInt()) }
    var durationMs by remember { mutableIntStateOf((videoInfo.duration * 1000).toInt().coerceAtLeast(1000)) }
    var playbackSpeed by remember { mutableFloatStateOf(1.0f) }
    var showControls by remember { mutableStateOf(true) }
    var isLocked by remember { mutableStateOf(false) }
    var showLockHint by remember { mutableStateOf(false) }

    // Hardware-accelerated MediaPlayer & Texture Surface holder
    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }
    var currentSurface by remember { mutableStateOf<Surface?>(null) }
    var isVideoReady by remember { mutableStateOf(false) }

    // Auto-hide controls after 4 seconds of playback
    LaunchedEffect(showControls, isPlaying, isLocked) {
        if (showControls && isPlaying && !isLocked) {
            delay(4000)
            showControls = false
        }
    }

    // Auto-hide lock icon hint after 3 seconds
    LaunchedEffect(showLockHint) {
        if (showLockHint) {
            delay(3000)
            showLockHint = false
        }
    }

    // High-frequency progress polling timer
    LaunchedEffect(isPlaying, isVideoReady) {
        while (isPlaying) {
            try {
                mediaPlayer?.let { mp ->
                    if (mp.isPlaying) {
                        val pos = mp.currentPosition
                        val dur = mp.duration
                        if (pos >= 0) currentPositionMs = pos
                        if (dur > 0) durationMs = max(dur, durationMs)
                    }
                }
            } catch (e: Exception) {
                // Ignore transient media player calls
            }
            delay(400)
        }
    }

    // Cleanup on dispose
    DisposableEffect(Unit) {
        onDispose {
            try {
                mediaPlayer?.stop()
                mediaPlayer?.reset()
                mediaPlayer?.release()
                currentSurface?.release()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    val windowWidthPx = with(density) { windowWidthDp.dp.toPx() }
    val windowHeightPx = with(density) { windowHeightDp.dp.toPx() }

    // Ensure the floating window stays strictly within screen boundaries at all times
    LaunchedEffect(windowWidthDp, windowHeightDp, screenWidth, screenHeight) {
        val curW = with(density) { windowWidthDp.dp.toPx() }
        val curH = with(density) { windowHeightDp.dp.toPx() }
        val maxOffsetX = (screenWidth - curW).coerceAtLeast(0f)
        val maxOffsetY = (screenHeight - curH).coerceAtLeast(0f)
        offsetX = offsetX.coerceIn(0f, maxOffsetX)
        offsetY = offsetY.coerceIn(0f, maxOffsetY)
    }

    // --- Container Box Modifier ---
    val rootModifier = if (isDesktopPiP) {
        // Desktop Picture-in-Picture: full window bleed
        modifier
            .fillMaxSize()
            .background(Color.Black)
    } else {
        // In-App Floating Window: draggable, rounded corner container
        modifier
            .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
            .size(windowWidthDp.dp, windowHeightDp.dp)
            .shadow(12.dp, RoundedCornerShape(16.dp))
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF0F172A))
            .border(
                1.5.dp,
                if (lockAspectRatio) Color(0xFF60A5FA) else Color(0xFF38BDF8),
                RoundedCornerShape(16.dp)
            )
    }

    Box(modifier = rootModifier) {
        // --- 1. Native TextureView Video Surface (Zero-Lag Hardware Accelerated) ---
        AndroidView(
            factory = { ctx ->
                TextureView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                        override fun onSurfaceTextureAvailable(st: SurfaceTexture, w: Int, h: Int) {
                            val surface = Surface(st)
                            currentSurface = surface

                            val mp = MediaPlayer().apply {
                                setSurface(surface)
                                isLooping = true
                                setOnErrorListener { _, _, _ ->
                                    true // Graceful error suppression
                                }
                                setOnPreparedListener { player ->
                                    isVideoReady = true
                                    val startPos = (videoInfo.currentTime * 1000).toInt()
                                    if (startPos > 0) {
                                        player.seekTo(startPos)
                                        currentPositionMs = startPos
                                    }
                                    if (player.duration > 0) {
                                        durationMs = max(player.duration, durationMs)
                                    }
                                    try {
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                            player.playbackParams = PlaybackParams().setSpeed(playbackSpeed)
                                        }
                                    } catch (e: Exception) {}
                                    player.start()
                                    isPlaying = true
                                }
                            }
                            mediaPlayer = mp

                            // Prepare playback with HTTP anti-hotlinking headers (Referer & UserAgent)
                            try {
                                val urlStr = videoInfo.url.trim()
                                if (urlStr.isNotBlank() && !urlStr.startsWith("blob:")) {
                                    val headers = mutableMapOf<String, String>()
                                    if (videoInfo.pageUrl.isNotBlank()) {
                                        headers["Referer"] = videoInfo.pageUrl
                                    }
                                    headers["User-Agent"] = "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"

                                    mp.setDataSource(ctx, Uri.parse(urlStr), headers)
                                    mp.prepareAsync()
                                }
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }

                        override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, w: Int, h: Int) {}
                        override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean {
                            try {
                                mediaPlayer?.setSurface(null)
                                currentSurface?.release()
                                currentSurface = null
                            } catch (e: Exception) {}
                            return true
                        }
                        override fun onSurfaceTextureUpdated(st: SurfaceTexture) {}
                    }
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // --- 2. Screen Lock Overlay & Unlock Trigger ---
        if (isLocked) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTapGestures {
                            showLockHint = !showLockHint
                        }
                    }
            ) {
                if (showLockHint) {
                    IconButton(
                        onClick = {
                            isLocked = false
                            showControls = true
                            showLockHint = false
                        },
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .padding(start = 12.dp)
                            .size(44.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "点击解锁",
                            tint = Color(0xFF38BDF8),
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
            }
        }

        // --- 3. Fluid In-App Drag Gesture when Controls are Hidden ---
        if (!isDesktopPiP && !showControls && !isLocked) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            val curW = with(density) { windowWidthDp.dp.toPx() }
                            val curH = with(density) { windowHeightDp.dp.toPx() }
                            val maxOffsetX = (screenWidth - curW).coerceAtLeast(0f)
                            val maxOffsetY = (screenHeight - curH).coerceAtLeast(0f)
                            offsetX = (offsetX + dragAmount.x).coerceIn(0f, maxOffsetX)
                            offsetY = (offsetY + dragAmount.y).coerceIn(0f, maxOffsetY)
                        }
                    }
                    .pointerInput(Unit) {
                        detectTapGestures {
                            showControls = true
                        }
                    }
            )
        }

        // --- 4. Floating Video Player Controls Overlay (Figure 1 UI in both PiP & In-App) ---
        AnimatedVisibility(
            visible = showControls && !isLocked,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            val overlayModifier = if (isDesktopPiP) {
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.65f))
                    .pointerInput(Unit) {
                        detectTapGestures { showControls = false }
                    }
            } else {
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.65f))
                    .pointerInput(Unit) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            val curW = with(density) { windowWidthDp.dp.toPx() }
                            val curH = with(density) { windowHeightDp.dp.toPx() }
                            val maxOffsetX = (screenWidth - curW).coerceAtLeast(0f)
                            val maxOffsetY = (screenHeight - curH).coerceAtLeast(0f)
                            offsetX = (offsetX + dragAmount.x).coerceIn(0f, maxOffsetX)
                            offsetY = (offsetY + dragAmount.y).coerceIn(0f, maxOffsetY)
                        }
                    }
            }

            Box(modifier = overlayModifier) {
                // Top Header (Title + PiP + Fullscreen + Close)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.5f))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = videoInfo.title.ifBlank { "网页视频" },
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f).padding(end = 8.dp)
                    )

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Global PiP button (hidden if already in PiP)
                        if (!isDesktopPiP) {
                            IconButton(
                                onClick = onEnterGlobalPiP,
                                modifier = Modifier.size(34.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PictureInPictureAlt,
                                    contentDescription = "桌面小窗",
                                    tint = Color.White,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                        // Fullscreen button
                        IconButton(
                            onClick = onEnterFullscreen,
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Fullscreen,
                                contentDescription = "全屏播放",
                                tint = Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        // Close button (passes back current progress to sync with webpage video)
                        IconButton(
                            onClick = { onClose(currentPositionMs / 1000.0) },
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "关闭小窗",
                                tint = Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }

                // Left-Middle Lock Button (Figure 2 clean icon only style)
                IconButton(
                    onClick = {
                        isLocked = true
                        showControls = false
                        showLockHint = true
                    },
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .padding(start = 8.dp)
                        .size(if (isDesktopPiP) 36.dp else 44.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.LockOpen,
                        contentDescription = "锁定屏幕",
                        tint = Color.White,
                        modifier = Modifier.size(if (isDesktopPiP) 22.dp else 28.dp)
                    )
                }

                // Right-Middle Download Button (Figure 2 clean icon only style)
                IconButton(
                    onClick = {
                        onDownloadVideo?.invoke(videoInfo.url, videoInfo.title.ifBlank { "网页视频" })
                    },
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 8.dp)
                        .size(if (isDesktopPiP) 36.dp else 44.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowDownward,
                        contentDescription = "下载视频",
                        tint = Color.White,
                        modifier = Modifier.size(if (isDesktopPiP) 22.dp else 28.dp)
                    )
                }

                // Center Rewind 10s / Play-Pause / Forward 10s (Clean icons only)
                Row(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .fillMaxWidth(if (isDesktopPiP) 0.55f else 0.65f),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Rewind 10s
                    IconButton(
                        onClick = {
                            mediaPlayer?.let { mp ->
                                try {
                                    val target = max(0, mp.currentPosition - 10000)
                                    mp.seekTo(target)
                                    currentPositionMs = target
                                } catch (e: Exception) {}
                            }
                        },
                        modifier = Modifier.size(if (isDesktopPiP) 32.dp else 42.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Replay10,
                            contentDescription = "快退10秒",
                            tint = Color.White,
                            modifier = Modifier.size(if (isDesktopPiP) 20.dp else 26.dp)
                        )
                    }

                    // Play / Pause Button (Clean icon only - Figure 2 style)
                    IconButton(
                        onClick = {
                            mediaPlayer?.let { mp ->
                                try {
                                    if (isPlaying) {
                                        mp.pause()
                                        isPlaying = false
                                    } else {
                                        mp.start()
                                        isPlaying = true
                                    }
                                } catch (e: Exception) {}
                            }
                        },
                        modifier = Modifier.size(if (isDesktopPiP) 38.dp else 48.dp)
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isPlaying) "暂停" else "播放",
                            tint = Color.White,
                            modifier = Modifier.size(if (isDesktopPiP) 28.dp else 36.dp)
                        )
                    }

                    // Forward 10s
                    IconButton(
                        onClick = {
                            mediaPlayer?.let { mp ->
                                try {
                                    val target = (mp.currentPosition + 10000).coerceAtMost(durationMs)
                                    mp.seekTo(target)
                                    currentPositionMs = target
                                } catch (e: Exception) {}
                            }
                        },
                        modifier = Modifier.size(if (isDesktopPiP) 32.dp else 42.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Forward10,
                            contentDescription = "快进10秒",
                            tint = Color.White,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }

                // Return to Origin Tab Button (if watching from another tab)
                if (!isDesktopPiP && videoInfo.originTabIndex != null && videoInfo.originTabIndex != currentTabIndex && onReturnToOriginTab != null) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = Color(0xFF2563EB).copy(alpha = 0.95f),
                        shadowElevation = 4.dp,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 54.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .clickable { onReturnToOriginTab(videoInfo.originTabIndex) }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "返回视频标签",
                                tint = Color.White,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "返回原标签页",
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // Bottom Progress Bar, Speed Pill & Time Labels
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
                            color = Color.White.copy(alpha = 0.9f),
                            fontSize = 11.sp
                        )
                        // Speed Switcher Pill Button
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
                                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                                mediaPlayer?.playbackParams = PlaybackParams().setSpeed(playbackSpeed)
                                            }
                                        } catch (e: Exception) {}
                                    })
                                }
                        )
                        Text(
                            text = formatTime(durationMs),
                            color = Color.White.copy(alpha = 0.9f),
                            fontSize = 11.sp
                        )
                    }

                    Slider(
                        value = if (durationMs > 0) currentPositionMs.toFloat() / durationMs.toFloat() else 0f,
                        onValueChange = { frac ->
                            val target = (frac * durationMs).toInt()
                            currentPositionMs = target
                            try {
                                mediaPlayer?.seekTo(target)
                            } catch (e: Exception) {}
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

        // --- 5. Arbitrary Resizing Handles (In-App Only: Top, Bottom, Left, Right & Corners) ---
        // Resizing cannot exceed screen width or move/expand outside phone screen
        if (!isDesktopPiP && !isLocked) {
            // TOP EDGE RESIZE
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .height(16.dp)
                    .pointerInput(lockAspectRatio, baseRatio) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            val deltaDp = with(density) { dragAmount.y.toDp().value }
                            val maxAllowedHeightDp = windowHeightDp + with(density) { offsetY.toDp().value }
                            val maxH = minOf(maxHeightDp, maxAllowedHeightDp)
                            val newHeight = (windowHeightDp - deltaDp).coerceIn(minHeightDp, maxH)
                            val heightDiffPx = with(density) { (newHeight - windowHeightDp).dp.toPx() }
                            offsetY = (offsetY - heightDiffPx).coerceAtLeast(0f)
                            windowHeightDp = newHeight
                            if (lockAspectRatio) {
                                val maxW = minOf(maxWidthDp, with(density) { (screenWidth - offsetX).toDp().value })
                                val newWidth = (newHeight * baseRatio).coerceIn(minWidthDp, maxW)
                                val newWidthPx = with(density) { newWidth.dp.toPx() }
                                offsetX = offsetX.coerceIn(0f, (screenWidth - newWidthPx).coerceAtLeast(0f))
                                windowWidthDp = newWidth
                            }
                        }
                    }
            )

            // BOTTOM EDGE RESIZE
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(16.dp)
                    .pointerInput(lockAspectRatio, baseRatio) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            val deltaDp = with(density) { dragAmount.y.toDp().value }
                            val maxAllowedHeightDp = with(density) { (screenHeight - offsetY).toDp().value }
                            val maxH = minOf(maxHeightDp, maxAllowedHeightDp)
                            val newHeight = (windowHeightDp + deltaDp).coerceIn(minHeightDp, maxH)
                            windowHeightDp = newHeight
                            if (lockAspectRatio) {
                                val maxW = minOf(maxWidthDp, with(density) { (screenWidth - offsetX).toDp().value })
                                val newWidth = (newHeight * baseRatio).coerceIn(minWidthDp, maxW)
                                val newWidthPx = with(density) { newWidth.dp.toPx() }
                                offsetX = offsetX.coerceIn(0f, (screenWidth - newWidthPx).coerceAtLeast(0f))
                                windowWidthDp = newWidth
                            }
                        }
                    }
            )

            // LEFT EDGE RESIZE
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .fillMaxHeight()
                    .width(16.dp)
                    .pointerInput(lockAspectRatio, baseRatio) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            val deltaDp = with(density) { dragAmount.x.toDp().value }
                            val maxAllowedWidthDp = windowWidthDp + with(density) { offsetX.toDp().value }
                            val maxW = minOf(maxWidthDp, maxAllowedWidthDp)
                            val newWidth = (windowWidthDp - deltaDp).coerceIn(minWidthDp, maxW)
                            val widthDiffPx = with(density) { (newWidth - windowWidthDp).dp.toPx() }
                            offsetX = (offsetX - widthDiffPx).coerceAtLeast(0f)
                            windowWidthDp = newWidth
                            if (lockAspectRatio) {
                                val maxH = minOf(maxHeightDp, with(density) { (screenHeight - offsetY).toDp().value })
                                val newHeight = (newWidth / baseRatio).coerceIn(minHeightDp, maxH)
                                val newHeightPx = with(density) { newHeight.dp.toPx() }
                                offsetY = offsetY.coerceIn(0f, (screenHeight - newHeightPx).coerceAtLeast(0f))
                                windowHeightDp = newHeight
                            }
                        }
                    }
            )

            // RIGHT EDGE RESIZE
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
                    .width(16.dp)
                    .pointerInput(lockAspectRatio, baseRatio) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            val deltaDp = with(density) { dragAmount.x.toDp().value }
                            val maxAllowedWidthDp = with(density) { (screenWidth - offsetX).toDp().value }
                            val maxW = minOf(maxWidthDp, maxAllowedWidthDp)
                            val newWidth = (windowWidthDp + deltaDp).coerceIn(minWidthDp, maxW)
                            windowWidthDp = newWidth
                            if (lockAspectRatio) {
                                val maxH = minOf(maxHeightDp, with(density) { (screenHeight - offsetY).toDp().value })
                                val newHeight = (newWidth / baseRatio).coerceIn(minHeightDp, maxH)
                                val newHeightPx = with(density) { newHeight.dp.toPx() }
                                offsetY = offsetY.coerceIn(0f, (screenHeight - newHeightPx).coerceAtLeast(0f))
                                windowHeightDp = newHeight
                            }
                        }
                    }
            )

            // CORNER RESIZE VISUAL ACCENTS (Bottom-Right Corner Handle)
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(32.dp)
                    .pointerInput(lockAspectRatio, baseRatio) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            val deltaXDp = with(density) { dragAmount.x.toDp().value }
                            val deltaYDp = with(density) { dragAmount.y.toDp().value }
                            val maxAllowedWidthDp = with(density) { (screenWidth - offsetX).toDp().value }
                            val maxAllowedHeightDp = with(density) { (screenHeight - offsetY).toDp().value }
                            val maxW = minOf(maxWidthDp, maxAllowedWidthDp)
                            val maxH = minOf(maxHeightDp, maxAllowedHeightDp)

                            var targetWidth = (windowWidthDp + deltaXDp).coerceIn(minWidthDp, maxW)
                            var targetHeight = (windowHeightDp + deltaYDp).coerceIn(minHeightDp, maxH)

                            if (lockAspectRatio) {
                                val hFromW = targetWidth / baseRatio
                                if (hFromW <= maxH && hFromW >= minHeightDp) {
                                    targetHeight = hFromW
                                } else {
                                    targetHeight = targetHeight.coerceIn(minHeightDp, maxH)
                                    targetWidth = (targetHeight * baseRatio).coerceIn(minWidthDp, maxW)
                                }
                            }
                            windowWidthDp = targetWidth
                            windowHeightDp = targetHeight
                        }
                    }
            ) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(5.dp)
                        .size(10.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(Color(0xFF38BDF8))
                )
            }
        }
    }
}

private fun formatTime(ms: Int): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%02d:%02d", minutes, seconds)
}
