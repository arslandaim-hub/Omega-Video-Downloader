/*
 * Omega Video Downloader Project Original (2026)
 * arslandaim-hub (GitHub.com/arslandaim-hub)
 * Licenced Under GPL-3.0+
*/
package com.arslandaim.omegavideodownloader.ui

import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arslandaim.omegavideodownloader.*
import com.arslandaim.omegavideodownloader.data.DownloadRepository
import com.arslandaim.omegavideodownloader.data.VideoRepository
import com.arslandaim.omegavideodownloader.player.StreamRegistry
import com.arslandaim.omegavideodownloader.resolver.MovieSearchEngine
import com.arslandaim.omegavideodownloader.resolver.ResolveResult
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MoviesViewModel(
    private val videoRepository: VideoRepository,
    private val downloadRepository: DownloadRepository,
    private val settingsManager: SettingsManager
) : ViewModel() {

    // Movie suggestions toggle state (default OFF)
    var suggestionsEnabled by mutableStateOf(false)
        private set

    // Pre-fetched catalog state (only loaded when suggestionsEnabled == true)
    var prefetchedMovies by mutableStateOf<List<MovieSearchResult>>(emptyList())
        private set
    var isLoadingCatalog by mutableStateOf(false)
        private set
    var catalogError by mutableStateOf<String?>(null)
        private set

    // Search-on-top state
    var searchQuery by mutableStateOf("")
    var activeSearchResults by mutableStateOf<List<MovieSearchResult>>(emptyList())
        private set
    var isSearching by mutableStateOf(false)
        private set
    var hasExecutedSearch by mutableStateOf(false)
        private set
    var searchError by mutableStateOf<String?>(null)
        private set

    // Movie search history & pause toggle
    var movieSearchHistory by mutableStateOf<List<String>>(emptyList())
        private set
    var isMovieSearchHistoryPaused by mutableStateOf(false)
        private set

    // Movie Detail state
    var selectedMovie by mutableStateOf<MovieSearchResult?>(null)
        private set
    var resolvedMetadata by mutableStateOf<VideoMetadata?>(null)
        private set
    var isResolvingDetail by mutableStateOf(false)
        private set
    var detailResolveStatus by mutableStateOf<String?>(null)
        private set
    var detailError by mutableStateOf<String?>(null)
        private set

    // One-time disclaimer gate
    var showDisclaimerDialog by mutableStateOf(false)
        private set
    private var pendingDisclaimerAction: (() -> Unit)? = null

    private var catalogJob: Job? = null
    private var searchJob: Job? = null
    private var detailJob: Job? = null

    /** Movies ready for rendering in the Movies hub (search results or toggled-on suggestions). */
    val displayedMovies: List<MovieSearchResult> by derivedStateOf {
        val q = searchQuery.trim().lowercase()
        if (hasExecutedSearch && q.isNotEmpty()) {
            activeSearchResults.sortedWith(MovieSearchEngine.newestMovieComparator())
        } else if (!suggestionsEnabled) {
            emptyList()
        } else {
            val base = if (q.isEmpty()) prefetchedMovies else filterByQuery(prefetchedMovies, q)
            base.sortedWith(MovieSearchEngine.newestMovieComparator())
        }
    }

    /** Top spotlight picks (prefer movies with thumbnails & higher year/popularity). */
    val featuredSpotlight: List<MovieSearchResult> by derivedStateOf {
        if (!suggestionsEnabled) emptyList()
        else prefetchedMovies
            .filter { !it.thumbnailUrl.isNullOrBlank() }
            .sortedWith(MovieSearchEngine.newestMovieComparator())
            .take(6)
    }

    /** Newest releases strip (2020s or highest release year first). */
    val newestReleases: List<MovieSearchResult> by derivedStateOf {
        if (!suggestionsEnabled) emptyList()
        else prefetchedMovies
            .sortedWith(MovieSearchEngine.newestMovieComparator())
            .take(16)
    }

    val hollywoodCatalog: List<MovieSearchResult> by derivedStateOf {
        if (!suggestionsEnabled) emptyList()
        else prefetchedMovies
            .filter { it.industry == MovieIndustry.HOLLYWOOD }
            .sortedWith(MovieSearchEngine.newestMovieComparator())
            .take(18)
    }

    val bollywoodCatalog: List<MovieSearchResult> by derivedStateOf {
        if (!suggestionsEnabled) emptyList()
        else prefetchedMovies
            .filter { it.industry == MovieIndustry.BOLLYWOOD }
            .sortedWith(MovieSearchEngine.newestMovieComparator())
            .take(18)
    }

    init {
        viewModelScope.launch {
            settingsManager.movieSearchHistory.collect { movieSearchHistory = it }
        }
        viewModelScope.launch {
            settingsManager.pauseMovieSearchHistory.collect { isMovieSearchHistoryPaused = it }
        }
        viewModelScope.launch {
            settingsManager.movieSuggestionsEnabled.collect { enabled ->
                suggestionsEnabled = enabled
                if (enabled) {
                    if (prefetchedMovies.isEmpty() && catalogJob?.isActive != true) {
                        loadCatalog(forceRefresh = false)
                    }
                } else {
                    catalogJob?.cancel()
                    isLoadingCatalog = false
                    catalogError = null
                    prefetchedMovies = emptyList()
                }
            }
        }
    }

    fun setMovieSuggestionsEnabled(enabled: Boolean) {
        suggestionsEnabled = enabled
        if (enabled && prefetchedMovies.isEmpty() && catalogJob?.isActive != true) {
            loadCatalog(forceRefresh = false)
        } else if (!enabled) {
            catalogJob?.cancel()
            isLoadingCatalog = false
            catalogError = null
            prefetchedMovies = emptyList()
        }
        viewModelScope.launch {
            settingsManager.setMovieSuggestionsEnabled(enabled)
        }
    }

    fun loadCatalog(forceRefresh: Boolean = false) {
        if (!suggestionsEnabled) return
        catalogJob?.cancel()
        isLoadingCatalog = prefetchedMovies.isEmpty() || forceRefresh
        catalogError = null

        catalogJob = viewModelScope.launch {
            val fast = videoRepository.fetchFastMovieCatalog(forceRefresh)
            if (!suggestionsEnabled) return@launch
            if (fast.isNotEmpty()) {
                prefetchedMovies = fast
            }
            isLoadingCatalog = false
            if (fast.isEmpty()) {
                catalogError = "Unable to reach movie catalogs right now. Tap Retry."
            }

            // Enrich asynchronously with YouTube full-length Hollywood & Bollywood movies
            val ytMovies = videoRepository.fetchYouTubeMovieCatalog(forceRefresh)
            if (!suggestionsEnabled) return@launch
            if (ytMovies.isNotEmpty()) {
                val merged = (prefetchedMovies + ytMovies)
                    .distinctBy { "${it.source}:${it.id}" }
                    .sortedWith(MovieSearchEngine.newestMovieComparator())
                prefetchedMovies = merged
                catalogError = null
            }
        }
    }

    fun submitSearch() {
        val q = searchQuery.trim()
        if (q.isEmpty()) {
            clearSearch()
            return
        }
        searchJob?.cancel()
        isSearching = true
        hasExecutedSearch = true
        searchError = null

        searchJob = viewModelScope.launch {
            if (!isMovieSearchHistoryPaused) {
                settingsManager.addMovieSearchQuery(q)
            }
            val results = videoRepository.searchMovies(q, SearchFilter.FULL_MOVIES)
            activeSearchResults = results
            isSearching = false
            if (results.isEmpty()) {
                searchError = "No full movies found for \"$q\". Try another movie name."
            }
        }
    }

    fun searchFromHistory(query: String) {
        searchQuery = query
        submitSearch()
    }

    fun togglePauseMovieSearchHistory() {
        val next = !isMovieSearchHistoryPaused
        isMovieSearchHistoryPaused = next
        viewModelScope.launch {
            settingsManager.setPauseMovieSearchHistory(next)
        }
    }

    fun removeMovieSearchHistoryItem(query: String) {
        viewModelScope.launch {
            settingsManager.removeMovieSearchQuery(query)
        }
    }

    fun clearMovieSearchHistory() {
        viewModelScope.launch {
            settingsManager.clearMovieSearchHistory()
        }
    }

    fun clearSearch() {
        searchJob?.cancel()
        searchQuery = ""
        activeSearchResults = emptyList()
        hasExecutedSearch = false
        isSearching = false
        searchError = null
    }

    private fun filterByQuery(list: List<MovieSearchResult>, q: String): List<MovieSearchResult> {
        return list.filter { m ->
            m.title.lowercase().contains(q) ||
                (m.subtitle?.lowercase()?.contains(q) == true) ||
                (m.year?.contains(q) == true) ||
                m.genres.any { it.lowercase().contains(q) }
        }
    }

    // ---------------- Movie Detail & Playback / Download ----------------

    fun openMovieDetail(movie: MovieSearchResult) {
        detailJob?.cancel()
        selectedMovie = movie
        resolvedMetadata = null
        detailError = null
        isResolvingDetail = true
        detailResolveStatus = "Loading movie streams & details..."

        detailJob = viewModelScope.launch {
            when (val res = videoRepository.resolveSearchResult(movie) { detailResolveStatus = it }) {
                is ResolveResult.Success -> {
                    resolvedMetadata = res.metadata
                    isResolvingDetail = false
                    detailResolveStatus = null
                }
                is ResolveResult.NeedsBrowserSniffer -> {
                    detailError = "This movie host requires browser verification. Paste its link in Downloader to scan."
                    isResolvingDetail = false
                    detailResolveStatus = null
                }
                is ResolveResult.Error -> {
                    detailError = res.message
                    isResolvingDetail = false
                    detailResolveStatus = null
                }
            }
        }
    }

    fun openLikedMovie(liked: LikedVideo) {
        openMovieDetail(liked.toMovieSearchResult())
    }

    fun closeMovieDetail() {
        detailJob?.cancel()
        selectedMovie = null
        resolvedMetadata = null
        isResolvingDetail = false
        detailResolveStatus = null
        detailError = null
    }

    fun retryMovieDetail() {
        selectedMovie?.let { openMovieDetail(it) }
    }

    fun prepareStreamSession(preferredCandidate: StreamCandidate? = null): String? {
        val meta = resolvedMetadata ?: return null
        if (meta.streams.isEmpty()) return null
        val ordered = if (preferredCandidate != null) {
            listOf(preferredCandidate) + meta.streams.filter { it.url != preferredCandidate.url }
        } else {
            meta.streams
        }
        val pageUrl = meta.sourceUrl.ifBlank { selectedMovie?.pageUrl.orEmpty() }
        return StreamRegistry.createSession(
            title = meta.title,
            pageUrl = pageUrl,
            candidates = ordered,
            thumbnailUrl = meta.thumbnailUrl ?: selectedMovie?.thumbnailUrl,
            subtitleTracks = meta.subtitleTracks
        )
    }

    fun startMovieDownload(quality: VideoQuality) {
        val meta = resolvedMetadata ?: return
        val pageUrl = meta.sourceUrl.ifBlank { selectedMovie?.pageUrl.orEmpty() }
        downloadRepository.startDownload(
            metadata = meta,
            quality = quality,
            pageUrl = pageUrl,
            isMovie = true
        )
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
                pendingDisclaimerAction = action
                showDisclaimerDialog = true
            }
        }
    }

    fun confirmDisclaimer() {
        showDisclaimerDialog = false
        val action = pendingDisclaimerAction
        pendingDisclaimerAction = null
        viewModelScope.launch {
            settingsManager.setDisclaimerAccepted()
            action?.invoke()
        }
    }

    fun dismissDisclaimer() {
        showDisclaimerDialog = false
        pendingDisclaimerAction = null
    }
}
