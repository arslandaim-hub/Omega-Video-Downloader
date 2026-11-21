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
import com.arslandaim.omegavideodownloader.MovieSearchResult
import com.arslandaim.omegavideodownloader.SearchFilter
import com.arslandaim.omegavideodownloader.SettingsManager
import com.arslandaim.omegavideodownloader.StreamCandidate
import com.arslandaim.omegavideodownloader.UrlUtils
import com.arslandaim.omegavideodownloader.VideoMetadata
import com.arslandaim.omegavideodownloader.VideoQuality
import com.arslandaim.omegavideodownloader.data.DownloadRepository
import com.arslandaim.omegavideodownloader.data.VideoRepository
import com.arslandaim.omegavideodownloader.player.StreamRegistry
import com.arslandaim.omegavideodownloader.resolver.ResolveResult
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

enum class DownloaderMode(val label: String) {
    PASTE_LINK("Paste Link"),
    SEARCH_VIDEOS("Search YT Videos")
}

class HomeViewModel(
    private val videoRepository: VideoRepository,
    private val downloadRepository: DownloadRepository,
    private val settingsManager: SettingsManager
) : ViewModel() {

    var url by mutableStateOf("")
    var downloaderMode by mutableStateOf(DownloaderMode.PASTE_LINK)
    var videoMetadata by mutableStateOf<VideoMetadata?>(null)
    var isLoading by mutableStateOf(false)
    var resolveStatus by mutableStateOf<String?>(null)
    var errorMessage by mutableStateOf<String?>(null)
    var selectedQuality by mutableStateOf<VideoQuality?>(null)

    // Recently resolved links in this session for quick re-download (e.g. Audio after Video)
    var recentExtractions by mutableStateOf<List<VideoMetadata>>(emptyList())
        private set

    // YouTube Video/Music search results (when in SEARCH_VIDEOS mode)
    var searchResults by mutableStateOf<List<MovieSearchResult>>(emptyList())
        private set
    var selectedSearchResult by mutableStateOf<MovieSearchResult?>(null)
        private set
    var isSearching by mutableStateOf(false)
        private set

    // Layer 3 hidden-until-captcha WebView sniffer state
    var snifferUrl by mutableStateOf<String?>(null)
        private set
    var forceSnifferVisible by mutableStateOf(false)
        private set

    // Video search history & pause toggle
    var videoSearchHistory by mutableStateOf<List<String>>(emptyList())
        private set
    var isVideoSearchHistoryPaused by mutableStateOf(false)
        private set

    // One-time legal responsibility disclaimer
    var showDisclaimerDialog by mutableStateOf(false)
        private set
    private var pendingActionAfterDisclaimer: (() -> Unit)? = null

    private var resolveJob: Job? = null

    init {
        viewModelScope.launch {
            settingsManager.videoSearchHistory.collect { videoSearchHistory = it }
        }
        viewModelScope.launch {
            settingsManager.pauseVideoSearchHistory.collect { isVideoSearchHistoryPaused = it }
        }
    }

    fun isLinkInput(input: String = url): Boolean = UrlUtils.extractUrl(input) != null

    /** Smart entry point: if URL, extracts directly; otherwise searches YouTube videos/music. */
    fun submitInput() {
        val raw = url.trim()
        if (raw.isEmpty() || isLoading || isSearching) return
        if (isLinkInput(raw)) {
            downloaderMode = DownloaderMode.PASTE_LINK
            searchResults = emptyList()
            selectedSearchResult = null
            fetchMetadata()
        } else {
            downloaderMode = DownloaderMode.SEARCH_VIDEOS
            searchYouTubeVideos()
        }
    }

    fun selectMode(mode: DownloaderMode) {
        if (downloaderMode == mode) return
        downloaderMode = mode
        errorMessage = null
        if (mode == DownloaderMode.PASTE_LINK) {
            searchResults = emptyList()
            selectedSearchResult = null
        } else {
            videoMetadata = null
        }
    }

    fun searchYouTubeVideos() {
        val query = url.trim()
        if (query.isEmpty()) return

        resolveJob?.cancel()
        isSearching = true
        isLoading = false
        videoMetadata = null
        selectedSearchResult = null
        errorMessage = null
        snifferUrl = null
        forceSnifferVisible = false
        resolveStatus = "Searching YouTube videos & music..."

        resolveJob = viewModelScope.launch {
            if (!isVideoSearchHistoryPaused) {
                settingsManager.addVideoSearchQuery(query)
            }
            val results = videoRepository.searchMovies(query, SearchFilter.YOUTUBE_ANY)
            searchResults = results
            isSearching = false
            resolveStatus = null
            if (results.isEmpty()) {
                errorMessage = "No YouTube videos found for \"$query\"."
            }
        }
    }

    fun searchFromHistory(query: String) {
        url = query
        downloaderMode = DownloaderMode.SEARCH_VIDEOS
        searchYouTubeVideos()
    }

    fun togglePauseVideoSearchHistory() {
        val next = !isVideoSearchHistoryPaused
        isVideoSearchHistoryPaused = next
        viewModelScope.launch {
            settingsManager.setPauseVideoSearchHistory(next)
        }
    }

    fun removeVideoSearchHistoryItem(query: String) {
        viewModelScope.launch {
            settingsManager.removeVideoSearchQuery(query)
        }
    }

    fun clearVideoSearchHistory() {
        viewModelScope.launch {
            settingsManager.clearVideoSearchHistory()
        }
    }

    fun clearRecentExtractions() {
        recentExtractions = emptyList()
    }

    fun selectSearchResult(result: MovieSearchResult) {
        if (isLoading) return
        resolveJob?.cancel()
        selectedSearchResult = result
        isLoading = true
        videoMetadata = null
        errorMessage = null
        resolveStatus = "Fetching quality options..."

        resolveJob = viewModelScope.launch {
            when (val res = videoRepository.resolveSearchResult(result) { status -> resolveStatus = status }) {
                is ResolveResult.Success -> {
                    setResolvedMetadata(res.metadata)
                    isLoading = false
                    resolveStatus = null
                }
                is ResolveResult.NeedsBrowserSniffer -> {
                    snifferUrl = res.url
                    forceSnifferVisible = false
                    resolveStatus = "Scanning page in background..."
                }
                is ResolveResult.Error -> {
                    errorMessage = res.message
                    isLoading = false
                    resolveStatus = null
                }
            }
        }
    }

    fun openRecentExtraction(meta: VideoMetadata) {
        url = meta.sourceUrl
        downloaderMode = DownloaderMode.PASTE_LINK
        searchResults = emptyList()
        selectedSearchResult = null
        errorMessage = null
        videoMetadata = meta
    }

    fun backToSearchResults() {
        resolveJob?.cancel()
        videoMetadata = null
        selectedSearchResult = null
        errorMessage = null
        isLoading = false
        resolveStatus = null
    }

    fun fetchMetadata() {
        val raw = url.trim()
        if (raw.isEmpty() || isLoading) return

        resolveJob?.cancel()
        isLoading = true
        videoMetadata = null
        errorMessage = null
        snifferUrl = null
        forceSnifferVisible = false
        resolveStatus = "Checking link..."

        resolveJob = viewModelScope.launch {
            val allowFallback = try {
                settingsManager.browserFallback.first()
            } catch (_: Exception) {
                true
            }
            when (val result = videoRepository.resolveUrl(raw, allowFallback) { status ->
                resolveStatus = status
            }) {
                is ResolveResult.Success -> {
                    setResolvedMetadata(result.metadata)
                    isLoading = false
                    resolveStatus = null
                }
                is ResolveResult.NeedsBrowserSniffer -> {
                    snifferUrl = result.url
                    forceSnifferVisible = false
                    resolveStatus = "Scanning page in background..."
                }
                is ResolveResult.Error -> {
                    errorMessage = result.message
                    isLoading = false
                    resolveStatus = null
                }
            }
        }
    }

    private fun setResolvedMetadata(meta: VideoMetadata) {
        videoMetadata = meta
        val key = meta.sourceUrl.trim()
        if (key.isNotEmpty() && !meta.isPlaylist) {
            recentExtractions = (listOf(meta) + recentExtractions.filterNot { it.sourceUrl.trim() == key }).take(6)
        }
    }

    fun cancelResolve() {
        resolveJob?.cancel()
        resolveJob = null
        snifferUrl = null
        forceSnifferVisible = false
        isLoading = false
        isSearching = false
        resolveStatus = null
    }

    fun openManualBrowser() {
        val target = snifferUrl ?: UrlUtils.extractUrl(url) ?: "https://www.google.com"
        resolveJob?.cancel()
        errorMessage = null
        snifferUrl = target
        forceSnifferVisible = true
        isLoading = true
        resolveStatus = "Waiting for stream in browser..."
    }

    fun onSnifferCaptchaDetected() {
        resolveStatus = "Verification required — complete check in browser"
    }

    fun onSnifferStreamsFound(metadata: VideoMetadata) {
        snifferUrl = null
        forceSnifferVisible = false
        isLoading = false
        resolveStatus = null
        errorMessage = null
        setResolvedMetadata(metadata)
    }

    fun onSnifferTimeout() {
        snifferUrl = null
        forceSnifferVisible = false
        isLoading = false
        resolveStatus = null
        errorMessage = "No playable video stream was detected automatically. Tap 'Scan in Browser' below and press Play on the video."
    }

    fun dismissSniffer() {
        snifferUrl = null
        forceSnifferVisible = false
        isLoading = false
        resolveStatus = null
    }

    fun runWithDisclaimerCheck(action: () -> Unit) {
        viewModelScope.launch {
            val accepted = try {
                settingsManager.disclaimerAccepted.first()
            } catch (_: Exception) {
                false
            }
            if (accepted) {
                action()
            } else {
                pendingActionAfterDisclaimer = action
                showDisclaimerDialog = true
            }
        }
    }

    fun confirmDisclaimer() {
        showDisclaimerDialog = false
        val action = pendingActionAfterDisclaimer
        pendingActionAfterDisclaimer = null
        viewModelScope.launch {
            settingsManager.setDisclaimerAccepted()
            action?.invoke()
        }
    }

    fun dismissDisclaimer() {
        showDisclaimerDialog = false
        pendingActionAfterDisclaimer = null
    }

    /** Creates an online streaming session in [StreamRegistry] and returns the player route key. */
    fun prepareStreamSession(
        preferredCandidate: StreamCandidate? = null,
        targetMetadata: VideoMetadata? = null
    ): String? {
        val meta = targetMetadata ?: videoMetadata ?: return null
        if (meta.streams.isEmpty()) return null
        val ordered = if (preferredCandidate != null) {
            listOf(preferredCandidate) + meta.streams.filter { it.url != preferredCandidate.url }
        } else {
            meta.streams
        }
        val pageUrl = meta.sourceUrl.ifBlank { UrlUtils.extractUrl(url) ?: url }
        return StreamRegistry.createSession(
            title = meta.title,
            pageUrl = pageUrl,
            candidates = ordered,
            thumbnailUrl = meta.thumbnailUrl,
            subtitleTracks = meta.subtitleTracks
        )
    }

    fun startDownload(quality: VideoQuality, targetMetadata: VideoMetadata? = null) {
        selectedQuality = quality
        val meta = targetMetadata ?: videoMetadata ?: return
        val pageUrl = meta.sourceUrl.ifBlank { UrlUtils.extractUrl(url) ?: url }
        downloadRepository.startDownload(
            metadata = meta,
            quality = quality,
            pageUrl = pageUrl
        )
    }

    fun startDownloadAll(items: List<VideoMetadata>) {
        items.forEach { item ->
            val bestQuality = item.qualities.firstOrNull() ?: item.audioQualities.firstOrNull()
            if (bestQuality != null) {
                val pageUrl = item.sourceUrl.ifBlank { UrlUtils.extractUrl(url) ?: url }
                downloadRepository.startDownload(
                    metadata = item,
                    quality = bestQuality,
                    pageUrl = pageUrl
                )
            }
        }
    }

    fun clearUrl() {
        cancelResolve()
        url = ""
        videoMetadata = null
        searchResults = emptyList()
        selectedSearchResult = null
        errorMessage = null
    }

    fun onIncomingSharedText(sharedText: String) {
        val extracted = UrlUtils.extractUrl(sharedText) ?: return
        downloaderMode = DownloaderMode.PASTE_LINK
        url = extracted
        searchResults = emptyList()
        selectedSearchResult = null
        cancelResolve()
        fetchMetadata()
    }
}
