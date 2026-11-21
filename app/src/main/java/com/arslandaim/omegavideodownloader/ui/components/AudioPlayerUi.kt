/*
 * Omega Video Downloader Project Original (2026)
 * arslandaim-hub (GitHub.com/arslandaim-hub)
 * Licenced Under GPL-3.0+
*/
package com.arslandaim.omegavideodownloader.ui.components

import android.app.Activity
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.media3.common.Player
import com.arslandaim.omegavideodownloader.DownloadedVideo
import com.arslandaim.omegavideodownloader.formatTime
import com.arslandaim.omegavideodownloader.ui.theme.OmegaTheme

@Composable
fun MiniAudioPlayerBar(
    video: DownloadedVideo,
    isPlaying: Boolean,
    onPlayPauseToggle: () -> Unit,
    onClose: () -> Unit,
    onExpand: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val omegaColors = OmegaTheme.colors

    Surface(
        color = omegaColors.cardElevated,
        shape = RoundedCornerShape(20.dp),
        shadowElevation = 8.dp,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .border(0.8.dp, omegaColors.borderBright, RoundedCornerShape(20.dp))
            .clickable {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onExpand()
            }
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Album / Audio Art Disc
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(omegaColors.primary.copy(alpha = 0.15f), CircleShape)
                    .border(1.dp, omegaColors.primary.copy(alpha = 0.4f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                if (isPlaying) {
                    NowPlayingIndicator()
                } else {
                    Icon(
                        imageVector = Icons.Default.MusicNote,
                        contentDescription = null,
                        tint = omegaColors.primary,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = video.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = omegaColors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "Audio Player",
                    style = MaterialTheme.typography.labelSmall,
                    color = omegaColors.primary,
                    fontWeight = FontWeight.SemiBold
                )
            }

            IconButton(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onPlayPauseToggle()
                },
                modifier = Modifier
                    .size(36.dp)
                    .background(omegaColors.primary.copy(alpha = 0.15f), CircleShape)
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    tint = omegaColors.primary,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(4.dp))

            IconButton(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onClose()
                },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close Player",
                    tint = omegaColors.textSecondary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
fun ExpandedAudioPlayerSheet(
    video: DownloadedVideo,
    isPlaying: Boolean,
    currentPosition: Long,
    duration: Long,
    repeatMode: Int = Player.REPEAT_MODE_OFF,
    onPlayPauseToggle: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onSkipPrevious: () -> Unit,
    onSkipNext: () -> Unit,
    onSkipBackward: () -> Unit,
    onSkipForward: () -> Unit,
    onToggleRepeat: () -> Unit,
    onDismiss: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val view = LocalView.current
    val omegaColors = OmegaTheme.colors
    val isDark = omegaColors.isDark

    // Match System Bar Icons to active theme surface
    DisposableEffect(view, isDark) {
        val window = (view.context as? Activity)?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        val previousLightStatus = controller?.isAppearanceLightStatusBars ?: false
        val previousLightNav = controller?.isAppearanceLightNavigationBars ?: false

        controller?.isAppearanceLightStatusBars = !isDark
        controller?.isAppearanceLightNavigationBars = !isDark

        onDispose {
            controller?.isAppearanceLightStatusBars = previousLightStatus
            controller?.isAppearanceLightNavigationBars = previousLightNav
        }
    }

    // Rotation Animation for Vinyl Disc
    val infiniteTransition = rememberInfiniteTransition(label = "vinyl_rotation")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(12000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation_angle"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(brush = omegaColors.backgroundBrush)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .size(40.dp)
                        .background(omegaColors.cardElevated, CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = "Minimize",
                        tint = omegaColors.textPrimary,
                        modifier = Modifier.size(28.dp)
                    )
                }

                Text(
                    text = "NOW PLAYING",
                    style = MaterialTheme.typography.labelLarge,
                    color = omegaColors.textSecondary,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp
                )

                Box(modifier = Modifier.size(40.dp))
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Large Animated Vinyl Album Art
            Box(
                modifier = Modifier
                    .size(260.dp)
                    .clip(CircleShape)
                    .background(
                        brush = Brush.radialGradient(
                            colors = listOf(omegaColors.primary.copy(alpha = 0.28f), omegaColors.backgroundTop)
                        )
                    )
                    .border(2.dp, omegaColors.primary.copy(alpha = 0.55f), CircleShape)
                    .rotate(if (isPlaying) rotation else 0f),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    modifier = Modifier.size(240.dp),
                    shape = CircleShape,
                    color = Color(0xFF11131D)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        // Vinyl Grooves
                        Box(
                            modifier = Modifier
                                .size(180.dp)
                                .border(1.dp, Color.White.copy(alpha = 0.08f), CircleShape)
                        )
                        Box(
                            modifier = Modifier
                                .size(120.dp)
                                .border(1.dp, Color.White.copy(alpha = 0.12f), CircleShape)
                        )

                        // Center Sticker with Duo-Tone Accent
                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .clip(CircleShape)
                                .background(omegaColors.accentBrush)
                                .border(2.dp, Color.White.copy(alpha = 0.35f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.MusicNote,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(36.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Track Details
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = video.title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = omegaColors.textPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    color = omegaColors.primary.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = if (video.isLocked) "Private Locker" else "Audio Library",
                        style = MaterialTheme.typography.labelMedium,
                        color = omegaColors.primary,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Timeline Scrubber & Progress
            Column(modifier = Modifier.fillMaxWidth()) {
                Slider(
                    value = currentPosition.toFloat(),
                    onValueChange = { onSeekTo(it.toLong()) },
                    valueRange = 0f..duration.toFloat().coerceAtLeast(1f),
                    colors = SliderDefaults.colors(
                        thumbColor = omegaColors.primary,
                        activeTrackColor = omegaColors.primary,
                        inactiveTrackColor = omegaColors.borderBright
                    )
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = formatTime(currentPosition),
                        style = MaterialTheme.typography.labelSmall,
                        color = omegaColors.textSecondary
                    )
                    Text(
                        text = formatTime(duration),
                        style = MaterialTheme.typography.labelSmall,
                        color = omegaColors.textSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Playback Controls (5-Button Master Row)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 1. Repeat Button
                IconButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onToggleRepeat()
                    },
                    modifier = Modifier
                        .size(44.dp)
                        .background(
                            if (repeatMode != Player.REPEAT_MODE_OFF) omegaColors.primary.copy(alpha = 0.18f)
                            else omegaColors.cardElevated,
                            CircleShape
                        )
                ) {
                    Icon(
                        imageVector = if (repeatMode == Player.REPEAT_MODE_ONE) Icons.Default.RepeatOne else Icons.Default.Repeat,
                        contentDescription = "Repeat Mode",
                        tint = if (repeatMode != Player.REPEAT_MODE_OFF) omegaColors.primary else omegaColors.textSecondary,
                        modifier = Modifier.size(22.dp)
                    )
                }

                // 2. Previous Track Button
                IconButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onSkipPrevious()
                    },
                    modifier = Modifier
                        .size(48.dp)
                        .background(omegaColors.cardElevated, CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipPrevious,
                        contentDescription = "Previous Track",
                        tint = omegaColors.textPrimary,
                        modifier = Modifier.size(26.dp)
                    )
                }

                // 3. Play / Pause Button (Large Center)
                IconButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onPlayPauseToggle()
                    },
                    modifier = Modifier
                        .size(72.dp)
                        .background(omegaColors.primary, CircleShape)
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        tint = Color.White,
                        modifier = Modifier.size(40.dp)
                    )
                }

                // 4. Next Track Button
                IconButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onSkipNext()
                    },
                    modifier = Modifier
                        .size(48.dp)
                        .background(omegaColors.cardElevated, CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipNext,
                        contentDescription = "Next Track",
                        tint = omegaColors.textPrimary,
                        modifier = Modifier.size(26.dp)
                    )
                }

                // 5. Forward 10s Button
                IconButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onSkipForward()
                    },
                    modifier = Modifier
                        .size(44.dp)
                        .background(omegaColors.cardElevated, CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.Forward10,
                        contentDescription = "Forward 10s",
                        tint = omegaColors.textPrimary,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
