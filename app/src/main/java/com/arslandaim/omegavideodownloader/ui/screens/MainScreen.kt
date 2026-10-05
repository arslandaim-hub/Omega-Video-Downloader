/*
 * Omega Video Downloader Project Original (2026)
 * arslandaim-hub (GitHub.com/arslandaim-hub)
 * Licenced Under GPL-3.0+
*/
package com.arslandaim.omegavideodownloader.ui.screens

import android.app.DownloadManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.navigation.NavController
import coil.ImageLoader
import com.arslandaim.omegavideodownloader.R
import com.arslandaim.omegavideodownloader.*
import com.arslandaim.omegavideodownloader.ui.*
import com.arslandaim.omegavideodownloader.ui.components.ExpandedAudioPlayerSheet
import com.arslandaim.omegavideodownloader.ui.components.MiniAudioPlayerBar
import com.arslandaim.omegavideodownloader.ui.components.OmegaLogo
import com.arslandaim.omegavideodownloader.ui.components.PinDialog
import com.arslandaim.omegavideodownloader.ui.theme.glassBackground
import com.arslandaim.omegavideodownloader.ui.theme.glassBorder
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class, ExperimentalSharedTransitionApi::class)
@Composable
fun MainScreen(
    navController: NavController,
    mainViewModel: MainViewModel,
    homeViewModel: HomeViewModel,
    downloadsViewModel: DownloadsViewModel,
    lockerViewModel: LockerViewModel,
    settingsManager: SettingsManager,
    imageLoader: ImageLoader,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope
) {
    val context = LocalContext.current
    val activity = context as AppCompatActivity
    val downloadManager = remember { context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager }
    val scope = rememberCoroutineScope()
    val lockerManager = remember { LockerManager(context) }
    val keyboardController = LocalSoftwareKeyboardController.current
    val haptic = LocalHapticFeedback.current

    val isLockerSet by lockerViewModel.isLockerSet.collectAsState()
    val downloadedVideos by downloadsViewModel.downloadedVideos.collectAsState()
    val lockerVideos by lockerViewModel.lockerVideos.collectAsState()

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
        future.addListener({
            try {
                val controller = future.get()
                mediaController = controller
                audioRepeatMode = controller.repeatMode
                controller.addListener(object : Player.Listener {
                    override fun onIsPlayingChanged(playing: Boolean) {
                        isAudioPlaying = playing
                    }
                    override fun onPlaybackStateChanged(state: Int) {
                        audioDuration = controller.duration.coerceAtLeast(0L)
                    }
                    override fun onRepeatModeChanged(repeatMode: Int) {
                        audioRepeatMode = repeatMode
                    }
                    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                        val currentIndex = controller.currentMediaItemIndex
                        if (audioPlaylist.isNotEmpty() && currentIndex in audioPlaylist.indices) {
                            val track = audioPlaylist[currentIndex]
                            activeAudioTrack = track
                            mainViewModel.setCurrentlyPlaying(track.localPath)
                        }
                    }
                })
            } catch (_: Exception) {}
        }, MoreExecutors.directExecutor())

        onDispose {}
    }

    LaunchedEffect(isAudioPlaying, activeAudioTrack) {
        while (isAudioPlaying && activeAudioTrack != null) {
            audioPosition = mediaController?.currentPosition ?: 0L
            audioDuration = (mediaController?.duration ?: 0L).coerceAtLeast(0L)
            delay(500)
        }
    }

    val onMediaClick: (DownloadedVideo) -> Unit = { video ->
        if (video.type == "audio") {
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
            val encodedUrl = URLEncoder.encode(video.localPath, StandardCharsets.UTF_8.toString())
            navController.navigate("player/$encodedUrl")
        }
    }
    
    val onAuthSuccess: (DownloadedVideo) -> Unit = { video ->
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        lockerViewModel.toggleLock(video) { success ->
            if (success) {
                Toast.makeText(context, if (video.isLocked) "Video Unlocked" else "Video Locked & Hidden", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, if (video.isLocked) "Unlock Failed" else "Lock Failed", Toast.LENGTH_SHORT).show()
            }
        }
    }

    if (showPinSetup) {
        PinDialog(
            title = "Set Locker PIN",
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
            icon = { Icon(imageVector = Icons.Default.Delete, contentDescription = "Delete", tint = Color.Red) },
            title = { Text("WARNING!", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold) },
            text = { Text("\"${video.title}\"\nSelected item will be permanently deleted.\nThis action cannot be undone.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center) },
            confirmButton = {
                TextButton(
                    onClick = {
                        downloadsViewModel.deleteVideo(video)
                        Toast.makeText(context, "Item Deleted", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Text("Delete", color = Color.Red, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { downloadsViewModel.videoToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    val pagerState = rememberPagerState { 3 }
    val selectedPageIndex = pagerState.currentPage
    
    // Security: Reset authorization when leaving the locker tab
    LaunchedEffect(selectedPageIndex) {
        if (selectedPageIndex != 2) {
            lockerViewModel.resetAuth()
        }
        keyboardController?.hide()
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            if (!isAudioSheetExpanded) {
                Surface(
                    color = MaterialTheme.colorScheme.glassBackground,
                    modifier = Modifier
                        .statusBarsPadding()
                        .fillMaxWidth()
                        .border(
                            0.5.dp, 
                            MaterialTheme.colorScheme.glassBorder, 
                            RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp)
                        ),
                    shape = RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OmegaLogo(modifier = Modifier.size(40.dp), color = Color(0xFF1877F2))
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(text = stringResource(R.string.app_name), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onSurface, letterSpacing = (-0.5).sp)
                        }

                        IconButton(
                            onClick = { 
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                navController.navigate("settings") 
                            },
                            modifier = Modifier
                                .size(40.dp)
                                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f), RoundedCornerShape(12.dp))
                        ) {
                            Icon(
                                imageVector = Icons.Default.Settings, 
                                contentDescription = "Settings", 
                                tint = Color(0xFF1877F2), 
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }
            }
        },
        bottomBar = {
            if (!isAudioSheetExpanded) {
                Column {
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

                    Surface(
                        color = MaterialTheme.colorScheme.glassBackground,
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(
                                0.5.dp, 
                                MaterialTheme.colorScheme.glassBorder, 
                                RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
                            ),
                        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
                    ) {
                        NavigationBar(containerColor = Color.Transparent, contentColor = MaterialTheme.colorScheme.onSurface, tonalElevation = 0.dp) {
                            val navIcons = listOf(
                                Icons.Default.Home to "Home",
                                Icons.Default.History to "Downloads",
                                (if (selectedPageIndex == 2) Icons.Default.LockOpen else Icons.Default.Lock) to "Locker"
                            )
                            
                            navIcons.forEachIndexed { index, iconData ->
                                val (icon, label) = iconData
                                val isSelected = selectedPageIndex == index
                                val activeColor = if (index == 2) Color(0xFFFF5722) else Color(0xFF1877F2)
                                val iconScale by animateFloatAsState(targetValue = if (isSelected) 1.25f else 1f, animationSpec = spring(stiffness = Spring.StiffnessLow, dampingRatio = Spring.DampingRatioMediumBouncy))
                                
                                NavigationBarItem(
                                    selected = isSelected,
                                    onClick = { 
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        scope.launch { pagerState.animateScrollToPage(index) } 
                                    },
                                    icon = { Icon(imageVector = icon, contentDescription = label, modifier = Modifier.size(29.dp).graphicsLayer { scaleX = iconScale; scaleY = iconScale }) },
                                    label = { Text(text = label, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium, modifier = Modifier.graphicsLayer { alpha = if (isSelected) 1f else 0.8f }) },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = activeColor,
                                        unselectedIconColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                                        selectedTextColor = activeColor,
                                        unselectedTextColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
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
        Box(modifier = Modifier.fillMaxSize()) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize().padding(top = innerPadding.calculateTopPadding()),
                beyondViewportPageCount = 1
            ) { page ->
                val pageOffset = ((pagerState.currentPage - page) + pagerState.currentPageOffsetFraction)
                val absOffset = abs(pageOffset)
                
                Box(
                    modifier = Modifier.fillMaxSize().graphicsLayer {
                        val scale = 0.92f + (1f - 0.92f) * (1f - absOffset.coerceIn(0f, 1f))
                        scaleX = scale
                        scaleY = scale
                        alpha = 1f - absOffset.coerceIn(0f, 1f)
                        clip = true
                        shape = RoundedCornerShape(16.dp * absOffset.coerceIn(0f, 1f))
                    }
                ) {
                    when (page) {
                        0 -> HomeScreen(viewModel = homeViewModel) { type ->
                            scope.launch {
                                downloadsViewModel.selectedTab = if (type == "audio") "Music" else "Videos"
                                pagerState.animateScrollToPage(1)
                            }
                        }
                        1 -> DownloadsScreen(
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
                            sharedTransitionScope = sharedTransitionScope,
                            animatedVisibilityScope = animatedVisibilityScope
                        )
                        2 -> {
                            LockerScreen(
                                viewModel = lockerViewModel,
                                mainViewModel = mainViewModel,
                                imageLoader = imageLoader,
                                lockerManager = lockerManager,
                                activity = activity,
                                onCancel = { scope.launch { pagerState.animateScrollToPage(0) } },
                                onVideoClick = onMediaClick,
                                onDeleteVideo = { video -> downloadsViewModel.videoToDelete = video },
                                sharedTransitionScope = sharedTransitionScope,
                                animatedVisibilityScope = animatedVisibilityScope
                            )
                        }
                    }
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
}
