/*
 * Omega Video Downloader Project Original (2026)
 * arslandaim-hub (GitHub.com/arslandaim-hub)
 * Licenced Under GPL-3.0+
*/
package com.arslandaim.omegavideodownloader.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.ImageLoader
import com.arslandaim.omegavideodownloader.R
import com.arslandaim.omegavideodownloader.*
import com.arslandaim.omegavideodownloader.ui.DownloadsViewModel
import com.arslandaim.omegavideodownloader.ui.MainViewModel
import com.arslandaim.omegavideodownloader.ui.components.ActiveDownloadItem
import com.arslandaim.omegavideodownloader.ui.components.DownloadItem
import com.arslandaim.omegavideodownloader.ui.components.FilterButton
import com.arslandaim.omegavideodownloader.ui.components.MovieSearchResultCard
import com.arslandaim.omegavideodownloader.ui.components.PlaylistDetailView
import com.arslandaim.omegavideodownloader.ui.components.WatchHistoryCard
import com.arslandaim.omegavideodownloader.ui.theme.OmegaTheme

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun LibraryScreen(
    viewModel: DownloadsViewModel,
    mainViewModel: MainViewModel,
    imageLoader: ImageLoader,
    onVideoClick: (DownloadedVideo) -> Unit,
    onLockClick: (DownloadedVideo) -> Unit,
    onResumeHistoryItem: (WatchHistoryItem) -> Unit,
    onOpenWatchlistItem: (LikedVideo) -> Unit,
    sharedTransitionScope: SharedTransitionScope
) {
    val omegaColors = OmegaTheme.colors
    val downloadedVideos by viewModel.downloadedVideos.collectAsState()
    val activeDownloads by mainViewModel.activeDownloads.collectAsState()
    val currentlyPlaying by mainViewModel.currentlyPlayingPath.collectAsState()
    val watchHistory by mainViewModel.watchHistory.collectAsState()
    val pauseContinueWatching by mainViewModel.pauseContinueWatching.collectAsState()
    val likedVideos by mainViewModel.likedVideos.collectAsState()
    val wifiOnlyDownloads by mainViewModel.wifiOnlyDownloads.collectAsState()

    var downloadToCancel by remember { mutableStateOf<ActiveDownload?>(null) }
    var showClearWatchHistoryDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.syncHistory()
    }

    viewModel.videoToLock?.let { video ->
        AlertDialog(
            onDismissRequest = { viewModel.videoToLock = null },
            icon = { Icon(Icons.Default.Lock, null, tint = omegaColors.secondary) },
            title = { Text("Move to Vault?", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold) },
            text = {
                Text(
                    "Do you want to move \"${video.title}\" to your Private Vault?",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onLockClick(video)
                    viewModel.videoToLock = null
                }) {
                    Text("Lock", fontWeight = FontWeight.ExtraBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.videoToLock = null }) {
                    Text("Cancel", fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    downloadToCancel?.let { download ->
        AlertDialog(
            onDismissRequest = { downloadToCancel = null },
            icon = { Icon(Icons.Default.Cancel, null, tint = omegaColors.danger) },
            title = { Text("Cancel Download?", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold) },
            text = {
                Text(
                    "Are you sure you want to terminate download?\nThis cannot be resumed.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    mainViewModel.cancelDownload(download.id)
                    downloadToCancel = null
                }) {
                    Text("Stop Download", color = omegaColors.danger, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { downloadToCancel = null }) {
                    Text("Keep")
                }
            }
        )
    }

    if (showClearWatchHistoryDialog) {
        AlertDialog(
            onDismissRequest = { showClearWatchHistoryDialog = false },
            title = { Text("Clear Watch History?", fontWeight = FontWeight.Bold) },
            text = { Text("Remove all ${watchHistory.size} items from your watch history? Downloaded files will not be deleted.") },
            confirmButton = {
                TextButton(onClick = {
                    showClearWatchHistoryDialog = false
                    mainViewModel.clearWatchHistory()
                }) {
                    Text("Clear All", color = omegaColors.danger, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearWatchHistoryDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Library",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = omegaColors.textPrimary
            )
            if (activeDownloads.isNotEmpty()) {
                val totalSpeed = activeDownloads.sumOf { if (!it.isPaused) it.speedBytesPerSec else 0L }
                Surface(
                    color = omegaColors.primary.copy(alpha = 0.14f),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(
                        text = buildString {
                            append("${activeDownloads.size} active")
                            if (totalSpeed > 0L) append(" • ${StorageUtils.formatSpeed(totalSpeed)}")
                        },
                        color = omegaColors.primary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(bottom = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            val queueLabel = if (activeDownloads.isNotEmpty()) "Queue (${activeDownloads.size})" else "Queue"
            FilterButton(text = queueLabel, isSelected = viewModel.selectedTab == "Queue", onClick = { viewModel.selectedTab = "Queue" })
            FilterButton(text = "Movies", isSelected = viewModel.selectedTab == "Movies", onClick = { viewModel.selectedTab = "Movies" })
            FilterButton(text = "Videos", isSelected = viewModel.selectedTab == "Videos", onClick = { viewModel.selectedTab = "Videos" })
            FilterButton(text = "Music", isSelected = viewModel.selectedTab == "Music", onClick = { viewModel.selectedTab = "Music" })
            FilterButton(text = "Watchlist", isSelected = viewModel.selectedTab == "Watchlist" || viewModel.selectedTab == "Liked", onClick = { viewModel.selectedTab = "Watchlist" })
            FilterButton(text = "History", isSelected = viewModel.selectedTab == "History", onClick = { viewModel.selectedTab = "History" })
        }

        when (viewModel.selectedTab) {
            "Queue" -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    contentPadding = PaddingValues(bottom = 175.dp)
                ) {
                    if (wifiOnlyDownloads) {
                        item {
                            Surface(
                                color = omegaColors.primary.copy(alpha = 0.09f),
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(0.5.dp, omegaColors.primary.copy(alpha = 0.25f)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Wifi, null, tint = omegaColors.primary, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        "Wi-Fi Only mode enabled — downloads wait for unmetered Wi-Fi.",
                                        fontSize = 12.sp,
                                        color = omegaColors.textPrimary.copy(alpha = 0.8f)
                                    )
                                }
                            }
                        }
                    }

                    if (activeDownloads.isEmpty()) {
                        item {
                            Box(modifier = Modifier.fillParentMaxHeight(0.68f), contentAlignment = Alignment.Center) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        imageVector = Icons.Default.CloudDone,
                                        contentDescription = null,
                                        tint = omegaColors.primary.copy(alpha = 0.5f),
                                        modifier = Modifier.size(46.dp)
                                    )
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Text(
                                        "No active downloads",
                                        fontWeight = FontWeight.SemiBold,
                                        color = omegaColors.textPrimary.copy(alpha = 0.75f)
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        "Start a movie or video download to track live speed & progress here.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = omegaColors.textSecondary,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                    } else {
                        items(items = activeDownloads, key = { it.id }) { download ->
                            ActiveDownloadItem(
                                download = download,
                                onPause = { mainViewModel.pauseDownload(it) },
                                onResume = { mainViewModel.resumeDownload(it) },
                                onCancel = { downloadToCancel = download }
                            )
                        }
                    }
                }
            }

            "Watchlist", "Liked" -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 175.dp)
                ) {
                    if (likedVideos.isEmpty()) {
                        item {
                            Box(modifier = Modifier.fillParentMaxHeight(0.68f), contentAlignment = Alignment.Center) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        imageVector = Icons.Default.FavoriteBorder,
                                        contentDescription = null,
                                        tint = omegaColors.watchlistRose.copy(alpha = 0.6f),
                                        modifier = Modifier.size(44.dp)
                                    )
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Text(
                                        "Your Watchlist is empty",
                                        fontWeight = FontWeight.SemiBold,
                                        color = omegaColors.textPrimary.copy(alpha = 0.75f)
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        "Tap the heart icon on movies or videos to save them here.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = omegaColors.textSecondary,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                    } else {
                        item {
                            Text(
                                "Watchlist (${likedVideos.size})",
                                style = MaterialTheme.typography.labelMedium,
                                color = omegaColors.textSecondary,
                                modifier = Modifier.padding(bottom = 2.dp)
                            )
                        }
                        items(items = likedVideos, key = { it.pageUrl }) { liked ->
                            MovieSearchResultCard(
                                result = liked.toMovieSearchResult(),
                                isLiked = true,
                                onLikeClick = { mainViewModel.removeLikedVideo(liked.pageUrl) },
                                onClick = { onOpenWatchlistItem(liked) },
                                modifier = Modifier.animateItem()
                            )
                        }
                    }
                }
            }

            "History" -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 175.dp)
                ) {
                    if (watchHistory.isEmpty()) {
                        item {
                            Box(modifier = Modifier.fillParentMaxHeight(0.68f), contentAlignment = Alignment.Center) {
                                Text(
                                    if (pauseContinueWatching) "Continue Watching is paused in Settings" else "No watch history yet",
                                    color = omegaColors.textSecondary
                                )
                            }
                        }
                    } else {
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    if (pauseContinueWatching) {
                                        "Continue Watching (${watchHistory.size}) • Paused"
                                    } else {
                                        "Continue Watching (${watchHistory.size})"
                                    },
                                    style = MaterialTheme.typography.labelMedium,
                                    color = omegaColors.textSecondary
                                )
                                if (watchHistory.size > 1) {
                                    TextButton(
                                        onClick = { showClearWatchHistoryDialog = true },
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                                    ) {
                                        Text("Clear All", color = omegaColors.danger, fontWeight = FontWeight.SemiBold)
                                    }
                                }
                            }
                        }
                        items(items = watchHistory, key = { it.id }) { item ->
                            WatchHistoryCard(
                                item = item,
                                imageLoader = imageLoader,
                                isResuming = mainViewModel.resumingHistoryId == item.id,
                                onClick = { onResumeHistoryItem(item) },
                                onDelete = { mainViewModel.deleteWatchHistoryItem(item.id) },
                                modifier = Modifier.animateItem()
                            )
                        }
                    }
                }
            }

            else -> {
                // "Movies", "Videos", or "Music"
                val filteredDownloaded = when (viewModel.selectedTab) {
                    "Movies" -> downloadedVideos.filter {
                        it.type == "video" && !it.isPlaylistGroup &&
                            (it.isMovie || it.durationMs >= 20 * 60 * 1000L)
                    }
                    "Music" -> downloadedVideos.filter {
                        it.type == "audio" && (it.playlistId == null || it.isPlaylistGroup)
                    }
                    else -> downloadedVideos.filter {
                        it.type == "video" && (it.playlistId == null || it.isPlaylistGroup) &&
                            !(!it.isPlaylistGroup && (it.isMovie || it.durationMs >= 20 * 60 * 1000L))
                    }
                }

                Box(modifier = Modifier.fillMaxSize()) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        contentPadding = PaddingValues(bottom = 175.dp)
                    ) {
                        if (filteredDownloaded.isEmpty()) {
                            item {
                                Box(modifier = Modifier.fillParentMaxHeight(0.68f), contentAlignment = Alignment.Center) {
                                    val emptyMsg = when (viewModel.selectedTab) {
                                        "Movies" -> "No offline movies saved yet"
                                        "Music" -> "No music downloads yet"
                                        else -> stringResource(R.string.no_downloads_yet)
                                    }
                                    Text(emptyMsg, color = omegaColors.textSecondary)
                                }
                            }
                        } else {
                            item {
                                Text(
                                    "${viewModel.selectedTab} (${filteredDownloaded.size})",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = omegaColors.textSecondary,
                                    modifier = Modifier.padding(bottom = 8.dp)
                                )
                            }
                            itemsIndexed(
                                items = filteredDownloaded,
                                key = { _, video -> video.timestamp + video.localPath.hashCode() }
                            ) { _, video ->
                                val state = remember { MutableTransitionState(false).apply { targetState = true } }
                                androidx.compose.animation.AnimatedVisibility(
                                    visibleState = state,
                                    enter = fadeIn(spring(stiffness = Spring.StiffnessLow)) +
                                        slideInVertically(spring(stiffness = Spring.StiffnessLow, dampingRatio = Spring.DampingRatioLowBouncy)) { it / 2 },
                                    modifier = Modifier.animateItem()
                                ) {
                                    DownloadItem(
                                        video = video,
                                        isCurrentlyPlaying = currentlyPlaying == video.localPath,
                                        imageLoader = imageLoader,
                                        onDelete = { viewModel.videoToDelete = video },
                                        onLock = { viewModel.videoToLock = video },
                                        sharedTransitionScope = sharedTransitionScope,
                                        animatedVisibilityScope = this,
                                        onClick = {
                                            if (video.isPlaylistGroup) {
                                                viewModel.openPlaylistId = video.playlistId
                                            } else {
                                                onVideoClick(video)
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }

                    // Playlist Detail Sub-view
                    androidx.compose.animation.AnimatedVisibility(
                        visible = viewModel.openPlaylistId != null,
                        enter = slideInHorizontally(initialOffsetX = { it }) + fadeIn(),
                        exit = slideOutHorizontally(targetOffsetX = { it }) + fadeOut()
                    ) {
                        PlaylistDetailView(
                            playlistId = viewModel.openPlaylistId,
                            downloadedVideos = downloadedVideos,
                            currentlyPlaying = currentlyPlaying,
                            imageLoader = imageLoader,
                            onBack = { viewModel.openPlaylistId = null },
                            onVideoClick = onVideoClick,
                            onDeleteVideo = { viewModel.videoToDelete = it },
                            onLockVideo = { viewModel.videoToLock = it },
                            sharedTransitionScope = sharedTransitionScope
                        )
                    }
                }
            }
        }
    }
}
