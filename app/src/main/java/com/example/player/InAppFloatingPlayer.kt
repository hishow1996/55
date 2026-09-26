package com.example.player

import android.graphics.SurfaceTexture
import android.net.Uri
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

    LaunchedEffect(videoInfo.url, videoInfo.pageUrl, videoInfo.originTabIndex) {
        VideoPlaybackSessionManager.start(videoInfo)
    }

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
    var isPlaying by remember { mutableStateOf(true) }
    var currentPositionMs by remember { mutableIntStateOf((videoInfo.currentTime * 1000).toInt()) }
    var durationMs by remember { mutableIntStateOf((videoInfo.duration * 1000).toInt().coerceAtLeast(1000)) }
    var playbackSpeed by remember { mutableFloatStateOf(VideoPlaybackSessionManager.current()?.playbackRate ?: 1.0f) }
    var showControls by remember { mutableStateOf(true) }
    var isLocked by remember { mutableStateOf(false) }
    var showLockHint by remember { mutableStateOf(false) }

    // The player is application-wide. This UI only attaches a surface and
    // controls the already-running native Media3 instance.
    val mediaPlayer = NativeVideoPlaybackManager.player()
    var currentSurface by remember { mutableStateOf<Surface?>(null) }
    var isVideoReady by remember { mutableStateOf(mediaPlayer != null) }

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

    // UI state is read from the single native player. WebView timeupdate
    // is no longer the playback clock after native takeover.
    LaunchedEffect(Unit) {
        while (true) {
            try {
                val pos = NativeVideoPlaybackManager.currentPositionMs()
                val dur = NativeVideoPlaybackManager.durationMs()
                currentPositionMs = pos.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
                if (dur > 0L) durationMs = dur.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
                isPlaying = NativeVideoPlaybackManager.isPlaying()
                VideoPlaybackSessionManager.updatePosition(pos)
            } catch (_: Exception) {}
            delay(250)
        }
    }
