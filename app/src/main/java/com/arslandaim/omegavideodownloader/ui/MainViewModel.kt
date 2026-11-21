/*
 * Omega Video Downloader Project Original (2026)
 * arslandaim-hub (GitHub.com/arslandaim-hub)
 * Licenced Under GPL-3.0+
*/
package com.arslandaim.omegavideodownloader.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arslandaim.omegavideodownloader.ActiveDownload
import com.arslandaim.omegavideodownloader.ActiveVideoSession
import com.arslandaim.omegavideodownloader.DownloadedVideo
import com.arslandaim.omegavideodownloader.LikedVideo
import com.arslandaim.omegavideodownloader.MovieSearchResult
import com.arslandaim.omegavideodownloader.ResolveSource
import com.arslandaim.omegavideodownloader.SearchSource
import com.arslandaim.omegavideodownloader.SettingsManager
import com.arslandaim.omegavideodownloader.StreamCandidate
import com.arslandaim.omegavideodownloader.StreamKind
import com.arslandaim.omegavideodownloader.VideoMetadata
import com.arslandaim.omegavideodownloader.WatchHistoryItem
import com.arslandaim.omegavideodownloader.data.DownloadRepository
import com.arslandaim.omegavideodownloader.data.VideoRepository
import com.arslandaim.omegavideodownloader.player.StreamRegistry
import com.arslandaim.omegavideodownloader.resolver.ResolveResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainViewModel(
    private val settingsManager: SettingsManager,
    private val downloadRepository: DownloadRepository,
    private val videoRepository: VideoRepository
) : ViewModel() {

    companion object {
        private const val UPDATE_INTERVAL_MS = 24L * 60 * 60 * 1000 // 24 hours
    }

    val themeMode: StateFlow<String> = settingsManager.themeMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "System")

    val themeColorPreset: StateFlow<String> = settingsManager.themeColorPreset
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), com.arslandaim.omegavideodownloader.ui.theme.ThemeColorPreset.ELECTRIC_OCEAN.id)

    val appLanguage: StateFlow<String> = settingsManager.appLanguage
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "English (US)")

    val activeDownloads: StateFlow<List<ActiveDownload>> = settingsManager.activeDownloads
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val currentlyPlayingPath: StateFlow<String?> = settingsManager.currentlyPlayingPath
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val watchHistory: StateFlow<List<WatchHistoryItem>> = settingsManager.watchHistory
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val likedVideos: StateFlow<List<LikedVideo>> = settingsManager.likedVideos
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val likedUrls: StateFlow<Set<String>> = settingsManager.likedVideos
        .map { list -> list.mapTo(HashSet()) { it.pageUrl.trim() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    val lastActiveTab: StateFlow<Int> = settingsManager.lastActiveTab
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val wifiOnlyDownloads: StateFlow<Boolean> = settingsManager.wifiOnlyDownloads
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val pauseContinueWatching: StateFlow<Boolean> = settingsManager.pauseContinueWatching
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    // Active Video Player + Mini Player State
    var activeVideoSession by mutableStateOf<ActiveVideoSession?>(null)
        private set

    var isVideoPlayerExpanded by mutableStateOf(false)
        private set

    /** Non-null while re-resolving a Watch History stream before opening the player. */
    var resumingHistoryId by mutableStateOf<String?>(null)
        private set

    var resumeToastMessage by mutableStateOf<String?>(null)
        private set

    var pendingAudioTrack by mutableStateOf<DownloadedVideo?>(null)
        private set

    private var resumeJob: Job? = null

    fun requestAudioPlayback(video: DownloadedVideo) {
        pendingAudioTrack = video
    }

    fun consumePendingAudioTrack() {
        pendingAudioTrack = null
    }

    init {
        autoUpdateYtDlpIfDue()
    }

    private fun autoUpdateYtDlpIfDue() {
        viewModelScope.launch(Dispatchers.IO) {
            val autoUpdate = settingsManager.autoUpdateYtDlp.first()
            if (!autoUpdate) return@launch
            val lastUpdate = settingsManager.lastYtDlpUpdate.first()
            val now = System.currentTimeMillis()
            if (now - lastUpdate < UPDATE_INTERVAL_MS) return@launch
            val result = videoRepository.updateYtDlpBinary()
            if (result.isSuccess) {
                settingsManager.setLastYtDlpUpdate(now)
            }
        }
    }

    fun consumeResumeToast() {
        resumeToastMessage = null
    }

    fun setCurrentlyPlaying(path: String?) {
        settingsManager.setCurrentlyPlaying(path)
    }

    fun setLastActiveTab(index: Int) {
        viewModelScope.launch {
            settingsManager.setLastActiveTab(index)
        }
    }

    fun cancelDownload(id: Long) {
        downloadRepository.cancelDownload(id)
    }

    fun pauseDownload(id: Long) {
        downloadRepository.pauseDownload(id)
    }

    fun resumeDownload(id: Long) {
        downloadRepository.resumeDownload(id)
    }

    /** Opens a downloaded (or locked) local video in Full-Screen player, resuming where left off. */
    fun openLocalVideo(video: DownloadedVideo) {
        resumeJob?.cancel()
        resumingHistoryId = null
        val historyId = WatchHistoryItem.localId(video.localPath)
        // If already active in mini player, expand immediately without reloading
        if (activeVideoSession?.historyId == historyId && activeVideoSession?.videoUrl == video.localPath) {
            isVideoPlayerExpanded = true
            return
        }
        viewModelScope.launch {
            val resumeMs = if (video.isLocked) 0L else settingsManager.getResumePosition(historyId)
            activeVideoSession = ActiveVideoSession(
                historyId = historyId,
                videoUrl = video.localPath,
                title = video.title,
                thumbnailUrl = video.thumbnailUrl,
                sourceUrl = video.localPath,
                isStream = false,
                isLocked = video.isLocked,
                initialPositionMs = resumeMs
            )
            isVideoPlayerExpanded = true
        }
    }

    /** Opens an online streaming session created in [StreamRegistry], resuming where left off. */
    fun openOnlineStream(sessionKey: String) {
        resumeJob?.cancel()
        resumingHistoryId = null
        val session = StreamRegistry.session(sessionKey) ?: return
        val sourceUrl = session.pageUrl.ifBlank { session.candidates.firstOrNull()?.url ?: sessionKey }
        val historyId = WatchHistoryItem.streamId(sourceUrl)

        viewModelScope.launch {
            val resumeMs = settingsManager.getResumePosition(historyId)
            activeVideoSession = ActiveVideoSession(
                historyId = historyId,
                videoUrl = sessionKey,
                title = session.title,
                thumbnailUrl = session.thumbnailUrl,
                sourceUrl = sourceUrl,
                isStream = true,
                isLocked = false,
                initialPositionMs = resumeMs
            )
            isVideoPlayerExpanded = true
        }
    }

    /** Resumes any [WatchHistoryItem] (downloaded file or online stream) at its saved timestamp. */
    fun resumeWatchHistoryItem(item: WatchHistoryItem) {
        // If this item is already loaded in the mini player, just expand it immediately
        if (activeVideoSession?.historyId == item.id) {
            isVideoPlayerExpanded = true
            return
        }

        resumeJob?.cancel()
        if (!item.isStream) {
            resumeJob = viewModelScope.launch {
                val exists = withContext(Dispatchers.IO) {
                    settingsManager.doesMediaFileExist(item.sourceUrl)
                }
                if (!exists) {
                    resumeToastMessage = "Video file was moved or deleted from device storage."
                    return@launch
                }
                activeVideoSession = ActiveVideoSession(
                    historyId = item.id,
                    videoUrl = item.sourceUrl,
                    title = item.title,
                    thumbnailUrl = item.thumbnailUrl,
                    sourceUrl = item.sourceUrl,
                    isStream = false,
                    isLocked = false,
                    initialPositionMs = item.resumePositionMs
                )
                isVideoPlayerExpanded = true
            }
            return
        }

        // Fast path: check if an active in-memory StreamRegistry session already exists for this URL
        val existingKey = StreamRegistry.findSessionByPageUrl(item.sourceUrl)
        if (existingKey != null) {
            activeVideoSession = ActiveVideoSession(
                historyId = item.id,
                videoUrl = existingKey,
                title = item.title,
                thumbnailUrl = item.thumbnailUrl,
                sourceUrl = item.sourceUrl,
                isStream = true,
                isLocked = false,
                initialPositionMs = item.resumePositionMs
            )
            isVideoPlayerExpanded = true
            return
        }

        // Re-resolve stream URLs (handles app restarts and expired signed CDN links)
        resumingHistoryId = item.id
        resumeJob = viewModelScope.launch {
            try {
                when (val result = videoRepository.resolveForResume(item)) {
                    is ResolveResult.Success -> {
                        val streams = result.metadata.streams
                        if (streams.isNotEmpty()) {
                            val preferred = item.qualityLabel?.let { lbl ->
                                streams.firstOrNull { it.label.equals(lbl, ignoreCase = true) }
                            }
                            val ordered = if (preferred != null) {
                                listOf(preferred) + streams.filter { it.url != preferred.url }
                            } else {
                                streams
                            }
                            val key = StreamRegistry.createSession(
                                title = item.title.ifBlank { result.metadata.title },
                                pageUrl = item.sourceUrl,
                                candidates = ordered,
                                thumbnailUrl = item.thumbnailUrl ?: result.metadata.thumbnailUrl,
                                subtitleTracks = result.metadata.subtitleTracks
                            )
                            activeVideoSession = ActiveVideoSession(
                                historyId = item.id,
                                videoUrl = key,
                                title = item.title.ifBlank { result.metadata.title },
                                thumbnailUrl = item.thumbnailUrl ?: result.metadata.thumbnailUrl,
                                sourceUrl = item.sourceUrl,
                                isStream = true,
                                isLocked = false,
                                initialPositionMs = item.resumePositionMs
                            )
                            isVideoPlayerExpanded = true
                            return@launch
                        }
                    }
                    else -> {}
                }

                // Fallback: try last known direct stream URL if present (e.g., Archive.org direct MP4)
                val fallbackUrl = item.lastStreamUrl
                if (!fallbackUrl.isNullOrBlank() && fallbackUrl.startsWith("http", ignoreCase = true)) {
                    val kind = when {
                        fallbackUrl.contains(".m3u8", ignoreCase = true) -> StreamKind.HLS
                        fallbackUrl.contains(".mpd", ignoreCase = true) -> StreamKind.DASH
                        else -> StreamKind.PROGRESSIVE
                    }
                    val candidate = StreamCandidate(
                        url = fallbackUrl,
                        kind = kind,
                        label = item.qualityLabel ?: "Auto",
                        source = ResolveSource.DIRECT
                    )
                    val key = StreamRegistry.createSession(
                        title = item.title,
                        pageUrl = item.sourceUrl,
                        candidates = listOf(candidate),
                        thumbnailUrl = item.thumbnailUrl
                    )
                    activeVideoSession = ActiveVideoSession(
                        historyId = item.id,
                        videoUrl = key,
                        title = item.title,
                        thumbnailUrl = item.thumbnailUrl,
                        sourceUrl = item.sourceUrl,
                        isStream = true,
                        isLocked = false,
                        initialPositionMs = item.resumePositionMs
                    )
                    isVideoPlayerExpanded = true
                } else {
                    resumeToastMessage = "Could not resume stream — link may have expired."
                }
            } finally {
                if (resumingHistoryId == item.id) {
                    resumingHistoryId = null
                }
            }
        }
    }

    /** Collapses full-screen player into the docked Mini Player (closes immediately for locked videos). */
    fun minimizeVideoPlayer() {
        val current = activeVideoSession ?: return
        if (current.isLocked) {
            closeVideoPlayer()
        } else {
            isVideoPlayerExpanded = false
        }
    }

    fun expandVideoPlayer() {
        if (activeVideoSession != null) {
            isVideoPlayerExpanded = true
        }
    }

    fun closeVideoPlayer() {
        activeVideoSession = null
        isVideoPlayerExpanded = false
    }

    fun saveWatchProgress(
        session: ActiveVideoSession,
        positionMs: Long,
        durationMs: Long,
        lastStreamUrl: String? = null,
        qualityLabel: String? = null
    ) {
        if (session.isLocked) return
        val safePos = positionMs.coerceAtLeast(0L)
        val safeDur = durationMs.coerceAtLeast(0L)
        // Record entry once we have a valid duration or playback started
        if (safePos <= 0L && safeDur <= 0L) return

        viewModelScope.launch {
            settingsManager.upsertWatchHistory(
                WatchHistoryItem(
                    id = session.historyId,
                    title = session.title,
                    thumbnailUrl = session.thumbnailUrl,
                    isStream = session.isStream,
                    sourceUrl = session.sourceUrl,
                    lastStreamUrl = lastStreamUrl,
                    qualityLabel = qualityLabel,
                    positionMs = safePos,
                    durationMs = safeDur,
                    lastWatchedAt = System.currentTimeMillis()
                )
            )
        }
    }

    fun deleteWatchHistoryItem(id: String) {
        viewModelScope.launch {
            settingsManager.removeWatchHistoryItem(id)
        }
    }

    fun clearWatchHistory() {
        viewModelScope.launch {
            settingsManager.clearWatchHistory()
        }
    }

    fun toggleLike(result: MovieSearchResult) {
        viewModelScope.launch {
            settingsManager.toggleLikedVideo(LikedVideo.fromSearchResult(result))
        }
    }

    fun toggleLikeMetadata(metadata: VideoMetadata) {
        val pageUrl = metadata.sourceUrl.trim()
        if (pageUrl.isBlank()) return
        val source = when {
            metadata.resolvedBy == ResolveSource.ARCHIVE || pageUrl.contains("archive.org", ignoreCase = true) -> SearchSource.ARCHIVE_ORG
            pageUrl.contains("dailymotion.com", ignoreCase = true) || pageUrl.contains("dai.ly", ignoreCase = true) -> SearchSource.DAILYMOTION
            pageUrl.contains("youtu.be", ignoreCase = true) || pageUrl.contains("youtube.com", ignoreCase = true) -> SearchSource.YOUTUBE
            else -> SearchSource.PEERTUBE
        }
        val sizeBytes = metadata.qualities.maxOfOrNull { it.sizeBytes } ?: 0L
        viewModelScope.launch {
            settingsManager.toggleLikedVideo(
                LikedVideo(
                    id = pageUrl,
                    title = metadata.title,
                    pageUrl = pageUrl,
                    source = source,
                    thumbnailUrl = metadata.thumbnailUrl,
                    durationSec = metadata.durationSec,
                    sizeBytes = sizeBytes,
                    likedAt = System.currentTimeMillis()
                )
            )
        }
    }

    fun removeLikedVideo(pageUrl: String) {
        viewModelScope.launch {
            settingsManager.removeLikedVideo(pageUrl)
        }
    }
}
