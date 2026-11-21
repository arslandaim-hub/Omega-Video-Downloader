/*
 * Omega Video Downloader Project Original (2026)
 * arslandaim-hub (GitHub.com/arslandaim-hub)
 * Licenced Under GPL-3.0+
*/
package com.arslandaim.omegavideodownloader.ui.components

import android.view.LayoutInflater
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.ui.PlayerView
import com.arslandaim.omegavideodownloader.ActiveVideoSession
import com.arslandaim.omegavideodownloader.R
import com.arslandaim.omegavideodownloader.formatTime
import com.arslandaim.omegavideodownloader.ui.theme.OmegaTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Docked live video mini player shown when minimizing a movie via Back.
 * Supports tap-to-expand, Play/Pause, live progress, and swipe-down-to-bottom to close.
 */
@OptIn(UnstableApi::class)
@Composable
fun MiniVideoPlayerBar(
    session: ActiveVideoSession,
    mediaController: MediaController?,
    onExpand: () -> Unit,
    onClose: (finalPosMs: Long, durationMs: Long) -> Unit,
    onSaveProgress: (posMs: Long, durationMs: Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val omegaColors = OmegaTheme.colors

    val dismissThresholdPx = with(density) { 56.dp.toPx() }
    val expandThresholdPx = with(density) { -48.dp.toPx() }
    val slideOffPx = with(density) { 220.dp.toPx() }

    val offsetY = remember(session.videoUrl) { Animatable(0f) }
    var isDismissing by remember(session.videoUrl) { mutableStateOf(false) }

    var isPlaying by remember(mediaController) { mutableStateOf(mediaController?.isPlaying == true) }
    var currentPos by remember(mediaController) { mutableLongStateOf(mediaController?.currentPosition?.coerceAtLeast(0L) ?: 0L) }
    var duration by remember(mediaController) { mutableLongStateOf(mediaController?.duration?.coerceAtLeast(0L) ?: 0L) }

    // Listen to live MediaController state while minimized
    DisposableEffect(mediaController, session.videoUrl) {
        val controller = mediaController
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
                if (!playing && controller != null) {
                    onSaveProgress(
                        controller.currentPosition.coerceAtLeast(0L),
                        controller.duration.coerceAtLeast(0L)
                    )
                }
            }

            override fun onPlaybackStateChanged(state: Int) {
                controller?.let { c ->
                    duration = c.duration.coerceAtLeast(0L)
                    if (state == Player.STATE_ENDED) {
                        val dur = c.duration.coerceAtLeast(0L)
                        onSaveProgress(dur, dur)
                    }
                }
            }
        }
        controller?.addListener(listener)
        onDispose {
            controller?.removeListener(listener)
        }
    }

    // Live UI progress tick + periodic Watch History persistence every ~5s
    LaunchedEffect(mediaController, session.videoUrl) {
        var lastSavedPos = -1L
        var ticksSinceSave = 0
        while (mediaController != null) {
            val pos = mediaController.currentPosition.coerceAtLeast(0L)
            val dur = mediaController.duration.coerceAtLeast(0L)
            currentPos = pos
            if (dur > 0L) duration = dur
            isPlaying = mediaController.isPlaying

            ticksSinceSave++
            if (ticksSinceSave >= 10 && kotlin.math.abs(pos - lastSavedPos) >= 2_000L) {
                onSaveProgress(pos, dur)
                lastSavedPos = pos
                ticksSinceSave = 0
            }
            delay(500)
        }
    }

    fun triggerDismiss() {
        if (isDismissing) return
        isDismissing = true
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        scope.launch {
            offsetY.animateTo(slideOffPx, animationSpec = tween(180))
            val pos = mediaController?.currentPosition?.coerceAtLeast(0L) ?: currentPos
            val dur = mediaController?.duration?.coerceAtLeast(0L) ?: duration
            onClose(pos, dur)
        }
    }

    val dragProgress = (offsetY.value / dismissThresholdPx).coerceIn(0f, 1.5f)
    val cardAlpha = (1f - dragProgress * 0.55f).coerceIn(0.2f, 1f)
    val cardScale = (1f - dragProgress * 0.05f).coerceIn(0.92f, 1f)

    Surface(
        color = omegaColors.cardElevated,
        shape = RoundedCornerShape(20.dp),
        shadowElevation = 8.dp,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 6.dp)
            .graphicsLayer {
                translationY = offsetY.value
                alpha = cardAlpha
                scaleX = cardScale
                scaleY = cardScale
            }
            .border(0.8.dp, omegaColors.borderBright, RoundedCornerShape(20.dp))
            .clip(RoundedCornerShape(20.dp))
            .pointerInput(session.videoUrl) {
                var lastDeltaY = 0f
                detectVerticalDragGestures(
                    onDragStart = { lastDeltaY = 0f },
                    onVerticalDrag = { change, dragAmount ->
                        if (isDismissing) return@detectVerticalDragGestures
                        change.consume()
                        lastDeltaY = dragAmount
                        val target = (offsetY.value + dragAmount).coerceAtLeast(-72f)
                        scope.launch { offsetY.snapTo(target) }
                    },
                    onDragCancel = {
                        if (!isDismissing) {
                            scope.launch {
                                offsetY.animateTo(0f, spring(stiffness = Spring.StiffnessMediumLow))
                            }
                        }
                    },
                    onDragEnd = {
                        if (isDismissing) return@detectVerticalDragGestures
                        when {
                            offsetY.value >= dismissThresholdPx || (offsetY.value > 24f && lastDeltaY > 14f) -> {
                                triggerDismiss()
                            }
                            offsetY.value <= expandThresholdPx -> {
                                scope.launch { offsetY.snapTo(0f) }
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onExpand()
                            }
                            else -> {
                                scope.launch {
                                    offsetY.animateTo(
                                        0f,
                                        spring(
                                            dampingRatio = Spring.DampingRatioMediumBouncy,
                                            stiffness = Spring.StiffnessMedium
                                        )
                                    )
                                }
                            }
                        }
                    }
                )
            }
            .clickable(enabled = !isDismissing) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onExpand()
            }
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Subtle drag handle indicating swipe-down-to-close / swipe-up-to-expand
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp, bottom = 2.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .width(34.dp)
                        .height(3.5.dp)
                        .clip(CircleShape)
                        .background(omegaColors.textSecondary.copy(alpha = 0.35f))
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 10.dp, end = 8.dp, top = 2.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Live TextureView-backed PlayerView
                Box(
                    modifier = Modifier
                        .size(width = 110.dp, height = 62.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.Black)
                ) {
                    AndroidView(
                        factory = { ctx ->
                            val parent = FrameLayout(ctx)
                            (LayoutInflater.from(ctx).inflate(R.layout.mini_player_view, parent, false) as PlayerView).apply {
                                player = mediaController
                            }
                        },
                        update = { view ->
                            if (view.player !== mediaController) {
                                view.player = mediaController
                            }
                        },
                        onRelease = { view ->
                            view.player = null
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = session.title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = omegaColors.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    val timeText = if (duration > 0L) {
                        "${formatTime(currentPos)} / ${formatTime(duration)}"
                    } else {
                        "Swipe down to close"
                    }
                    Text(
                        text = timeText,
                        style = MaterialTheme.typography.labelSmall,
                        color = omegaColors.primary,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1
                    )
                    Text(
                        text = "Swipe down to close",
                        fontSize = 10.sp,
                        color = omegaColors.textSecondary,
                        maxLines = 1
                    )
                }

                IconButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        mediaController?.let { c ->
                            if (c.isPlaying) c.pause() else c.play()
                        }
                    },
                    modifier = Modifier
                        .size(38.dp)
                        .background(omegaColors.primary.copy(alpha = 0.14f), CircleShape)
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        tint = omegaColors.primary,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Spacer(modifier = Modifier.width(2.dp))

                IconButton(
                    onClick = { triggerDismiss() },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close Mini Player",
                        tint = omegaColors.textSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // Thin duo-tone playback progress bar along bottom edge
            val progressFraction = if (duration > 0L) {
                (currentPos.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
            } else 0f
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.5.dp)
                    .background(omegaColors.border)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(progressFraction)
                        .background(omegaColors.accentBrush)
                )
            }
        }
    }
}
