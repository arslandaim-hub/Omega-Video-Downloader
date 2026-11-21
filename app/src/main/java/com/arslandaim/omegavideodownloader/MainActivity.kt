/*
 * Omega Video Downloader Project Original (2026)
 * arslandaim-hub (GitHub.com/arslandaim-hub)
 * Licenced Under GPL-3.0+
*/
package com.arslandaim.omegavideodownloader

import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.animation.*
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import coil.ImageLoader
import coil.decode.VideoFrameDecoder
import coil.disk.DiskCache
import coil.memory.MemoryCache
import coil.request.CachePolicy
import com.arslandaim.omegavideodownloader.data.DownloadRepository
import com.arslandaim.omegavideodownloader.data.VideoRepository
import com.arslandaim.omegavideodownloader.ui.*
import com.arslandaim.omegavideodownloader.ui.screens.LockerScreen
import com.arslandaim.omegavideodownloader.ui.screens.MainScreen
import com.arslandaim.omegavideodownloader.ui.screens.SettingsScreen
import com.arslandaim.omegavideodownloader.ui.screens.VideoPlayerScreen
import com.arslandaim.omegavideodownloader.ui.theme.OmegaTheme
import com.arslandaim.omegavideodownloader.ui.theme.OmegaVideoDownloaderTheme
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.flow.MutableStateFlow

class MainActivity : AppCompatActivity() {

    private lateinit var settingsManager: SettingsManager
    private lateinit var videoRepository: VideoRepository
    private lateinit var downloadRepository: DownloadRepository

    private val incomingSharedText = MutableStateFlow<String?>(null)

