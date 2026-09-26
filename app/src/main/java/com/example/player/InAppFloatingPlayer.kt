package com.example.player

import android.graphics.SurfaceTexture
import android.view.Surface
import android.view.TextureView
import android.view.ViewGroup
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import android.widget.Toast
import com.example.R
import com.example.model.VideoMediaInfo
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/**
 * Unified Floating Video Player Component
 * Provides identical UI and behavior for:
 * 1. In-App Floating Window: Draggable, resizable (top/bottom/left/right), rounded borders.
 * 2. Desktop Picture-in-Picture (PiP): Full-bleed layout with identical controls and buttons (Figure 1 UI).
 * 3. Video Engine: Uses the single application-wide Media3/ExoPlayer instance
 *    with the webpage Referer/Cookie/UA request context.
 * 4. Progress Sync: Reads and writes the shared native playback session.
 */
@Composable
fun InAppFloatingPlayer(
    videoInfo: VideoMediaInfo,
    onClose: (currentPositionSeconds: Double) -> Unit,
    onEnterGlobalPiP: () -> Unit,
    onEnterFullscreen: () -> Unit,
    modifier: Modifier = Modifier,
    isDesktopPiP: Boolean = false,
    isFullscreen: Boolean = false,
    isGlobalFloating: Boolean = false,
    onGlobalDrag: ((Float, Float) -> Unit)? = null,
    onGlobalResize: ((Float, Float) -> Unit)? = null,
    currentTabIndex: Int = 0,
    onReturnToOriginTab: ((Int) -> Unit)? = null,
    onDownloadVideo: ((url: String, title: String) -> Unit)? = null
) {
    val density = LocalDensity.current
    val context = LocalContext.current

    // Video aspect ratio calculation
    // Prefer the dimensions reported by the actual native decoder. WebView
    // dimensions are only the initial hint; the decoded stream can legitimately
    // use a different display aspect ratio (for example a 4:3 picture inside a
    // 16:9 transport). The floating surface follows the native decoder.
    var nativeVideoWidth by remember(videoInfo.videoWidth) {
        mutableIntStateOf(videoInfo.videoWidth)
    }
    var nativeVideoHeight by remember(videoInfo.videoHeight) {
        mutableIntStateOf(videoInfo.videoHeight)
    }
    var nativePixelAspectRatio by remember { mutableFloatStateOf(1f) }

    // The decoded frame dimensions plus pixel aspect ratio are the authoritative
    // display geometry. This keeps a 4:3 picture at 4:3 even when the transport
    // or WebView container was 16:9.
    val baseRatio = remember(
        nativeVideoWidth,
        nativeVideoHeight,
        nativePixelAspectRatio
    ) {
        if (nativeVideoWidth > 0 && nativeVideoHeight > 0) {
            (nativeVideoWidth.toFloat() * nativePixelAspectRatio / nativeVideoHeight.toFloat())
                .coerceIn(0.42f, 2.38f)
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

    val initialWidthDp = remember(screenWidthDp, baseRatio, maxHeightDp) {
        val ratioLimitedMaxWidth = minOf(maxWidthDp, maxHeightDp * baseRatio)
        (screenWidthDp * 0.75f).coerceIn(minWidthDp, ratioLimitedMaxWidth.coerceAtLeast(minWidthDp))
    }
    val initialHeightDp = remember(initialWidthDp, baseRatio) {
        (initialWidthDp / baseRatio).coerceIn(minHeightDp, maxHeightDp)
    }

    // In-app window size states
    var windowWidthDp by remember { mutableFloatStateOf(initialWidthDp) }
    var windowHeightDp by remember { mutableFloatStateOf(initialHeightDp) }

    // Lock aspect ratio during resizing
    var lockAspectRatio by remember { mutableStateOf(true) }

    LaunchedEffect(baseRatio, isDesktopPiP, isFullscreen) {
        if (!isDesktopPiP && !isFullscreen && baseRatio > 0f) {
            val targetHeight = (windowWidthDp / baseRatio).coerceIn(minHeightDp, maxHeightDp)
            val targetWidth = (targetHeight * baseRatio).coerceIn(minWidthDp, maxWidthDp)
            windowWidthDp = targetWidth
            windowHeightDp = targetHeight
        }
    }

    // Resizing visual feedback states
    var isActivelyResizing by remember { mutableStateOf(false) }
    var cornerDragDistance by remember { mutableFloatStateOf(0f) }

    // Floating window position offset (centered horizontally initially, strictly inside screen)
    var offsetX by remember {
        val initWidthPx = with(density) { initialWidthDp.dp.toPx() }
        mutableFloatStateOf(((screenWidth - initWidthPx) / 2f).coerceAtLeast(0f))
    }
    var offsetY by remember {
        mutableFloatStateOf((120f * density.density).coerceIn(0f, (screenHeight - 200f).coerceAtLeast(0f)))
    }

    // Playback state
    var isPlaying by remember { mutableStateOf(runCatching { NativeVideoPlaybackManager.isPlaying() }.getOrDefault(videoInfo.isPlaying)) }
    var currentPositionMs by remember {
        mutableIntStateOf(
            runCatching { NativeVideoPlaybackManager.currentPositionMs() }
                .getOrDefault((videoInfo.currentTime * 1000).toLong())
                .coerceAtMost(Int.MAX_VALUE.toLong())
                .toInt()
        )
    }
    var durationMs by remember {
        mutableIntStateOf(
            runCatching { NativeVideoPlaybackManager.durationMs() }
                .getOrDefault(0L)
                .takeIf { it > 0L }
                ?.coerceAtMost(Int.MAX_VALUE.toLong())
                ?.toInt()
                ?: (videoInfo.duration * 1000)
                    .toLong()
                    .coerceAtLeast(0L)
                    .coerceAtMost(Int.MAX_VALUE.toLong())
                    .toInt()
        )
    }
    var bufferedPositionMs by remember { mutableIntStateOf(0) }
    var isBuffering by remember { mutableStateOf(false) }
    var playbackSpeed by remember { mutableFloatStateOf(VideoPlaybackSessionManager.current()?.playbackRate ?: 1.0f) }
    var showControls by remember { mutableStateOf(true) }
    var fullscreenLocked by remember(isFullscreen) { mutableStateOf(false) }
    // The player is application-wide. This UI only attaches a surface and
    // controls the already-running native Media3 instance.
    var currentSurface by remember { mutableStateOf<Surface?>(null) }
    var isVideoReady by remember { mutableStateOf(NativeVideoPlaybackManager.player() != null) }

    // Auto-hide controls after 4 seconds of playback
    LaunchedEffect(showControls, isPlaying) {
        if (showControls && isPlaying) {
            delay(4000)
            showControls = false
        }
    }

    // UI state is read from the single native player. WebView timeupdate
    // is no longer the playback clock after native takeover.
    LaunchedEffect(Unit) {
        while (true) {
            try {
                val pos = NativeVideoPlaybackManager.currentPositionMs()
                val dur = NativeVideoPlaybackManager.durationMs()
                val buffered = NativeVideoPlaybackManager.bufferedPositionMs()
                val nativePlayer = NativeVideoPlaybackManager.player()
                val decodedSize = nativePlayer?.videoSize
                if (decodedSize != null && decodedSize.width > 0 && decodedSize.height > 0) {
                    nativeVideoWidth = decodedSize.width
                    nativeVideoHeight = decodedSize.height
                    nativePixelAspectRatio = decodedSize.pixelWidthHeightRatio
                        .takeIf { it.isFinite() && it > 0f }
                        ?: 1f
                }
                currentPositionMs = pos.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
                if (dur > 0L) durationMs = dur.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
                bufferedPositionMs = buffered.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
                isPlaying = NativeVideoPlaybackManager.isPlaying()
                isBuffering = NativeVideoPlaybackManager.playbackState() == androidx.media3.common.Player.STATE_BUFFERING
                VideoPlaybackSessionManager.updatePosition(pos)
            } catch (_: Exception) {}
            delay(250)
        }
    }

    // Closing/recomposing this UI only detaches the surface.
    DisposableEffect(Unit) {
        onDispose {
            try {
                NativeVideoPlaybackManager.detachSurface()
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
    val rootModifier = when {
        isGlobalFloating || isDesktopPiP || isFullscreen -> {
            // Fullscreen native player: the same Media3 surface expands to the
            // entire activity without creating a second player or reloading video.
            modifier
                .fillMaxSize()
                .zIndex(100f)
                .background(Color.Black)
        }
        else -> {
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
    }

    Box(modifier = rootModifier) {
        // --- 1. Native TextureView Video Surface (Zero-Lag Hardware Accelerated) ---
        AndroidView(
            factory = { ctx ->
                TextureView(ctx).apply {
                    keepScreenOn = true
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                        override fun onSurfaceTextureAvailable(st: SurfaceTexture, w: Int, h: Int) {
                            val surface = Surface(st)
                            currentSurface = surface

                            // The native session is created before this UI appears.
                            // Never create a second ExoPlayer here.
                            NativeVideoPlaybackManager.attachSurface(surface)
                            val session = VideoPlaybackSessionManager.current()
                            isPlaying = NativeVideoPlaybackManager.isPlaying()
                            playbackSpeed = session?.playbackRate ?: playbackSpeed
                            currentPositionMs = NativeVideoPlaybackManager.currentPositionMs()
                                .coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
                            durationMs = NativeVideoPlaybackManager.durationMs()
                                .coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
                                .coerceAtLeast(durationMs)
                            bufferedPositionMs = NativeVideoPlaybackManager.bufferedPositionMs()
                                .coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
                            isBuffering = NativeVideoPlaybackManager.playbackState() == androidx.media3.common.Player.STATE_BUFFERING
                            isVideoReady = NativeVideoPlaybackManager.player() != null
                        }

                        override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, w: Int, h: Int) {}
                        override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean {
                            try {
                                // Surface destruction is a rendering lifecycle event,
                                // not a seek event. The native player clock is already
                                // authoritative, so do not issue a redundant seek here.
                                NativeVideoPlaybackManager.detachSurface()
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

        // --- 3. Fluid In-App Drag Gesture when Controls are Hidden ---
        if (!isDesktopPiP && !showControls) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTapGestures {
                            showControls = true
                        }
                    }
            )
        }

        // --- 4. Floating Video Player Controls Overlay (Figure 1 UI in both PiP & In-App) ---
        AnimatedVisibility(
            visible = showControls,
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
                            if (isGlobalFloating) {
                                onGlobalDrag?.invoke(dragAmount.x, dragAmount.y)
                            } else {
                                offsetX = (offsetX + dragAmount.x).coerceIn(0f, maxOffsetX)
                                offsetY = (offsetY + dragAmount.y).coerceIn(0f, maxOffsetY)
                            }
                        }
                    }
            }

            Box(modifier = overlayModifier) {
                // Dedicated fullscreen header: back on the left, download/close on the right.
                if (isFullscreen) {
                    Row(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        IconButton(
                            onClick = { onEnterFullscreen() },
                            modifier = Modifier.size(42.dp).background(Color.Black.copy(alpha = 0.42f), CircleShape)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "退出全屏", tint = Color.White, modifier = Modifier.size(25.dp))
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (onDownloadVideo != null) {
                                IconButton(
                                    onClick = { onDownloadVideo(videoInfo.url, videoInfo.title.ifBlank { "网页视频" }) },
                                    modifier = Modifier.size(42.dp)
                                ) {
                                    Icon(Icons.Default.ArrowDownward, "下载当前视频", tint = Color.White, modifier = Modifier.size(24.dp))
                                }
                            }
                            IconButton(
                                onClick = {
                                    val position = VideoPlaybackSessionManager.positionMsOr(currentPositionMs.toLong()) / 1000.0
                                    onClose(position)
                                },
                                modifier = Modifier.size(42.dp)
                            ) {
                                Icon(Icons.Default.Close, "关闭播放器", tint = Color.White, modifier = Modifier.size(25.dp))
                            }
                        }
                    }
                }

                // Normal player/floating header controls
                if (!isFullscreen) Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.5f))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.End
                ) {
                    if (!isDesktopPiP && onDownloadVideo != null) {
                        IconButton(
                            onClick = { onDownloadVideo(videoInfo.url, videoInfo.title.ifBlank { "网页视频" }) },
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ArrowDownward,
                                contentDescription = "下载当前视频",
                                tint = Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                    if (!isDesktopPiP && !isGlobalFloating && !isFullscreen) {
                        IconButton(
                            onClick = { onEnterGlobalPiP() },
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.PictureInPictureAlt,
                                contentDescription = "全局悬浮播放",
                                tint = Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                    if (!isDesktopPiP && !isGlobalFloating) {
                        IconButton(
                            onClick = { onEnterFullscreen() },
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(
                                imageVector = if (isFullscreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                                contentDescription = if (isFullscreen) "退出全屏" else "全屏",
                                tint = Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                    // Close button (passes back current progress to sync with webpage video)
                    IconButton(
                        onClick = {
                            val position = VideoPlaybackSessionManager.positionMsOr(currentPositionMs.toLong()) / 1000.0
                            onClose(position)
                        },
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

                // Center Rewind 10s / Play-Pause / Forward 10s (Clean icons only)
                Row(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .fillMaxWidth(if (isDesktopPiP) 0.55f else if (isFullscreen) 0.42f else 0.65f),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Rewind 10s
                    IconButton(
                        onClick = {
                            try {
                                val current = NativeVideoPlaybackManager.currentPositionMs().coerceAtLeast(0L)
                                val target = (current - 10_000L).coerceAtLeast(0L)
                                NativeVideoPlaybackManager.seekTo(target)
                                VideoPlaybackSessionManager.updatePosition(target)
                                currentPositionMs = target.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
                            } catch (_: Exception) {}
                        },
                        modifier = Modifier.size(if (isDesktopPiP) 34.dp else if (isFullscreen) 56.dp else 46.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Replay10,
                            contentDescription = "快退10秒",
                            tint = Color.White,
                            modifier = Modifier.size(if (isDesktopPiP) 22.dp else if (isFullscreen) 38.dp else 30.dp)
                        )
                    }

                    // Play / Pause Button (Clean icon only - Figure 2 style)
                    IconButton(
                        onClick = {
                            try {
                                if (isPlaying) NativeVideoPlaybackManager.pause()
                                else NativeVideoPlaybackManager.play()
                                isPlaying = NativeVideoPlaybackManager.isPlaying()
                            } catch (_: Exception) {}
                        },
                        modifier = Modifier.size(if (isDesktopPiP) 40.dp else if (isFullscreen) 64.dp else 52.dp)
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isPlaying) "暂停" else "播放",
                            tint = Color.White,
                            modifier = Modifier.size(if (isDesktopPiP) 30.dp else if (isFullscreen) 48.dp else 40.dp)
                        )
                    }

                    // Forward 10s
                    IconButton(
                        onClick = {
                            try {
                                val current = NativeVideoPlaybackManager.currentPositionMs().coerceAtLeast(0L)
                                val nativeDuration = NativeVideoPlaybackManager.durationMs()
                                val effectiveDuration = nativeDuration.takeIf { it > 0L }
                                    ?: durationMs.toLong().takeIf { it > 0L }
                                    ?: Long.MAX_VALUE
                                val target = (current + 10_000L).coerceAtMost(effectiveDuration)
                                NativeVideoPlaybackManager.seekTo(target)
                                VideoPlaybackSessionManager.updatePosition(target)
                                currentPositionMs = target.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
                            } catch (_: Exception) {}
                        },
                        modifier = Modifier.size(if (isDesktopPiP) 32.dp else if (isFullscreen) 56.dp else 42.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Forward10,
                            contentDescription = "快进10秒",
                            tint = Color.White,
                            modifier = Modifier.size(if (isFullscreen) 38.dp else 26.dp)
                        )
                    }
                }

                if (isFullscreen) {
                    val progress = if (durationMs > 0) (currentPositionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f) else 0f
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp)
                    ) {
                        Text(
                            text = formatTime(currentPositionMs) + " / " + formatTime(durationMs),
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(3.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(20.dp)
                                .pointerInput(durationMs) {
                                    detectTapGestures { offset ->
                                        val width = size.width.toFloat().coerceAtLeast(1f)
                                        val target = (durationMs * (offset.x / width).coerceIn(0f, 1f)).toLong()
                                        NativeVideoPlaybackManager.seekTo(target)
                                        val actual = NativeVideoPlaybackManager.currentPositionMs()
                                        currentPositionMs = actual.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
                                        VideoPlaybackSessionManager.updatePosition(actual)
                                    }
                                }
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(3.dp)
                                    .align(Alignment.CenterStart)
                                    .background(Color.White.copy(alpha = 0.28f), RoundedCornerShape(2.dp))
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(progress)
                                    .height(3.dp)
                                    .align(Alignment.CenterStart)
                                    .background(Color.White, RoundedCornerShape(2.dp))
                            )
                        }
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

            }
        }

        // Lock control is intentionally available only in native fullscreen.
        // Non-fullscreen player and floating window must never show a lock button.
        if (isFullscreen) {
            IconButton(
                onClick = { fullscreenLocked = !fullscreenLocked },
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 8.dp)
                    .size(42.dp)
                    .background(Color.Black.copy(alpha = 0.48f), CircleShape)
            ) {
                Icon(
                    imageVector = if (fullscreenLocked) Icons.Default.LockOpen else Icons.Default.Lock,
                    contentDescription = if (fullscreenLocked) "解锁全屏播放器" else "锁定全屏播放器",
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
            }
        }

        // The thin live red progress line is exclusive to the Android global
        // floating-window presentation. System PiP and the browser player do not show it.
        if (isGlobalFloating) {
            val progress = if (durationMs > 0) {
                (currentPositionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
            } else 0f
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 7.dp, bottom = 0.dp)
            ) {
                Text(
                    text = "${formatTime(currentPositionMs)}/${formatTime(durationMs)}",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    modifier = Modifier.padding(start = 1.dp, bottom = 0.dp)
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(18.dp)
                        .pointerInput(durationMs) {
                            detectTapGestures { offset ->
                                val width = size.width.toFloat().coerceAtLeast(1f)
                                val target = (durationMs * (offset.x / width).coerceIn(0f, 1f)).toLong()
                                NativeVideoPlaybackManager.seekTo(target)
                                val actual = NativeVideoPlaybackManager.currentPositionMs()
                                currentPositionMs = actual.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
                                VideoPlaybackSessionManager.updatePosition(actual)
                            }
                        }
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(2.dp)
                            .align(Alignment.BottomCenter)
                            .background(Color.Black.copy(alpha = 0.28f))
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(progress)
                            .height(2.dp)
                            .align(Alignment.BottomStart)
                            .background(Color.Red)
                    )
                }
            }
    }

        // --- 5. Global floating resize handle ---
        // The global window is hosted by WindowManager, so its bottom-right
        // grip delegates size changes back to the service instead of resizing
        // only the Compose content.
        if (isGlobalFloating) {
            Box(
                contentAlignment = Alignment.BottomEnd,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(36.dp)
                    .pointerInput(baseRatio) {
                        detectDragGestures(
                            onDragStart = {
                                cornerDragDistance = 0f
                                isActivelyResizing = true
                            },
                            onDragEnd = {
                                isActivelyResizing = false
                            },
                            onDragCancel = {
                                isActivelyResizing = false
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                cornerDragDistance += dragAmount.x * dragAmount.x + dragAmount.y * dragAmount.y
                                val deltaXDp = with(density) { dragAmount.x.toDp().value }
                                val deltaYDp = with(density) { dragAmount.y.toDp().value }
                                val delta = if (kotlin.math.abs(deltaXDp) >= kotlin.math.abs(deltaYDp)) deltaXDp else deltaYDp
                                val maxAllowedWidthDp = screenWidthDp * 0.95f
                                val targetWidth = (windowWidthDp + delta).coerceIn(minWidthDp, maxAllowedWidthDp)
                                val targetHeight = (targetWidth / baseRatio).coerceIn(minHeightDp, maxHeightDp)
                                val actualWidth = targetHeight * baseRatio
                                windowWidthDp = actualWidth.coerceIn(minWidthDp, maxAllowedWidthDp)
                                windowHeightDp = targetHeight
                                onGlobalResize?.invoke(windowWidthDp, windowHeightDp)
                            }
                        )
                    }
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_resize_corner),
                    contentDescription = "调整悬浮窗口大小",
                    tint = Color.White.copy(alpha = if (isActivelyResizing || showControls) 0.9f else 0.55f),
                    modifier = Modifier
                        .padding(end = 3.dp, bottom = 3.dp)
                        .size(14.dp)
                )
            }
        }

        // --- 5. Arbitrary Resizing Handles (In-App Only: Top, Bottom, Left, Right & Corners) ---
        // Resizing cannot exceed screen width or move/expand outside phone screen
        if (!isGlobalFloating && !isDesktopPiP && !isFullscreen) {
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

            // LIVE RESIZING DIMENSION BADGE (Shown during drag resize)
            if (isActivelyResizing) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xEE0F172A),
                    border = BorderStroke(1.dp, Color(0xFF38BDF8)),
                    shadowElevation = 6.dp,
                    modifier = Modifier
                        .align(Alignment.Center)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_resize_corner),
                            contentDescription = null,
                            tint = Color(0xFF38BDF8),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "${windowWidthDp.roundToInt()} × ${windowHeightDp.roundToInt()} dp",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // UC-STYLE SUBTLE CORNER RESIZE GRIPPER (Bottom-Right Corner)
            Box(
                contentAlignment = Alignment.BottomEnd,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(32.dp)
                    .pointerInput(baseRatio, screenWidth, screenHeight) {
                        detectDragGestures(
                            onDragStart = {
                                cornerDragDistance = 0f
                                isActivelyResizing = true
                            },
                            onDragEnd = {
                                isActivelyResizing = false
                                if (cornerDragDistance < 8f) {
                                    // Tap / Click action: Toggle between comfortable presets
                                    val presets = listOf(
                                        (screenWidthDp * 0.75f).coerceIn(minWidthDp, maxWidthDp),
                                        (screenWidthDp * 0.95f).coerceIn(minWidthDp, maxWidthDp),
                                        (screenWidthDp * 0.55f).coerceIn(minWidthDp, maxWidthDp)
                                    )
                                    val nextW = when {
                                        windowWidthDp < screenWidthDp * 0.65f -> presets[0]
                                        windowWidthDp < screenWidthDp * 0.85f -> presets[1]
                                        else -> presets[2]
                                    }
                                    val effRatio = if (baseRatio >= 0.5f) baseRatio else (16f / 9f)
                                    val nextH = (nextW / effRatio).coerceIn(minHeightDp, maxHeightDp)
                                    val maxAllowedW = (screenWidth - offsetX).coerceAtLeast(minWidthDp)
                                    val maxAllowedH = (screenHeight - offsetY).coerceAtLeast(minHeightDp)
                                    windowWidthDp = minOf(nextW, maxAllowedW)
                                    windowHeightDp = minOf(nextH, maxAllowedH)
                                }
                            },
                            onDragCancel = {
                                isActivelyResizing = false
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                cornerDragDistance += (dragAmount.x * dragAmount.x + dragAmount.y * dragAmount.y)
                                val deltaXDp = with(density) { dragAmount.x.toDp().value }
                                val maxAllowedWidthDp = with(density) { (screenWidth - offsetX).toDp().value }
                                val maxAllowedHeightDp = with(density) { (screenHeight - offsetY).toDp().value }
                                val maxW = minOf(maxWidthDp, maxAllowedWidthDp)
                                val maxH = minOf(maxHeightDp, maxAllowedHeightDp)

                                // UC browser preserves video aspect ratio during corner resizing
                                val effRatio = if (baseRatio >= 0.5f) baseRatio else (16f / 9f)
                                val targetWidth = (windowWidthDp + deltaXDp).coerceIn(minWidthDp, maxW)
                                val targetHeight = (targetWidth / effRatio).coerceIn(minHeightDp, maxH)

                                windowWidthDp = targetWidth
                                windowHeightDp = targetHeight
                            }
                        )
                    }
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_resize_corner),
                    contentDescription = "调整窗口大小",
                    tint = Color.White.copy(alpha = if (isActivelyResizing || showControls) 0.85f else 0.4f),
                    modifier = Modifier
                        .padding(end = 3.dp, bottom = 3.dp)
                        .size(10.dp)
                )
            }
        }
    }
}

private fun formatTime(ms: Int): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format("%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%d:%02d", minutes, seconds)
    }
}
