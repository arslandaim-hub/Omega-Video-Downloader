/*
 * Omega Video Downloader Project Original (2026)
 * arslandaim-hub (GitHub.com/arslandaim-hub)
 * Licenced Under GPL-3.0+
*/
package com.arslandaim.omegavideodownloader.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.request.videoFrameMillis
import com.arslandaim.omegavideodownloader.R
import com.arslandaim.omegavideodownloader.*
import com.arslandaim.omegavideodownloader.ui.theme.OmegaTheme

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun PlaylistDetailView(
    playlistId: String?,
    downloadedVideos: List<DownloadedVideo>,
    currentlyPlaying: String?,
    imageLoader: ImageLoader,
    onBack: () -> Unit,
    onVideoClick: (DownloadedVideo) -> Unit,
    onDeleteVideo: (DownloadedVideo) -> Unit,
    onLockVideo: (DownloadedVideo) -> Unit,
    sharedTransitionScope: SharedTransitionScope
) {
    val omegaColors = OmegaTheme.colors
    val playlistVideos = downloadedVideos.filter { it.playlistId == playlistId && !it.isPlaylistGroup }
    val playlistGroup = downloadedVideos.find { it.playlistId == playlistId && it.isPlaylistGroup }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(omegaColors.backgroundBrush)
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = omegaColors.textPrimary)
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    playlistGroup?.title ?: "Playlist Items",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = omegaColors.textPrimary,
                    maxLines = 1
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items(items = playlistVideos, key = { it.timestamp + it.localPath.hashCode() }) { video ->
                    Box {
                        androidx.compose.animation.AnimatedVisibility(
                            visible = true,
                            enter = fadeIn(),
                            exit = fadeOut()
                        ) {
                            DownloadItem(
                                video = video,
                                isCurrentlyPlaying = currentlyPlaying == video.localPath,
                                imageLoader = imageLoader,
                                onDelete = { onDeleteVideo(video) },
                                onLock = { onLockVideo(video) },
                                sharedTransitionScope = sharedTransitionScope,
                                animatedVisibilityScope = this,
                                onClick = { onVideoClick(video) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun FilterButton(text: String, isSelected: Boolean, onClick: () -> Unit) {
    val omegaColors = OmegaTheme.colors
    Surface(
        onClick = onClick,
        color = if (isSelected) omegaColors.primary else omegaColors.card,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .height(42.dp)
            .widthIn(min = 88.dp),
        border = if (isSelected) null else BorderStroke(0.8.dp, omegaColors.border)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.padding(horizontal = 16.dp)
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Bold,
                color = if (isSelected) Color.White else omegaColors.textSecondary,
                maxLines = 1
            )
        }
    }
}

@Composable
fun ActiveDownloadItem(
    download: ActiveDownload,
    onPause: ((Long) -> Unit)? = null,
    onResume: ((Long) -> Unit)? = null,
    onCancel: (Long) -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val omegaColors = OmegaTheme.colors
    val typeAccent = if (download.type == "audio") omegaColors.secondary else omegaColors.primary

    Surface(
        color = omegaColors.card,
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .border(0.8.dp, omegaColors.border, RoundedCornerShape(20.dp))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(64.dp, 48.dp)
                        .background(omegaColors.cardElevated, RoundedCornerShape(12.dp))
                        .border(0.8.dp, typeAccent.copy(alpha = 0.35f), RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        if (download.type == "audio") Icons.Default.MusicNote else Icons.Default.PlayArrow,
                        null,
                        tint = typeAccent
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        download.title,
                        color = omegaColors.textPrimary,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        fontSize = 15.sp
                    )

                    val speedStr = StorageUtils.formatSpeed(download.speedBytesPerSec)
                    val etaStr = if (download.etaSeconds > 0) StorageUtils.formatEta(download.etaSeconds) else ""
                    val liveMetrics = listOf(speedStr, if (etaStr.isNotEmpty()) "$etaStr left" else "")
                        .filter { it.isNotEmpty() }
                        .joinToString(" • ")

                    if (download.playlistProgress != null) {
                        Text(
                            buildString {
                                append("Playlist: ${download.playlistProgress}")
                                if (liveMetrics.isNotEmpty()) append(" • $liveMetrics")
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = omegaColors.primary,
                            fontWeight = FontWeight.Bold
                        )
                    } else {
                        val sizePart = if (download.totalBytes > 0) {
                            "${StorageUtils.formatSize(download.downloadedBytes)} / ${StorageUtils.formatSize(download.totalBytes)}"
                        } else {
                            download.status
                        }
                        val statusText = when {
                            download.isPaused -> if (download.totalBytes > 0) "$sizePart • Paused" else "Paused"
                            download.status.equals("Queued", ignoreCase = true) ->
                                if (download.totalBytes > 0) "$sizePart • Queued" else "Queued"
                            liveMetrics.isNotEmpty() -> "$sizePart • $liveMetrics"
                            else -> sizePart
                        }
                        Text(
                            statusText,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (download.isPaused) omegaColors.gold else omegaColors.textSecondary
                        )
                    }
                }

                if (download.isPaused && onResume != null) {
                    IconButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onResume(download.id)
                        },
                        modifier = Modifier
                            .background(omegaColors.success.copy(alpha = 0.14f), CircleShape)
                            .size(32.dp)
                    ) {
                        Icon(
                            Icons.Default.PlayArrow,
                            contentDescription = "Resume",
                            tint = omegaColors.success,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                } else if (!download.isPaused && download.canPause && onPause != null) {
                    IconButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onPause(download.id)
                        },
                        modifier = Modifier
                            .background(omegaColors.primary.copy(alpha = 0.14f), CircleShape)
                            .size(32.dp)
                    ) {
                        Icon(
                            Icons.Default.Pause,
                            contentDescription = "Pause",
                            tint = omegaColors.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                }

                IconButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onCancel(download.id)
                    },
                    modifier = Modifier
                        .background(omegaColors.danger.copy(alpha = 0.12f), CircleShape)
                        .size(32.dp)
                ) {
                    Icon(Icons.Default.Cancel, "Cancel", tint = omegaColors.danger, modifier = Modifier.size(18.dp))
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            LinearProgressIndicator(
                progress = { download.progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(CircleShape),
                color = if (download.isPaused) omegaColors.gold else omegaColors.primary,
                trackColor = omegaColors.border
            )
        }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun DownloadItem(
    video: DownloadedVideo,
    isCurrentlyPlaying: Boolean,
    imageLoader: ImageLoader,
    onDelete: () -> Unit,
    onLock: () -> Unit,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    onClick: () -> Unit,
    isInsideLocker: Boolean = false
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val omegaColors = OmegaTheme.colors

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(if (isPressed) 0.98f else 1f, label = "item_scale")

    Surface(
        color = omegaColors.card,
        shape = RoundedCornerShape(20.dp),
        interactionSource = interactionSource,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                clip = true
                shape = RoundedCornerShape(20.dp)
            }
            .border(0.8.dp, omegaColors.border, RoundedCornerShape(20.dp)),
        onClick = {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onClick()
        }
    ) {
        Row(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier
                    .size(80.dp, 60.dp)
                    .clip(RoundedCornerShape(14.dp)),
                color = omegaColors.cardElevated
            ) {
                if (video.isLocked && !isInsideLocker) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Lock, "Locked", tint = omegaColors.textSecondary)
                    }
                } else if (video.isPlaylistGroup) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.Folder,
                            "Playlist",
                            tint = omegaColors.primary.copy(alpha = 0.7f),
                            modifier = Modifier.size(32.dp)
                        )
                    }
                } else if (video.type == "audio") {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.MusicNote,
                            "Audio",
                            tint = omegaColors.secondary.copy(alpha = 0.8f),
                            modifier = Modifier.size(32.dp)
                        )
                        if (isCurrentlyPlaying) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.Black.copy(alpha = 0.3f)),
                                contentAlignment = Alignment.Center
                            ) {
                                NowPlayingIndicator()
                            }
                        }
                    }
                } else {
                    val imageRequest = remember(video.localPath) {
                        ImageRequest.Builder(context)
                            .data(video.localPath)
                            .videoFrameMillis(1000)
                            .build()
                    }

                    with(sharedTransitionScope) {
                        AsyncImage(
                            model = imageRequest,
                            imageLoader = imageLoader,
                            contentDescription = "Thumbnail",
                            modifier = Modifier
                                .fillMaxSize()
                                .sharedElement(
                                    rememberSharedContentState(key = "video_thumb_${video.localPath}"),
                                    animatedVisibilityScope = animatedVisibilityScope
                                ),
                            contentScale = ContentScale.Crop
                        )
                    }

                    if (isCurrentlyPlaying) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.3f)),
                            contentAlignment = Alignment.Center
                        ) {
                            NowPlayingIndicator()
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    video.title,
                    color = omegaColors.textPrimary,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    fontSize = 15.sp,
                    lineHeight = 20.sp
                )
                if (!isInsideLocker) {
                    Text(
                        if (video.isLocked) "Private Locker"
                        else if (video.type == "audio") stringResource(R.string.music_library)
                        else stringResource(R.string.video_library),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (video.isLocked) omegaColors.secondary else omegaColors.textSecondary
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isInsideLocker) {
                    IconButton(
                        onClick = onLock,
                        modifier = Modifier
                            .background(omegaColors.primary.copy(alpha = 0.14f), CircleShape)
                            .size(36.dp)
                    ) {
                        Icon(
                            Icons.Default.LockOpen,
                            contentDescription = "Unlock",
                            tint = omegaColors.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                } else {
                    IconButton(
                        onClick = onLock,
                        modifier = Modifier
                            .background(omegaColors.cardElevated, CircleShape)
                            .size(36.dp)
                    ) {
                        Icon(
                            Icons.Default.Lock,
                            contentDescription = "Lock",
                            tint = omegaColors.secondary,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier
                            .background(omegaColors.cardElevated, CircleShape)
                            .size(36.dp)
                    ) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Delete",
                            tint = omegaColors.danger.copy(alpha = 0.8f),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun NowPlayingIndicator() {
    val infiniteTransition = rememberInfiniteTransition(label = "now_playing")
    val barColor = OmegaTheme.colors.primary

    @Composable
    fun Bar(delay: Int) {
        val height by infiniteTransition.animateFloat(
            initialValue = 0.2f,
            targetValue = 0.8f,
            animationSpec = infiniteRepeatable(
                animation = tween(400, delayMillis = delay, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "bar_height"
        )
        Box(
            modifier = Modifier
                .width(4.dp)
                .fillMaxHeight(height)
                .background(barColor, RoundedCornerShape(2.dp))
        )
    }

    Row(
        modifier = Modifier.height(24.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        Bar(0)
        Bar(150)
        Bar(300)
    }
}

@Composable
fun QualitySelectionSection(
    metadata: VideoMetadata,
    isLiked: Boolean = false,
    onLikeClick: (() -> Unit)? = null,
    onStreamClick: ((StreamCandidate?) -> Unit)? = null,
    onQualitySelected: (VideoQuality) -> Unit,
    onStreamItemClick: ((StreamCandidate?, VideoMetadata) -> Unit)? = null,
    onQualityItemSelected: ((VideoQuality, VideoMetadata) -> Unit)? = null,
    onDownloadAll: ((List<VideoMetadata>) -> Unit)? = null
) {
    val haptic = LocalHapticFeedback.current
    val omegaColors = OmegaTheme.colors
    val items = metadata.mediaItems
    var selectedItemIndex by remember(metadata) { mutableIntStateOf(0) }
    val activeMeta = items.getOrNull(selectedItemIndex) ?: metadata

    Surface(
        color = omegaColors.card,
        shape = RoundedCornerShape(28.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(0.8.dp, omegaColors.border, RoundedCornerShape(28.dp))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Media Header (Thumbnail, Title, Duration, Source Badge)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (!activeMeta.thumbnailUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = activeMeta.thumbnailUrl,
                        contentDescription = activeMeta.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(84.dp, 54.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(omegaColors.cardElevated)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = activeMeta.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = omegaColors.textPrimary,
                        maxLines = 2
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val badgeText = when (activeMeta.resolvedBy) {
                            ResolveSource.DIRECT -> "Direct Stream"
                            ResolveSource.BROWSER -> "Web Sniffer"
                            ResolveSource.ARCHIVE -> "Archive.org"
                            ResolveSource.YTDLP -> if (activeMeta.isPlaylist) "Playlist" else "Extractor"
                        }
                        Surface(
                            color = omegaColors.primary.copy(alpha = 0.14f),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = badgeText,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = omegaColors.primary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        if (activeMeta.isGif) {
                            Surface(
                                color = omegaColors.secondary.copy(alpha = 0.16f),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = "GIF",
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = omegaColors.secondary,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }
                        }
                        if (activeMeta.durationSec > 0) {
                            Text(
                                text = formatTime(activeMeta.durationSec * 1000L),
                                style = MaterialTheme.typography.labelSmall,
                                color = omegaColors.textSecondary
                            )
                        }
                    }
                }
                if (onLikeClick != null) {
                    IconButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onLikeClick()
                        },
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(
                                if (isLiked) omegaColors.watchlistRose.copy(alpha = 0.16f)
                                else omegaColors.cardElevated
                            )
                    ) {
                        Icon(
                            imageVector = if (isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = if (isLiked) "Remove from Liked" else "Save to Liked",
                            tint = if (isLiked) omegaColors.watchlistRose else omegaColors.textSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            // Multi-video / GIF carousel picker (e.g. Twitter/X posts with 2-4 videos)
            if (items.size > 1) {
                Spacer(modifier = Modifier.height(14.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Media in Post (${items.size})",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = omegaColors.textSecondary
                    )
                    if (onDownloadAll != null) {
                        TextButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onDownloadAll(items)
                            },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp)
                        ) {
                            Icon(Icons.Default.DownloadForOffline, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Download All", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    itemsIndexed(items) { idx, item ->
                        val selected = idx == selectedItemIndex
                        Surface(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                selectedItemIndex = idx
                            },
                            color = if (selected) omegaColors.primary.copy(alpha = 0.16f) else omegaColors.cardElevated,
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(
                                width = if (selected) 1.5.dp else 0.8.dp,
                                color = if (selected) omegaColors.primary else omegaColors.border
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (!item.thumbnailUrl.isNullOrBlank()) {
                                    AsyncImage(
                                        model = item.thumbnailUrl,
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .size(36.dp, 26.dp)
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(omegaColors.card)
                                    )
                                    Spacer(Modifier.width(8.dp))
                                }
                                Text(
                                    text = if (item.isGif) "GIF ${idx + 1}" else "Video ${idx + 1}",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (selected) omegaColors.primary else omegaColors.textPrimary
                                )
                            }
                        }
                    }
                }
            }

            // Stream Online CTA
            if (activeMeta.streams.isNotEmpty() && !activeMeta.isPlaylist && (onStreamItemClick != null || onStreamClick != null)) {
                Spacer(modifier = Modifier.height(16.dp))
                val primary = activeMeta.streams.first()
                Button(
                    onClick = {
                        if (onStreamItemClick != null) onStreamItemClick(primary, activeMeta)
                        else onStreamClick?.invoke(primary)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = omegaColors.primary,
                        contentColor = Color.White
                    )
                ) {
                    Icon(Icons.Default.LiveTv, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Stream Online (${primary.label})",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                }
            }

            if (activeMeta.qualities.isNotEmpty()) {
                Spacer(modifier = Modifier.height(18.dp))
                Text(
                    if (activeMeta.isGif) "Download GIF (MP4)" else "Download Video",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = omegaColors.textPrimary,
                    modifier = Modifier.align(Alignment.Start)
                )
                Spacer(modifier = Modifier.height(10.dp))

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    activeMeta.qualities.forEach { quality ->
                        val matchingStream = activeMeta.streams.firstOrNull {
                            it.url == quality.url ||
                                (it.height > 0 && quality.label.contains("${it.height}p"))
                        }
                        QualityItem(
                            quality = quality,
                            onStream = if (matchingStream != null && !activeMeta.isPlaylist && (onStreamItemClick != null || onStreamClick != null)) {
                                {
                                    if (onStreamItemClick != null) onStreamItemClick(matchingStream, activeMeta)
                                    else onStreamClick?.invoke(matchingStream)
                                }
                            } else null,
                            onQualitySelected = { q ->
                                if (onQualityItemSelected != null) onQualityItemSelected(q, activeMeta)
                                else onQualitySelected(q)
                            }
                        )
                    }
                }
            }

            if (activeMeta.audioQualities.isNotEmpty()) {
                Spacer(modifier = Modifier.height(18.dp))
                Text(
                    "Audio Only",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = omegaColors.textPrimary,
                    modifier = Modifier.align(Alignment.Start)
                )
                Spacer(modifier = Modifier.height(10.dp))

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    activeMeta.audioQualities.forEach { quality ->
                        QualityItem(
                            quality = quality,
                            onStream = null,
                            onQualitySelected = { q ->
                                if (onQualityItemSelected != null) onQualityItemSelected(q, activeMeta)
                                else onQualitySelected(q)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun QualityItem(
    quality: VideoQuality,
    onStream: (() -> Unit)? = null,
    onQualitySelected: (VideoQuality) -> Unit
) {
    val omegaColors = OmegaTheme.colors
    val accent = if (quality.type == "audio") omegaColors.secondary else omegaColors.primary
    Surface(
        onClick = { onQualitySelected(quality) },
        color = omegaColors.cardElevated,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 14.dp, vertical = 12.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .background(accent.copy(alpha = 0.18f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        if (quality.type == "audio") Icons.Default.MusicNote else Icons.Default.Download,
                        null,
                        modifier = Modifier.size(16.dp),
                        tint = accent
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    "Download ${quality.label}",
                    color = omegaColors.textPrimary,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    maxLines = 1
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (quality.size.isNotBlank() && quality.size != "Unknown" && quality.size != "Unknown Size") {
                    Surface(color = omegaColors.card, shape = RoundedCornerShape(8.dp)) {
                        Text(
                            quality.size,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = omegaColors.textSecondary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                if (onStream != null) {
                    IconButton(
                        onClick = onStream,
                        modifier = Modifier
                            .size(34.dp)
                            .background(omegaColors.success.copy(alpha = 0.16f), CircleShape)
                    ) {
                        Icon(
                            Icons.Default.PlayArrow,
                            contentDescription = "Stream this quality",
                            tint = omegaColors.success,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun MovieSearchResultCard(
    result: MovieSearchResult,
    isLiked: Boolean = false,
    onLikeClick: (() -> Unit)? = null,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val omegaColors = OmegaTheme.colors
    val (badgeColor, badgeLabel, placeholderIcon) = when (result.source) {
        SearchSource.ARCHIVE_ORG -> Triple(omegaColors.success, "Archive.org", Icons.Default.Movie)
        SearchSource.YOUTUBE -> Triple(Color(0xFFFF3B30), "YouTube", Icons.Default.PlayCircle)
        SearchSource.DAILYMOTION -> Triple(omegaColors.primary, "Dailymotion", Icons.Default.Movie)
        SearchSource.PEERTUBE -> Triple(omegaColors.secondary, "PeerTube", Icons.Default.Movie)
    }

    Surface(
        onClick = onClick,
        color = omegaColors.card,
        shape = RoundedCornerShape(18.dp),
        modifier = modifier
            .fillMaxWidth()
            .border(0.8.dp, omegaColors.border, RoundedCornerShape(18.dp))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp, 56.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(omegaColors.cardElevated),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = placeholderIcon,
                    contentDescription = null,
                    tint = badgeColor.copy(alpha = 0.55f),
                    modifier = Modifier.size(26.dp)
                )
                if (!result.thumbnailUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = result.thumbnailUrl,
                        contentDescription = result.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = result.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = omegaColors.textPrimary,
                    maxLines = 2,
                    lineHeight = 18.sp
                )
                Spacer(modifier = Modifier.height(5.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Surface(
                        color = badgeColor.copy(alpha = 0.14f),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = badgeLabel,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = badgeColor,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    result.year?.takeIf { it.isNotBlank() }?.let { yr ->
                        Text(
                            text = yr,
                            style = MaterialTheme.typography.labelSmall,
                            color = omegaColors.textSecondary
                        )
                    }
                    if (result.durationSec > 0) {
                        Text(
                            text = formatTime(result.durationSec * 1000L),
                            style = MaterialTheme.typography.labelSmall,
                            color = omegaColors.textSecondary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    if (result.sizeBytes > 0) {
                        Text(
                            text = StorageUtils.formatSize(result.sizeBytes),
                            style = MaterialTheme.typography.labelSmall,
                            color = omegaColors.textSecondary
                        )
                    }
                }
                result.subtitle?.takeIf { it.isNotBlank() }?.let { sub ->
                    Text(
                        text = sub,
                        style = MaterialTheme.typography.labelSmall,
                        color = omegaColors.textSecondary,
                        maxLines = 1,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }

            if (onLikeClick != null) {
                IconButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onLikeClick()
                    },
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(
                            if (isLiked) omegaColors.watchlistRose.copy(alpha = 0.16f)
                            else omegaColors.cardElevated
                        )
                ) {
                    Icon(
                        imageVector = if (isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = if (isLiked) "Remove from Liked" else "Save to Liked",
                        tint = if (isLiked) omegaColors.watchlistRose else omegaColors.textSecondary,
                        modifier = Modifier.size(19.dp)
                    )
                }
                Spacer(modifier = Modifier.width(2.dp))
            }

            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = "View Qualities",
                tint = omegaColors.textSecondary
            )
        }
    }
}
