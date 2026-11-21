/*
 * Omega Video Downloader Project Original (2026)
 * arslandaim-hub (GitHub.com/arslandaim-hub)
 * Licenced Under GPL-3.0+
*/
package com.arslandaim.omegavideodownloader.data

import android.content.Context
import com.arslandaim.omegavideodownloader.CookiesManager
import com.arslandaim.omegavideodownloader.MovieSearchResult
import com.arslandaim.omegavideodownloader.SearchFilter
import com.arslandaim.omegavideodownloader.SearchSource
import com.arslandaim.omegavideodownloader.StreamCandidate
import com.arslandaim.omegavideodownloader.YtDlpManager
import com.arslandaim.omegavideodownloader.resolver.DirectProbe
import com.arslandaim.omegavideodownloader.resolver.MediaResolver
import com.arslandaim.omegavideodownloader.resolver.MovieSearchEngine
import com.arslandaim.omegavideodownloader.resolver.ResolveResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class VideoRepository(context: Context) {
    private val appContext = context.applicationContext
    private val resolver = MediaResolver(appContext)

    suspend fun resolveUrl(
        videoUrl: String,
        allowBrowserFallback: Boolean,
        onProgress: (String) -> Unit = {}
    ): ResolveResult = withContext(Dispatchers.IO) {
        resolver.resolve(videoUrl, allowBrowserFallback, onProgress)
    }

    /** Fast HTTP catalog pre-fetch for the Movies hub (YouTube, Dailymotion, curated Archive.org). */
    suspend fun fetchFastMovieCatalog(forceRefresh: Boolean = false): List<MovieSearchResult> = withContext(Dispatchers.IO) {
        MovieSearchEngine.fetchFastCatalog(forceRefresh)
    }

    /** Background YouTube full-movie supplement for the Movies hub. */
    suspend fun fetchYouTubeMovieCatalog(forceRefresh: Boolean = false): List<MovieSearchResult> = withContext(Dispatchers.IO) {
        MovieSearchEngine.fetchYouTubeCatalog(appContext, forceRefresh)
    }

    /** Live fetch for a specific Industry + Genre + Release Year filter combination. */
    suspend fun fetchCategoryMovies(
        industry: com.arslandaim.omegavideodownloader.MovieIndustry,
        genre: com.arslandaim.omegavideodownloader.MovieGenre,
        yearFilter: com.arslandaim.omegavideodownloader.MovieYearFilter
    ): List<MovieSearchResult> = withContext(Dispatchers.IO) {
        MovieSearchEngine.fetchCategoryMovies(appContext, industry, genre, yearFilter)
    }

    /** Searches open movie & video catalogs (Archive.org, YouTube, Dailymotion, PeerTube) by title. */
    suspend fun searchMovies(
        query: String,
        filter: SearchFilter = SearchFilter.FULL_MOVIES
    ): List<MovieSearchResult> = withContext(Dispatchers.IO) {
        MovieSearchEngine.search(appContext, query, filter)
    }

    /** Resolves a tapped search result into playable streams & downloadable quality options. */
    suspend fun resolveSearchResult(
        result: MovieSearchResult,
        onProgress: (String) -> Unit = {}
    ): ResolveResult = withContext(Dispatchers.IO) {
        when (result.source) {
            SearchSource.ARCHIVE_ORG -> {
                onProgress("Fetching movie files & subtitles from Archive.org...")
                val meta = MovieSearchEngine.resolveArchiveMovie(result)
                if (meta != null) {
                    ResolveResult.Success(meta)
                } else {
                    resolver.resolve(result.pageUrl, allowBrowserFallback = false, onProgress = onProgress)
                }
            }
            SearchSource.YOUTUBE, SearchSource.DAILYMOTION, SearchSource.PEERTUBE -> {
                onProgress("Extracting movie qualities...")
                val res = resolver.resolve(result.pageUrl, allowBrowserFallback = false, onProgress = onProgress)
                if (res is ResolveResult.Success) {
                    val m = res.metadata
                    ResolveResult.Success(
                        m.copy(
                            year = m.year ?: result.year,
                            creator = m.creator ?: result.subtitle,
                            genres = m.genres.ifEmpty { result.genres },
                            description = m.description ?: result.description,
                            durationSec = if (m.durationSec > 0) m.durationSec else result.durationSec
                        )
                    )
                } else {
                    res
                }
            }
        }
    }

    /** Re-extracts fresh signed URLs when an online stream returns HTTP 403/410 mid-movie. */
    suspend fun refreshStreams(pageUrl: String): List<StreamCandidate> = withContext(Dispatchers.IO) {
        DirectProbe.probe(pageUrl)?.streams?.takeIf { it.isNotEmpty() }?.let { return@withContext it }
        val cookies = CookiesManager.getCookiesPath(appContext)
        YtDlpManager.getVideoInfo(appContext, pageUrl, cookies).metadata?.streams.orEmpty()
    }

    /**
     * Resolves a [com.arslandaim.omegavideodownloader.WatchHistoryItem] so an online movie can be
     * resumed seamlessly even after app restart or when signed URLs have expired.
     */
    suspend fun resolveForResume(
        item: com.arslandaim.omegavideodownloader.WatchHistoryItem,
        onProgress: (String) -> Unit = {}
    ): ResolveResult = withContext(Dispatchers.IO) {
        val source = item.sourceUrl.trim()
        // Fast path for Archive.org detail pages via the lightweight metadata API
        val archiveMatch = Regex("archive\\.org/details/([^/?#]+)", RegexOption.IGNORE_CASE).find(source)
        if (archiveMatch != null) {
            val archiveId = archiveMatch.groupValues[1]
            onProgress("Resuming from Archive.org...")
            val stub = MovieSearchResult(
                id = archiveId,
                title = item.title,
                pageUrl = source,
                source = SearchSource.ARCHIVE_ORG,
                thumbnailUrl = item.thumbnailUrl
            )
            val meta = MovieSearchEngine.resolveArchiveMovie(stub)
            if (meta != null && meta.streams.isNotEmpty()) {
                return@withContext ResolveResult.Success(meta)
            }
        }
        resolver.resolve(source, allowBrowserFallback = false, onProgress = onProgress)
    }

    suspend fun updateYtDlpBinary(): Result<String> = withContext(Dispatchers.IO) {
        YtDlpManager.updateBinary(appContext)
    }
}
