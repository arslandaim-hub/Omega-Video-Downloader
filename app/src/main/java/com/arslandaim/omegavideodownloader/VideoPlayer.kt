/*
 * Omega Video Downloader Project Original (2026)
 * arslandaim-hub (GitHub.com/arslandaim-hub)
 * Licenced Under GPL-3.0+
*/
package com.arslandaim.omegavideodownloader

import android.app.Activity
import android.app.PictureInPictureParams
import android.content.ComponentName
import android.content.Context
import android.content.pm.ActivityInfo
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Rational
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.HttpDataSource
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.arslandaim.omegavideodownloader.data.DownloadRepository
import com.arslandaim.omegavideodownloader.data.VideoRepository
import com.arslandaim.omegavideodownloader.player.StreamRegistry
import com.arslandaim.omegavideodownloader.ui.theme.OmegaTheme
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

private data class AbrTrackChoice(
    val label: String,
    val groupIndex: Int,
    val trackIndex: Int,
    val height: Int
)

private data class SubtitleChoice(
    val label: String,
    val groupIndex: Int,
    val trackIndex: Int
)

private data class AudioTrackChoice(
    val label: String,
    val groupIndex: Int,
    val trackIndex: Int
)

@OptIn(UnstableApi::class)
@Composable
fun VideoPlayer(
    videoUrl: String,
    titleOverride: String? = null,
    initialPositionMs: Long = 0L,
    sharedController: MediaController? = null,
    settingsManager: SettingsManager? = null,
    videoRepository: VideoRepository? = null,
    downloadRepository: DownloadRepository? = null,
    onProgressSave: ((positionMs: Long, durationMs: Long, streamUrl: String?, qualityLabel: String?) -> Unit)? = null,
    onBack: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val scope = rememberCoroutineScope()

    val isOnlineSession = videoUrl.startsWith(StreamRegistry.SESSION_PREFIX)
    val initialSession = remember(videoUrl) {
        if (isOnlineSession) StreamRegistry.session(videoUrl) else null
    }
    var candidates by remember(videoUrl) { mutableStateOf(initialSession?.candidates.orEmpty()) }
    var activeCandidateIndex by remember(videoUrl) { mutableIntStateOf(0) }
    val displayTitle = titleOverride?.takeIf { it.isNotBlank() } ?: initialSession?.title ?: "Omega Player"

    // Player State
    var mediaController by remember { mutableStateOf<MediaController?>(null) }
    var isPlaying by remember { mutableStateOf(false) }
    var currentPosition by remember { mutableLongStateOf(0L) }
    var bufferedPosition by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var playbackState by remember { mutableIntStateOf(Player.STATE_BUFFERING) }
    var playerError by remember { mutableStateOf<String?>(null) }
    var isRefreshingStream by remember { mutableStateOf(false) }
    var hasRetriedRefresh by remember(videoUrl) { mutableStateOf(false) }

    // Slider drag state (prevents calling seekTo every frame while scrubbing)
    var isDraggingSlider by remember { mutableStateOf(false) }
    var dragPosition by remember { mutableFloatStateOf(0f) }

    // Track / Speed / Cinema Feature Dialogs
    var playbackSpeed by remember { mutableFloatStateOf(1f) }
    var showSpeedDialog by remember { mutableStateOf(false) }
    var showQualityDialog by remember { mutableStateOf(false) }
    var showSubtitleDialog by remember { mutableStateOf(false) }
    var showAudioTrackDialog by remember { mutableStateOf(false) }
    var showSleepTimerDialog by remember { mutableStateOf(false) }
    var currentTracks by remember { mutableStateOf(Tracks.EMPTY) }
    var selectedAbrLabel by remember(videoUrl) { mutableStateOf("Auto") }
    var selectedSubtitleLabel by remember(videoUrl) { mutableStateOf("Off") }
    var selectedAudioLabel by remember(videoUrl) { mutableStateOf("Default") }
    var externalSubtitles by remember(videoUrl) {
        mutableStateOf(initialSession?.subtitleTracks.orEmpty())
    }

    // Cinema Controls: Touch Lock, Sleep Timer, PiP
    var isTouchLocked by remember { mutableStateOf(false) }
    var showUnlockPill by remember { mutableStateOf(false) }
    var sleepTimerMinutesRemaining by remember { mutableIntStateOf(0) } // -1 = End of movie
    var isInPipMode by remember { mutableStateOf(false) }

    // UI State
    var showControls by remember { mutableStateOf(true) }
    var resizeMode by remember { mutableIntStateOf(AspectRatioFrameLayout.RESIZE_MODE_FIT) }
    var isBackgroundPlayEnabled by remember { mutableStateOf(PlaybackService.isBackgroundPlayEnabled) }
    var showBackgroundPlayHint by remember { mutableStateOf(true) }
    val currentIsBackgroundPlayEnabled by rememberUpdatedState(isBackgroundPlayEnabled)

    // Gesture State
    var gestureType by remember { mutableStateOf<GestureType?>(null) }
    var gestureValue by remember { mutableFloatStateOf(0f) }
    var seekJump by remember { mutableIntStateOf(0) }
    var initialGestureValue by remember { mutableFloatStateOf(0f) }
    var maxVolume by remember { mutableIntStateOf(1) }

    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val haptic = LocalHapticFeedback.current
    val latestOnProgressSave by rememberUpdatedState(onProgressSave)
    val latestOnBack by rememberUpdatedState(onBack)

    val subtitlePickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        try {
            context.contentResolver.takePersistableUriPermission(
                uri,
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        } catch (_: Exception) {}
        val name = StorageUtils.displayName(context, uri.toString()) ?: "External Subtitles"
        val mime = if (name.endsWith(".vtt", ignoreCase = true)) {
            MimeTypes.TEXT_VTT
        } else {
            MimeTypes.APPLICATION_SUBRIP
        }
        val newSub = SubtitleTrack(label = name, url = uri.toString(), mimeType = mime)
        val updatedSubs = (externalSubtitles + newSub).distinctBy { it.url }
        externalSubtitles = updatedSubs
        mediaController?.let { c ->
            val pos = c.currentPosition.coerceAtLeast(0L)
            val wasPlaying = c.isPlaying
            val subConfigs = updatedSubs.map { sub ->
                MediaItem.SubtitleConfiguration.Builder(sub.url.toUri())
                    .setMimeType(sub.mimeType)
                    .setLanguage(sub.language)
                    .setLabel(sub.label)
                    .setSelectionFlags(if (sub.url == newSub.url) C.SELECTION_FLAG_DEFAULT else 0)
                    .build()
            }
            val currentItem = c.currentMediaItem ?: MediaItem.fromUri(videoUrl)
            val rebuilt = currentItem.buildUpon().setSubtitleConfigurations(subConfigs).build()
            c.setMediaItem(rebuilt, pos)
            c.prepare()
            c.playWhenReady = wasPlaying
            selectedSubtitleLabel = name.take(16)
        }
        Toast.makeText(context, "Loaded subtitle: $name", Toast.LENGTH_SHORT).show()
    }

    fun flushProgress(overridePosMs: Long? = null, overrideDurMs: Long? = null) {
        val c = mediaController ?: return
        val pos = (overridePosMs ?: c.currentPosition).coerceAtLeast(0L)
        val dur = (overrideDurMs ?: c.duration).coerceAtLeast(0L)
        if (pos <= 0L && dur <= 0L) return
        val activeCand = candidates.getOrNull(activeCandidateIndex)
        latestOnProgressSave?.invoke(pos, dur, activeCand?.url, activeCand?.label)
    }

    fun handleBackAction() {
        if (isTouchLocked) {
            showUnlockPill = true
            return
        }
        flushProgress()
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        latestOnBack?.invoke() ?: activity?.finish()
    }

    fun enterPipMode() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && activity != null) {
            try {
                isInPipMode = true
                showControls = false
                val params = PictureInPictureParams.Builder()
                    .setAspectRatio(Rational(16, 9))
                    .build()
                activity.enterPictureInPictureMode(params)
            } catch (_: Exception) {
                isInPipMode = false
                Toast.makeText(context, "PiP is unavailable on this device", Toast.LENGTH_SHORT).show()
            }
        }
    }

    BackHandler(enabled = onBack != null) {
        handleBackAction()
    }

    fun playCandidate(controller: MediaController, candidate: StreamCandidate, seekPosMs: Long = 0L) {
        playerError = null
        val item = StreamRegistry.prepare(candidate, displayTitle, externalSubtitles)
        // Clear any previous ABR track override when switching sources
        controller.trackSelectionParameters = controller.trackSelectionParameters
            .buildUpon()
            .clearOverridesOfType(C.TRACK_TYPE_VIDEO)
            .build()
        selectedAbrLabel = "Auto"
        if (externalSubtitles.isNotEmpty() && selectedSubtitleLabel == "Off") {
            selectedSubtitleLabel = externalSubtitles.first().label.take(14)
        }
        controller.setMediaItem(item, seekPosMs)
        controller.prepare()
        controller.playWhenReady = true
    }

    fun recoverOrReportError(controller: MediaController, error: PlaybackException) {
        val resumePos = controller.currentPosition.coerceAtLeast(0L)
        val isHttpExpired = (error.cause as? HttpDataSource.InvalidResponseCodeException)?.responseCode in listOf(403, 410) ||
            error.errorCode == PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS

        // 1. Try next pre-extracted candidate if available
        val nextIndex = activeCandidateIndex + 1
        if (isOnlineSession && nextIndex < candidates.size) {
            activeCandidateIndex = nextIndex
            Toast.makeText(context, "Switching to backup stream (${candidates[nextIndex].label})...", Toast.LENGTH_SHORT).show()
            playCandidate(controller, candidates[nextIndex], resumePos)
            return
        }

        // 2. Re-extract fresh signed URLs once on HTTP 403/410
        val pageUrl = initialSession?.pageUrl
        if (isOnlineSession && isHttpExpired && !hasRetriedRefresh && videoRepository != null && !pageUrl.isNullOrBlank()) {
            hasRetriedRefresh = true
            isRefreshingStream = true
            playerError = null
            scope.launch {
                val refreshed = videoRepository.refreshStreams(pageUrl)
                isRefreshingStream = false
                if (refreshed.isNotEmpty()) {
                    StreamRegistry.replaceCandidates(videoUrl, refreshed)
                    candidates = refreshed
                    activeCandidateIndex = 0
                    playCandidate(controller, refreshed.first(), resumePos)
                } else {
                    playerError = "Stream link expired. Go back and re-scan the link."
                }
            }
            return
        }

        playerError = error.localizedMessage ?: "Playback failed (code ${error.errorCode})"
    }

    // Properly scoped MediaController lifecycle (reuses sharedController for zero-gap mini-player transitions)
    DisposableEffect(videoUrl, sharedController) {
        val externalController = sharedController
        val controllerFuture = if (externalController == null) {
            val sessionToken = SessionToken(context, ComponentName(context, PlaybackService::class.java))
            MediaController.Builder(context, sessionToken).buildAsync()
        } else null
        var boundController: MediaController? = null

        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
                if (!playing) {
                    boundController?.let { c ->
                        val activeCand = candidates.getOrNull(activeCandidateIndex)
                        latestOnProgressSave?.invoke(
                            c.currentPosition.coerceAtLeast(0L),
                            c.duration.coerceAtLeast(0L),
                            activeCand?.url,
                            activeCand?.label
                        )
                    }
                }
            }

            override fun onPlaybackStateChanged(state: Int) {
                playbackState = state
                boundController?.let { c ->
                    duration = c.duration.coerceAtLeast(0L)
                    bufferedPosition = c.bufferedPosition.coerceAtLeast(0L)
                    if (state == Player.STATE_ENDED && duration > 0L) {
                        val activeCand = candidates.getOrNull(activeCandidateIndex)
                        latestOnProgressSave?.invoke(duration, duration, activeCand?.url, activeCand?.label)
                    }
                }
            }

            override fun onTracksChanged(tracks: Tracks) {
                currentTracks = tracks
            }

            override fun onPlayerError(error: PlaybackException) {
                boundController?.let { c -> recoverOrReportError(c, error) }
                    ?: run { playerError = error.localizedMessage ?: "Playback error" }
            }
        }

        fun bindController(controller: MediaController) {
            boundController = controller
            mediaController = controller
            controller.addListener(listener)

            if (isOnlineSession) {
                val active = candidates.getOrNull(activeCandidateIndex)
                if (active != null) {
                    val currentUri = controller.currentMediaItem?.localConfiguration?.uri?.toString()
                    val alreadyPlayingCandidate = candidates.indexOfFirst { it.url == currentUri }
                    if (alreadyPlayingCandidate != -1) {
                        // Expanding from Mini Player — zero reload, keep exact frame & buffer
                        activeCandidateIndex = alreadyPlayingCandidate
                    } else {
                        playCandidate(controller, active, initialPositionMs)
                        if (initialPositionMs >= 3_000L) {
                            Toast.makeText(context, "Resuming at ${formatTime(initialPositionMs)}", Toast.LENGTH_SHORT).show()
                        }
                    }
                } else {
                    playerError = "Stream session expired. Please re-open the link."
                }
            } else {
                val currentUri = controller.currentMediaItem?.localConfiguration?.uri?.toString()
                if (currentUri != videoUrl) {
                    controller.setMediaItem(MediaItem.fromUri(videoUrl), initialPositionMs)
                    controller.prepare()
                    controller.playWhenReady = true
                    if (initialPositionMs >= 3_000L) {
                        Toast.makeText(context, "Resuming at ${formatTime(initialPositionMs)}", Toast.LENGTH_SHORT).show()
                    }
                }
            }

            isPlaying = controller.isPlaying
            playbackState = controller.playbackState
            duration = controller.duration.coerceAtLeast(0L)
            currentPosition = controller.currentPosition.coerceAtLeast(0L)
            bufferedPosition = controller.bufferedPosition.coerceAtLeast(0L)
            currentTracks = controller.currentTracks
            playbackSpeed = controller.playbackParameters.speed
        }

        if (externalController != null) {
            bindController(externalController)
        } else {
            controllerFuture?.addListener({
                try {
                    bindController(controllerFuture.get())
                } catch (e: Exception) {
                    playerError = "Could not connect to playback service: ${e.message}"
                }
            }, MoreExecutors.directExecutor())
        }

        onDispose {
            boundController?.let { c ->
                val pos = c.currentPosition.coerceAtLeast(0L)
                val dur = c.duration.coerceAtLeast(0L)
                if (pos > 0L || dur > 0L) {
                    val activeCand = candidates.getOrNull(activeCandidateIndex)
                    latestOnProgressSave?.invoke(pos, dur, activeCand?.url, activeCand?.label)
                }
                c.removeListener(listener)
            }
            mediaController = null
            if (controllerFuture != null) {
                MediaController.releaseFuture(controllerFuture)
            }
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    // Progress & buffer polling loop + periodic Watch History persistence
    LaunchedEffect(mediaController, playbackState, isPlaying) {
        var lastSavedPos = -1L
        var ticksSinceSave = 0
        while (mediaController != null && playbackState != Player.STATE_ENDED) {
            mediaController?.let { c ->
                val pos = c.currentPosition.coerceAtLeast(0L)
                if (!isDraggingSlider) {
                    currentPosition = pos
                }
                bufferedPosition = c.bufferedPosition.coerceAtLeast(0L)
                val d = c.duration
                if (d > 0) duration = d

                ticksSinceSave++
                if (ticksSinceSave >= 30 && kotlin.math.abs(pos - lastSavedPos) >= 2_000L) {
                    flushProgress(overridePosMs = pos, overrideDurMs = d.coerceAtLeast(0L))
                    lastSavedPos = pos
                    ticksSinceSave = 0
                }
            }
            delay(500)
        }
    }

    // Auto-hide controls while playing
    LaunchedEffect(showControls, isPlaying, isDraggingSlider) {
        if (showControls && isPlaying && !isDraggingSlider) {
            delay(3500)
            showControls = false
        }
    }

    LaunchedEffect(Unit) {
        delay(3000)
        showBackgroundPlayHint = false
    }

    // Sleep timer countdown
    LaunchedEffect(sleepTimerMinutesRemaining) {
        if (sleepTimerMinutesRemaining > 0) {
            var remaining = sleepTimerMinutesRemaining
            while (remaining > 0) {
                delay(60_000L)
                remaining--
                sleepTimerMinutesRemaining = remaining
            }
            mediaController?.pause()
            Toast.makeText(context, "Sleep timer finished — playback paused", Toast.LENGTH_SHORT).show()
        }
    }

    // End-of-movie sleep timer trigger
    LaunchedEffect(playbackState, sleepTimerMinutesRemaining) {
        if (sleepTimerMinutesRemaining == -1 && playbackState == Player.STATE_ENDED) {
            mediaController?.pause()
            sleepTimerMinutesRemaining = 0
        }
    }

    // Auto-hide touch-lock pill
    LaunchedEffect(showUnlockPill, isTouchLocked) {
        if (showUnlockPill && isTouchLocked) {
            delay(3000)
            showUnlockPill = false
        }
    }

    // Lifecycle Management (supports Picture-in-Picture cleanly without pausing while in PiP)
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            val inPip = activity?.isInPictureInPictureMode == true
            isInPipMode = inPip
            when (event) {
                Lifecycle.Event.ON_PAUSE, Lifecycle.Event.ON_STOP -> {
                    flushProgress()
                    if (!currentIsBackgroundPlayEnabled && !inPip) {
                        mediaController?.pause()
                    }
                }
                Lifecycle.Event.ON_RESUME -> {
                    isInPipMode = inPip
                    if (mediaController?.playWhenReady == true && playerError == null) {
                        mediaController?.play()
                    }
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Extract available ABR video tracks, audio tracks & subtitle tracks from Media3 Tracks
    val abrTracks = remember(currentTracks) {
        buildList {
            currentTracks.groups.forEachIndexed { gIdx, group ->
                if (group.type == C.TRACK_TYPE_VIDEO) {
                    for (tIdx in 0 until group.length) {
                        if (group.isTrackSupported(tIdx)) {
                            val fmt = group.getTrackFormat(tIdx)
                            if (fmt.height > 0) {
                                val bitrateMbps = if (fmt.bitrate > 0) String.format(Locale.US, " • %.1f Mbps", fmt.bitrate / 1_000_000f) else ""
                                add(AbrTrackChoice("${fmt.height}p$bitrateMbps", gIdx, tIdx, fmt.height))
                            }
                        }
                    }
                }
            }
        }.distinctBy { it.height }.sortedByDescending { it.height }
    }

    val audioTracks = remember(currentTracks) {
        buildList {
            currentTracks.groups.forEachIndexed { gIdx, group ->
                if (group.type == C.TRACK_TYPE_AUDIO) {
                    for (tIdx in 0 until group.length) {
                        if (group.isTrackSupported(tIdx)) {
                            val fmt = group.getTrackFormat(tIdx)
                            val lang = fmt.language?.let { Locale.forLanguageTag(it).displayLanguage }?.takeIf { it.isNotBlank() }
                            val name = listOfNotNull(fmt.label, lang).distinct().joinToString(" • ")
                                .ifBlank { "Audio Track ${tIdx + 1}" }
                            add(AudioTrackChoice(name, gIdx, tIdx))
                        }
                    }
                }
            }
        }
    }

    val subtitleTracks = remember(currentTracks) {
        buildList {
            currentTracks.groups.forEachIndexed { gIdx, group ->
                if (group.type == C.TRACK_TYPE_TEXT) {
                    for (tIdx in 0 until group.length) {
                        val fmt = group.getTrackFormat(tIdx)
                        val name = fmt.label ?: fmt.language?.let { Locale.forLanguageTag(it).displayLanguage } ?: "Track ${tIdx + 1}"
                        add(SubtitleChoice(name, gIdx, tIdx))
                    }
                }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(isTouchLocked) {
                detectTapGestures(
                    onTap = {
                        if (isTouchLocked) {
                            showUnlockPill = !showUnlockPill
                        } else {
                            showControls = !showControls
                        }
                    },
                    onDoubleTap = { offset ->
                        if (isTouchLocked) {
                            showUnlockPill = true
                            return@detectTapGestures
                        }
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        val isLeft = offset.x < size.width / 2
                        seekJump = if (isLeft) -10 else 10
                        mediaController?.let {
                            val maxDur = if (duration > 0) duration else Long.MAX_VALUE
                            val target = (it.currentPosition + (seekJump * 1000L)).coerceIn(0L, maxDur)
                            it.seekTo(target)
                            flushProgress(overridePosMs = target)
                        }
                    }
                )
            }
            .pointerInput(isTouchLocked) {
                detectVerticalDragGestures(
                    onDragStart = { offset ->
                        if (isTouchLocked) return@detectVerticalDragGestures
                        val isLeft = offset.x < size.width / 2
                        gestureType = if (isLeft) GestureType.BRIGHTNESS else GestureType.VOLUME

                        if (gestureType == GestureType.BRIGHTNESS) {
                            activity?.window?.let { window ->
                                val params = window.attributes
                                initialGestureValue = if (params.screenBrightness < 0) {
                                    try {
                                        Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS) / 255f
                                    } catch (_: Exception) {
                                        0.5f
                                    }
                                } else params.screenBrightness
                            }
                        } else {
                            maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
                            initialGestureValue = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / maxVolume
                        }
                        gestureValue = initialGestureValue
                    },
                    onDragEnd = { gestureType = null },
                    onVerticalDrag = { _, dragAmount ->
                        if (isTouchLocked) return@detectVerticalDragGestures
                        val sensitivity = 0.003f
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
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    keepScreenOn = true
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
            },
            update = { view ->
                if (view.player !== mediaController) {
                    view.player = mediaController
                }
                view.resizeMode = resizeMode
            },
            onRelease = { view ->
                view.player = null
            },
            modifier = Modifier.fillMaxSize()
        )

        // Buffering / Refreshing Spinner
        if ((playbackState == Player.STATE_BUFFERING || isRefreshingStream) && playerError == null) {
            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                CircularProgressIndicator(
                    color = OmegaTheme.colors.primary,
                    strokeWidth = 3.dp,
                    modifier = Modifier.size(48.dp)
                )
                if (isRefreshingStream) {
                    Spacer(Modifier.height(10.dp))
                    Text("Refreshing stream link...", color = Color.White, fontSize = 13.sp)
                }
            }
        }

        // Error Overlay
        playerError?.let { err ->
            Surface(
                color = Color.Black.copy(alpha = 0.85f),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 32.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(Icons.Default.ErrorOutline, null, tint = Color(0xFFFF5722), modifier = Modifier.size(40.dp))
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = err,
                        color = Color.White,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedButton(onClick = { handleBackAction() }) {
                            Text("Back", color = Color.White)
                        }
                        Button(
                            onClick = {
                                playerError = null
                                hasRetriedRefresh = false
                                mediaController?.let { c ->
                                    val candidate = candidates.getOrNull(activeCandidateIndex)
                                    if (isOnlineSession && candidate != null) {
                                        playCandidate(c, candidate, currentPosition)
                                    } else {
                                        c.prepare()
                                        c.play()
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = OmegaTheme.colors.primary)
                        ) {
                            Text("Retry", color = Color.White)
                        }
                    }
                }
            }
        }

        // Gesture Indicator
        AnimatedVisibility(
            visible = gestureType != null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.Center)
        ) {
            GestureIndicatorOverlay(type = gestureType, value = gestureValue)
        }

        // Seek Indicator
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

        // Touch-Lock Unlock Pill
        AnimatedVisibility(
            visible = isTouchLocked && showUnlockPill && !isInPipMode,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 32.dp)
        ) {
            Surface(
                onClick = {
                    isTouchLocked = false
                    showUnlockPill = false
                    showControls = true
                },
                color = Color.Black.copy(alpha = 0.78f),
                shape = CircleShape,
                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.3f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.LockOpen, null, tint = Color(0xFF2979FF), modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Screen Locked • Tap to Unlock", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        // Controls Layer
        AnimatedVisibility(
            visible = showControls && !isTouchLocked && !isInPipMode && playerError == null,
            enter = fadeIn(tween(250)),
            exit = fadeOut(tween(250))
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                // Top Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.55f))
                        .statusBarsPadding()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        IconButton(onClick = { handleBackAction() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Minimize to Mini Player", tint = Color.White)
                        }
                        Text(
                            text = displayTitle,
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Picture-in-Picture
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            IconButton(onClick = { enterPipMode() }) {
                                Icon(Icons.Default.PictureInPictureAlt, "Picture-in-Picture", tint = Color.White)
                            }
                        }

                        // In-player Download button for online streams
                        if (isOnlineSession && downloadRepository != null && candidates.isNotEmpty()) {
                            IconButton(onClick = {
                                val active = candidates.getOrElse(activeCandidateIndex) { candidates.first() }
                                val canUseDirectUrl = active.kind == StreamKind.PROGRESSIVE && active.audioUrl == null
                                val quality = VideoQuality(
                                    label = active.label,
                                    url = active.url,
                                    size = if (active.sizeBytes > 0) StorageUtils.formatSize(active.sizeBytes) else "Variable",
                                    type = "video",
                                    formatId = "best",
                                    isCombined = true,
                                    sizeBytes = active.sizeBytes,
                                    useDirectUrl = canUseDirectUrl,
                                    headers = active.headers
                                )
                                val meta = VideoMetadata(
                                    title = displayTitle,
                                    qualities = listOf(quality),
                                    thumbnailUrl = initialSession?.thumbnailUrl,
                                    sourceUrl = initialSession?.pageUrl ?: active.url,
                                    streams = candidates,
                                    durationSec = duration / 1000L,
                                    subtitleTracks = externalSubtitles
                                )
                                downloadRepository.startDownload(meta, quality, meta.sourceUrl)
                                Toast.makeText(context, "Download started in background", Toast.LENGTH_SHORT).show()
                            }) {
                                Icon(Icons.Default.Download, "Download Movie", tint = Color(0xFF00E676))
                            }
                        }

                        Box(contentAlignment = Alignment.TopCenter) {
                            IconButton(onClick = {
                                isBackgroundPlayEnabled = !isBackgroundPlayEnabled
                                PlaybackService.isBackgroundPlayEnabled = isBackgroundPlayEnabled
                            }) {
                                Icon(
                                    imageVector = if (isBackgroundPlayEnabled) Icons.Default.Headset else Icons.Default.HeadsetOff,
                                    contentDescription = "Background Play",
                                    tint = if (isBackgroundPlayEnabled) Color(0xFF2979FF) else Color.White
                                )
                            }
                            BackgroundPlayHint(visible = showBackgroundPlayHint)
                        }

                        IconButton(onClick = {
                            activity?.requestedOrientation =
                                if (activity.requestedOrientation == ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE) {
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
                            Icon(Icons.Default.AspectRatio, "Resize", tint = Color.White)
                        }
                    }
                }

                // Center Transport Row (-10s, Play/Pause, +10s)
                if (playbackState != Player.STATE_BUFFERING && !isRefreshingStream) {
                    Row(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalArrangement = Arrangement.spacedBy(28.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = {
                                mediaController?.let {
                                    val target = (it.currentPosition - 10_000L).coerceAtLeast(0L)
                                    it.seekTo(target)
                                    flushProgress(overridePosMs = target)
                                    seekJump = -10
                                }
                            },
                            modifier = Modifier
                                .size(54.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.4f))
                        ) {
                            Icon(Icons.Default.Replay10, "Back 10s", tint = Color.White, modifier = Modifier.size(30.dp))
                        }

                        IconButton(
                            onClick = {
                                mediaController?.let {
                                    if (it.isPlaying) it.pause() else it.play()
                                }
                            },
                            modifier = Modifier
                                .size(76.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.45f))
                        ) {
                            Icon(
                                if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (isPlaying) "Pause" else "Play",
                                tint = Color.White,
                                modifier = Modifier.size(46.dp)
                            )
                        }

                        IconButton(
                            onClick = {
                                mediaController?.let {
                                    val maxDur = if (duration > 0) duration else Long.MAX_VALUE
                                    val target = (it.currentPosition + 10_000L).coerceAtMost(maxDur)
                                    it.seekTo(target)
                                    flushProgress(overridePosMs = target)
                                    seekJump = 10
                                }
                            },
                            modifier = Modifier
                                .size(54.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.4f))
                        ) {
                            Icon(Icons.Default.Forward10, "Forward 10s", tint = Color.White, modifier = Modifier.size(30.dp))
                        }
                    }
                }

                // Bottom Controls
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.6f))
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 14.dp)
                ) {
                    val shownPos = if (isDraggingSlider) dragPosition.toLong() else currentPosition
                    if (isDraggingSlider) {
                        Surface(
                            color = Color(0xFF2979FF),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .align(Alignment.CenterHorizontally)
                                .padding(bottom = 6.dp)
                        ) {
                            Text(
                                text = formatTime(shownPos),
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                            )
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(formatTime(shownPos), color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        Text(formatTime(duration), color = Color.White.copy(alpha = 0.8f), fontSize = 13.sp)
                    }

                    // Buffered indicator + Seek-on-release Slider
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        val bufferFraction = if (duration > 0) (bufferedPosition.toFloat() / duration.toFloat()).coerceIn(0f, 1f) else 0f
                        LinearProgressIndicator(
                            progress = { bufferFraction },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp)
                                .height(3.dp)
                                .clip(CircleShape),
                            color = Color.White.copy(alpha = 0.45f),
                            trackColor = Color.White.copy(alpha = 0.15f)
                        )
                        Slider(
                            value = shownPos.toFloat().coerceIn(0f, duration.toFloat().coerceAtLeast(1f)),
                            onValueChange = { newVal ->
                                isDraggingSlider = true
                                dragPosition = newVal
                            },
                            onValueChangeFinished = {
                                val target = dragPosition.toLong()
                                currentPosition = target
                                mediaController?.seekTo(target)
                                flushProgress(overridePosMs = target)
                                isDraggingSlider = false
                            },
                            valueRange = 0f..duration.toFloat().coerceAtLeast(1f),
                            colors = SliderDefaults.colors(
                                thumbColor = Color(0xFF2979FF),
                                activeTrackColor = Color(0xFF2979FF),
                                inactiveTrackColor = Color.Transparent
                            )
                        )
                    }

                    // Bottom Action Chips: Lock, Speed, Quality, Audio, Subtitles, Sleep Timer
                    androidx.compose.foundation.lazy.LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        item {
                            PlayerChip(
                                icon = Icons.Default.Lock,
                                label = "Lock",
                                onClick = {
                                    isTouchLocked = true
                                    showControls = false
                                    showUnlockPill = true
                                }
                            )
                        }
                        item {
                            PlayerChip(
                                icon = Icons.Default.Speed,
                                label = if (playbackSpeed == 1f) "1x" else "${playbackSpeed}x",
                                onClick = { showSpeedDialog = true }
                            )
                        }

                        if (abrTracks.isNotEmpty() || candidates.size > 1) {
                            item {
                                val qualityLabel = if (abrTracks.isNotEmpty()) selectedAbrLabel
                                else candidates.getOrNull(activeCandidateIndex)?.label ?: "Auto"
                                PlayerChip(
                                    icon = Icons.Default.HighQuality,
                                    label = qualityLabel,
                                    onClick = { showQualityDialog = true }
                                )
                            }
                        }

                        if (audioTracks.size > 1) {
                            item {
                                PlayerChip(
                                    icon = Icons.Default.Audiotrack,
                                    label = selectedAudioLabel,
                                    onClick = { showAudioTrackDialog = true }
                                )
                            }
                        }

                        item {
                            PlayerChip(
                                icon = Icons.Default.ClosedCaption,
                                label = if (selectedSubtitleLabel == "Off") "CC" else selectedSubtitleLabel.take(12),
                                onClick = { showSubtitleDialog = true }
                            )
                        }

                        item {
                            val sleepLabel = when {
                                sleepTimerMinutesRemaining == -1 -> "End"
                                sleepTimerMinutesRemaining > 0 -> "${sleepTimerMinutesRemaining}m"
                                else -> "Sleep"
                            }
                            PlayerChip(
                                icon = Icons.Default.Bedtime,
                                label = sleepLabel,
                                onClick = { showSleepTimerDialog = true }
                            )
                        }
                    }
                }
            }
        }
    }

    // Speed Picker Dialog
    if (showSpeedDialog) {
        val speeds = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f)
        AlertDialog(
            onDismissRequest = { showSpeedDialog = false },
            title = { Text("Playback Speed", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    speeds.forEach { spd ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    playbackSpeed = spd
                                    mediaController?.setPlaybackSpeed(spd)
                                    showSpeedDialog = false
                                }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(if (spd == 1.0f) "Normal (1.0x)" else "${spd}x")
                            if (playbackSpeed == spd) {
                                Icon(Icons.Default.Check, null, tint = OmegaTheme.colors.primary)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSpeedDialog = false }) { Text("Close") }
            }
        )
    }

    // Quality Picker Dialog (supports both HLS/DASH ABR tracks and multi-source candidates)
    if (showQualityDialog) {
        AlertDialog(
            onDismissRequest = { showQualityDialog = false },
            title = { Text("Stream Quality", fontWeight = FontWeight.Bold) },
            text = {
                LazyColumn(modifier = Modifier.heightIn(max = 320.dp)) {
                    if (abrTracks.isNotEmpty()) {
                        item {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        mediaController?.let { c ->
                                            c.trackSelectionParameters = c.trackSelectionParameters
                                                .buildUpon()
                                                .clearOverridesOfType(C.TRACK_TYPE_VIDEO)
                                                .build()
                                        }
                                        selectedAbrLabel = "Auto"
                                        showQualityDialog = false
                                    }
                                    .padding(vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Auto (Adaptive)", fontWeight = FontWeight.SemiBold)
                                if (selectedAbrLabel == "Auto") Icon(Icons.Default.Check, null, tint = OmegaTheme.colors.primary)
                            }
                        }
                        itemsIndexed(abrTracks) { _, track ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        mediaController?.let { c ->
                                            val group = currentTracks.groups.getOrNull(track.groupIndex)
                                            if (group != null) {
                                                c.trackSelectionParameters = c.trackSelectionParameters
                                                    .buildUpon()
                                                    .setOverrideForType(
                                                        TrackSelectionOverride(group.mediaTrackGroup, track.trackIndex)
                                                    )
                                                    .build()
                                                selectedAbrLabel = "${track.height}p"
                                            }
                                        }
                                        showQualityDialog = false
                                    }
                                    .padding(vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(track.label)
                                if (selectedAbrLabel == "${track.height}p") {
                                    Icon(Icons.Default.Check, null, tint = OmegaTheme.colors.primary)
                                }
                            }
                        }
                    }

                    if (candidates.size > 1) {
                        if (abrTracks.isNotEmpty()) {
                            item {
                                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                                Text(
                                    "Alternative Sources",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                    modifier = Modifier.padding(bottom = 4.dp)
                                )
                            }
                        }
                        itemsIndexed(candidates) { idx, cand ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        activeCandidateIndex = idx
                                        mediaController?.let { c ->
                                            playCandidate(c, cand, currentPosition)
                                        }
                                        showQualityDialog = false
                                    }
                                    .padding(vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(cand.label)
                                if (idx == activeCandidateIndex) {
                                    Icon(Icons.Default.Check, null, tint = OmegaTheme.colors.primary)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showQualityDialog = false }) { Text("Close") }
            }
        )
    }

    // Subtitle / CC Picker Dialog + External .srt/.vtt Loader
    if (showSubtitleDialog) {
        AlertDialog(
            onDismissRequest = { showSubtitleDialog = false },
            title = { Text("Subtitles", fontWeight = FontWeight.Bold) },
            text = {
                LazyColumn(modifier = Modifier.heightIn(max = 300.dp)) {
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    mediaController?.let { c ->
                                        c.trackSelectionParameters = c.trackSelectionParameters
                                            .buildUpon()
                                            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                                            .build()
                                    }
                                    selectedSubtitleLabel = "Off"
                                    showSubtitleDialog = false
                                }
                                .padding(vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Off")
                            if (selectedSubtitleLabel == "Off") Icon(Icons.Default.Check, null, tint = Color(0xFF2979FF))
                        }
                    }
                    itemsIndexed(subtitleTracks) { _, sub ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    mediaController?.let { c ->
                                        val group = currentTracks.groups.getOrNull(sub.groupIndex)
                                        if (group != null) {
                                            c.trackSelectionParameters = c.trackSelectionParameters
                                                .buildUpon()
                                                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                                                .setOverrideForType(
                                                    TrackSelectionOverride(group.mediaTrackGroup, sub.trackIndex)
                                                )
                                                .build()
                                            selectedSubtitleLabel = sub.label
                                        }
                                    }
                                    showSubtitleDialog = false
                                }
                                .padding(vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(sub.label)
                            if (selectedSubtitleLabel == sub.label) Icon(Icons.Default.Check, null, tint = Color(0xFF2979FF))
                        }
                    }
                    item {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                        TextButton(
                            onClick = {
                                showSubtitleDialog = false
                                subtitlePickerLauncher.launch(arrayOf("*/*"))
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.FolderOpen, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Load Local .SRT / .VTT File", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSubtitleDialog = false }) { Text("Close") }
            }
        )
    }

    // Audio Track Picker Dialog
    if (showAudioTrackDialog) {
        AlertDialog(
            onDismissRequest = { showAudioTrackDialog = false },
            title = { Text("Audio Track", fontWeight = FontWeight.Bold) },
            text = {
                LazyColumn(modifier = Modifier.heightIn(max = 280.dp)) {
                    itemsIndexed(audioTracks) { _, track ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    mediaController?.let { c ->
                                        val group = currentTracks.groups.getOrNull(track.groupIndex)
                                        if (group != null) {
                                            c.trackSelectionParameters = c.trackSelectionParameters
                                                .buildUpon()
                                                .setOverrideForType(
                                                    TrackSelectionOverride(group.mediaTrackGroup, track.trackIndex)
                                                )
                                                .build()
                                            selectedAudioLabel = track.label.take(14)
                                        }
                                    }
                                    showAudioTrackDialog = false
                                }
                                .padding(vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(track.label)
                            if (selectedAudioLabel == track.label.take(14)) {
                                Icon(Icons.Default.Check, null, tint = Color(0xFF2979FF))
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAudioTrackDialog = false }) { Text("Close") }
            }
        )
    }

    // Sleep Timer Dialog
    if (showSleepTimerDialog) {
        val options = listOf(
            "Off" to 0,
            "15 minutes" to 15,
            "30 minutes" to 30,
            "45 minutes" to 45,
            "60 minutes" to 60,
            "End of Movie" to -1
        )
        AlertDialog(
            onDismissRequest = { showSleepTimerDialog = false },
            title = { Text("Sleep Timer", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    options.forEach { (label, mins) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    sleepTimerMinutesRemaining = mins
                                    showSleepTimerDialog = false
                                    val msg = when (mins) {
                                        0 -> "Sleep timer turned off"
                                        -1 -> "Playback will pause at end of movie"
                                        else -> "Playback will pause in $mins minutes"
                                    }
                                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                }
                                .padding(vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(label)
                            if (sleepTimerMinutesRemaining == mins) {
                                Icon(Icons.Default.Check, null, tint = Color(0xFF2979FF))
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSleepTimerDialog = false }) { Text("Close") }
            }
        )
    }
}

@Composable
private fun PlayerChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        color = Color.White.copy(alpha = 0.14f),
        shape = RoundedCornerShape(10.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, null, tint = Color.White, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(label, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
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
            color = OmegaTheme.colors.primary,
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
    val safeMs = ms.coerceAtLeast(0L)
    val totalSeconds = safeMs / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format(Locale.getDefault(), "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
    }
}
