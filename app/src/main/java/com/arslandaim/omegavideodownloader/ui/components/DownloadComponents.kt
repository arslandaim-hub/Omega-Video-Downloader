/*
 * Omega Video Downloader Project Original (2026)
 * arslandaim-hub (GitHub.com/arslandaim-hub)
 * Licenced Under GPL-3.0+
*/
package com.arslandaim.omegavideodownloader.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.ImageLoader
import coil.compose.AsyncImage
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import coil.request.ImageRequest
import coil.request.videoFrameMillis
import com.arslandaim.omegavideodownloader.R
import com.arslandaim.omegavideodownloader.*
import com.arslandaim.omegavideodownloader.ui.theme.glassBackground
import com.arslandaim.omegavideodownloader.ui.theme.glassBorder

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
    val playlistVideos = downloadedVideos.filter { it.playlistId == playlistId && !it.isPlaylistGroup }
    val playlistGroup = downloadedVideos.find { it.playlistId == playlistId && it.isPlaylistGroup }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    playlistGroup?.title ?: "Playlist Items",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
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
    Surface(
        onClick = onClick,
        color = if (isSelected) Color(0xFF1877F2) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .height(42.dp)
            .width(100.dp),
        border = if (isSelected) null else androidx.compose.foundation.BorderStroke(0.5.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f))
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Bold,
                color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
        }
    }
}

@Composable
fun ActiveDownloadItem(download: ActiveDownload, onCancel: (Long) -> Unit) {
    val haptic = LocalHapticFeedback.current
    Surface(
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .border(0.5.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f), RoundedCornerShape(20.dp))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(64.dp, 48.dp)
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f), RoundedCornerShape(12.dp))
                        .border(
                            0.5.dp, 
                            if (download.type == "audio") Color(0xFFFF5722).copy(alpha = 0.3f) 
                            else Color(0xFF1877F2).copy(alpha = 0.3f), 
                            RoundedCornerShape(12.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        if (download.type == "audio") Icons.Default.MusicNote else Icons.Default.PlayArrow, 
                        null, 
                        tint = if (download.type == "audio") Color(0xFFFF5722).copy(alpha = 0.6f) 
                        else Color(0xFF1877F2).copy(alpha = 0.6f)
                    )
                }
                
                Spacer(modifier = Modifier.width(16.dp))
                
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        download.title,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        fontSize = 15.sp
                    )
                    
                    if (download.playlistProgress != null) {
                        Text(
                            "Playlist Progress: ${download.playlistProgress}",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFFFF5722),
                            fontWeight = FontWeight.Bold
                        )
                    } else {
                        val downloadedStr = StorageUtils.formatSize(download.downloadedBytes)
                        val totalStr = StorageUtils.formatSize(download.totalBytes)
                        Text(
                            if (download.totalBytes > 0) "$downloadedStr / $totalStr" else "Downloading...",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                    }
                }

                IconButton(
                    onClick = { 
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onCancel(download.id) 
                    },
                    modifier = Modifier
                        .background(Color.Red.copy(alpha = 0.1f), CircleShape)
                        .size(32.dp)
                ) {
                    Icon(Icons.Default.Cancel, "Cancel", tint = Color.Red.copy(alpha = 0.7f), modifier = Modifier.size(18.dp))
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            LinearProgressIndicator(
                progress = { download.progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(CircleShape),
                color = Color(0xFF1877F2),
                trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
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
    
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(if (isPressed) 0.98f else 1f, label = "item_scale")

    Surface(
        color = MaterialTheme.colorScheme.glassBackground,
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
            .border(0.5.dp, MaterialTheme.colorScheme.glassBorder, RoundedCornerShape(20.dp)),
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
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
            ) {
                if (video.isLocked && !isInsideLocker) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Lock, "Locked", tint = Color.White.copy(alpha = 0.5f))
                    }
                } else if (video.isPlaylistGroup) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.Folder, 
                            "Playlist", 
                            tint = Color(0xFF1877F2).copy(alpha = 0.5f), 
                            modifier = Modifier.size(32.dp)
                        )
                    }
                } else if (video.type == "audio") {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.MusicNote, 
                            "Audio", 
                            tint = Color(0xFFFF5722).copy(alpha = 0.5f), 
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
                    color = MaterialTheme.colorScheme.onSurface,
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
                        color = if (video.isLocked) Color(0xFFFF5722) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isInsideLocker) {
                    IconButton(
                        onClick = onLock,
                        modifier = Modifier
                            .background(Color(0xFFFF5722).copy(alpha = 0.1f), CircleShape)
                            .size(36.dp)
                    ) {
                        Icon(
                            Icons.Default.LockOpen,
                            contentDescription = "Unlock",
                            tint = Color(0xFFFF5722),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                } else {
                    IconButton(
                        onClick = onLock,
                        modifier = Modifier
                            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f), CircleShape)
                            .size(36.dp)
                    ) {
                        Icon(
                            Icons.Default.Lock,
                            contentDescription = "Lock",
                            tint = Color(0xFFFF5722),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    
                    Spacer(modifier = Modifier.width(8.dp))
                    
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier
                            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f), CircleShape)
                            .size(36.dp)
                    ) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Delete",
                            tint = Color.Red.copy(alpha = 0.6f),
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
                .background(Color(0xFF1877F2), RoundedCornerShape(2.dp))
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
fun QualitySelectionSection(metadata: VideoMetadata, onQualitySelected: (VideoQuality) -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f),
        shape = RoundedCornerShape(28.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(0.5.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f), RoundedCornerShape(28.dp))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 450.dp)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (metadata.qualities.isNotEmpty()) {
                Text(
                    "Video Qualities",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(16.dp))
                
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    metadata.qualities.forEach { quality ->
                        QualityItem(quality, onQualitySelected)
                    }
                }
                
                if (metadata.audioQualities.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }

            if (metadata.audioQualities.isNotEmpty()) {
                Text(
                    "Audio Only",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(16.dp))
                
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    metadata.audioQualities.forEach { quality ->
                        QualityItem(quality, onQualitySelected)
                    }
                }
            }
        }
    }
}

@Composable
fun QualityItem(quality: VideoQuality, onQualitySelected: (VideoQuality) -> Unit) {
    Surface(
        onClick = { onQualitySelected(quality) },
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .background(
                            if (quality.type == "audio") Color(0xFFFF5722).copy(alpha = 0.2f)
                            else Color(0xFF1877F2).copy(alpha = 0.2f), 
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        if (quality.type == "audio") Icons.Default.MusicNote else Icons.Default.PlayArrow, 
                        null, 
                        modifier = Modifier.size(16.dp), 
                        tint = if (quality.type == "audio") Color(0xFFFF5722) else Color(0xFF1877F2)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    if (quality.type == "audio") "Download Audio" else "Download ${quality.label}",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp
                )
            }
            if (quality.size != "Unknown Size") {
                Surface(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f), shape = RoundedCornerShape(8.dp)) {
                    Text(
                        quality.size,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
