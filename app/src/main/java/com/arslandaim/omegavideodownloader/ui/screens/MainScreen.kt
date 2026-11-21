/*
 * Omega Video Downloader Project Original (2026)
 * arslandaim-hub (GitHub.com/arslandaim-hub)
 * Licenced Under GPL-3.0+
*/
package com.arslandaim.omegavideodownloader.ui.screens

import android.content.ComponentName
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.navigation.NavController
import coil.ImageLoader
import com.arslandaim.omegavideodownloader.*
import com.arslandaim.omegavideodownloader.data.DownloadRepository
import com.arslandaim.omegavideodownloader.data.VideoRepository
import com.arslandaim.omegavideodownloader.resolver.WebViewSnifferOverlay
import com.arslandaim.omegavideodownloader.ui.*
import com.arslandaim.omegavideodownloader.ui.components.ExpandedAudioPlayerSheet
import com.arslandaim.omegavideodownloader.ui.components.MiniAudioPlayerBar
import com.arslandaim.omegavideodownloader.ui.components.MiniVideoPlayerBar
import com.arslandaim.omegavideodownloader.ui.components.OmegaLogo
import com.arslandaim.omegavideodownloader.ui.components.PinDialog
import com.arslandaim.omegavideodownloader.ui.theme.OmegaTheme
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun MainScreen(
    navController: NavController,
    mainViewModel: MainViewModel,
    moviesViewModel: MoviesViewModel,
    homeViewModel: HomeViewModel,
    downloadsViewModel: DownloadsViewModel,
    lockerViewModel: LockerViewModel,
    settingsManager: SettingsManager,
    videoRepository: VideoRepository,
    downloadRepository: DownloadRepository,
    imageLoader: ImageLoader,
    sharedTransitionScope: SharedTransitionScope
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val keyboardController = LocalSoftwareKeyboardController.current
    val haptic = LocalHapticFeedback.current
    val omegaColors = OmegaTheme.colors

    val isLockerSet by lockerViewModel.isLockerSet.collectAsState()
    val downloadedVideos by downloadsViewModel.downloadedVideos.collectAsState()
    val lockerVideos by lockerViewModel.lockerVideos.collectAsState()
    val activeDownloads by mainViewModel.activeDownloads.collectAsState()
    val watchHistory by mainViewModel.watchHistory.collectAsState()
    val likedUrls by mainViewModel.likedUrls.collectAsState()
    val selectedTabIndex by mainViewModel.lastActiveTab.collectAsState()

    val activeVideoSession = mainViewModel.activeVideoSession
    val isVideoPlayerExpanded = mainViewModel.isVideoPlayerExpanded
    val selectedMovie = moviesViewModel.selectedMovie

    LaunchedEffect(mainViewModel.resumeToastMessage) {
        val msg = mainViewModel.resumeToastMessage ?: return@LaunchedEffect
        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
        mainViewModel.consumeResumeToast()
    }

    LaunchedEffect(selectedTabIndex) {
        keyboardController?.hide()
    }

    var showPinSetup by remember { mutableStateOf(false) }
    var pendingVideoToLock by remember { mutableStateOf<DownloadedVideo?>(null) }

    // Audio Player State
    var activeAudioTrack by remember { mutableStateOf<DownloadedVideo?>(null) }
    var audioPlaylist by remember { mutableStateOf<List<DownloadedVideo>>(emptyList()) }
    var isAudioPlaying by remember { mutableStateOf(false) }
    var audioPosition by remember { mutableLongStateOf(0L) }
    var audioDuration by remember { mutableLongStateOf(0L) }
    var audioRepeatMode by remember { mutableIntStateOf(Player.REPEAT_MODE_OFF) }
    var isAudioSheetExpanded by remember { mutableStateOf(false) }
    var mediaController by remember { mutableStateOf<MediaController?>(null) }

    BackHandler(enabled = isAudioSheetExpanded) {
        isAudioSheetExpanded = false
    }

    DisposableEffect(context) {
        val sessionToken = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val future = MediaController.Builder(context, sessionToken).buildAsync()
        var boundController: MediaController? = null

        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isAudioPlaying = playing
            }

            override fun onPlaybackStateChanged(state: Int) {
                audioDuration = boundController?.duration?.coerceAtLeast(0L) ?: 0L
            }

            override fun onRepeatModeChanged(repeatMode: Int) {
                audioRepeatMode = repeatMode
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                val currentIndex = boundController?.currentMediaItemIndex ?: return
                if (audioPlaylist.isNotEmpty() && currentIndex in audioPlaylist.indices) {
                    val track = audioPlaylist[currentIndex]
                    activeAudioTrack = track
                    mainViewModel.setCurrentlyPlaying(track.localPath)
                }
            }
        }

        future.addListener({
            try {
                val controller = future.get()
                boundController = controller
                mediaController = controller
                audioRepeatMode = controller.repeatMode
                controller.addListener(listener)
            } catch (_: Exception) {}
        }, MoreExecutors.directExecutor())

        onDispose {
            boundController?.removeListener(listener)
            mediaController = null
            MediaController.releaseFuture(future)
        }
    }

    LaunchedEffect(isAudioPlaying, activeAudioTrack) {
        while (isAudioPlaying && activeAudioTrack != null) {
            audioPosition = mediaController?.currentPosition ?: 0L
            audioDuration = (mediaController?.duration ?: 0L).coerceAtLeast(0L)
            delay(500)
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                val session = mainViewModel.activeVideoSession
                if (session != null && !mainViewModel.isVideoPlayerExpanded) {
                    mediaController?.let { c ->
                        mainViewModel.saveWatchProgress(
                            session = session,
                            positionMs = c.currentPosition.coerceAtLeast(0L),
                            durationMs = c.duration.coerceAtLeast(0L)
                        )
                        if (!PlaybackService.isBackgroundPlayEnabled) {
                            c.pause()
                        }
                    }
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    fun flushCurrentVideoIfDifferent(nextHistoryId: String?) {
        val current = mainViewModel.activeVideoSession ?: return
        if (nextHistoryId != null && current.historyId == nextHistoryId) return
        mediaController?.let { c ->
            mainViewModel.saveWatchProgress(
                session = current,
                positionMs = c.currentPosition.coerceAtLeast(0L),
                durationMs = c.duration.coerceAtLeast(0L)
            )
        }
    }

    val onMediaClick: (DownloadedVideo) -> Unit = { video ->
        if (video.type == "audio") {
            flushCurrentVideoIfDifferent(null)
            if (mainViewModel.activeVideoSession != null) {
                mainViewModel.closeVideoPlayer()
            }

            activeAudioTrack = video

            val sourceList = if (video.isLocked) {
                lockerVideos.filter { it.type == "audio" && !it.isPlaylistGroup }
            } else {
                downloadedVideos.filter { it.type == "audio" && !it.isPlaylistGroup }
            }

            audioPlaylist = if (sourceList.none { it.localPath == video.localPath }) {
                listOf(video)
            } else {
                sourceList
            }

            mediaController?.let { controller ->
                val mediaItems = audioPlaylist.map { MediaItem.fromUri(it.localPath) }
                val clickedIndex = audioPlaylist.indexOfFirst { it.localPath == video.localPath }.coerceAtLeast(0)

                controller.setMediaItems(mediaItems, clickedIndex, 0L)
                controller.prepare()
                controller.playWhenReady = true
                controller.play()
                isAudioPlaying = true
                audioDuration = controller.duration.coerceAtLeast(0L)
            }
        } else {
            flushCurrentVideoIfDifferent(WatchHistoryItem.localId(video.localPath))
            activeAudioTrack = null
            isAudioSheetExpanded = false
            mainViewModel.openLocalVideo(video)
        }
    }

    LaunchedEffect(mainViewModel.pendingAudioTrack, mediaController) {
        val pending = mainViewModel.pendingAudioTrack ?: return@LaunchedEffect
        if (mediaController != null) {
            mainViewModel.consumePendingAudioTrack()
            onMediaClick(pending)
        }
    }

    val onAuthSuccess: (DownloadedVideo) -> Unit = { video ->
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        lockerViewModel.toggleLock(video) { success ->
            if (success) {
                Toast.makeText(context, if (video.isLocked) "Item Unlocked" else "Moved to Private Vault", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, if (video.isLocked) "Unlock Failed" else "Lock Failed", Toast.LENGTH_SHORT).show()
            }
        }
    }

    if (showPinSetup) {
        PinDialog(
            title = "Set Vault PIN",
            onDismiss = { showPinSetup = false },
            onConfirm = { pin, question, answer ->
                scope.launch {
                    settingsManager.setLockerPin(pin, question, answer)
                    showPinSetup = false
                    pendingVideoToLock?.let { onAuthSuccess(it) }
                    pendingVideoToLock = null
                }
            }
        )
    }

    downloadsViewModel.videoToDelete?.let { video ->
        AlertDialog(
            onDismissRequest = { downloadsViewModel.videoToDelete = null },
            icon = { Icon(imageVector = Icons.Default.Delete, contentDescription = "Delete", tint = omegaColors.danger) },
            title = { Text("WARNING!", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold) },
            text = {
                Text(
                    "\"${video.title}\"\nSelected item will be permanently deleted.\nThis action cannot be undone.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        downloadsViewModel.deleteVideo(video)
                        Toast.makeText(context, "Item Deleted", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Text("Delete", color = omegaColors.danger, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { downloadsViewModel.videoToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Layer 3 Hidden-Until-CAPTCHA WebView Sniffer
        homeViewModel.snifferUrl?.let { targetUrl ->
            WebViewSnifferOverlay(
                url = targetUrl,
                forceVisible = homeViewModel.forceSnifferVisible,
                onCaptchaDetected = { homeViewModel.onSnifferCaptchaDetected() },
                onStreamsFound = { metadata -> homeViewModel.onSnifferStreamsFound(metadata) },
                onTimeout = { homeViewModel.onSnifferTimeout() },
                onDismiss = { homeViewModel.dismissSniffer() }
            )
        }

        Scaffold(
            modifier = Modifier
                .fillMaxSize()
                .zIndex(0f),
            containerColor = Color.Transparent,
            topBar = {
                if (!isAudioSheetExpanded && !isVideoPlayerExpanded && selectedMovie == null) {
                    val topTitle = when (selectedTabIndex) {
                        0 -> "Omega Cinema"
                        1 -> "Downloader"
                        else -> "Library"
                    }

                    Surface(
                        color = omegaColors.surface,
                        shadowElevation = if (omegaColors.isDark) 0.dp else 4.dp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(
                                0.5.dp,
                                omegaColors.border,
                                RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp)
                            ),
                        shape = RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .statusBarsPadding()
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                OmegaLogo(modifier = Modifier.size(38.dp), color = omegaColors.primary)
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = topTitle,
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Black,
                                    color = omegaColors.textPrimary,
                                    letterSpacing = (-0.5).sp
                                )
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // Private Vault button
                                IconButton(
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        navController.navigate("vault")
                                    },
                                    modifier = Modifier
                                        .size(40.dp)
                                        .background(omegaColors.cardElevated, RoundedCornerShape(12.dp))
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Lock,
                                        contentDescription = "Private Vault",
                                        tint = omegaColors.secondary,
                                        modifier = Modifier.size(21.dp)
                                    )
                                }

                                // Settings button
                                IconButton(
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        navController.navigate("settings")
                                    },
                                    modifier = Modifier
                                        .size(40.dp)
                                        .background(omegaColors.cardElevated, RoundedCornerShape(12.dp))
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Settings,
                                        contentDescription = "Settings",
                                        tint = omegaColors.primary,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            },
            bottomBar = {
                if (!isAudioSheetExpanded && !isVideoPlayerExpanded && selectedMovie == null) {
                    Column {
                        if (activeVideoSession != null) {
                            MiniVideoPlayerBar(
                                session = activeVideoSession,
                                mediaController = mediaController,
                                onExpand = { mainViewModel.expandVideoPlayer() },
                                onClose = { finalPos, dur ->
                                    mainViewModel.saveWatchProgress(activeVideoSession, finalPos, dur)
                                    mediaController?.stop()
                                    mediaController?.clearMediaItems()
                                    mainViewModel.closeVideoPlayer()
                                },
                                onSaveProgress = { pos, dur ->
                                    mainViewModel.saveWatchProgress(activeVideoSession, pos, dur)
                                }
                            )
                        } else {
                            activeAudioTrack?.let { video ->
                                MiniAudioPlayerBar(
                                    video = video,
                                    isPlaying = isAudioPlaying,
                                    onPlayPauseToggle = {
                                        mediaController?.let { c ->
                                            if (c.isPlaying) c.pause() else c.play()
                                        }
                                    },
                                    onClose = {
                                        mediaController?.stop()
                                        activeAudioTrack = null
                                        isAudioPlaying = false
                                    },
                                    onExpand = {
                                        isAudioSheetExpanded = true
                                    }
                                )
                            }
                        }

                        val unselectedColor = omegaColors.textSecondary.copy(alpha = 0.65f)

                        Surface(
                            color = omegaColors.surface,
                            shadowElevation = if (omegaColors.isDark) 0.dp else 10.dp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(
                                    0.5.dp,
                                    omegaColors.border,
                                    RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
                                ),
                            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
                        ) {
                            NavigationBar(
                                containerColor = Color.Transparent,
                                contentColor = omegaColors.textPrimary,
                                tonalElevation = 0.dp
                            ) {
                                val navItems = listOf(
                                    Icons.Default.Movie to "Movies",
                                    Icons.Default.Download to "Downloader",
                                    Icons.Default.VideoLibrary to "Library"
                                )

                                navItems.forEachIndexed { index, (icon, label) ->
                                    val isSelected = selectedTabIndex == index
                                    val activeColor = omegaColors.primary
                                    val iconScale by animateFloatAsState(
                                        targetValue = if (isSelected) 1.2f else 1f,
                                        animationSpec = spring(stiffness = Spring.StiffnessLow, dampingRatio = Spring.DampingRatioMediumBouncy),
                                        label = "nav_icon_scale"
                                    )

                                    NavigationBarItem(
                                        selected = isSelected,
                                        onClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            mainViewModel.setLastActiveTab(index)
                                        },
                                        icon = {
                                            if (index == 2 && activeDownloads.isNotEmpty()) {
                                                BadgedBox(
                                                    badge = {
                                                        Badge(containerColor = omegaColors.primary, contentColor = Color.White) {
                                                            Text(activeDownloads.size.toString(), fontSize = 10.sp)
                                                        }
                                                    }
                                                ) {
                                                    Icon(
                                                        imageVector = icon,
                                                        contentDescription = label,
                                                        modifier = Modifier
                                                            .size(27.dp)
                                                            .graphicsLayer { scaleX = iconScale; scaleY = iconScale }
                                                    )
                                                }
                                            } else {
                                                Icon(
                                                    imageVector = icon,
                                                    contentDescription = label,
                                                    modifier = Modifier
                                                        .size(27.dp)
                                                        .graphicsLayer { scaleX = iconScale; scaleY = iconScale }
                                                )
                                            }
                                        },
                                        label = {
                                            Text(
                                                text = label,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                modifier = Modifier.graphicsLayer { alpha = if (isSelected) 1f else 0.8f }
                                            )
                                        },
                                        colors = NavigationBarItemDefaults.colors(
                                            selectedIconColor = activeColor,
                                            unselectedIconColor = unselectedColor,
                                            selectedTextColor = activeColor,
                                            unselectedTextColor = unselectedColor,
                                            indicatorColor = Color.Transparent
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = innerPadding.calculateTopPadding())
            ) {
                Crossfade(
                    targetState = selectedTabIndex,
                    animationSpec = tween(220),
                    label = "main_tab_crossfade"
                ) { tab ->
                    when (tab) {
                        0 -> MoviesScreen(
                            viewModel = moviesViewModel,
                            watchHistory = watchHistory,
                            likedUrls = likedUrls,
                            resumingHistoryId = mainViewModel.resumingHistoryId,
                            imageLoader = imageLoader,
                            onMovieClick = { movie ->
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                moviesViewModel.openMovieDetail(movie)
                            },
                            onToggleLike = { movie ->
                                mainViewModel.toggleLike(movie)
                            },
                            onResumeHistoryItem = { item ->
                                flushCurrentVideoIfDifferent(item.id)
                                activeAudioTrack = null
                                isAudioSheetExpanded = false
                                mainViewModel.resumeWatchHistoryItem(item)
                            }
                        )

                        1 -> DownloaderScreen(
                            viewModel = homeViewModel,
                            likedUrls = likedUrls,
                            imageLoader = imageLoader,
                            onToggleLikeResult = { result -> mainViewModel.toggleLike(result) },
                            onToggleLikeMetadata = { metadata -> mainViewModel.toggleLikeMetadata(metadata) },
                            onStreamClick = { sessionKey ->
                                flushCurrentVideoIfDifferent(null)
                                activeAudioTrack = null
                                isAudioSheetExpanded = false
                                mainViewModel.openOnlineStream(sessionKey)
                            },
                            onDownloadStarted = {
                                downloadsViewModel.selectedTab = "Queue"
                                mainViewModel.setLastActiveTab(2)
                            }
                        )

                        else -> LibraryScreen(
                            viewModel = downloadsViewModel,
                            mainViewModel = mainViewModel,
                            imageLoader = imageLoader,
                            onVideoClick = onMediaClick,
                            onLockClick = { video ->
                                if (!isLockerSet) {
                                    pendingVideoToLock = video
                                    showPinSetup = true
                                } else {
                                    onAuthSuccess(video)
                                }
                            },
                            onResumeHistoryItem = { item ->
                                flushCurrentVideoIfDifferent(item.id)
                                activeAudioTrack = null
                                isAudioSheetExpanded = false
                                mainViewModel.resumeWatchHistoryItem(item)
                            },
                            onOpenWatchlistItem = { liked ->
                                moviesViewModel.openLikedMovie(liked)
                                mainViewModel.setLastActiveTab(0)
                            },
                            sharedTransitionScope = sharedTransitionScope
                        )
                    }
                }

                // Expanded Full-Screen Audio Player Overlay
                AnimatedVisibility(
                    visible = isAudioSheetExpanded && activeAudioTrack != null,
                    enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                    exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
                ) {
                    activeAudioTrack?.let { video ->
                        ExpandedAudioPlayerSheet(
                            video = video,
                            isPlaying = isAudioPlaying,
                            currentPosition = audioPosition,
                            duration = audioDuration,
                            repeatMode = audioRepeatMode,
                            onPlayPauseToggle = {
                                mediaController?.let { c ->
                                    if (c.isPlaying) c.pause() else c.play()
                                }
                            },
                            onSeekTo = { pos ->
                                mediaController?.seekTo(pos)
                                audioPosition = pos
                            },
                            onSkipPrevious = {
                                mediaController?.let { c ->
                                    if (c.currentPosition > 3000L) {
                                        c.seekTo(0L)
                                    } else if (c.hasPreviousMediaItem()) {
                                        c.seekToPreviousMediaItem()
                                    } else if (audioPlaylist.isNotEmpty()) {
                                        c.seekTo(audioPlaylist.lastIndex, 0L)
                                    }
                                    audioPosition = c.currentPosition
                                }
                            },
                            onSkipNext = {
                                mediaController?.let { c ->
                                    if (c.hasNextMediaItem()) {
                                        c.seekToNextMediaItem()
                                    } else if (audioPlaylist.isNotEmpty()) {
                                        c.seekTo(0, 0L)
                                    }
                                    audioPosition = c.currentPosition
                                }
                            },
                            onSkipBackward = {
                                mediaController?.let { c ->
                                    val newPos = (c.currentPosition - 10000L).coerceAtLeast(0L)
                                    c.seekTo(newPos)
                                    audioPosition = newPos
                                }
                            },
                            onSkipForward = {
                                mediaController?.let { c ->
                                    val newPos = (c.currentPosition + 10000L).coerceAtMost(audioDuration)
                                    c.seekTo(newPos)
                                    audioPosition = newPos
                                }
                            },
                            onToggleRepeat = {
                                mediaController?.let { controller ->
                                    val nextMode = when (controller.repeatMode) {
                                        Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ONE
                                        Player.REPEAT_MODE_ONE -> Player.REPEAT_MODE_ALL
                                        else -> Player.REPEAT_MODE_OFF
                                    }
                                    controller.repeatMode = nextMode
                                    audioRepeatMode = nextMode
                                }
                            },
                            onDismiss = {
                                isAudioSheetExpanded = false
                            }
                        )
                    }
                }
            }
        }

        // Full-Screen Movie Detail Overlay
        AnimatedVisibility(
            visible = selectedMovie != null,
            enter = slideInVertically(initialOffsetY = { it / 5 }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it / 5 }) + fadeOut(),
            modifier = Modifier
                .fillMaxSize()
                .zIndex(10f)
        ) {
            selectedMovie?.let { movie ->
                MovieDetailScreen(
                    movie = movie,
                    viewModel = moviesViewModel,
                    watchHistory = watchHistory,
                    isLiked = movie.pageUrl.trim() in likedUrls,
                    imageLoader = imageLoader,
                    onToggleLike = { mainViewModel.toggleLike(movie) },
                    onStreamClick = { sessionKey ->
                        flushCurrentVideoIfDifferent(null)
                        activeAudioTrack = null
                        isAudioSheetExpanded = false
                        mainViewModel.openOnlineStream(sessionKey)
                    },
                    onDownloadStarted = {
                        moviesViewModel.closeMovieDetail()
                        downloadsViewModel.selectedTab = "Queue"
                        mainViewModel.setLastActiveTab(2)
                    },
                    onBack = { moviesViewModel.closeMovieDetail() }
                )
            }
        }

        // Full-Screen Video Player Overlay (Back button collapses smoothly into MiniVideoPlayerBar)
        if (isVideoPlayerExpanded && activeVideoSession != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(20f)
                    .background(Color.Black)
            ) {
                VideoPlayer(
                    videoUrl = activeVideoSession.videoUrl,
                    titleOverride = activeVideoSession.title,
                    initialPositionMs = activeVideoSession.initialPositionMs,
                    sharedController = mediaController,
                    settingsManager = settingsManager,
                    videoRepository = videoRepository,
                    downloadRepository = downloadRepository,
                    onProgressSave = { pos, dur, streamUrl, qualityLabel ->
                        mainViewModel.saveWatchProgress(
                            session = activeVideoSession,
                            positionMs = pos,
                            durationMs = dur,
                            lastStreamUrl = streamUrl,
                            qualityLabel = qualityLabel
                        )
                    },
                    onBack = {
                        if (activeVideoSession.isLocked) {
                            mediaController?.stop()
                            mediaController?.clearMediaItems()
                            mainViewModel.closeVideoPlayer()
                        } else {
                            mainViewModel.minimizeVideoPlayer()
                        }
                    }
                )
            }
        }
    }
}
