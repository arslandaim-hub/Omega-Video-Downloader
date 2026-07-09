/*
 * Omega Video Downloader Project Original (2026)
 * arslandaim-hub (GitHub.com/arslandaim-hub)
 * Licenced Under GPL-3.0+
*/
package com.arslandaim.omegavideodownloader
// Developed by Arslan Daim Shar

import android.Manifest
import android.app.DownloadManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.clickable
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.decode.VideoFrameDecoder
import coil.disk.DiskCache
import coil.memory.MemoryCache
import coil.request.CachePolicy
import coil.request.videoFrameMillis
import com.arslandaim.omegavideodownloader.ui.theme.OmegaVideoDownloaderTheme
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.*
import java.io.File
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.Locale
import kotlin.time.Duration.Companion.seconds

// API Response Models


class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        OmegaCrashHandler.initialize(applicationContext)
        YtDlpManager.init(applicationContext)
        val settingsManager = SettingsManager(this)
        val downloadManager = getSystemService(DOWNLOAD_SERVICE) as DownloadManager
        enableEdgeToEdge()

        // Track foreground state for strict notification visibility
        lifecycle.addObserver(LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> {
                    PlaybackService.isAppInForeground = true
                }
                Lifecycle.Event.ON_STOP -> {
                    PlaybackService.isAppInForeground = false
                }
                else -> {}
            }
        })
        
        // Optimized Global Download Monitoring Loop - Offloaded to IO Dispatcher
        lifecycleScope.launch(Dispatchers.IO) {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (true) {
                    val query = DownloadManager.Query()
                    downloadManager.query(query)?.use { cursor ->
                        val idCol = cursor.getColumnIndex(DownloadManager.COLUMN_ID)
                        val statusCol = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
                        val titleCol = cursor.getColumnIndex(DownloadManager.COLUMN_TITLE)
                        val totalCol = cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
                        val downloadedCol = cursor.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
                        val descCol = cursor.getColumnIndex(DownloadManager.COLUMN_DESCRIPTION)

                        val activeList = mutableListOf<ActiveDownload>()
                        while (cursor.moveToNext()) {
                            val status = cursor.getInt(statusCol)
                            if ((status == DownloadManager.STATUS_RUNNING) || (status == DownloadManager.STATUS_PENDING) || (status == DownloadManager.STATUS_PAUSED)) {
                                val id = cursor.getLong(idCol)
                                val title = cursor.getString(titleCol) ?: "Video"
                                val total = cursor.getLong(totalCol)
                                val downloaded = cursor.getLong(downloadedCol)
                                val progress = if (total > 0) downloaded.toFloat() / total else 0f
                                val desc = cursor.getString(descCol) ?: ""
                                val type = if (desc.contains("audio", ignoreCase = true)) "audio" else "video"

                                activeList.add(
                                    ActiveDownload(
                                        id = id,
                                        title = title,
                                        progress = progress,
                                        totalBytes = total,
                                        downloadedBytes = downloaded,
                                        status = when (status) {
                                            DownloadManager.STATUS_PENDING -> "Pending"
                                            DownloadManager.STATUS_PAUSED -> "Paused"
                                            else -> "Downloading"
                                        },
                                        thumbnailUrl = null,
                                        type = type
                                    )
                                )
                            }
                        }
                        withContext(Dispatchers.Main) {
                            settingsManager.updateActiveDownloads(activeList)
                        }
                    }
                    delay(1.seconds)
                }
            }
        }

        setContent {
            val themeMode by settingsManager.themeMode.collectAsState(initial = "System")
            val language by settingsManager.appLanguage.collectAsState(initial = "English (US)")
            
            val isDarkTheme = when (themeMode) {
                "Dark" -> true
                "Light" -> false
                else -> androidx.compose.foundation.isSystemInDarkTheme()
            }
            
            LaunchedEffect(isDarkTheme) {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(
                        android.graphics.Color.TRANSPARENT,
                        android.graphics.Color.TRANSPARENT,
                    ) { isDarkTheme },
                    navigationBarStyle = SystemBarStyle.auto(
                        android.graphics.Color.TRANSPARENT,
                        android.graphics.Color.TRANSPARENT,
                    ) { isDarkTheme },
                )
            }

            // Modern Language update logic using AppCompatDelegate
            LaunchedEffect(language) {
                val localeTag = if (language == "Arabic") "ar" else "en-US"
                val appLocales = LocaleListCompat.forLanguageTags(localeTag)
                AppCompatDelegate.setApplicationLocales(appLocales)
            }

            OmegaVideoDownloaderTheme(themeMode = themeMode) {
                val navController = rememberNavController()

                // Global Playback Monitor
                val context = LocalContext.current
                DisposableEffect(Unit) {
                    val sessionToken = SessionToken(context, ComponentName(context, PlaybackService::class.java))
                    val controllerFuture = MediaController.Builder(context, sessionToken).buildAsync()
                    var controller: MediaController? = null
                    
                    val listener = object : Player.Listener {
                        override fun onIsPlayingChanged(isPlaying: Boolean) {
                            controller?.let { c ->
                                val path = if (isPlaying) c.currentMediaItem?.localConfiguration?.uri?.toString() else null
                                settingsManager.setCurrentlyPlaying(path)
                            }
                        }
                        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                            controller?.let { c ->
                                val path = if (c.isPlaying) mediaItem?.localConfiguration?.uri?.toString() else null
                                settingsManager.setCurrentlyPlaying(path)
                            }
                        }
                    }

                    controllerFuture.addListener({
                        try {
                            val c = controllerFuture.get()
                            controller = c
                            c.addListener(listener)
                            // Initial Sync
                            if (c.isPlaying) {
                                settingsManager.setCurrentlyPlaying(c.currentMediaItem?.localConfiguration?.uri?.toString())
                            }
                        } catch (_: Exception) {}
                    }, MoreExecutors.directExecutor())

                    onDispose {
                        controller?.removeListener(listener)
                        MediaController.releaseFuture(controllerFuture)
                    }
                }

                // Crash Detection Logic
                var crashReport by remember { mutableStateOf<String?>(null) }

                LaunchedEffect(Unit) {
                    crashReport = OmegaCrashHandler.getCrashReport(context)
                }

                if (crashReport != null) {
                    AlertDialog(
                        onDismissRequest = {
                            OmegaCrashHandler.clearCrashReport(context)
                            crashReport = null
                        },
                        title = { Text("App Crash Report") },
                        text = { Text("The app closed unexpectedly last session. Would you like to share an anonymous error log to help improve the app?") },
                        confirmButton = {
                            TextButton(onClick = {
                                val intent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_SUBJECT, "Omega Crash Report")
                                    putExtra(Intent.EXTRA_TEXT, crashReport)
                                }
                                context.startActivity(Intent.createChooser(intent, "Share Log"))
                                OmegaCrashHandler.clearCrashReport(context)
                                crashReport = null
                            }) {
                                Text("Share Log")
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = {
                                OmegaCrashHandler.clearCrashReport(context)
                                crashReport = null
                            }) {
                                Text("Dismiss")
                            }
                        }
                    )
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            brush = Brush.verticalGradient(
                                colors = if (isDarkTheme) {
                                    listOf(Color(0xFF161823), Color.Black)
                                } else {
                                    listOf(Color(0xFFF0F2F5), Color(0xFFE3E6EA))
                                }
                            )
                        )
                ) {
                    SharedTransitionLayout {
                        val navTween = tween<IntOffset>(400, easing = FastOutSlowInEasing)
                        val fadeTween = tween<Float>(300, easing = LinearOutSlowInEasing)
                        val scaleTween = tween<Float>(400, easing = FastOutSlowInEasing)
                        
                        NavHost(
                            navController = navController,
                            startDestination = "home",
                            enterTransition = { fadeIn(fadeTween) + scaleIn(initialScale = 0.95f, animationSpec = scaleTween) },
                            exitTransition = { fadeOut(fadeTween) + scaleOut(targetScale = 0.95f, animationSpec = scaleTween) },
                            popEnterTransition = { fadeIn(fadeTween) + scaleIn(initialScale = 0.95f, animationSpec = scaleTween) },
                            popExitTransition = { fadeOut(fadeTween) + scaleOut(targetScale = 0.95f, animationSpec = scaleTween) }
                        ) {
                            composable("home") {
                                MainScreen(navController, settingsManager, this@SharedTransitionLayout, this)
                            }
                            composable(
                                "player/{videoUrl}",
                                enterTransition = { 
                                    scaleIn(tween(500, easing = FastOutSlowInEasing), initialScale = 0.8f) + 
                                    fadeIn(tween(400)) 
                                },
                                exitTransition = { 
                                    scaleOut(tween(500, easing = FastOutSlowInEasing), targetScale = 0.8f) + 
                                    fadeOut(tween(400)) 
                                }
                            ) { backStackEntry ->
                                val videoUrl = backStackEntry.arguments?.getString("videoUrl") ?: ""
                                VideoPlayerScreen(
                                    videoUrl = videoUrl,
                                    settingsManager = settingsManager,
                                    sharedTransitionScope = this@SharedTransitionLayout,
                                    animatedVisibilityScope = this,
                                    onBack = { navController.popBackStack() }
                                )
                            }
                            composable(
                                "settings",
                                enterTransition = { slideInHorizontally(initialOffsetX = { it }, animationSpec = navTween) + fadeIn(fadeTween) + scaleIn(initialScale = 0.92f, animationSpec = scaleTween) },
                                exitTransition = { slideOutHorizontally(targetOffsetX = { -it }, animationSpec = navTween) + fadeOut(fadeTween) + scaleOut(targetScale = 0.92f, animationSpec = scaleTween) },
                                popEnterTransition = { slideInHorizontally(initialOffsetX = { -it }, animationSpec = navTween) + fadeIn(fadeTween) + scaleIn(initialScale = 0.92f, animationSpec = scaleTween) },
                                popExitTransition = { slideOutHorizontally(targetOffsetX = { it }, animationSpec = navTween) + fadeOut(fadeTween) + scaleOut(targetScale = 0.92f, animationSpec = scaleTween) }
                            ) {
                                SettingsScreen(settingsManager) { navController.popBackStack() }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun OmegaLogo(modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.primary) {
    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height
        val strokeWidth = width * 0.15f
        
        // Stylish modern Omega symbol (Ω)
        // Draw the main horseshoe curve
        drawArc(
            color = color,
            startAngle = 140f,
            sweepAngle = 260f,
            useCenter = false,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
            topLeft = androidx.compose.ui.geometry.Offset(width * 0.1f, height * 0.05f),
            size = androidx.compose.ui.geometry.Size(width * 0.8f, height * 0.8f)
        )
        
        // Draw the two horizontal "feet" of the Ω
        // Left foot
        drawLine(
            color = color,
            start = androidx.compose.ui.geometry.Offset(width * 0.05f, height * 0.85f),
            end = androidx.compose.ui.geometry.Offset(width * 0.35f, height * 0.85f),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round
        )
        
        // Right foot
        drawLine(
            color = color,
            start = androidx.compose.ui.geometry.Offset(width * 0.65f, height * 0.85f),
            end = androidx.compose.ui.geometry.Offset(width * 0.95f, height * 0.85f),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round
        )

        // Download Arrow Symbol
        val arrowStrokeWidth = strokeWidth * 0.8f
        // Arrow stem
        drawLine(
            color = color,
            start = androidx.compose.ui.geometry.Offset(width * 0.5f, height * 0.25f),
            end = androidx.compose.ui.geometry.Offset(width * 0.5f, height * 0.62f),
            strokeWidth = arrowStrokeWidth,
            cap = StrokeCap.Round
        )
        // Arrowhead (Left part)
        drawLine(
            color = color,
            start = androidx.compose.ui.geometry.Offset(width * 0.35f, height * 0.48f),
            end = androidx.compose.ui.geometry.Offset(width * 0.5f, height * 0.62f),
            strokeWidth = arrowStrokeWidth,
            cap = StrokeCap.Round
        )
        // Arrowhead (Right part)
        drawLine(
            color = color,
            start = androidx.compose.ui.geometry.Offset(width * 0.65f, height * 0.48f),
            end = androidx.compose.ui.geometry.Offset(width * 0.5f, height * 0.62f),
            strokeWidth = arrowStrokeWidth,
            cap = StrokeCap.Round
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalSharedTransitionApi::class)
@Composable
fun MainScreen(
    navController: androidx.navigation.NavController,
    settingsManager: SettingsManager,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope
) {
    val context = LocalContext.current
    val activity = context as AppCompatActivity
    val downloadManager = remember { context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager }
    val scope = rememberCoroutineScope()
    val downloadedVideos by settingsManager.downloadedVideos.collectAsState(initial = emptyList())
    val lockerManager = remember { LockerManager(context) }
    val keyboardController = LocalSoftwareKeyboardController.current

    var showPinSetup by remember { mutableStateOf(false) }
    var pendingVideoToLock by remember { mutableStateOf<DownloadedVideo?>(null) }
    var videoToDelete by remember { mutableStateOf<DownloadedVideo?>(null) }
    
    val isLockerSet by settingsManager.isLockerSet.collectAsState(initial = false)

    val haptic = LocalHapticFeedback.current

    val onAuthSuccess: (DownloadedVideo) -> Unit = { video ->
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        if (video.isLocked) {
            scope.launch {
                val newPath = lockerManager.unlockFile(video)
                if (newPath != null) {
                    settingsManager.updateVideoLockStatus(video, false, newPath)
                    Toast.makeText(context, "Video Unlocked", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "Unlock Failed", Toast.LENGTH_SHORT).show()
                }
            }
        } else {
            scope.launch {
                val newPath = lockerManager.lockFile(video)
                if (newPath != null) {
                    settingsManager.updateVideoLockStatus(video, true, newPath)
                    Toast.makeText(context, "Video Locked & Hidden", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "Lock Failed", Toast.LENGTH_SHORT).show()
                }
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

    videoToDelete?.let { video ->
        AlertDialog(
            onDismissRequest = { videoToDelete = null },
            icon = {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete",
                    // Uses the standard red/destructive color for the warning icon
                    tint = Color.Red
                )
            },
            title = { Text("WARNING!", style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold) },
            text = {Text("\"${video.title}\"\nSelected item will be permanently deleted.\nThis action cannot be undone.",style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center) },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            settingsManager.removeDownloadedVideo(video)
                            // Physically delete the file from localPath
                            try {
                                val file = File(video.localPath)
                                if (file.exists()) file.delete()

                                // Also handle content URIs
                                val uri = Uri.parse(video.localPath)
                                if (uri.scheme == "content") {
                                    context.contentResolver.delete(uri, null, null)
                                }
                            } catch (_: Exception) {}
                            
                            videoToDelete = null
                            Toast.makeText(context, "Item Deleted", Toast.LENGTH_SHORT).show()
                        }
                    }
                ) {
                    Text("Delete", color = Color.Red, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { videoToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    val imageLoader = remember {
        ImageLoader.Builder(context)
            .components { add(VideoFrameDecoder.Factory()) }
            .memoryCache {
                MemoryCache.Builder(context)
                    .maxSizePercent(0.25) // Use 25% of available RAM
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(context.cacheDir.resolve("thumbnail_cache"))
                    .maxSizeBytes(100 * 1024 * 1024) // 100 MB limit
                    .build()
            }
            // Optimization: Prefer local cache over re-decoding for performance
            .diskCachePolicy(CachePolicy.ENABLED)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .crossfade(true) // Smoother transition from placeholder to image
            .build()
    }

    val pagerState = rememberPagerState { 3 }
    val selectedPageIndex = pagerState.currentPage
    
    var downloadsTab by rememberSaveable { mutableStateOf("Videos") }
    var isLockerAuthorized by rememberSaveable { mutableStateOf(false) }

    // Security: Reset authorization when leaving the locker tab
    LaunchedEffect(selectedPageIndex) {
        if (selectedPageIndex != 2) {
            isLockerAuthorized = false
        }
        // UI: Hide keyboard when switching tabs
        keyboardController?.hide()
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            Surface(
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                modifier = Modifier
                    .statusBarsPadding()
                    .fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OmegaLogo(
                            modifier = Modifier.size(40.dp),
                            color = Color(0xFF1877F2)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = stringResource(R.string.app_name),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.onSurface,
                            letterSpacing = (-0.5).sp
                        )
                    }

                    IconButton(
                        onClick = { 
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            navController.navigate("settings") 
                        },
                        modifier = Modifier
                            .size(40.dp)
                            .background(
                                color = Color(0xFF006699),
                                shape = RoundedCornerShape(12.dp)
                            )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        },
        bottomBar = {
            Surface(
                // Use a more substantial semi-transparent color for a "glass" effect
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                modifier = Modifier.fillMaxWidth()
            ) {
                NavigationBar(
                    containerColor = Color.Transparent,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    tonalElevation = 0.dp
                ) {
                    val navIcons = listOf(
                        Icons.Default.Home to "Home",
                        Icons.Default.History to "Downloads",
                        (if (selectedPageIndex == 2) Icons.Default.LockOpen else Icons.Default.Lock) to "Locker"
                    )
                    
                    navIcons.forEachIndexed { index, iconData ->
                        val (icon, label) = iconData
                        val isSelected = selectedPageIndex == index
                        val iconScale by animateFloatAsState(
                            targetValue = if (isSelected) 1.25f else 1f,
                            animationSpec = spring(stiffness = Spring.StiffnessLow, dampingRatio = Spring.DampingRatioMediumBouncy),
                            label = "nav_icon_scale"
                        )
                        
                        NavigationBarItem(
                            selected = isSelected,
                            onClick = { 
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                scope.launch { pagerState.animateScrollToPage(index) } 
                            },
                            icon = { 
                                Icon(
                                    imageVector = icon, 
                                    contentDescription = label,
                                    modifier = Modifier
                                        .size(29.dp)
                                        .graphicsLayer {
                                            scaleX = iconScale
                                            scaleY = iconScale
                                        }
                                ) 
                            },
                            label = { 
                                Text(
                                    text = label,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    modifier = Modifier.graphicsLayer {
                                        alpha = if (isSelected) 1f else 0.8f
                                    }
                                ) 
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = if (index == 2) Color(0xFFFF5722) else Color(0xFF1877F2),
                                unselectedIconColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                                selectedTextColor = MaterialTheme.colorScheme.onSurface,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                                indicatorColor = Color.Transparent
                            )
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxSize()
                // Only pad the top to allow content to flow under the semi-transparent bottom bar
                .padding(top = innerPadding.calculateTopPadding()),
            beyondViewportPageCount = 1 // Pre-loads adjacent pages for zero-latency switching
        ) { page ->
            // High-performance silky smooth transformation logic
            val pageOffset = ((pagerState.currentPage - page) + pagerState.currentPageOffsetFraction)
            val absOffset = kotlin.math.abs(pageOffset)
            
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        // Smoothly scale down and fade out pages as they move away
                        val scale = 0.92f + (1f - 0.92f) * (1f - absOffset.coerceIn(0f, 1f))
                        scaleX = scale
                        scaleY = scale
                        alpha = 1f - absOffset.coerceIn(0f, 1f)
                        
                        // Hardware acceleration for the entire page transition
                        clip = true
                        shape = RoundedCornerShape(16.dp * absOffset.coerceIn(0f, 1f))
                    }
            ) {
                when (page) {
                0 -> HomeScreen(
                    settingsManager = settingsManager,
                ) { type ->
                    scope.launch {
                        downloadsTab = if (type == "audio") "Music" else "Videos"
                        pagerState.animateScrollToPage(1)
                    }
                }
                1 -> DownloadsScreen(
                    settingsManager = settingsManager,
                    downloadedVideos = downloadedVideos,
                    activeDownloads = settingsManager.activeDownloads.collectAsState().value,
                    imageLoader = imageLoader,
                    selectedTab = downloadsTab,
                    onTabSelected = { downloadsTab = it },
                    onVideoClick = { video ->
                        val encodedUrl = URLEncoder.encode(video.localPath, StandardCharsets.UTF_8.toString())
                        navController.navigate("player/$encodedUrl")
                    },
                    onDeleteVideo = { video -> videoToDelete = video },
                    onCancelDownload = { id -> downloadManager.remove(id) },
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
                    val lockerVideos by settingsManager.lockerVideos.collectAsState(initial = emptyList())
                    LockerScreen(
                        isAuthorized = isLockerAuthorized,
                        lockerVideos = lockerVideos,
                        imageLoader = imageLoader,
                        settingsManager = settingsManager,
                        lockerManager = lockerManager,
                        activity = activity,
                        onAuthorize = { isLockerAuthorized = true },
                        onCancel = {
                            scope.launch {
                                pagerState.animateScrollToPage(0)
                            }
                        },
                        onVideoClick = { video ->
                            val encodedUrl = URLEncoder.encode(video.localPath, StandardCharsets.UTF_8.toString())
                            navController.navigate("player/$encodedUrl")
                        },
                        onUnlockClick = { video ->
                            onAuthSuccess(video)
                        },
                        onDeleteVideo = { video -> videoToDelete = video },
                        sharedTransitionScope = sharedTransitionScope,
                        animatedVisibilityScope = animatedVisibilityScope
                    )
                }
            }
        }
    }
}
}

@Composable
fun PinDialog(title: String, onDismiss: () -> Unit, onConfirm: (String, String, String) -> Unit) {
    var pin by remember { mutableStateOf("") }
    var securityQuestion by remember { mutableStateOf("") }
    var securityAnswer by remember { mutableStateOf("") }
    val questions = listOf(
        "What is your childhood nickname?",
        "What is your mother's name?",
        "What was your first pet's name?",
        "What city were you born in?"
    )
    var expanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = pin,
                    onValueChange = { if (it.length <= 4 && it.all { c -> c.isDigit() }) pin = it },
                    label = { Text("4-Digit PIN") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Text("Security Question", style = MaterialTheme.typography.labelMedium)
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = { expanded = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(securityQuestion.ifEmpty { "Select a Question" }, maxLines = 1)
                    }
                    DropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false },
                        modifier = Modifier.fillMaxWidth(0.8f)
                    ) {
                        questions.forEach { q ->
                            DropdownMenuItem(
                                text = { Text(q) },
                                onClick = {
                                    securityQuestion = q
                                    expanded = false
                                }
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                OutlinedTextField(
                    value = securityAnswer,
                    onValueChange = { securityAnswer = it },
                    label = { Text("Your Answer") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                
                Text(
                    "This will be used to recover your PIN if forgotten.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { if (pin.length == 4 && securityQuestion.isNotEmpty() && securityAnswer.isNotEmpty()) onConfirm(pin, securityQuestion, securityAnswer) },
                enabled = pin.length == 4 && securityQuestion.isNotEmpty() && securityAnswer.isNotEmpty()
            ) {
                Text("Confirm")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun ChangePinDialog(
    settingsManager: SettingsManager,
    onDismiss: () -> Unit,
    onSuccess: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val securityQuestion by settingsManager.securityQuestion.collectAsState(initial = "")
    var securityAnswer by remember { mutableStateOf("") }
    var newPin by remember { mutableStateOf("") }
    var isVerified by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (!isVerified) "Verify Security Question" else "Set New PIN") },
        text = {
            Column {
                if (!isVerified) {
                    Text(securityQuestion ?: "No security question set", fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = securityAnswer,
                        onValueChange = { securityAnswer = it },
                        label = { Text("Your Answer") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    OutlinedTextField(
                        value = newPin,
                        onValueChange = { if (it.length <= 4 && it.all { c -> c.isDigit() }) newPin = it },
                        label = { Text("New 4-Digit PIN") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    scope.launch {
                        if (!isVerified) {
                            if (settingsManager.verifySecurityAnswer(securityAnswer)) {
                                isVerified = true
                            } else {
                                Toast.makeText(context, "Incorrect Answer", Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            if (newPin.length == 4) {
                                settingsManager.updateLockerPin(newPin)
                                Toast.makeText(context, "PIN Updated Successfully", Toast.LENGTH_SHORT).show()
                                onSuccess()
                            }
                        }
                    }
                }
            ) {
                Text(if (!isVerified) "Verify" else "Update PIN")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun HomeScreen(settingsManager: SettingsManager, onDownloadStarted: (String) -> Unit) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val downloadManager = remember { context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager }

    var url by remember { mutableStateOf("") }
    var videoMetadata by remember { mutableStateOf<VideoMetadata?>(null) }
    var isLoading by remember { mutableStateOf(value = false) }
    var selectedQuality by remember { mutableStateOf<VideoQuality?>(null) }

    val activeDownloads by settingsManager.activeDownloads.collectAsState()
    var currentDownloadId by remember { mutableStateOf<Long?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.entries.all { it.value }
        if (allGranted) {
            selectedQuality?.let {
                currentDownloadId = startDownload(context, it.url, videoMetadata?.title ?: "Video", it.type, it.formatId)
                onDownloadStarted(it.type)
            }
        } else {
            Toast.makeText(context, "Permissions required to download and play videos", Toast.LENGTH_SHORT).show()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.weight(0.1f))

        Surface(
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f),
            shape = RoundedCornerShape(28.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(0.5.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f), RoundedCornerShape(28.dp))
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "Video Link",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.align(Alignment.Start).padding(bottom = 12.dp, start = 4.dp)
                )

                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    placeholder = { Text("Paste URL here...", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f),
                        unfocusedContainerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f),
                        unfocusedBorderColor = Color.Transparent,
                        focusedBorderColor = Color(0xFF1877F2).copy(alpha = 0.5f),
                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                    ),
                    trailingIcon = {
                        if (url.isNotEmpty()) {
                            IconButton(onClick = { url = "" }) {
                                Icon(Icons.Default.Clear, "Clear", tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
                            }
                        }
                    }
                )

                Spacer(modifier = Modifier.height(20.dp))

                Button(
                    onClick = {
                        if (url.isNotEmpty() && !isLoading) {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            isLoading = true
                            videoMetadata = null
                            scope.launch {
                                val result = fetchVideoMetadata(context, url)
                                if (result != null) {
                                    if (result.qualities.isEmpty() && result.audioQualities.isEmpty()) {
                                        Toast.makeText(context, "No downloadable formats found. Try updating cookies.", Toast.LENGTH_LONG).show()
                                        videoMetadata = null
                                    } else {
                                        videoMetadata = result
                                    }
                                } else {
                                    Toast.makeText(context, "Failed to fetch video. Check link or update cookies in Settings.", Toast.LENGTH_LONG).show()
                                }
                                isLoading = false
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF1877F2),
                        contentColor = Color.White,
                        disabledContainerColor = Color(0xFF1877F2).copy(alpha = 0.3f)
                    ),
                    shape = RoundedCornerShape(16.dp),
                    enabled = url.isNotEmpty() && !isLoading
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White, strokeWidth = 2.dp)
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.PlayArrow, null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Download Now", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val platforms = listOf("YouTube", "Facebook", "Twitter", "Spotify")
            platforms.forEach { name ->
                Box(modifier = Modifier.weight(1f)) {
                    PlatformBadge(name)
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        AnimatedVisibility(
            visible = (videoMetadata != null) && !isLoading,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            videoMetadata?.let { metadata ->
                QualitySelectionSection(
                    metadata = metadata,
                ) { quality ->
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    selectedQuality = quality
                        val permissions = mutableListOf<String>()
                        
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            permissions.add(Manifest.permission.READ_MEDIA_VIDEO)
                            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
                                permissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                            }
                            permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
                        }

                        val toRequest = permissions.filter { 
                            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED 
                        }

                        if (toRequest.isEmpty()) {
                            currentDownloadId = startDownload(
                                context = context,
                                pageUrl = url,
                                title = metadata.title,
                                type = quality.type,
                                formatId = quality.formatId,
                                isCombined = quality.isCombined
                            )
                            onDownloadStarted(quality.type)
                        } else {
                            permissionLauncher.launch(toRequest.toTypedArray())
                        }
                    }
            }
        }
        Spacer(modifier = Modifier.weight(0.1f))
        Spacer(modifier = Modifier.height(80.dp))
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun DownloadsScreen(
    settingsManager: SettingsManager,
    downloadedVideos: List<DownloadedVideo>,
    activeDownloads: List<ActiveDownload>,
    imageLoader: ImageLoader,
    selectedTab: String,
    onTabSelected: (String) -> Unit,
    onVideoClick: (DownloadedVideo) -> Unit,
    onDeleteVideo: (DownloadedVideo) -> Unit,
    onCancelDownload: (Long) -> Unit,
    onLockClick: (DownloadedVideo) -> Unit,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope
) {
    var videoToLock by remember { mutableStateOf<DownloadedVideo?>(null) }
    var downloadToCancel by remember { mutableStateOf<ActiveDownload?>(null) }
    val currentlyPlaying by settingsManager.currentlyPlayingPath.collectAsState()

    LaunchedEffect(Unit) {
        settingsManager.syncHistoryWithStorage()
    }


    videoToLock?.let { video ->
        AlertDialog(
            onDismissRequest = { videoToLock = null },
            icon = {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = "Lock",
                    tint = Color(0xFFFF5722)
                )
            },
            title = { Text("Lock Item?", style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold) },
            text = { Text("Do you want to move \"${video.title}\" to the Private Locker?",style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center) },
            confirmButton = {
                TextButton(onClick = {
                    onLockClick(video)
                    videoToLock = null
                }) {
                    Text("Lock", fontWeight = FontWeight.ExtraBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { videoToLock = null }) {
                    Text("Cancel",fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    downloadToCancel?.let { download ->
        AlertDialog(
            onDismissRequest = { downloadToCancel = null },
            icon = {
                Icon(
                    imageVector = Icons.Default.Cancel,
                    contentDescription = "Cancel",
                    tint = Color.Red
                )
            },
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
                TextButton(
                    onClick = {
                        onCancelDownload(download.id)
                        downloadToCancel = null
                    }
                ) {
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Downloads",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Selection Tabs: Videos and Music
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            FilterButton(
                text = "Videos",
                isSelected = selectedTab == "Videos",
                onClick = { onTabSelected("Videos") }
            )
            FilterButton(
                text = "Music",
                isSelected = selectedTab == "Music",
                onClick = { onTabSelected("Music") }
            )
        }

        val currentType = if (selectedTab == "Videos") "video" else "audio"
        val filteredActive by remember(activeDownloads, currentType) {
            derivedStateOf { activeDownloads.filter { it.type == currentType } }
        }
        val filteredDownloaded by remember(downloadedVideos, currentType) {
            derivedStateOf { downloadedVideos.filter { it.type == currentType } }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            contentPadding = PaddingValues(bottom = 80.dp) // Extra room for smooth scrolling
        ) {
            if (filteredActive.isNotEmpty()) {
                item(key = "active_header", contentType = "header") {
                    Text(
                        "Downloading",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }
                items(
                    items = filteredActive,
                    key = { it.id },
                    contentType = { "active_download" }
                ) { download ->
                    ActiveDownloadItem(download) { _ ->
                        downloadToCancel = download
                    }
                }
                item(key = "active_spacer") { Spacer(modifier = Modifier.height(24.dp)) }
            }

            if (filteredDownloaded.isEmpty() && filteredActive.isEmpty()) {
                item(key = "empty_state", contentType = "empty_state") {
                    Box(modifier = Modifier.fillParentMaxHeight(0.7f), contentAlignment = Alignment.Center) {
                        Text(
                            if (selectedTab == "Videos") stringResource(R.string.no_downloads_yet) 
                            else "No music downloads yet",
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                    }
                }
            } else if (filteredDownloaded.isNotEmpty()) {
                item(key = "completed_header", contentType = "header") {
                    Text(
                        "Completed",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }
                itemsIndexed(
                    items = filteredDownloaded,
                    key = { _, video: DownloadedVideo -> video.timestamp + video.localPath.hashCode() },
                    contentType = { _, _ -> "downloaded_item" }
                ) { index, video ->
                    // High-performance smooth entry animation
                    val state = remember { MutableTransitionState(false).apply { targetState = true } }
                    AnimatedVisibility(
                        visibleState = state,
                        enter = fadeIn(spring(stiffness = Spring.StiffnessLow)) + 
                                slideInVertically(spring(stiffness = Spring.StiffnessLow, dampingRatio = Spring.DampingRatioLowBouncy)) { it / 2 }
                    ) {
                        DownloadItem(
                            video = video,
                            isCurrentlyPlaying = currentlyPlaying == video.localPath,
                            imageLoader = imageLoader,
                            onDelete = { onDeleteVideo(video) },
                            onLock = { videoToLock = video },
                            sharedTransitionScope = sharedTransitionScope,
                            animatedVisibilityScope = animatedVisibilityScope,
                            onClick = { onVideoClick(video) }
                        )
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
        border = if (isSelected) null else BorderStroke(0.5.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f))
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
                    val downloadedStr = formatSize(download.downloadedBytes)
                    val totalStr = formatSize(download.totalBytes)
                    Text(
                        if (download.totalBytes > 0) "$downloadedStr / $totalStr" else "Downloading...",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
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
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.04f),
        shape = RoundedCornerShape(20.dp),
        interactionSource = interactionSource,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .graphicsLayer { 
                scaleX = scale
                scaleY = scale
                // Hardware acceleration for each list item
                clip = true
                shape = RoundedCornerShape(20.dp)
            }
            .border(0.5.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f), RoundedCornerShape(20.dp)),
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
            with(sharedTransitionScope) {
                Surface(
                    modifier = Modifier
                        .size(80.dp, 60.dp)
                        .sharedElement(
                            rememberSharedContentState(key = "video_container_${video.localPath}"),
                            animatedVisibilityScope = animatedVisibilityScope,
                            boundsTransform = { _, _ ->
                                spring(
                                    stiffness = Spring.StiffnessLow,
                                    dampingRatio = Spring.DampingRatioLowBouncy
                                )
                            }
                        )
                        .clip(RoundedCornerShape(14.dp)),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
                ) {
                    if (video.isLocked && !isInsideLocker) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Lock, "Locked", tint = Color.White.copy(alpha = 0.5f))
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
                            coil.request.ImageRequest.Builder(context)
                                .data(video.localPath)
                                .videoFrameMillis(1000)
                                .build()
                        }
                        
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
                    // This is to show only Unlock button inside the locker
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
                    // Show Lock and Delete buttons in main downloads
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(settingsManager: SettingsManager, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val themeMode by settingsManager.themeMode.collectAsState(initial = "System")
    val language by settingsManager.appLanguage.collectAsState(initial = "English (US)")

    var cacheSize by remember { mutableStateOf("0.0 MB") }
    var totalAppSize by remember { mutableStateOf("0.0 MB") }

    val cookiePickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            val success = CookiesManager.importCookies(context, it)
            if (success) {
                Toast.makeText(context, "Cookies imported successfully", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "Failed to import cookies", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun refreshSizes() {
        val cache = calculateFolderSize(context.cacheDir)
        val files = calculateFolderSize(context.filesDir)
        cacheSize = formatSize(cache)
        totalAppSize = formatSize(cache + files)
    }

    LaunchedEffect(Unit) {
        refreshSizes()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = if (MaterialTheme.colorScheme.surface == Color(0xFF161823)) {
                        listOf(Color(0xFF161823), Color.Black)
                    } else {
                        listOf(Color(0xFFF0F2F5), Color(0xFFE3E6EA))
                    }
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 20.dp)
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f), CircleShape)
                        .size(40.dp)
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onSurface)
                }
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    "Settings",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                // Personalization Section
                item {
                    SettingsSection(title = "Personalization") {
                        SettingsItem(
                            icon = Icons.Default.Palette,
                            title = "Appearance",
                            subtitle = themeMode
                        ) {
                            var showDialog by remember { mutableStateOf(value = false) }
                            if (showDialog) {
                                OptionSelectionDialog(
                                    title = "Choose Theme",
                                    options = listOf("System", "Light", "Dark"),
                                    selectedOption = themeMode,
                                    onDismiss = { showDialog = false },
                                    onOptionSelected = {
                                        scope.launch { settingsManager.setThemeMode(it) }
                                        showDialog = false
                                    }
                                )
                            }
                            IconButton(onClick = { showDialog = true }) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .size(40.dp) // The compact outer container size
                                        .clip(CircleShape) // Makes it a perfect circle
                                        // Uses a very soft tint of your primary color (or onSurface)
                                        .background(Color.Black.copy(alpha = 0.2f))
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ChevronRight,
                                        contentDescription = null,
                                        // A stronger, crisp tint so the arrow is clearly readable
                                        tint = Color(0xFF0377F5),
                                        modifier = Modifier.size(30.dp) // Sized slightly smaller to sit nicely in the circle
                                    )
                                }
                            }
                        }

                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            thickness = 0.5.dp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)
                        )

                        SettingsItem(
                            icon = Icons.Default.Translate,
                            title = "Language",
                            subtitle = language
                        ) {
                            var showDialog by remember { mutableStateOf(value = false) }
                            if (showDialog) {
                                OptionSelectionDialog(
                                    title = "Choose Language",
                                    options = listOf("English (US)", "English (UK)"),
                                    selectedOption = language,
                                    onDismiss = { showDialog = false },
                                    onOptionSelected = { selectedLang ->
                                        scope.launch {
                                            settingsManager.setAppLanguage(selectedLang)
                                            val localeTag = when (selectedLang) {
                                                "English (UK)" -> "en-GB"
                                                else -> "en-US"
                                            }
                                            val appLocales = LocaleListCompat.forLanguageTags(localeTag)
                                            AppCompatDelegate.setApplicationLocales(appLocales)
                                        }
                                        showDialog = false
                                    }
                                )
                            }
                            IconButton(onClick = { showDialog = true }) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .size(40.dp) // The compact outer container size
                                        .clip(CircleShape) // Makes it a perfect circle
                                        // Uses a very soft tint of your primary color (or onSurface)
                                        .background(Color.Black.copy(alpha = 0.2f))
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ChevronRight,
                                        contentDescription = null,
                                        // A stronger, crisp tint so the arrow is clearly readable
                                        tint = Color(0xFF0377F5),
                                        modifier = Modifier.size(30.dp) // Sized slightly smaller to sit nicely in the circle
                                    )
                                }
                            }
                        }
                    }
                }

                // Security Section
                item {
                    val useBiometric by settingsManager.useBiometric.collectAsState(initial = false)
                    val isLockerSet by settingsManager.isLockerSet.collectAsState(initial = false)
                    val lockerManager = remember { LockerManager(context) }
                    val isFingerprintAvailable = remember { lockerManager.isFingerprintSupported() }
                    var showChangePin by remember { mutableStateOf(false) }

                    if (showChangePin) {
                        ChangePinDialog(
                            settingsManager = settingsManager,
                            onDismiss = { showChangePin = false },
                            onSuccess = { showChangePin = false }
                        )
                    }

                    SettingsSection(title = "Security & Privacy") {
                        SettingsItem(
                            icon = Icons.Default.Cookie,
                            title = "Import Cookies",
                            subtitle = "Use cookies.txt to bypass bot detection"
                        ) {
                            Row {
                                TextButton(onClick = { 
                                    CookiesManager.clearCookies(context)
                                    Toast.makeText(context, "Cookies cleared", Toast.LENGTH_SHORT).show()
                                }) {
                                    Text("Clear", color = Color.Red)
                                }
                                TextButton(onClick = { cookiePickerLauncher.launch("*/*") }) {
                                    Text("Import", fontWeight = FontWeight.Bold, color = Color(0xFF1877F2))
                                }
                            }
                        }

                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            thickness = 0.5.dp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)
                        )

                        SettingsItem(
                            icon = Icons.Default.LockReset,
                            title = "Change Locker PIN",
                            subtitle = if (isLockerSet) "Update your security credentials" else "Set up a locker first"
                        ) {
                            TextButton(
                                onClick = { showChangePin = true },
                                enabled = isLockerSet
                            ) {
                                Text("Change",fontWeight = FontWeight.Bold, color = Color(0xFFFF5722))
                            }
                        }

                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            thickness = 0.5.dp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)
                        )

                        SettingsItem(
                            icon = Icons.Default.Fingerprint,
                            title = "Fingerprint Unlock",
                            subtitle = if (isFingerprintAvailable) "Protect locker with biometric" else "Not supported on this device"
                        ) {
                            var localBiometricState by remember { mutableStateOf(useBiometric) }
                            
                            LaunchedEffect(useBiometric) {
                                localBiometricState = useBiometric
                            }

                            Switch(
                                checked = localBiometricState,
                                onCheckedChange = { checked -> 
                                    localBiometricState = checked
                                    scope.launch { settingsManager.setUseBiometric(checked) }
                                },
                                enabled = isFingerprintAvailable,
                                colors = SwitchDefaults.colors(
                                    // 1. Sets the thumb color when toggled on
                                    checkedThumbColor = Color(0xFF0377F5),

                                    // 2. Sets the light gray background track color when checked
                                    checkedTrackColor = Color.LightGray,

                                    // 3. Sets the light gray background track color when unchecked
                                    uncheckedTrackColor = Color.LightGray,

                                    // 4. Optional: Sets the background color when disabled (isFingerprintAvailable = false)
                                    disabledUncheckedTrackColor = Color.LightGray.copy(alpha = 0.5f),
                                    disabledCheckedTrackColor = Color.LightGray.copy(alpha = 0.5f)
                                )
                            )
                        }
                    }
                }

                // Storage Section
                item {
                    SettingsSection(title = "Storage & Data") {
                        SettingsItem(
                            icon = Icons.Default.Storage,
                            title = "Total Storage Used",
                            subtitle = totalAppSize
                        ) {}
                        
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            thickness = 0.5.dp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)
                        )

                        SettingsItem(
                            icon = Icons.Default.Cached,
                            title = "Cache Size",
                            subtitle = cacheSize
                        ) {}
                    }
                }

                // Feedback & Support Section
                item {
                    SettingsSection(title = "Help & Feedback") {
                        SettingsItem(
                            icon = Icons.Default.BugReport,
                            title = "Report a bug",
                            subtitle = "Please share logs through Gmail"
                        ) {
                            IconButton(onClick = {
                                scope.launch {
                                    val currentLog = OmegaCrashHandler.getCrashReport(context)
                                    if (currentLog != null) {
                                        val intent = Intent(Intent.ACTION_SENDTO).apply {
                                            data = Uri.parse("mailto:onlyforandroiddev@gmail.com")
                                            putExtra(Intent.EXTRA_SUBJECT, "Omega Bug Report")
                                            putExtra(Intent.EXTRA_TEXT, currentLog)
                                        }
                                        try {
                                            context.startActivity(intent)
                                        } catch (e: Exception) {
                                            Toast.makeText(context, "No email app found", Toast.LENGTH_SHORT).show()
                                        }
                                    } else {
                                        Toast.makeText(context, "No Logs to share", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .size(40.dp) // Total size of the icon container
                                        .clip(RoundedCornerShape(8.dp)) // Gives it a nice, subtle rounded corner
                                        .background(Color.Black.copy(alpha = 0.2f)) // Light matching blue background so the rounding is visible
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.Send,
                                        contentDescription = null,
                                        tint = Color(0xFF0377F5),
                                        modifier = Modifier.size(24.dp) // The size of the actual arrow inside
                                    )
                                }
                            }
                        }

                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            thickness = 0.5.dp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)
                        )

                        SettingsItem(
                            icon = Icons.Default.VolunteerActivism,
                            title = "Support Developer",
                            subtitle = "Help sustain open-source projects"
                        ) {
                            IconButton(onClick = {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://patreon.com/ArslanDaim77"))
                                context.startActivity(intent)
                            }) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .size(40.dp) // Total size of the icon container
                                        .clip(RoundedCornerShape(8.dp)) // Gives it a nice, subtle rounded corner
                                        .background(Color.Black.copy(alpha = 0.2f)) // Light matching blue background so the rounding is visible
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Support,
                                        contentDescription = null,
                                        tint = Color(0xFF0377F5),
                                        modifier = Modifier.size(24.dp) // The size of the actual arrow inside
                                    )
                                }
                            }
                        }
                    }
                }

                // About Section
                item {
                    var isAboutExpanded by remember { mutableStateOf(false) }
                    SettingsSection(title = "About") {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Surface(
                                onClick = { isAboutExpanded = !isAboutExpanded },
                                color = Color.Transparent,
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            Icons.Default.Info,
                                            null,
                                            tint = Color(0xFF1877F2),
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Text(
                                            "About Developer",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                    Icon(
                                        imageVector = if (isAboutExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                                    )
                                }
                            }

                            AnimatedVisibility(
                                visible = isAboutExpanded,
                                enter = expandVertically() + fadeIn(),
                                exit = shrinkVertically() + fadeOut()
                            ) {
                                Column {
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text(
                                        text = "Arsalan Daim Shar, an AI enthusiast and ML engineer building side projects such as this as a hobby and love for open-source applications",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                                        lineHeight = 20.sp
                                    )
                                    
                                    Spacer(modifier = Modifier.height(16.dp))
                                    
                                    Button(
                                        onClick = { /* Future GitHub Link */ },
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.1f),
                                            contentColor = Color(0xFF1877F2)
                                        ),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Icon(Icons.Default.Code, null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("View Source Code", fontWeight = FontWeight.Bold)
                                    }
                                    
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Surface(
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                            horizontalArrangement = Arrangement.Absolute.Center,
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Text(
                                                "Version",
                                                style = MaterialTheme.typography.labelLarge,
                                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                "1.7.1 (Beta)",
                                                style = MaterialTheme.typography.labelLarge,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFF1877F2)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                
                item { Spacer(modifier = Modifier.height(20.dp)) }
            }
        }
    }
}

@Composable
fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 4.dp, bottom = 12.dp)
        )
        Surface(
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.04f),
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(0.5.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f), RoundedCornerShape(24.dp)),
            content = { Column(content = content) }
        )
    }
}

@Composable
fun SettingsItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    action: @Composable () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f), RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = Color(0xFF1877F2), modifier = Modifier.size(20.dp))
        }
        
        Spacer(modifier = Modifier.width(16.dp))
        
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )
        }
        
        action()
    }
}

@Composable
fun OptionSelectionDialog(
    title: String,
    options: List<String>,
    selectedOption: String,
    onDismiss: () -> Unit,
    onOptionSelected: (String) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text = {
            Box(modifier = Modifier.heightIn(max = 400.dp)) {
                LazyColumn(Modifier.selectableGroup()) {
                    items(options) { option ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .height(56.dp)
                                .selectable(
                                    selected = (option == selectedOption),
                                    onClick = { onOptionSelected(option) },
                                    role = Role.RadioButton
                                )
                                .padding(horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = (option == selectedOption),
                                onClick = null,
                                colors = RadioButtonDefaults.colors(selectedColor = Color(0xFF1877F2))
                            )
                            Text(
                                text = option,
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.padding(start = 16.dp),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

fun calculateFolderSize(file: File): Long {
    var length: Long = 0
    val files = file.listFiles()
    if (files != null) {
        for (f in files) {
            length += if (f.isFile) f.length() else calculateFolderSize(f)
        }
    }
    return length
}

fun formatSize(bytes: Long): String {
    if (bytes <= 0) return "0.0 MB"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt()
    return String.format(Locale.US, "%.1f %s", bytes / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
}



suspend fun fetchVideoMetadata(context: Context, videoUrl: String): VideoMetadata? = withContext(Dispatchers.IO) {
    val cookiesPath = CookiesManager.getCookiesPath(context)
    YtDlpManager.getVideoInfo(videoUrl, cookiesPath)
}

fun startDownload(context: Context, pageUrl: String, title: String, type: String = "video", formatId: String? = null, isCombined: Boolean = false): Long? {
    val cookiesPath = CookiesManager.getCookiesPath(context)
    val intent = Intent(context, DownloadService::class.java).apply {
        putExtra("url", pageUrl)
        putExtra("title", title)
        putExtra("type", type)
        putExtra("formatId", formatId)
        putExtra("isCombined", isCombined)
        putExtra("cookiesPath", cookiesPath)
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        context.startForegroundService(intent)
    } else {
        context.startService(intent)
    }
    return 0L // Dummy ID
}

@Composable
fun PlatformBadge(name: String) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(if (isPressed) 0.96f else 1f, label = "badge_scale")
    
    Surface(
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .border(0.5.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f), RoundedCornerShape(14.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = {}
            )
    ) {
        Text(
            text = name,
            modifier = Modifier.padding(vertical = 10.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
            textAlign = TextAlign.Center
        )
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

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun LockerScreen(
    isAuthorized: Boolean,
    lockerVideos: List<DownloadedVideo>,
    imageLoader: ImageLoader,
    settingsManager: SettingsManager,
    lockerManager: LockerManager,
    activity: AppCompatActivity,
    onAuthorize: () -> Unit,
    onCancel: () -> Unit,
    onVideoClick: (DownloadedVideo) -> Unit,
    onUnlockClick: (DownloadedVideo) -> Unit,
    onDeleteVideo: (DownloadedVideo) -> Unit,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isInputVisible by remember { mutableStateOf(false) }
    var pinValue by remember { mutableStateOf("") }
    var videoToUnlock by remember { mutableStateOf<DownloadedVideo?>(null) }
    val isLockerSet by settingsManager.isLockerSet.collectAsState(initial = false)
    val useBiometric by settingsManager.useBiometric.collectAsState(initial = false)
    val currentlyPlaying by settingsManager.currentlyPlayingPath.collectAsState()
    var selectedTab by rememberSaveable { mutableStateOf("Videos") }

    if (!isAuthorized) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Surface(
                modifier = Modifier.padding(32.dp),
                shape = RoundedCornerShape(32.dp),
                color = MaterialTheme.colorScheme.surfaceColorAtElevation(8.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .background(Color(0xFFFF5722).copy(alpha = 0.1f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Lock,
                            null,
                            modifier = Modifier.size(40.dp),
                            tint = Color(0xFFFF5722)
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    Text(
                        "Locked Videos",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    
                    Text(
                        "Hide your important videos & audio here",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        textAlign = TextAlign.Center
                    )
                    
                    Spacer(modifier = Modifier.height(32.dp))
                    
                    if (!isLockerSet) {
                        Text(
                            "Please download a video/audio first to setup a lock PIN.",
                            color = MaterialTheme.colorScheme.error, 
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        Button(
                            onClick = onCancel,
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.secondary,
                                // Automatically adapts text and icon colors perfectly to the container's theme state
                                contentColor = MaterialTheme.colorScheme.onSecondary
                            )
                        ) {
                            Text("Go Back Home",
                                )
                        }
                    } else if (!isInputVisible) {
                        Button(
                            onClick = { 
                                if (useBiometric && lockerManager.isFingerprintSupported()) {
                                    lockerManager.showBiometricPrompt(
                                        activity = activity,
                                        onSuccess = onAuthorize,
                                        onError = { isInputVisible = true }
                                    )
                                } else {
                                    isInputVisible = true 
                                }
                            },
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth(),
                            contentPadding = PaddingValues(16.dp)
                        ) {
                            Text("Unlock", fontWeight = FontWeight.Bold)
                        }
                    } else {
                        // Integrated PIN Input UI
                        Text(
                            "Enter 4-Digit PIN",
                            style = MaterialTheme.typography.labelLarge,
                            color = Color(0xFFFF5722),
                            fontWeight = FontWeight.Bold
                        )
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        OutlinedTextField(
                            value = pinValue,
                            onValueChange = { 
                                if (it.length <= 4 && it.all { c -> c.isDigit() }) {
                                    pinValue = it
                                    if (it.length == 4) {
                                        scope.launch {
                                            if (settingsManager.verifyLockerPin(it)) {
                                                onAuthorize()
                                                isInputVisible = false
                                                pinValue = ""
                                            } else {
                                                Toast.makeText(context, "Incorrect PIN", Toast.LENGTH_SHORT).show()
                                                pinValue = ""
                                            }
                                        }
                                    }
                                }
                            },
                            modifier = Modifier.width(200.dp),
                            shape = RoundedCornerShape(16.dp),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                            visualTransformation = PasswordVisualTransformation(),
                            singleLine = true,
                            textStyle = LocalTextStyle.current.copy(textAlign = TextAlign.Center, fontSize = 20.sp, letterSpacing = 8.sp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFFFF5722),
                                unfocusedBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
                            )
                        )
                        
                        Spacer(modifier = Modifier.height(24.dp))
                        
                        TextButton(
                            onClick = { 
                                isInputVisible = false
                                pinValue = ""
                            }
                        ) {
                            Text("Hide Input", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
                        }
                    }
                }
            }
        }
    } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.LockOpen, null, tint = Color(0xFFFF5722), modifier = Modifier.size(24.dp))
                Spacer(modifier = Modifier.width(12.dp))
                    Text(
                    "Private Locker",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFFF5722)
                )
            }
            Text(
                "Locked files are moved to private storage",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                modifier = Modifier.padding(start = 36.dp)
            )

            videoToUnlock?.let { video ->
                AlertDialog(
                    onDismissRequest = { videoToUnlock = null },
                    icon = {
                        Icon(
                            imageVector = Icons.Default.LockOpen,
                            contentDescription = "Unlock",
                            tint = Color(0xFFFF5722)
                        )
                    },
                    title = { 
                        Text(
                            "Unlock Item?", 
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.SemiBold
                        ) 
                    },
                    text = { 
                        Text(
                            "Do you want to move \"${video.title}\" back to the Public Gallery?",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        ) 
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                onUnlockClick(video)
                                videoToUnlock = null
                            }
                        ) {
                            Text("Unlock", fontWeight = FontWeight.ExtraBold, color = Color(0xFFFF5722))
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { videoToUnlock = null }) {
                            Text("Cancel", fontWeight = FontWeight.Bold)
                        }
                    }
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Selection Tabs: Videos and Music
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                FilterButton(
                    text = "Videos",
                    isSelected = selectedTab == "Videos",
                    onClick = { selectedTab = "Videos" }
                )
                FilterButton(
                    text = "Music",
                    isSelected = selectedTab == "Music",
                    onClick = { selectedTab = "Music" }
                )
            }

            val currentType = if (selectedTab == "Videos") "video" else "audio"
            val filteredLocker by remember(lockerVideos, currentType) {
                derivedStateOf { lockerVideos.filter { it.type == currentType } }
            }

            if (filteredLocker.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = if (selectedTab == "Videos") Icons.Default.FolderOpen else Icons.Default.MusicNote, 
                            contentDescription = null, 
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f), 
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            if (selectedTab == "Videos") "No locked videos" else "No locked music", 
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    contentPadding = PaddingValues(bottom = 80.dp)
                ) {
                    items(
                        items = filteredLocker,
                        key = { it.timestamp + it.localPath.hashCode() },
                        contentType = { "locked_item" }
                    ) { video ->
                        DownloadItem(
                            video = video,
                            isCurrentlyPlaying = currentlyPlaying == video.localPath,
                            imageLoader = imageLoader,
                            onDelete = { onDeleteVideo(video) },
                            onLock = { videoToUnlock = video },
                            sharedTransitionScope = sharedTransitionScope,
                            animatedVisibilityScope = animatedVisibilityScope,
                            onClick = { onVideoClick(video) },
                            isInsideLocker = true
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

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun VideoPlayerScreen(
    videoUrl: String,
    settingsManager: SettingsManager,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    onBack: () -> Unit
) {
    with(sharedTransitionScope) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .sharedElement(
                    rememberSharedContentState(key = "video_container_$videoUrl"),
                    animatedVisibilityScope = animatedVisibilityScope,
                    boundsTransform = { _, _ ->
                        spring(
                            stiffness = Spring.StiffnessLow,
                            dampingRatio = Spring.DampingRatioLowBouncy
                        )
                    }
                )
        ) {
            VideoPlayer(videoUrl = videoUrl, settingsManager = settingsManager, onBack = onBack)
        }
    }
}
