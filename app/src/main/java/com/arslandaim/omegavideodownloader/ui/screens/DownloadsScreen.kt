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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.ImageLoader
import com.arslandaim.omegavideodownloader.R
import com.arslandaim.omegavideodownloader.*
import com.arslandaim.omegavideodownloader.ui.DownloadsViewModel
import com.arslandaim.omegavideodownloader.ui.MainViewModel
import com.arslandaim.omegavideodownloader.ui.components.ActiveDownloadItem
import com.arslandaim.omegavideodownloader.ui.components.DownloadItem
import com.arslandaim.omegavideodownloader.ui.components.FilterButton

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun DownloadsScreen(
    viewModel: DownloadsViewModel,
    mainViewModel: MainViewModel,
    imageLoader: ImageLoader,
    onVideoClick: (DownloadedVideo) -> Unit,
    onLockClick: (DownloadedVideo) -> Unit,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope
) {
    val downloadedVideos by viewModel.downloadedVideos.collectAsState()
    val activeDownloads by mainViewModel.activeDownloads.collectAsState()
    val currentlyPlaying by mainViewModel.currentlyPlayingPath.collectAsState()
    
    var downloadToCancel by remember { mutableStateOf<ActiveDownload?>(null) }

    LaunchedEffect(Unit) {
        viewModel.syncHistory()
    }

    viewModel.videoToLock?.let { video ->
        AlertDialog(
            onDismissRequest = { viewModel.videoToLock = null },
            icon = { Icon(Icons.Default.Lock, null, tint = Color(0xFFFF5722)) },
            title = { Text("Lock Item?", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold) },
            text = { Text("Do you want to move \"${video.title}\" to the Private Locker?", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center) },
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
            icon = { Icon(Icons.Default.Cancel, null, tint = Color.Red) },
            title = { Text("Cancel Download?", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold) },
            text = { Text("Are you sure you want to terminate download?\nThis cannot be resumed.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center) },
            confirmButton = {
                TextButton(onClick = {
                    mainViewModel.cancelDownload(download.id)
                    downloadToCancel = null
                }) {
                    Text("Stop Download", color = Color.Red, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { downloadToCancel = null }) {
                    Text("Keep")
                }
            }
        )
    }

    Column(modifier = Modifier.fillMaxSize().padding(20.dp)) {
        Text("Downloads", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
        Spacer(modifier = Modifier.height(16.dp))

        Row(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            FilterButton(text = "Videos", isSelected = viewModel.selectedTab == "Videos", onClick = { viewModel.selectedTab = "Videos" })
            FilterButton(text = "Music", isSelected = viewModel.selectedTab == "Music", onClick = { viewModel.selectedTab = "Music" })
        }

        val currentType = if (viewModel.selectedTab == "Videos") "video" else "audio"
        val filteredActive = activeDownloads.filter { it.type == currentType }
        
        // Show only standalone videos and playlist groups in the main list
        val filteredDownloaded = downloadedVideos.filter { 
            it.type == currentType && (it.playlistId == null || it.isPlaylistGroup)
        }

        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(4.dp), contentPadding = PaddingValues(bottom = 80.dp)) {
                if (filteredActive.isNotEmpty()) {
                    item { Text("Downloading", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f), modifier = Modifier.padding(bottom = 8.dp)) }
                    items(items = filteredActive, key = { it.id }) { download ->
                        ActiveDownloadItem(download) { downloadToCancel = download }
                    }
                    item { Spacer(modifier = Modifier.height(24.dp)) }
                }

                if (filteredDownloaded.isEmpty() && filteredActive.isEmpty()) {
                    item {
                        Box(modifier = Modifier.fillParentMaxHeight(0.7f), contentAlignment = Alignment.Center) {
                            Text(if (viewModel.selectedTab == "Videos") stringResource(R.string.no_downloads_yet) else "No music downloads yet", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                        }
                    }
                } else if (filteredDownloaded.isNotEmpty()) {
                    item { Text("Completed", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f), modifier = Modifier.padding(bottom = 8.dp)) }
                    itemsIndexed(items = filteredDownloaded, key = { _, video -> video.timestamp + video.localPath.hashCode() }) { _, video ->
                        val state = remember { MutableTransitionState(false).apply { targetState = true } }
                        androidx.compose.animation.AnimatedVisibility(
                            visibleState = state, 
                            enter = fadeIn(spring(stiffness = Spring.StiffnessLow)) + slideInVertically(spring(stiffness = Spring.StiffnessLow, dampingRatio = Spring.DampingRatioLowBouncy)) { it / 2 },
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
                    // We need an AnimatedVisibilityScope here for DownloadItem
                    // Since this is a simple list, we can just wrap it or use a dummy
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
