/*
 * Omega Video Downloader Project Original (2026)
 * arslandaim-hub (GitHub.com/arslandaim-hub)
 * Licenced Under GPL-3.0+
*/
package com.arslandaim.omegavideodownloader

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.pm.ActivityInfo
import android.media.AudioManager
import android.provider.Settings
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.*
import java.util.*

@OptIn(UnstableApi::class)
@Composable
fun VideoPlayer(videoUrl: String, settingsManager: SettingsManager? = null, onBack: (() -> Unit)? = null) {
    val context = LocalContext.current
    val activity = context as? Activity
    
    // Player State
    var mediaController by remember { mutableStateOf<MediaController?>(null) }
    var isPlaying by remember { mutableStateOf(false) }
    var currentPosition by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var playbackState by remember { mutableIntStateOf(Player.STATE_IDLE) }
    
    // UI State
    var showControls by remember { mutableStateOf(true) }
    var resizeMode by remember { mutableIntStateOf(AspectRatioFrameLayout.RESIZE_MODE_FIT) }
    var isBackgroundPlayEnabled by remember { mutableStateOf(false) }
    var showBackgroundPlayHint by remember { mutableStateOf(true) }
    
    // Crucial: Use updated state to prevent closure bugs in Lifecycle Observer
    val currentIsBackgroundPlayEnabled by rememberUpdatedState(isBackgroundPlayEnabled)
    
    // Overlay State
    var gestureType by remember { mutableStateOf<GestureType?>(null) }
    var gestureValue by remember { mutableFloatStateOf(0f) }
    var seekJump by remember { mutableIntStateOf(0) }
    
    // Gesture Cache to prevent lag
    var initialGestureValue by remember { mutableFloatStateOf(0f) }
    var maxVolume by remember { mutableIntStateOf(0) }

    // Controllers
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val haptic = LocalHapticFeedback.current

    // MediaController Initialization
    LaunchedEffect(videoUrl) {
        // Strategic delay to ensure navigation expansion animation is in full swing
        // Reduced to 100ms for faster, snappier playback start
        delay(100)

        val sessionToken = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val future = MediaController.Builder(context, sessionToken).buildAsync()
        
        future.addListener({
            val controller = future.get()
            mediaController = controller
            
            // Smart Continuity: Check if we are already playing this exact video
            val currentMediaItem = controller.currentMediaItem
            val alreadyPlayingThis = currentMediaItem?.localConfiguration?.uri?.toString() == videoUrl

            if (!alreadyPlayingThis) {
                val mediaItem = MediaItem.fromUri(videoUrl)
                controller.setMediaItem(mediaItem)
                controller.prepare()
            }
            
            // Force playWhenReady to true immediately for faster start
            controller.playWhenReady = true
            
            // Sync initial state immediately
            isPlaying = controller.isPlaying
            duration = controller.duration.coerceAtLeast(0L)
            currentPosition = controller.currentPosition
            
            // Observer loop for progress
            controller.addListener(object : Player.Listener {
                override fun onIsPlayingChanged(playing: Boolean) {
                    isPlaying = playing
                }
                override fun onPlaybackStateChanged(state: Int) {
                    playbackState = state
                    duration = controller.duration.coerceAtLeast(0L)
                }
            })
        }, MoreExecutors.directExecutor())
    }

    // Orientation Reset on Exit
    DisposableEffect(Unit) {
        onDispose {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    // Progress Loop
    LaunchedEffect(isPlaying, playbackState) {
        while (isPlaying && playbackState != Player.STATE_ENDED) {
            currentPosition = mediaController?.currentPosition ?: 0L
            delay(500)
        }
    }

    // Auto-hide controls
    LaunchedEffect(showControls, isPlaying) {
        if (showControls && isPlaying) {
            delay(3000)
            showControls = false
        }
    }

    // Auto-hide background play hint
    LaunchedEffect(Unit) {
        delay(3000)
        showBackgroundPlayHint = false
    }

    // Lifecycle Management
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> {
                    // Fix: Use the current state to decide whether to pause
                    if (!currentIsBackgroundPlayEnabled) {
                        mediaController?.pause()
                    }
                }
                Lifecycle.Event.ON_STOP -> {
                    if (!currentIsBackgroundPlayEnabled) {
                        mediaController?.pause()
                    }
                }
                Lifecycle.Event.ON_RESUME -> {
                    // Only resume if it's supposed to be playing
                    if (mediaController?.playWhenReady == true) {
                        mediaController?.play()
                    }
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            // Note: We don't pause here to allow background play to continue 
            // if the user minimized the app. 
            // However, we release the controller to prevent leaks.
            mediaController?.release()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { showControls = !showControls },
                    onDoubleTap = { offset ->
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        val isLeft = offset.x < size.width / 2
                        seekJump = if (isLeft) -10 else 10
                        mediaController?.let {
                            it.seekTo((it.currentPosition + (seekJump * 1000)).coerceIn(0, duration))
                        }
                    }
                )
            }
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragStart = { offset ->
                        val isLeft = offset.x < size.width / 2
                        gestureType = if (isLeft) GestureType.BRIGHTNESS else GestureType.VOLUME
                        
                        if (gestureType == GestureType.BRIGHTNESS) {
                            activity?.window?.let { window ->
                                val params = window.attributes
                                initialGestureValue = if (params.screenBrightness < 0) {
                                    Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS) / 255f
                                } else params.screenBrightness
                            }
                        } else {
                            maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                            initialGestureValue = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / maxVolume
                        }
                        gestureValue = initialGestureValue
                    },
                    onDragEnd = { gestureType = null },
                    onVerticalDrag = { _, dragAmount ->
                        val sensitivity = 0.003f // Fine-tuned for premium smoothness
                        when (gestureType) {
                            GestureType.BRIGHTNESS -> {
                                activity?.window?.let { window ->
                                    val params = window.attributes
                                    val newBrightness = (gestureValue - (dragAmount * sensitivity)).coerceIn(0.01f, 1f)
                                    params.screenBrightness = newBrightness
                                    window.attributes = params
                                    gestureValue = newBrightness
                                }
                            }
                            GestureType.VOLUME -> {
                                val newVolumePercent = (gestureValue - (dragAmount * sensitivity)).coerceIn(0f, 1f)
                                val newVolIndex = (newVolumePercent * maxVolume).toInt()
                                
                                // Optimization: Only hit the system audio service if the integer index actually changes
                                if (newVolIndex != audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)) {
                                    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, newVolIndex, 0)
                                }
                                gestureValue = newVolumePercent
                            }
                            else -> {}
                        }
                    }
                )
            }
    ) {
        // Video Layer
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = false
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
            },
            update = { view ->
                view.player = mediaController
                view.resizeMode = resizeMode
            },
            modifier = Modifier.fillMaxSize()
        )

        // Gesture Indicator
        AnimatedVisibility(
            visible = gestureType != null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.Center)
        ) {
            GestureIndicatorOverlay(type = gestureType, value = gestureValue)
        }

        // Seek Indicator with Animation
        LaunchedEffect(seekJump) {
            if (seekJump != 0) {
                delay(600)
                seekJump = 0
            }
        }
        
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 48.dp),
            contentAlignment = if (seekJump < 0) Alignment.CenterStart else Alignment.CenterEnd
        ) {
            if (seekJump != 0) {
                SeekIndicator(isForward = seekJump > 0)
            }
        }

        // Controls Layer
        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn(tween(300)),
            exit = fadeOut(tween(300))
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                // Top Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.4f))
                        .statusBarsPadding()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { onBack?.invoke() ?: activity?.finish() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Color.White)
                    }
                    Text("Omega Player", color = Color.White, fontSize = 18.sp)
                    
                        Row {
                            Box(contentAlignment = Alignment.TopCenter) {
                                IconButton(onClick = { 
                                    isBackgroundPlayEnabled = !isBackgroundPlayEnabled 
                                    PlaybackService.isBackgroundPlayEnabled = isBackgroundPlayEnabled
                                }) {
                                    Icon(
                                        imageVector = if (isBackgroundPlayEnabled) Icons.Default.Headset else Icons.Default.HeadsetOff,
                                        contentDescription = "Background Play",
                                        tint = if (isBackgroundPlayEnabled) Color(0xFF1877F2) else Color.White
                                    )
                                }
                                
                                BackgroundPlayHint(visible = showBackgroundPlayHint)
                            }

                            IconButton(onClick = {
                                activity?.requestedOrientation = if (activity.requestedOrientation == ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE) {
                                    ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                                } else {
                                    ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                                }
                            }) {
                                Icon(Icons.Default.ScreenRotation, "Rotate", tint = Color.White)
                            }

                            IconButton(onClick = {
                            resizeMode = when (resizeMode) {
                                AspectRatioFrameLayout.RESIZE_MODE_FIT -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                                AspectRatioFrameLayout.RESIZE_MODE_FILL -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                                else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                            }
                        }) {
                            Icon(Icons.Default.AspectRatio, null, tint = Color.White)
                        }
                    }
                }

                // Center Play/Pause
                IconButton(
                    onClick = {
                        mediaController?.let {
                            if (it.isPlaying) it.pause() else it.play()
                        }
                    },
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(80.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.3f))
                ) {
                    Icon(
                        if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        null,
                        tint = Color.White,
                        modifier = Modifier.size(48.dp)
                    )
                }

                // Bottom Controls
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.4f))
                        .padding(horizontal = 16.dp, vertical = 24.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(formatTime(currentPosition), color = Color.White, fontSize = 14.sp)
                        Text(formatTime(duration), color = Color.White, fontSize = 14.sp)
                    }
                    Slider(
                        value = currentPosition.toFloat(),
                        onValueChange = { mediaController?.seekTo(it.toLong()) },
                        valueRange = 0f..duration.toFloat().coerceAtLeast(1f),
                        colors = SliderDefaults.colors(
                            thumbColor = Color(0xFF1877F2),
                            activeTrackColor = Color(0xFF1877F2)
                        )
                    )
                }
            }
        }
    }
}