    private fun handleShareIntent(intent: Intent?) {
        if (intent?.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            val shared = intent.getStringExtra(Intent.EXTRA_TEXT)?.trim()
            if (!shared.isNullOrBlank()) {
                incomingSharedText.value = shared
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleShareIntent(intent)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settingsManager = SettingsManager(this)
        videoRepository = VideoRepository(this)
        downloadRepository = DownloadRepository(this)
        handleShareIntent(intent)

        enableEdgeToEdge()

        lifecycle.addObserver(LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> PlaybackService.isAppInForeground = true
                Lifecycle.Event.ON_STOP -> PlaybackService.isAppInForeground = false
                else -> {}
            }
        })

        setContent {
            val viewModelFactory = remember {
                object : ViewModelProvider.Factory {
                    @Suppress("UNCHECKED_CAST")
                    override fun <T : ViewModel> create(modelClass: Class<T>): T {
                        return when {
                            modelClass.isAssignableFrom(MainViewModel::class.java) ->
                                MainViewModel(settingsManager, downloadRepository, videoRepository) as T
                            modelClass.isAssignableFrom(MoviesViewModel::class.java) ->
                                MoviesViewModel(videoRepository, downloadRepository, settingsManager) as T
                            modelClass.isAssignableFrom(HomeViewModel::class.java) ->
                                HomeViewModel(videoRepository, downloadRepository, settingsManager) as T
                            modelClass.isAssignableFrom(DownloadsViewModel::class.java) ->
                                DownloadsViewModel(applicationContext, settingsManager) as T
                            modelClass.isAssignableFrom(LockerViewModel::class.java) ->
                                LockerViewModel(settingsManager, LockerManager(this@MainActivity)) as T
                            else -> throw IllegalArgumentException("Unknown ViewModel class")
                        }
                    }
                }
            }

            val mainViewModel: MainViewModel = viewModel(factory = viewModelFactory)
            val moviesViewModel: MoviesViewModel = viewModel(factory = viewModelFactory)
            val homeViewModel: HomeViewModel = viewModel(factory = viewModelFactory)
            val downloadsViewModel: DownloadsViewModel = viewModel(factory = viewModelFactory)
            val lockerViewModel: LockerViewModel = viewModel(factory = viewModelFactory)

            val themeMode by mainViewModel.themeMode.collectAsState()
            val themeColorPreset by mainViewModel.themeColorPreset.collectAsState()
            val language by mainViewModel.appLanguage.collectAsState()
            val sharedText by incomingSharedText.collectAsState()

            val isDarkTheme = when (themeMode) {
                "Dark" -> true
                "Light" -> false
                else -> androidx.compose.foundation.isSystemInDarkTheme()
            }

            LaunchedEffect(isDarkTheme) {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT) { isDarkTheme },
                    navigationBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT) { isDarkTheme },
                )
            }

            LaunchedEffect(language) {
                val appLocales = LocaleListCompat.forLanguageTags(SettingsManager.localeTagFor(language))
                if (AppCompatDelegate.getApplicationLocales() != appLocales) {
                    AppCompatDelegate.setApplicationLocales(appLocales)
                }
            }

            OmegaVideoDownloaderTheme(
                themeMode = themeMode,
                themeColorId = themeColorPreset
            ) {
                val navController = rememberNavController()
                val context = LocalContext.current

                // Process links shared via Android Share sheet -> jump straight to Downloader tab
                LaunchedEffect(sharedText) {
                    val text = sharedText ?: return@LaunchedEffect
                    incomingSharedText.value = null
                    navController.popBackStack("home", inclusive = false)
                    moviesViewModel.closeMovieDetail()
                    mainViewModel.setLastActiveTab(1)
                    homeViewModel.onIncomingSharedText(text)
                }

                // Global Playback Monitor
                DisposableEffect(Unit) {
                    val sessionToken = SessionToken(context, ComponentName(context, PlaybackService::class.java))
                    val controllerFuture = MediaController.Builder(context, sessionToken).buildAsync()
                    var controller: MediaController? = null

                    val listener = object : Player.Listener {
                        override fun onIsPlayingChanged(isPlaying: Boolean) {
                            controller?.let { c ->
                                val path = if (isPlaying) c.currentMediaItem?.localConfiguration?.uri?.toString() else null
                                mainViewModel.setCurrentlyPlaying(path)
                            }
                        }

                        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                            controller?.let { c ->
                                val path = if (c.isPlaying) mediaItem?.localConfiguration?.uri?.toString() else null
                                mainViewModel.setCurrentlyPlaying(path)
                            }
                        }
                    }

                    controllerFuture.addListener({
                        try {
                            val c = controllerFuture.get()
                            controller = c
                            c.addListener(listener)
                            if (c.isPlaying) {
                                mainViewModel.setCurrentlyPlaying(c.currentMediaItem?.localConfiguration?.uri?.toString())
                            }
                        } catch (_: Exception) {}
                    }, MoreExecutors.directExecutor())

                    onDispose {
                        controller?.removeListener(listener)
                        MediaController.releaseFuture(controllerFuture)
                    }
                }

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

                val imageLoader = remember {
                    ImageLoader.Builder(context)
                        .components { add(VideoFrameDecoder.Factory()) }
                        .memoryCache { MemoryCache.Builder(context).maxSizePercent(0.25).build() }
                        .diskCache { DiskCache.Builder().directory(context.cacheDir.resolve("thumbnail_cache")).maxSizeBytes(100 * 1024 * 1024).build() }
                        .diskCachePolicy(CachePolicy.ENABLED)
                        .memoryCachePolicy(CachePolicy.ENABLED)
                        .crossfade(true)
                        .build()
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(brush = OmegaTheme.colors.backgroundBrush)
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
                                MainScreen(
                                    navController = navController,
                                    mainViewModel = mainViewModel,
                                    moviesViewModel = moviesViewModel,
                                    homeViewModel = homeViewModel,
                                    downloadsViewModel = downloadsViewModel,
                                    lockerViewModel = lockerViewModel,
                                    settingsManager = settingsManager,
                                    videoRepository = videoRepository,
                                    downloadRepository = downloadRepository,
                                    imageLoader = imageLoader,
                                    sharedTransitionScope = this@SharedTransitionLayout
                                )
                            }
                            composable(
                                "vault",
                                enterTransition = { slideInHorizontally(initialOffsetX = { it }, animationSpec = navTween) + fadeIn(fadeTween) },
                                exitTransition = { slideOutHorizontally(targetOffsetX = { -it }, animationSpec = navTween) + fadeOut(fadeTween) },
                                popEnterTransition = { slideInHorizontally(initialOffsetX = { -it }, animationSpec = navTween) + fadeIn(fadeTween) },
                                popExitTransition = { slideOutHorizontally(targetOffsetX = { it }, animationSpec = navTween) + fadeOut(fadeTween) }
                            ) {
                                val lockerManager = remember { LockerManager(context) }
                                downloadsViewModel.videoToDelete?.let { video ->
                                    AlertDialog(
                                        onDismissRequest = { downloadsViewModel.videoToDelete = null },
                                        icon = { Icon(imageVector = Icons.Default.Delete, contentDescription = "Delete", tint = Color.Red) },
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
                                            TextButton(onClick = { downloadsViewModel.deleteVideo(video) }) {
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
                                LockerScreen(
                                    viewModel = lockerViewModel,
                                    mainViewModel = mainViewModel,
                                    imageLoader = imageLoader,
                                    lockerManager = lockerManager,
                                    activity = this@MainActivity,
                                    onCancel = { navController.popBackStack() },
                                    onVideoClick = { video ->
                                        if (video.type == "audio") {
                                            mainViewModel.requestAudioPlayback(video)
                                        } else {
                                            mainViewModel.openLocalVideo(video)
                                        }
                                        navController.popBackStack()
                                    },
                                    onDeleteVideo = { video -> downloadsViewModel.videoToDelete = video },
                                    sharedTransitionScope = this@SharedTransitionLayout,
                                    animatedVisibilityScope = this
                                )
                            }
                            composable(
                                "player/{videoUrl}",
                                enterTransition = { fadeIn(tween(200)) },
                                exitTransition = { ExitTransition.None },
                                popEnterTransition = { EnterTransition.None },
                                popExitTransition = { ExitTransition.None }
                            ) { backStackEntry ->
                                val videoUrl = backStackEntry.arguments?.getString("videoUrl") ?: ""
                                VideoPlayerScreen(
                                    videoUrl = videoUrl,
                                    settingsManager = settingsManager,
                                    videoRepository = videoRepository,
                                    downloadRepository = downloadRepository,
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
