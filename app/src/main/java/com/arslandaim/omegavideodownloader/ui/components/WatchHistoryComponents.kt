/*
 * Omega Video Downloader Project Original (2026)
 * arslandaim-hub (GitHub.com/arslandaim-hub)
 * Licenced Under GPL-3.0+
*/
package com.arslandaim.omegavideodownloader.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.request.videoFrameMillis
import com.arslandaim.omegavideodownloader.WatchHistoryItem
import com.arslandaim.omegavideodownloader.formatTime
import com.arslandaim.omegavideodownloader.ui.theme.OmegaTheme

@Composable
fun WatchHistorySection(
    items: List<WatchHistoryItem>,
    imageLoader: ImageLoader,
    resumingHistoryId: String?,
    onItemClick: (WatchHistoryItem) -> Unit,
    onDeleteItem: (WatchHistoryItem) -> Unit,
    onClearAll: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (items.isEmpty()) return

    val omegaColors = OmegaTheme.colors
    var showClearAllDialog by remember { mutableStateOf(false) }

    if (showClearAllDialog) {
        AlertDialog(
            onDismissRequest = { showClearAllDialog = false },
            title = { Text("Clear Watch History?", fontWeight = FontWeight.Bold) },
            text = { Text("Remove all ${items.size} items from your watch history? Your downloaded files will not be deleted.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearAllDialog = false
                        onClearAll()
                    }
                ) {
                    Text("Clear All", color = omegaColors.danger, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearAllDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.History,
                    contentDescription = null,
                    tint = omegaColors.primary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Watch History",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = omegaColors.textPrimary
                )
            }

            if (items.size > 1) {
                TextButton(
                    onClick = { showClearAllDialog = true },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "Clear All",
                        style = MaterialTheme.typography.labelMedium,
                        color = omegaColors.danger,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        items.forEach { item ->
            WatchHistoryCard(
                item = item,
                imageLoader = imageLoader,
                isResuming = resumingHistoryId == item.id,
                onClick = { onItemClick(item) },
                onDelete = { onDeleteItem(item) }
            )
        }
    }
}

@Composable
fun WatchHistoryCard(
    item: WatchHistoryItem,
    imageLoader: ImageLoader,
    isResuming: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val omegaColors = OmegaTheme.colors
    val accentColor = if (item.isStream) omegaColors.primary else omegaColors.success

    Surface(
        color = omegaColors.card,
        shape = RoundedCornerShape(20.dp),
        modifier = modifier
            .fillMaxWidth()
            .border(0.8.dp, omegaColors.border, RoundedCornerShape(20.dp))
            .clip(RoundedCornerShape(20.dp))
            .clickable(enabled = !isResuming) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Thumbnail with overlaid resume progress bar
            Box(
                modifier = Modifier
                    .size(width = 112.dp, height = 66.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(
                                accentColor.copy(alpha = 0.22f),
                                omegaColors.cardElevated
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Movie,
                    contentDescription = null,
                    tint = accentColor.copy(alpha = 0.6f),
                    modifier = Modifier.size(26.dp)
                )

                val thumbModel = when {
                    !item.thumbnailUrl.isNullOrBlank() -> item.thumbnailUrl
                    !item.isStream -> item.sourceUrl
                    else -> null
                }

                if (thumbModel != null) {
                    val request = remember(thumbModel, item.isStream) {
                        ImageRequest.Builder(context)
                            .data(thumbModel)
                            .apply {
                                if (!item.isStream) {
                                    videoFrameMillis(item.positionMs.coerceAtLeast(1000L))
                                }
                            }
                            .crossfade(true)
                            .build()
                    }
                    AsyncImage(
                        model = request,
                        imageLoader = imageLoader,
                        contentDescription = item.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                // Play / Resuming center badge
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.58f)),
                    contentAlignment = Alignment.Center
                ) {
                    if (isResuming) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = Color.White
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Resume",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                // Bottom progress bar overlay
                if (item.durationMs > 0L) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .fillMaxWidth()
                            .height(3.5.dp)
                            .background(Color.Black.copy(alpha = 0.55f))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(item.progressFraction.coerceIn(0.02f, 1f))
                                .background(accentColor)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Title + resume metadata
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = omegaColors.textPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(5.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Surface(
                        color = accentColor.copy(alpha = 0.14f),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = if (item.isStream) "Stream" else "Saved",
                            color = accentColor,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }

                    val statusText = when {
                        isResuming -> "Resuming..."
                        item.isCompleted -> "Watched • Replay"
                        item.positionMs >= 3_000L && item.durationMs > 0L ->
                            "Resume ${formatTime(item.positionMs)} / ${formatTime(item.durationMs)}"
                        item.positionMs >= 3_000L ->
                            "Resume ${formatTime(item.positionMs)}"
                        item.durationMs > 0L -> formatTime(item.durationMs)
                        else -> "Recently watched"
                    }

                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.labelSmall,
                        color = omegaColors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = formatRelativeTime(item.lastWatchedAt),
                    fontSize = 11.sp,
                    color = omegaColors.textSecondary.copy(alpha = 0.75f),
                    maxLines = 1
                )
            }

            Spacer(modifier = Modifier.width(4.dp))

            // Per-card Delete Button
            IconButton(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onDelete()
                },
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(omegaColors.danger.copy(alpha = 0.1f))
            ) {
                Icon(
                    imageVector = Icons.Default.DeleteOutline,
                    contentDescription = "Delete from watch history",
                    tint = omegaColors.danger,
                    modifier = Modifier.size(19.dp)
                )
            }
        }
    }
}

private fun formatRelativeTime(timestampMs: Long): String {
    val diffMs = (System.currentTimeMillis() - timestampMs).coerceAtLeast(0L)
    val mins = diffMs / 60_000L
    val hours = mins / 60L
    val days = hours / 24L
    return when {
        mins < 1 -> "Just now"
        mins < 60 -> "${mins}m ago"
        hours < 24 -> "${hours}h ago"
        days == 1L -> "Yesterday"
        days < 7 -> "${days}d ago"
        else -> "${days / 7}w ago"
    }
}