@Composable
fun BackgroundPlayHint(visible: Boolean) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically(),
        modifier = Modifier.padding(top = 48.dp)
    ) {
        Surface(
            color = Color(0xFF1877F2),
            shape = RoundedCornerShape(8.dp),
            tonalElevation = 4.dp
        ) {
            Text(
                "Background Play",
                color = Color.White,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                maxLines = 1
            )
        }
    }
}

enum class GestureType { BRIGHTNESS, VOLUME }

@Composable
fun GestureIndicatorOverlay(type: GestureType?, value: Float) {
    Surface(
        color = Color.Black.copy(alpha = 0.6f),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = if (type == GestureType.BRIGHTNESS) Icons.Default.BrightnessMedium else Icons.AutoMirrored.Filled.VolumeUp,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(48.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
            LinearProgressIndicator(
                progress = { value },
                color = Color.White,
                trackColor = Color.White.copy(alpha = 0.3f),
                modifier = Modifier.width(100.dp).height(4.dp).clip(CircleShape)
            )
        }
    }
}

@Composable
fun SeekIndicator(isForward: Boolean) {
    val infiniteTransition = rememberInfiniteTransition(label = "seek_chase")
    
    // Cascading Alpha for Arrows
    val alpha1 by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 600
                0.2f at 0
                1f at 200
                0.2f at 400
            }
        ),
        label = "alpha1"
    )
    val alpha2 by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 600
                0.2f at 100
                1f at 300
                0.2f at 500
            }
        ),
        label = "alpha2"
    )
    val alpha3 by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 600
                0.2f at 200
                1f at 400
                0.2f at 600
            }
        ),
        label = "alpha3"
    )

    Surface(
        color = Color.White.copy(alpha = 0.12f),
        shape = CircleShape,
        modifier = Modifier.size(110.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxSize()
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy((-16).dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val icon = if (isForward) Icons.Default.ChevronRight else Icons.Default.ChevronLeft
                // Order of icons is important for "forward" or "backward" direction
                if (isForward) {
                    Icon(icon, null, tint = Color.White.copy(alpha = alpha1), modifier = Modifier.size(28.dp))
                    Icon(icon, null, tint = Color.White.copy(alpha = alpha2), modifier = Modifier.size(36.dp))
                    Icon(icon, null, tint = Color.White.copy(alpha = alpha3), modifier = Modifier.size(44.dp))
                } else {
                    Icon(icon, null, tint = Color.White.copy(alpha = alpha3), modifier = Modifier.size(44.dp))
                    Icon(icon, null, tint = Color.White.copy(alpha = alpha2), modifier = Modifier.size(36.dp))
                    Icon(icon, null, tint = Color.White.copy(alpha = alpha1), modifier = Modifier.size(28.dp))
                }
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = if (isForward) "10s »" else "« 10s",
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 0.5.sp
            )
        }
    }
}

fun formatTime(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
}
