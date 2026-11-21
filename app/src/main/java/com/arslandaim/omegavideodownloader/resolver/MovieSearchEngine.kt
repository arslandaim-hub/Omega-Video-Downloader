/*
 * Omega Video Downloader Project Original (2026)
 * arslandaim-hub (GitHub.com/arslandaim-hub)
 * Licenced Under GPL-3.0+
*/
package com.arslandaim.omegavideodownloader.resolver

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.media3.common.MimeTypes
import com.arslandaim.omegavideodownloader.*
import com.arslandaim.omegavideodownloader.player.OmegaHttp
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import java.util.Calendar
import java.util.concurrent.ConcurrentHashMap

/**
 * Multi-source search & catalog pre-fetch engine for complete, streamable/downloadable movies:
 *  - YouTube Full Movies (fast OkHttp `ytInitialData` parser with `yt-dlp` fallback, 50+ min)
 *  - Dailymotion (official public Graph API, `sort=visited`, 50+ min)
 *  - Internet Archive (`archive.org` curated Feature Films, Sci-Fi/Horror, Noir, Comedy)
 *  - PeerTube / Open Movies (explicit search & World Cinema)
 */
object MovieSearchEngine {
    private const val TAG = "MovieSearchEngine"

    /** Minimum duration (50 minutes) for modern feature movies to exclude episodes/vlogs. */
    private const val MIN_MODERN_MOVIE_SEC = 50L * 60L

    /** Minimum duration (40 minutes) for classics / Archive.org featurettes & older films. */
    private const val MIN_MOVIE_DURATION_SEC = 40L * 60L

    /** Maximum duration (4 hours) to block 10-hour lofi/rain/sleep/compilation loops. */
    private const val MAX_MOVIE_DURATION_SEC = 4L * 3600L

    /** Minimum total item size (150 MB) for Archive.org items when runtime metadata is missing. */
    private const val MIN_ARCHIVE_ITEM_BYTES = 150L * 1024L * 1024L

    private const val PREFETCH_TTL_MS = 15L * 60L * 1000L

    private const val YOUTUBE_BROWSER_UA =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"

    private const val YOUTUBE_CONSENT_COOKIE =
        "SOCS=CAESHAgBEhJnd3NfMjAyMzA4MTAtMF9SQzIaAmVuIAEaBgiA_LyaBg"

    private val VIDEO_EXTENSIONS = setOf("mp4", "mkv", "webm", "m4v", "mov", "ogv")
    private val AUDIO_EXTENSIONS = setOf("mp3", "m4a", "ogg", "flac", "opus")
    private val SUBTITLE_EXTENSIONS = setOf("srt", "vtt")
    private val ARCHIVE_VIDEO_FORMATS = setOf(
        "mpeg4", "h.264", "h.264 ia", "matroska", "webm", "ogg video", "quicktime", "512kb mpeg4"
    )

    private val STOP_WORDS = setOf(
        "the", "a", "an", "of", "in", "on", "at", "to", "for", "and", "or", "by", "with", "from"
    )

    private val NON_MOVIE_PATTERNS = listOf(
        "trailer", "teaser", "preview", "promo", "commercial", "tv spot",
        "reaction", "review", "recap", "explained", "ending explained",
        "breakdown", "analysis", "pitch meeting", "honest trailer",
        "soundtrack", "full ost", "theme song", "music video", "jukebox",
        "song collection", "songs collection", "nonstop", "non stop", "mashup",
        "remix", "lofi", "bhajan", "qawwali", "kirtan", "aarti", "naat", "full album",
        "episode", "ep.", "ep ", "full episode", "drama serial", "serial", "natak",
        "season 1", "season 2", "s01e", "s02e",
        "gameplay", "walkthrough", "playthrough", "let's play", "cutscenes", "full game",
        "podcast", "interview", "behind the scenes", "making of", "blooper",
        "deleted scene", "best scenes", "top 10", "tribute", "amv",
        "fan film", "fan made", "parody", "audiobook", "unboxing", "audition",
        "webinar", "lecture", "sermon", "conference", "keynote", "panel discussion",
        "live stream", "livestream", "news", "highlights", "full match",
        "vlog", "storytelling", "meditation", "sleep sounds", "rain sounds",
        "shorts", "#shorts"
    )

    private val ARCHIVE_TRAILER_FILE_WORDS = listOf(
        "trailer", "teaser", "preview", "sample", "promo", "commercial", "clip"
    )

    private val BOLLYWOOD_KEYWORDS = listOf(
        "bollywood", "hindi", "urdu", "punjabi", "tollywood", "kollywood",
        "tamil", "telugu", "malayalam", "marathi", "indian movie", "desi",
        "shemaroo", "goldmines", "rajshri", "ultra bollywood", "eros now", "b4u"
    )

    private val WORLD_KEYWORDS = listOf(
        "french", "spanish", "korean", "japanese", "anime", "italian",
        "german", "russian", "chinese", "cantonese", "turkish", "arabic", "persian"
    )

    private val GENRE_KEYWORD_MAP: List<Pair<MovieGenre, List<String>>> = listOf(
        MovieGenre.ACTION to listOf("action", "martial arts", "kung fu", "war", "fight", "adventure", "western", "superhero", "commando"),
        MovieGenre.SCIFI to listOf("sci-fi", "scifi", "science fiction", "space", "alien", "cyberpunk", "future", "robot", "galaxy"),
        MovieGenre.HORROR to listOf("horror", "zombie", "ghost", "vampire", "haunted", "slasher", "monster", "dracula", "bhoot", "conjuring"),
        MovieGenre.THRILLER to listOf("thriller", "mystery", "noir", "crime", "detective", "suspense", "murder", "spy", "heist"),
        MovieGenre.COMEDY to listOf("comedy", "funny", "humour", "humor", "slapstick", "romcom", "satire"),
        MovieGenre.DRAMA to listOf("drama", "family", "biography", "biopic", "historical"),
        MovieGenre.ROMANCE to listOf("romance", "romantic", "love story", "melodrama", "ishq", "pyar"),
        MovieGenre.DOCUMENTARY to listOf("documentary", "docu", "history", "nature", "wildlife"),
        MovieGenre.ANIMATION to listOf("animation", "animated", "cartoon", "anime")
    )

    private data class CacheEntry(val items: List<MovieSearchResult>, val timestamp: Long)

    @Volatile private var cachedFastCatalog: List<MovieSearchResult> = emptyList()
    @Volatile private var cachedFastCatalogTime: Long = 0L
    @Volatile private var cachedYoutubeCatalog: List<MovieSearchResult> = emptyList()
    @Volatile private var cachedYoutubeCatalogTime: Long = 0L
    private val categoryCache = ConcurrentHashMap<String, CacheEntry>()

    /**
     * Fast HTTP catalog pre-fetch (YouTube `ytInitialData`, Dailymotion most-visited, curated Archive.org).
     * Runs purely over OkHttp (~1-2s) so the Movies tab opens immediately with HD movie cards.
     */
    suspend fun fetchFastCatalog(forceRefresh: Boolean = false): List<MovieSearchResult> = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        if (!forceRefresh && cachedFastCatalog.isNotEmpty() && (now - cachedFastCatalogTime) < PREFETCH_TTL_MS) {
            return@withContext cachedFastCatalog
        }

        val results = coroutineScope {
            val ytHollyJob = async {
                withTimeoutOrNull(8_000L) {
                    searchYouTubeHtmlFast(
                        query = "hollywood full movie in english 2024",
                        defaultIndustry = MovieIndustry.HOLLYWOOD,
                        extraTags = listOf("Action"),
                        limit = 16
                    )
                }.orEmpty()
            }
            val ytBollyJob = async {
                withTimeoutOrNull(8_000L) {
                    searchYouTubeHtmlFast(
                        query = "bollywood full movie hindi 2024",
                        defaultIndustry = MovieIndustry.BOLLYWOOD,
                        extraTags = listOf("Drama"),
                        limit = 16
                    )
                }.orEmpty()
            }
            val ytHollyActionJob = async {
                withTimeoutOrNull(8_000L) {
                    searchYouTubeHtmlFast(
                        query = "hollywood action thriller full movie english",
                        defaultIndustry = MovieIndustry.HOLLYWOOD,
                        extraTags = listOf("Action", "Thriller"),
                        limit = 12
                    )
                }.orEmpty()
            }
            val ytBollyComedyJob = async {
                withTimeoutOrNull(8_000L) {
                    searchYouTubeHtmlFast(
                        query = "bollywood comedy romance full movie hindi",
                        defaultIndustry = MovieIndustry.BOLLYWOOD,
                        extraTags = listOf("Comedy", "Romance"),
                        limit = 12
                    )
                }.orEmpty()
            }
            val dmHollyJob = async {
                withTimeoutOrNull(7_000L) {
                    browseDailymotionMovies("hollywood full movie english", MovieIndustry.HOLLYWOOD, limit = 10)
                }.orEmpty()
            }
            val dmBollyJob = async {
                withTimeoutOrNull(7_000L) {
                    browseDailymotionMovies("bollywood full movie hindi", MovieIndustry.BOLLYWOOD, limit = 10)
                }.orEmpty()
            }
            val archiveFeatureJob = async {
                withTimeoutOrNull(8_000L) {
                    browseArchiveOrg(
                        luceneQuery = "mediatype:(movies) AND collection:(feature_films OR scifi_horror OR film_noir OR comedy_films) AND -collection:(movie_trailers OR trailers OR commercials)",
                        sort = "downloads desc",
                        limit = 14,
                        defaultIndustry = MovieIndustry.HOLLYWOOD
                    )
                }.orEmpty()
            }

            (
                ytHollyJob.await() +
                    ytBollyJob.await() +
                    ytHollyActionJob.await() +
                    ytBollyComedyJob.await() +
                    dmHollyJob.await() +
                    dmBollyJob.await() +
                    archiveFeatureJob.await()
                )
                .distinctBy { "${it.source}:${it.id}" }
                .sortedWith(newestMovieComparator())
        }

        if (results.isNotEmpty()) {
            cachedFastCatalog = results
            cachedFastCatalogTime = System.currentTimeMillis()
        }
        results
    }

    /**
     * Enriches the Movies hub with additional genre diversity (Sci-Fi, Horror, Animation, South Hindi Dubbed).
     * Uses fast OkHttp YouTube HTML first, falling back to `yt-dlp` only if fast fetch returns empty.
     */
    suspend fun fetchYouTubeCatalog(context: Context, forceRefresh: Boolean = false): List<MovieSearchResult> = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        if (!forceRefresh && cachedYoutubeCatalog.isNotEmpty() && (now - cachedYoutubeCatalogTime) < PREFETCH_TTL_MS) {
            return@withContext cachedYoutubeCatalog
        }

        val combined = coroutineScope {
            val sciFiJob = async {
                withTimeoutOrNull(8_000L) {
                    searchYouTubeMoviesWithFallback(context, "hollywood sci fi full movie english", MovieIndustry.HOLLYWOOD, listOf("Sci-Fi"), 10)
                }.orEmpty()
            }
            val horrorJob = async {
                withTimeoutOrNull(8_000L) {
                    searchYouTubeMoviesWithFallback(context, "hollywood horror full movie english", MovieIndustry.HOLLYWOOD, listOf("Horror"), 10)
                }.orEmpty()
            }
            val bollyActionJob = async {
                withTimeoutOrNull(8_000L) {
                    searchYouTubeMoviesWithFallback(context, "hindi dubbed south indian action full movie", MovieIndustry.BOLLYWOOD, listOf("Action"), 12)
                }.orEmpty()
            }
            val animDocJob = async {
                withTimeoutOrNull(8_000L) {
                    searchYouTubeMoviesWithFallback(context, "animated full movie english", MovieIndustry.HOLLYWOOD, listOf("Animation"), 8)
                }.orEmpty()
            }
            (sciFiJob.await() + bollyActionJob.await() + horrorJob.await() + animDocJob.await())
                .distinctBy { "${it.source}:${it.id}" }
                .sortedWith(newestMovieComparator())
        }

        if (combined.isNotEmpty()) {
            cachedYoutubeCatalog = combined
            cachedYoutubeCatalogTime = System.currentTimeMillis()
        }
        combined
    }

    /**
     * Fetches targeted full movies for any combination of [industry], [genre], and [yearFilter].
     * Called directly when the user taps category/filter chips in the Movies hub.
     */
    suspend fun fetchCategoryMovies(
        context: Context,
        industry: MovieIndustry,
        genre: MovieGenre,
        yearFilter: MovieYearFilter,
        forceRefresh: Boolean = false
    ): List<MovieSearchResult> = withContext(Dispatchers.IO) {
        val cacheKey = "${industry.name}|${genre.name}|${yearFilter.name}"
        val now = System.currentTimeMillis()
        if (!forceRefresh) {
            categoryCache[cacheKey]?.let { entry ->
                if ((now - entry.timestamp) < PREFETCH_TTL_MS && entry.items.isNotEmpty()) {
                    return@withContext entry.items
                }
            }
        }

        val genreQuery = when (genre) {
            MovieGenre.ALL -> ""
            MovieGenre.ACTION -> "action"
            MovieGenre.SCIFI -> "sci fi"
            MovieGenre.HORROR -> "horror"
            MovieGenre.THRILLER -> "thriller suspense"
            MovieGenre.COMEDY -> "comedy"
            MovieGenre.DRAMA -> "drama"
            MovieGenre.ROMANCE -> "romance love"
            MovieGenre.DOCUMENTARY -> "documentary"
            MovieGenre.ANIMATION -> "animated cartoon"
        }

        val yearQuery = when (yearFilter) {
            MovieYearFilter.ALL -> ""
            MovieYearFilter.Y2020S -> "2024"
            MovieYearFilter.Y2010S -> "2017"
            MovieYearFilter.Y2000S -> "2006"
            MovieYearFilter.CLASSIC -> "classic"
        }

        val industryTerms: List<Pair<String, MovieIndustry>> = when (industry) {
            MovieIndustry.ALL -> listOf(
                "hollywood english" to MovieIndustry.HOLLYWOOD,
                "bollywood hindi" to MovieIndustry.BOLLYWOOD
            )
            MovieIndustry.HOLLYWOOD -> listOf("hollywood english" to MovieIndustry.HOLLYWOOD)
            MovieIndustry.BOLLYWOOD -> listOf("bollywood hindi" to MovieIndustry.BOLLYWOOD)
            MovieIndustry.WORLD -> listOf("korean spanish french" to MovieIndustry.WORLD)
        }

        val forcedGenres = if (genre == MovieGenre.ALL) emptyList() else listOf(genre.label)

        val rawResults = coroutineScope {
            val ytJobs = industryTerms.map { (indTerm, indEnum) ->
                val fullQuery = listOf(indTerm, genreQuery, yearQuery, "full movie")
                    .filter { it.isNotBlank() }
                    .joinToString(" ")
                async {
                    withTimeoutOrNull(9_000L) {
                        searchYouTubeMoviesWithFallback(context, fullQuery, indEnum, forcedGenres, limit = 16)
                    }.orEmpty()
                }
            }

            val dmJobs = industryTerms.map { (indTerm, indEnum) ->
                val fullQuery = listOf(indTerm, genreQuery, "full movie")
                    .filter { it.isNotBlank() }
                    .joinToString(" ")
                async {
                    withTimeoutOrNull(7_000L) {
                        browseDailymotionMovies(fullQuery, indEnum, limit = 10, extraTags = forcedGenres)
                    }.orEmpty()
                }
            }

            val archiveJob = async {
                val shouldQueryArchive = yearFilter == MovieYearFilter.CLASSIC ||
                    yearFilter == MovieYearFilter.Y2000S ||
                    genre in setOf(MovieGenre.DOCUMENTARY, MovieGenre.SCIFI, MovieGenre.HORROR, MovieGenre.ANIMATION) ||
                    industry == MovieIndustry.BOLLYWOOD

                if (!shouldQueryArchive) emptyList()
                else withTimeoutOrNull(8_000L) {
                    val archiveCol = when (genre) {
                        MovieGenre.DOCUMENTARY -> "documentaries"
                        MovieGenre.ANIMATION -> "animationandcartoons"
                        MovieGenre.SCIFI, MovieGenre.HORROR -> "scifi_horror"
                        MovieGenre.COMEDY -> "comedy_films"
                        MovieGenre.THRILLER -> "film_noir"
                        else -> "feature_films"
                    }
                    val yearClause = when (yearFilter) {
                        MovieYearFilter.Y2020S -> "AND year:[2020 TO 2026]"
                        MovieYearFilter.Y2010S -> "AND year:[2010 TO 2019]"
                        MovieYearFilter.Y2000S -> "AND year:[2000 TO 2009]"
                        MovieYearFilter.CLASSIC -> "AND year:[1920 TO 1999]"
                        MovieYearFilter.ALL -> ""
                    }
                    val indClause = if (industry == MovieIndustry.BOLLYWOOD) "(hindi OR bollywood) AND " else ""
                    browseArchiveOrg(
                        luceneQuery = "${indClause}mediatype:(movies) AND collection:($archiveCol) $yearClause AND -collection:(movie_trailers OR trailers OR commercials)",
                        sort = "downloads desc",
                        limit = 12,
                        defaultIndustry = if (industry == MovieIndustry.ALL) MovieIndustry.HOLLYWOOD else industry,
                        defaultGenres = forcedGenres
                    )
                }.orEmpty()
            }

            val peerTubeJob = async {
                if (industry != MovieIndustry.WORLD) emptyList()
                else withTimeoutOrNull(7_000L) {
                    val q = listOf(genreQuery, "full movie").filter { it.isNotBlank() }.joinToString(" ")
                    browsePeerTubeMovies(q, MovieIndustry.WORLD, limit = 10, extraTags = forcedGenres)
                }.orEmpty()
            }

            (ytJobs.flatMap { it.await() } +
                dmJobs.flatMap { it.await() } +
                archiveJob.await() +
                peerTubeJob.await())
        }

        val normalized = rawResults
            .distinctBy { "${it.source}:${it.id}" }
            .map { item ->
                val adjustedIndustry = when {
                    industry == MovieIndustry.ALL -> item.industry
                    item.industry == MovieIndustry.BOLLYWOOD && industry == MovieIndustry.HOLLYWOOD -> item.industry
                    else -> industry
                }
                val adjustedGenres = if (genre != MovieGenre.ALL && item.genres.none { it.equals(genre.label, ignoreCase = true) }) {
                    (listOf(genre.label) + item.genres).distinct()
                } else {
                    item.genres
                }
                item.copy(industry = adjustedIndustry, genres = adjustedGenres)
            }
            .let { list ->
                when (yearFilter) {
                    MovieYearFilter.ALL -> list
                    MovieYearFilter.Y2020S -> list.filter { it.releaseYear >= 2020 || it.releaseYear == 0 }.ifEmpty { list }
                    MovieYearFilter.Y2010S -> list.filter { it.releaseYear in 2010..2019 }.ifEmpty { list }
                    MovieYearFilter.Y2000S -> list.filter { it.releaseYear in 2000..2009 }.ifEmpty { list }
                    MovieYearFilter.CLASSIC -> list.filter { it.releaseYear in 1..1999 }.ifEmpty { list }
                }
            }
            .sortedWith(newestMovieComparator())

        if (normalized.isNotEmpty()) {
            categoryCache[cacheKey] = CacheEntry(normalized, System.currentTimeMillis())
        }
        normalized
    }

    suspend fun search(
        context: Context,
        rawQuery: String,
        filter: SearchFilter = SearchFilter.FULL_MOVIES
    ): List<MovieSearchResult> = withContext(Dispatchers.IO) {
        val query = rawQuery.trim()
        if (query.isBlank()) return@withContext emptyList()

        when (filter) {
            SearchFilter.YOUTUBE_ANY -> {
                val fast = searchYouTubeHtmlFast(query, MovieIndustry.HOLLYWOOD, emptyList(), limit = 18, minDurationSec = 0L)
                fast.ifEmpty { YtDlpManager.searchYouTube(context, query, limit = 15) }
            }
            SearchFilter.ARCHIVE -> {
                searchArchiveOrg(query, limit = 25)
                    .sortedWith(resultComparator(query))
            }
            SearchFilter.FULL_MOVIES -> {
                coroutineScope {
                    val archiveJob = async {
                        withTimeoutOrNull(10_000L) { searchArchiveOrg(query, limit = 18) }.orEmpty()
                    }
                    val youtubeJob = async {
                        withTimeoutOrNull(12_000L) { searchYouTubeFullMovies(context, query) }.orEmpty()
                    }
                    val dailymotionJob = async {
                        withTimeoutOrNull(9_000L) { searchDailymotionFullMovies(query) }.orEmpty()
                    }
                    val peertubeJob = async {
                        withTimeoutOrNull(8_000L) { searchPeerTubeFullMovies(query) }.orEmpty()
                    }

                    val combined = youtubeJob.await() +
                        archiveJob.await() +
                        dailymotionJob.await() +
                        peertubeJob.await()

                    combined
                        .distinctBy { "${it.source}:${it.id}" }
                        .sortedWith(resultComparator(query))
                }
            }
        }
    }

    // ---------------- Metadata Inference & Comparators ----------------

    fun newestMovieComparator(): Comparator<MovieSearchResult> =
        compareByDescending<MovieSearchResult> { it.releaseYear }
            .thenByDescending { it.popularityScore }
            .thenBy {
                when (it.source) {
                    SearchSource.YOUTUBE -> 0
                    SearchSource.DAILYMOTION -> 1
                    SearchSource.ARCHIVE_ORG -> 2
                    SearchSource.PEERTUBE -> 3
                }
            }
            .thenByDescending { it.durationSec }

    private fun extractYear(title: String, rawYear: String? = null): Int {
        val titleMatch = Regex("""\b(19\d{2}|20[0-2]\d)\b""").findAll(title).lastOrNull()
            ?.groupValues?.getOrNull(1)?.toIntOrNull()
        if (titleMatch != null && titleMatch in 1900..2030) return titleMatch
        rawYear?.trim()?.take(4)?.toIntOrNull()?.takeIf { it in 1900..2030 }?.let { return it }
        return 0
    }

    private fun parseRelativePublishedYear(publishedText: String?): Int {
        if (publishedText.isNullOrBlank()) return 0
        val lower = publishedText.lowercase()
        val currentYear = Calendar.getInstance().get(Calendar.YEAR)
        if (lower.contains("day") || lower.contains("hour") || lower.contains("week") || lower.contains("month")) {
            return currentYear
        }
        val num = Regex("""(\d+)\s+year""").find(lower)?.groupValues?.getOrNull(1)?.toIntOrNull()
        if (num != null) return (currentYear - num).coerceAtLeast(1920)
        return 0
    }

    private fun parseViewCount(viewText: String?): Long {
        if (viewText.isNullOrBlank()) return 0L
        val digits = viewText.filter { it.isDigit() }
        return digits.toLongOrNull() ?: 0L
    }

    private fun inferIndustry(
        title: String,
        subtitle: String?,
        tags: List<String>,
        fallback: MovieIndustry
    ): MovieIndustry {
        val blob = buildString {
            append(title.lowercase()).append(' ')
            subtitle?.let { append(it.lowercase()).append(' ') }
            tags.forEach { append(it.lowercase()).append(' ') }
        }
        return when {
            BOLLYWOOD_KEYWORDS.any { blob.contains(it) } -> MovieIndustry.BOLLYWOOD
            WORLD_KEYWORDS.any { blob.contains(it) } -> MovieIndustry.WORLD
            else -> fallback
        }
    }

    private fun inferGenres(
        title: String,
        description: String?,
        tags: List<String>,
        fallback: List<String> = emptyList()
    ): List<String> {
        val blob = buildString {
            append(title.lowercase()).append(' ')
            description?.let { append(it.lowercase()).append(' ') }
            tags.forEach { append(it.lowercase()).append(' ') }
        }
        val detected = GENRE_KEYWORD_MAP.mapNotNull { (genre, keywords) ->
            if (keywords.any { blob.contains(it) }) genre.label else null
        }
        return (detected + fallback).distinct().ifEmpty { listOf(MovieGenre.DRAMA.label) }
    }

    private fun enrichMovieMetadata(
        item: MovieSearchResult,
        defaultIndustry: MovieIndustry = MovieIndustry.HOLLYWOOD,
        extraTags: List<String> = emptyList(),
        rawDescription: String? = null
    ): MovieSearchResult {
        val yearInt = extractYear(item.title, item.year)
        val yearStr = yearInt.takeIf { it > 0 }?.toString() ?: item.year?.takeIf { it.isNotBlank() }
        val industry = inferIndustry(item.title, item.subtitle, extraTags, defaultIndustry)
        val genres = inferGenres(item.title, rawDescription ?: item.description, extraTags)
        return item.copy(
            year = yearStr,
            releaseYear = yearInt,
            industry = industry,
            genres = genres,
            description = rawDescription ?: item.description
        )
    }

    // ---------------- Title & Movie Quality Filters ----------------

    private fun normalizeWords(text: String): List<String> =
        text.lowercase()
            .replace(Regex("""[^a-z0-9\s]"""), " ")
            .split(Regex("""\s+"""))
            .filter { it.isNotBlank() }

    private fun significantTokens(query: String): List<String> {
        val words = normalizeWords(query)
        val filtered = words.filter { it.length >= 2 && it !in STOP_WORDS }
        return filtered.ifEmpty { words }
    }

    private fun matchesMovieTitle(candidateTitle: String, query: String): Boolean {
        val tokens = significantTokens(query)
        if (tokens.isEmpty()) return false
        val titleWords = normalizeWords(candidateTitle).toSet()
        val normalizedTitle = normalizeWords(candidateTitle).joinToString(" ")
        val matchedCount = tokens.count { token ->
            token in titleWords || (token.length >= 4 && normalizedTitle.contains(token))
        }
        val required = if (tokens.size <= 3) tokens.size else ((tokens.size * 4 + 4) / 5)
        return matchedCount >= required
    }

    private fun isTrailerOrNonMovie(title: String): Boolean {
        val lower = title.lowercase()
        return NON_MOVIE_PATTERNS.any { pattern -> lower.contains(pattern) }
    }

    private fun titleRelevanceScore(candidateTitle: String, query: String): Int {
        val normTitle = normalizeWords(candidateTitle).joinToString(" ")
        val normQuery = normalizeWords(query).joinToString(" ")
        return when {
            normTitle == normQuery -> 100
            normTitle.startsWith("$normQuery ") -> 85
            normTitle.contains(normQuery) -> 70
            else -> 40
        }
    }

    private fun resultComparator(query: String): Comparator<MovieSearchResult> =
        compareByDescending<MovieSearchResult> { titleRelevanceScore(it.title, query) }
            .thenByDescending { it.releaseYear }
            .thenByDescending { it.popularityScore }
            .thenBy {
                when (it.source) {
                    SearchSource.YOUTUBE -> 0
                    SearchSource.ARCHIVE_ORG -> 1
                    SearchSource.DAILYMOTION -> 2
                    SearchSource.PEERTUBE -> 3
                }
            }
            .thenByDescending { it.durationSec }
            .thenByDescending { it.sizeBytes }

    // ---------------- Fast YouTube HTML Movie Engine (OkHttp) ----------------

    private suspend fun searchYouTubeMoviesWithFallback(
        context: Context,
        query: String,
        defaultIndustry: MovieIndustry,
        extraTags: List<String>,
        limit: Int
    ): List<MovieSearchResult> {
        val fast = searchYouTubeHtmlFast(query, defaultIndustry, extraTags, limit)
        if (fast.isNotEmpty()) return fast

        val raw = YtDlpManager.searchYouTube(context, query, limit = limit + 6)
        return raw.filter { item ->
            item.durationSec in MIN_MODERN_MOVIE_SEC..MAX_MOVIE_DURATION_SEC &&
                !isTrailerOrNonMovie(item.title)
        }.map { item ->
            enrichMovieMetadata(item, defaultIndustry = defaultIndustry, extraTags = extraTags)
        }.take(limit)
    }

    /**
     * Parses YouTube search `ytInitialData` directly over OkHttp (~350ms, no Python startup).
     * Extracts HD `hq720.jpg` thumbnails, exact runtime, view count, channel, and inferred year.
     */
    private fun searchYouTubeHtmlFast(
        query: String,
        defaultIndustry: MovieIndustry,
        extraTags: List<String>,
        limit: Int,
        minDurationSec: Long = MIN_MODERN_MOVIE_SEC
    ): List<MovieSearchResult> {
        return try {
            val builder = "https://www.youtube.com/results".toHttpUrl().newBuilder()
                .addQueryParameter("search_query", query)
            if (minDurationSec >= MIN_MOVIE_DURATION_SEC) {
                // YouTube built-in filter: Videos > 20 minutes
                builder.addQueryParameter("sp", "EgIYAg==")
            }

            val req = Request.Builder()
                .url(builder.build())
                .header("User-Agent", YOUTUBE_BROWSER_UA)
                .header("Accept-Language", "en-US,en;q=0.9")
                .header("Cookie", YOUTUBE_CONSENT_COOKIE)
                .get()
                .build()

            val html = OmegaHttp.client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return emptyList()
                resp.body.string()
            }

            val jsonStr = extractYtInitialDataJson(html) ?: return emptyList()
            val root = JsonParser.parseString(jsonStr).asJsonObjectOrNull() ?: return emptyList()
            val sections = root["contents"]?.asJsonObjectOrNull()
                ?.get("twoColumnSearchResultsRenderer")?.asJsonObjectOrNull()
                ?.get("primaryContents")?.asJsonObjectOrNull()
                ?.get("sectionListRenderer")?.asJsonObjectOrNull()
                ?.get("contents")?.asJsonArrayOrNull()
                ?: return emptyList()

            val out = mutableListOf<MovieSearchResult>()
            for (secEl in sections) {
                val items = secEl.asJsonObjectOrNull()
                    ?.get("itemSectionRenderer")?.asJsonObjectOrNull()
                    ?.get("contents")?.asJsonArrayOrNull() ?: continue

                for (itemEl in items) {
                    val vr = itemEl.asJsonObjectOrNull()?.get("videoRenderer")?.asJsonObjectOrNull() ?: continue
                    val videoId = vr.str("videoId") ?: continue
                    val title = extractRunsOrSimpleText(vr["title"]) ?: continue
                    if (minDurationSec > 0L && isTrailerOrNonMovie(title)) continue

                    val durationText = extractRunsOrSimpleText(vr["lengthText"]) ?: continue
                    val durationSec = parseDurationSeconds(durationText)
                    if (minDurationSec > 0L && (durationSec < minDurationSec || durationSec > MAX_MOVIE_DURATION_SEC)) {
                        continue
                    }

                    val publishedText = extractRunsOrSimpleText(vr["publishedTimeText"])
                    if (minDurationSec > 0L && publishedText?.contains("Streamed", ignoreCase = true) == true) {
                        continue
                    }

                    val viewText = extractRunsOrSimpleText(vr["viewCountText"])
                    val views = parseViewCount(viewText)
                    if (minDurationSec > 0L && views in 1L..300L) continue

                    val channel = extractRunsOrSimpleText(vr["ownerText"])
                        ?: extractRunsOrSimpleText(vr["longBylineText"])
                    val titleYear = extractYear(title, null)
                    val inferredYear = if (titleYear > 0) titleYear else parseRelativePublishedYear(publishedText)
                    val yearStr = inferredYear.takeIf { it > 0 }?.toString()

                    // Prefer 1280x720 borderless HD thumbnail; fallback handled cleanly by MovieComponents
                    val apiThumb = vr["thumbnail"]?.asJsonObjectOrNull()
                        ?.get("thumbnails")?.asJsonArrayOrNull()
                        ?.mapNotNull { it.asJsonObjectOrNull() }
                        ?.maxByOrNull { it.long("width") ?: 0L }
                        ?.str("url")
                        ?.substringBefore("?")
                    val hdThumb = if (apiThumb != null && (apiThumb.contains("hq720") || apiThumb.contains("maxresdefault"))) {
                        apiThumb
                    } else {
                        "https://i.ytimg.com/vi/$videoId/hq720.jpg"
                    }

                    val base = MovieSearchResult(
                        id = videoId,
                        title = title,
                        pageUrl = "https://www.youtube.com/watch?v=$videoId",
                        source = SearchSource.YOUTUBE,
                        thumbnailUrl = hdThumb,
                        year = yearStr,
                        durationSec = durationSec,
                        subtitle = channel ?: "YouTube",
                        releaseYear = inferredYear,
                        popularityScore = views
                    )
                    out.add(enrichMovieMetadata(base, defaultIndustry, extraTags))
                    if (out.size >= limit) break
                }
                if (out.size >= limit) break
            }
            out
        } catch (e: Exception) {
            Log.w(TAG, "Fast YouTube HTML search failed for '$query': ${e.message}")
            emptyList()
        }
    }

    private fun extractYtInitialDataJson(html: String): String? {
        val markers = listOf("var ytInitialData = ", "window[\"ytInitialData\"] = ")
        var startIdx = -1
        for (marker in markers) {
            val idx = html.indexOf(marker)
            if (idx != -1) {
                startIdx = idx + marker.length
                break
            }
        }
        if (startIdx == -1) return null

        val braceStart = html.indexOf('{', startIdx)
        if (braceStart == -1) return null

        var depth = 0
        var inString = false
        var escaped = false
        val len = html.length
        for (i in braceStart until len) {
            val c = html[i]
            if (inString) {
                if (escaped) {
                    escaped = false
                } else if (c == '\\') {
                    escaped = true
                } else if (c == '"') {
                    inString = false
                }
            } else {
                when (c) {
                    '"' -> inString = true
                    '{' -> depth++
                    '}' -> {
                        depth--
                        if (depth == 0) {
                            return html.substring(braceStart, i + 1)
                        }
                    }
                }
            }
        }
        return null
    }

    private fun extractRunsOrSimpleText(el: JsonElement?): String? {
        val obj = el?.asJsonObjectOrNull() ?: return null
        obj.str("simpleText")?.let { return it }
        val runs = obj["runs"]?.asJsonArrayOrNull() ?: return null
        val sb = StringBuilder()
        for (r in runs) {
            r.asJsonObjectOrNull()?.str("text")?.let { sb.append(it) }
        }
        return sb.toString().trim().takeIf { it.isNotEmpty() }
    }

    // ---------------- Browse Helpers ----------------

    private fun browseArchiveOrg(
        luceneQuery: String,
        sort: String,
        limit: Int,
        defaultIndustry: MovieIndustry,
        defaultGenres: List<String> = emptyList()
    ): List<MovieSearchResult> {
        return try {
            val url = "https://archive.org/advancedsearch.php".toHttpUrl().newBuilder()
                .addQueryParameter("q", luceneQuery)
                .addQueryParameter("fl[]", "identifier")
                .addQueryParameter("fl[]", "title")
                .addQueryParameter("fl[]", "year")
                .addQueryParameter("fl[]", "creator")
                .addQueryParameter("fl[]", "subject")
                .addQueryParameter("fl[]", "description")
                .addQueryParameter("fl[]", "item_size")
                .addQueryParameter("fl[]", "downloads")
                .addQueryParameter("fl[]", "runtime")
                .addQueryParameter("fl[]", "format")
                .addQueryParameter("sort[]", sort)
                .addQueryParameter("rows", (limit * 2).coerceAtMost(40).toString())
                .addQueryParameter("page", "1")
                .addQueryParameter("output", "json")
                .build()

            val request = Request.Builder()
                .url(url)
                .header("User-Agent", YtDlpManager.DESKTOP_UA)
                .header("Accept", "application/json")
                .get()
                .build()

            OmegaHttp.client.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) return emptyList()
                val root = JsonParser.parseString(resp.body.string()).asJsonObjectOrNull() ?: return emptyList()
                val docs = root["response"]?.asJsonObjectOrNull()?.get("docs")?.asJsonArrayOrNull()
                    ?: return emptyList()

                buildList {
                    for (el in docs) {
                        val doc = el.asJsonObjectOrNull() ?: continue
                        val id = doc.str("identifier") ?: continue
                        val title = doc.strOrFirst("title") ?: continue
                        if (isTrailerOrNonMovie(title)) continue

                        val formats = doc.strList("format").map { it.lowercase() }
                        val hasPlayableFormat = formats.isEmpty() || formats.any { fmt ->
                            ARCHIVE_VIDEO_FORMATS.any { fmt.contains(it) }
                        }
                        if (!hasPlayableFormat) continue

                        val durationSec = parseDurationSeconds(doc.strOrFirst("runtime"))
                        val itemSizeBytes = doc.long("item_size") ?: 0L
                        val downloads = doc.long("downloads") ?: 0L
                        if (durationSec in 1 until MIN_MOVIE_DURATION_SEC) continue
                        if (durationSec > MAX_MOVIE_DURATION_SEC) continue
                        if (durationSec == 0L && itemSizeBytes in 1 until MIN_ARCHIVE_ITEM_BYTES) continue
                        if (durationSec == 0L && itemSizeBytes == 0L) continue

                        val year = doc.strOrFirst("year")?.take(4)
                        val creator = doc.strOrFirst("creator")
                        val subjects = doc.strList("subject")
                        val rawDesc = doc.strOrFirst("description")?.let(::cleanHtmlText)

                        val base = MovieSearchResult(
                            id = id,
                            title = title,
                            pageUrl = "https://archive.org/details/$id",
                            source = SearchSource.ARCHIVE_ORG,
                            thumbnailUrl = "https://archive.org/services/img/$id",
                            year = year,
                            durationSec = durationSec,
                            sizeBytes = itemSizeBytes,
                            subtitle = creator ?: "Internet Archive",
                            description = rawDesc,
                            popularityScore = downloads
                        )
                        add(enrichMovieMetadata(base, defaultIndustry, subjects + defaultGenres, rawDesc))
                        if (size >= limit) break
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Archive.org browse failed: ${e.message}")
            emptyList()
        }
    }

    private fun browseDailymotionMovies(
        query: String,
        defaultIndustry: MovieIndustry,
        limit: Int,
        extraTags: List<String> = emptyList()
    ): List<MovieSearchResult> {
        return try {
            val url = "https://api.dailymotion.com/videos".toHttpUrl().newBuilder()
                .addQueryParameter("search", query)
                .addQueryParameter("longer_than", "50")
                .addQueryParameter("sort", "visited")
                .addQueryParameter("limit", (limit + 6).toString())
                .addQueryParameter(
                    "fields",
                    "id,title,duration,created_time,views_total,thumbnail_1080_url,thumbnail_720_url,thumbnail_480_url,url,owner.screenname"
                )
                .build()

            val request = Request.Builder()
                .url(url)
                .header("User-Agent", YtDlpManager.DESKTOP_UA)
                .header("Accept", "application/json")
                .get()
                .build()

            OmegaHttp.client.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) return emptyList()
                val root = JsonParser.parseString(resp.body.string()).asJsonObjectOrNull() ?: return emptyList()
                val list = root["list"]?.asJsonArrayOrNull() ?: return emptyList()

                buildList {
                    for (el in list) {
                        val obj = el.asJsonObjectOrNull() ?: continue
                        val id = obj.str("id") ?: continue
                        val title = obj.str("title") ?: continue
                        val durationSec = obj.long("duration") ?: 0L
                        if (durationSec !in MIN_MODERN_MOVIE_SEC..MAX_MOVIE_DURATION_SEC) continue
                        if (isTrailerOrNonMovie(title)) continue

                        val views = obj.long("views_total") ?: 0L
                        val pageUrl = obj.str("url") ?: "https://www.dailymotion.com/video/$id"
                        val createdEpoch = obj.long("created_time") ?: 0L
                        val createdYear = if (createdEpoch > 0L) {
                            Calendar.getInstance().apply {
                                timeInMillis = createdEpoch * 1000L
                            }.get(Calendar.YEAR).toString()
                        } else null

                        val bestThumb = obj.str("thumbnail_1080_url")
                            ?: obj.str("thumbnail_720_url")
                            ?: obj.str("thumbnail_480_url")

                        val base = MovieSearchResult(
                            id = id,
                            title = title,
                            pageUrl = pageUrl,
                            source = SearchSource.DAILYMOTION,
                            thumbnailUrl = bestThumb,
                            year = createdYear,
                            durationSec = durationSec,
                            subtitle = obj.str("owner.screenname") ?: "Dailymotion",
                            popularityScore = views
                        )
                        add(enrichMovieMetadata(base, defaultIndustry, extraTags))
                        if (size >= limit) break
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Dailymotion browse failed: ${e.message}")
            emptyList()
        }
    }

    private fun browsePeerTubeMovies(
        query: String,
        defaultIndustry: MovieIndustry,
        limit: Int,
        extraTags: List<String> = emptyList()
    ): List<MovieSearchResult> {
        return try {
            val url = "https://sepiasearch.org/api/v1/search/videos".toHttpUrl().newBuilder()
                .addQueryParameter("search", query)
                .addQueryParameter("durationMin", MIN_MODERN_MOVIE_SEC.toString())
                .addQueryParameter("sort", "-views")
                .addQueryParameter("count", limit.toString())
                .addQueryParameter("nsfw", "false")
                .build()

            val request = Request.Builder()
                .url(url)
                .header("User-Agent", YtDlpManager.DESKTOP_UA)
                .header("Accept", "application/json")
                .get()
                .build()

            OmegaHttp.client.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) return emptyList()
                val root = JsonParser.parseString(resp.body.string()).asJsonObjectOrNull() ?: return emptyList()
                val data = root["data"]?.asJsonArrayOrNull() ?: return emptyList()

                buildList {
                    for (el in data) {
                        val obj = el.asJsonObjectOrNull() ?: continue
                        val id = obj.str("uuid") ?: obj.str("id") ?: continue
                        val title = obj.str("name") ?: continue
                        val pageUrl = obj.str("url") ?: continue
                        val durationSec = obj.long("duration") ?: 0L
                        if (durationSec !in MIN_MODERN_MOVIE_SEC..MAX_MOVIE_DURATION_SEC) continue
                        if (isTrailerOrNonMovie(title)) continue

                        val year = (obj.str("originallyPublishedAt") ?: obj.str("publishedAt"))?.take(4)
                        val channel = obj["channel"]?.asJsonObjectOrNull()?.str("displayName")
                        val desc = obj.str("description")?.let(::cleanHtmlText)
                        val views = obj.long("views") ?: 0L
                        val base = MovieSearchResult(
                            id = id,
                            title = title,
                            pageUrl = pageUrl,
                            source = SearchSource.PEERTUBE,
                            thumbnailUrl = obj.str("thumbnailUrl"),
                            year = year,
                            durationSec = durationSec,
                            subtitle = channel ?: "PeerTube",
                            description = desc,
                            popularityScore = views
                        )
                        add(enrichMovieMetadata(base, defaultIndustry, extraTags, rawDescription = desc))
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "PeerTube browse failed: ${e.message}")
            emptyList()
        }
    }

    // ---------------- Source 1: Internet Archive (Full Feature Films) ----------------

    private fun searchArchiveOrg(query: String, limit: Int): List<MovieSearchResult> {
        val tokens = significantTokens(query)
        if (tokens.isEmpty()) return emptyList()

        val titleClause = tokens.joinToString(" AND ") { "\"$it\"" }
        val filmCollections = "(feature_films OR opensource_movies OR scifi_horror OR film_noir OR " +
            "comedy_films OR silent_films OR animationandcartoons OR moviesandfilms OR documentaries)"
        val excludeClause = "-collection:(movie_trailers OR trailers OR commercials) " +
            "-title:(trailer OR teaser OR preview OR promo OR commercial)"

        val primary = executeArchiveQuery(
            luceneQuery = "title:($titleClause) AND mediatype:(movies) AND collection:$filmCollections AND $excludeClause",
            originalQuery = query,
            limit = limit
        )
        if (primary.size >= 5) return primary

        val fallback = executeArchiveQuery(
            luceneQuery = "title:($titleClause) AND mediatype:(movies) AND $excludeClause",
            originalQuery = query,
            limit = limit
        )
        return (primary + fallback).distinctBy { it.id }
    }

    private fun executeArchiveQuery(
        luceneQuery: String,
        originalQuery: String,
        limit: Int
    ): List<MovieSearchResult> {
        return try {
            val url = "https://archive.org/advancedsearch.php".toHttpUrl().newBuilder()
                .addQueryParameter("q", luceneQuery)
                .addQueryParameter("fl[]", "identifier")
                .addQueryParameter("fl[]", "title")
                .addQueryParameter("fl[]", "year")
                .addQueryParameter("fl[]", "creator")
                .addQueryParameter("fl[]", "subject")
                .addQueryParameter("fl[]", "description")
                .addQueryParameter("fl[]", "item_size")
                .addQueryParameter("fl[]", "downloads")
                .addQueryParameter("fl[]", "runtime")
                .addQueryParameter("fl[]", "format")
                .addQueryParameter("rows", (limit * 2).coerceAtMost(40).toString())
                .addQueryParameter("page", "1")
                .addQueryParameter("output", "json")
                .build()

            val request = Request.Builder()
                .url(url)
                .header("User-Agent", YtDlpManager.DESKTOP_UA)
                .header("Accept", "application/json")
                .get()
                .build()

            OmegaHttp.client.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) return emptyList()
                val root = JsonParser.parseString(resp.body.string()).asJsonObjectOrNull() ?: return emptyList()
                val docs = root["response"]?.asJsonObjectOrNull()?.get("docs")?.asJsonArrayOrNull()
                    ?: return emptyList()

                buildList {
                    for (el in docs) {
                        val doc = el.asJsonObjectOrNull() ?: continue
                        val id = doc.str("identifier") ?: continue
                        val title = doc.strOrFirst("title") ?: continue

                        if (isTrailerOrNonMovie(title)) continue
                        if (!matchesMovieTitle(title, originalQuery)) continue

                        val formats = doc.strList("format").map { it.lowercase() }
                        val hasPlayableFormat = formats.isEmpty() || formats.any { fmt ->
                            ARCHIVE_VIDEO_FORMATS.any { fmt.contains(it) }
                        }
                        if (!hasPlayableFormat) continue

                        val durationSec = parseDurationSeconds(doc.strOrFirst("runtime"))
                        val itemSizeBytes = doc.long("item_size") ?: 0L
                        val downloads = doc.long("downloads") ?: 0L

                        if (durationSec in 1 until MIN_MOVIE_DURATION_SEC) continue
                        if (durationSec == 0L && itemSizeBytes in 1 until MIN_ARCHIVE_ITEM_BYTES) continue
                        if (durationSec == 0L && itemSizeBytes == 0L) continue

                        val year = doc.strOrFirst("year")?.take(4)
                        val creator = doc.strOrFirst("creator")
                        val subjects = doc.strList("subject")
                        val rawDesc = doc.strOrFirst("description")?.let(::cleanHtmlText)
                        val base = MovieSearchResult(
                            id = id,
                            title = title,
                            pageUrl = "https://archive.org/details/$id",
                            source = SearchSource.ARCHIVE_ORG,
                            thumbnailUrl = "https://archive.org/services/img/$id",
                            year = year,
                            durationSec = durationSec,
                            sizeBytes = itemSizeBytes,
                            subtitle = creator ?: "Internet Archive",
                            description = rawDesc,
                            popularityScore = downloads
                        )
                        add(enrichMovieMetadata(base, MovieIndustry.HOLLYWOOD, subjects, rawDesc))
                        if (size >= limit) break
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Archive.org search failed: ${e.message}")
            emptyList()
        }
    }

    // ---------------- Source 2: YouTube Full Movies (40+ min) ----------------

    private suspend fun searchYouTubeFullMovies(context: Context, query: String): List<MovieSearchResult> {
        val searchTerm = if (query.contains("full movie", ignoreCase = true)) query else "$query full movie"
        val fast = searchYouTubeHtmlFast(searchTerm, MovieIndustry.HOLLYWOOD, emptyList(), limit = 20, minDurationSec = MIN_MOVIE_DURATION_SEC)
            .filter { matchesMovieTitle(it.title, query) }
            .take(12)
        if (fast.isNotEmpty()) return fast

        val raw = YtDlpManager.searchYouTube(context, searchTerm, limit = 25)
        return raw.filter { item ->
            item.durationSec in MIN_MOVIE_DURATION_SEC..MAX_MOVIE_DURATION_SEC &&
                !isTrailerOrNonMovie(item.title) &&
                matchesMovieTitle(item.title, query)
        }.map { enrichMovieMetadata(it) }.take(10)
    }

    // ---------------- Source 3: Dailymotion Full Movies (Official Graph API) ----------------

    private fun searchDailymotionFullMovies(query: String): List<MovieSearchResult> {
        return try {
            val searchTerm = if (query.contains("full movie", ignoreCase = true)) query else "$query full movie"
            val url = "https://api.dailymotion.com/videos".toHttpUrl().newBuilder()
                .addQueryParameter("search", searchTerm)
                .addQueryParameter("longer_than", "40")
                .addQueryParameter("limit", "15")
                .addQueryParameter(
                    "fields",
                    "id,title,duration,created_time,views_total,thumbnail_1080_url,thumbnail_720_url,thumbnail_480_url,url,owner.screenname"
                )
                .build()

            val request = Request.Builder()
                .url(url)
                .header("User-Agent", YtDlpManager.DESKTOP_UA)
                .header("Accept", "application/json")
                .get()
                .build()

            OmegaHttp.client.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) return emptyList()
                val root = JsonParser.parseString(resp.body.string()).asJsonObjectOrNull() ?: return emptyList()
                val list = root["list"]?.asJsonArrayOrNull() ?: return emptyList()

                buildList {
                    for (el in list) {
                        val obj = el.asJsonObjectOrNull() ?: continue
                        val id = obj.str("id") ?: continue
                        val title = obj.str("title") ?: continue
                        val durationSec = obj.long("duration") ?: 0L
                        if (durationSec !in MIN_MOVIE_DURATION_SEC..MAX_MOVIE_DURATION_SEC) continue
                        if (isTrailerOrNonMovie(title)) continue
                        if (!matchesMovieTitle(title, query)) continue

                        val pageUrl = obj.str("url") ?: "https://www.dailymotion.com/video/$id"
                        val thumb = obj.str("thumbnail_1080_url")
                            ?: obj.str("thumbnail_720_url")
                            ?: obj.str("thumbnail_480_url")
                        val views = obj.long("views_total") ?: 0L
                        val base = MovieSearchResult(
                            id = id,
                            title = title,
                            pageUrl = pageUrl,
                            source = SearchSource.DAILYMOTION,
                            thumbnailUrl = thumb,
                            durationSec = durationSec,
                            subtitle = obj.str("owner.screenname") ?: "Dailymotion",
                            popularityScore = views
                        )
                        add(enrichMovieMetadata(base))
                    }
                }.take(8)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Dailymotion search failed for '$query': ${e.message}")
            emptyList()
        }
    }

    // ---------------- Source 4: PeerTube / Open Movies (SepiaSearch API) ----------------

    private fun searchPeerTubeFullMovies(query: String): List<MovieSearchResult> {
        return try {
            val url = "https://sepiasearch.org/api/v1/search/videos".toHttpUrl().newBuilder()
                .addQueryParameter("search", query)
                .addQueryParameter("durationMin", MIN_MOVIE_DURATION_SEC.toString())
                .addQueryParameter("count", "12")
                .addQueryParameter("nsfw", "false")
                .build()

            val request = Request.Builder()
                .url(url)
                .header("User-Agent", YtDlpManager.DESKTOP_UA)
                .header("Accept", "application/json")
                .get()
                .build()

            OmegaHttp.client.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) return emptyList()
                val root = JsonParser.parseString(resp.body.string()).asJsonObjectOrNull() ?: return emptyList()
                val data = root["data"]?.asJsonArrayOrNull() ?: return emptyList()

                buildList {
                    for (el in data) {
                        val obj = el.asJsonObjectOrNull() ?: continue
                        val id = obj.str("uuid") ?: obj.str("id") ?: continue
                        val title = obj.str("name") ?: continue
                        val pageUrl = obj.str("url") ?: continue
                        val durationSec = obj.long("duration") ?: 0L
                        if (durationSec !in MIN_MOVIE_DURATION_SEC..MAX_MOVIE_DURATION_SEC) continue
                        if (isTrailerOrNonMovie(title)) continue
                        if (!matchesMovieTitle(title, query)) continue

                        val year = obj.str("originallyPublishedAt")?.take(4)
                        val channel = obj["channel"]?.asJsonObjectOrNull()?.str("displayName")
                        val desc = obj.str("description")?.let(::cleanHtmlText)
                        val views = obj.long("views") ?: 0L
                        val base = MovieSearchResult(
                            id = id,
                            title = title,
                            pageUrl = pageUrl,
                            source = SearchSource.PEERTUBE,
                            thumbnailUrl = obj.str("thumbnailUrl"),
                            year = year,
                            durationSec = durationSec,
                            subtitle = channel ?: "PeerTube",
                            description = desc,
                            popularityScore = views
                        )
                        add(enrichMovieMetadata(base, MovieIndustry.WORLD, rawDescription = desc))
                    }
                }.take(8)
            }
        } catch (e: Exception) {
            Log.w(TAG, "PeerTube search failed for '$query': ${e.message}")
            emptyList()
        }
    }

    // ---------------- Internet Archive Full-Movie File & Subtitle Resolution ----------------

    /**
     * Fetches the Internet Archive item metadata JSON and extracts complete movie files,
     * synopsis, creator, genres, and `.srt`/`.vtt` subtitle tracks.
     */
    suspend fun resolveArchiveMovie(result: MovieSearchResult): VideoMetadata? = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder()
                .url("https://archive.org/metadata/${Uri.encode(result.id)}")
                .header("User-Agent", YtDlpManager.DESKTOP_UA)
                .header("Accept", "application/json")
                .get()
                .build()

            OmegaHttp.client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext null
                val root = JsonParser.parseString(resp.body.string()).asJsonObjectOrNull() ?: return@withContext null
                val metaObj = root["metadata"]?.asJsonObjectOrNull()
                val filesArr = root["files"]?.asJsonArrayOrNull() ?: return@withContext null

                val title = metaObj?.strOrFirst("title") ?: result.title
                val description = metaObj?.strOrFirst("description")?.let(::cleanHtmlText) ?: result.description
                val year = metaObj?.strOrFirst("year")?.take(4) ?: result.year
                val creator = metaObj?.strOrFirst("creator") ?: result.subtitle
                val subjects = metaObj?.strList("subject").orEmpty()
                val genres = inferGenres(title, description, subjects, result.genres)
                var durationSec = parseDurationSeconds(metaObj?.strOrFirst("runtime"))
                if (durationSec == 0L) durationSec = result.durationSec

                data class RawFile(
                    val name: String,
                    val ext: String,
                    val url: String,
                    val height: Int,
                    val sizeBytes: Long,
                    val lengthSec: Long,
                    val formatLabel: String,
                    val isVideo: Boolean
                )

                val subtitleTracks = mutableListOf<SubtitleTrack>()
                var bestImageUrl: String? = null

                val rawFiles = buildList {
                    for (el in filesArr) {
                        val f = el.asJsonObjectOrNull() ?: continue
                        val name = f.str("name") ?: continue
                        val lowerName = name.lowercase()
                        if (lowerName.contains("_thumbs/") || lowerName.endsWith(".thumbs")) continue

                        val ext = lowerName.substringAfterLast('.', "")
                        val encodedName = Uri.encode(name, "/")
                        val downloadUrl = "https://archive.org/download/${Uri.encode(result.id)}/$encodedName"

                        if (bestImageUrl == null && ext in setOf("jpg", "jpeg", "png") && !lowerName.startsWith("__ia_thumb")) {
                            bestImageUrl = downloadUrl
                        }

                        if (ext in SUBTITLE_EXTENSIONS) {
                            val mime = if (ext == "vtt") MimeTypes.TEXT_VTT else MimeTypes.APPLICATION_SUBRIP
                            val cleanSubName = name.substringAfterLast('/').substringBeforeLast('.')
                            subtitleTracks.add(
                                SubtitleTrack(
                                    label = cleanSubName.take(28).ifBlank { "Subtitles (${ext.uppercase()})" },
                                    url = downloadUrl,
                                    mimeType = mime,
                                    language = if (lowerName.contains("hi") || lowerName.contains("hindi")) "hi" else "en"
                                )
                            )
                            continue
                        }

                        if (ARCHIVE_TRAILER_FILE_WORDS.any { lowerName.contains(it) }) continue

                        val isVideo = ext in VIDEO_EXTENSIONS
                        val isAudio = ext in AUDIO_EXTENSIONS
                        if (!isVideo && !isAudio) continue

                        val sizeBytes = f.str("size")?.toLongOrNull() ?: 0L
                        if (isVideo && sizeBytes in 1L..5_000_000L) continue

                        val height = f.str("height")?.toIntOrNull()
                            ?: Regex("""(\d{3,4})p""", RegexOption.IGNORE_CASE).find(name)?.groupValues?.getOrNull(1)?.toIntOrNull()
                            ?: 0
                        val len = parseDurationSeconds(f.str("length"))
                        if (len > durationSec) durationSec = len

                        val formatDesc = f.str("format") ?: ext.uppercase()

                        add(
                            RawFile(
                                name = name,
                                ext = ext,
                                url = downloadUrl,
                                height = height,
                                sizeBytes = sizeBytes,
                                lengthSec = len,
                                formatLabel = formatDesc,
                                isVideo = isVideo
                            )
                        )
                    }
                }

                val allVideos = rawFiles.filter { it.isVideo }
                val hasFullLengthFile = allVideos.any { it.lengthSec >= 1500L || it.sizeBytes >= 100L * 1024L * 1024L }

                val videos = allVideos
                    .filter { v ->
                        if (!hasFullLengthFile) true
                        else (v.lengthSec == 0L || v.lengthSec >= 1500L) && (v.sizeBytes == 0L || v.sizeBytes >= 50L * 1024L * 1024L)
                    }
                    .sortedWith(
                        compareByDescending<RawFile> { v ->
                            when {
                                v.height > 0 -> v.height
                                v.formatLabel.contains("512kb", ignoreCase = true) -> 240
                                v.sizeBytes >= 700L * 1024L * 1024L -> 1080
                                v.sizeBytes >= 300L * 1024L * 1024L -> 720
                                else -> 0
                            }
                        }
                            .thenBy { if (it.ext == "mp4") 0 else if (it.ext == "mkv") 1 else 2 }
                            .thenByDescending { it.sizeBytes }
                    )
                    .distinctBy { Triple(it.height, it.ext, it.sizeBytes / (5 * 1024 * 1024)) }
                    .take(10)

                val audios = rawFiles
                    .filter { !it.isVideo }
                    .sortedByDescending { it.sizeBytes }
                    .take(4)

                if (videos.isEmpty() && audios.isEmpty()) return@withContext null

                val streams = videos.mapIndexed { idx, v ->
                    val label = when {
                        v.height > 0 -> "${v.height}p ${v.ext.uppercase()}"
                        videos.size == 1 -> "Original (${v.ext.uppercase()})"
                        else -> "${v.ext.uppercase()} #${idx + 1}"
                    }
                    StreamCandidate(
                        url = v.url,
                        kind = StreamKind.PROGRESSIVE,
                        label = label,
                        height = v.height,
                        sizeBytes = v.sizeBytes,
                        headers = mapOf("User-Agent" to YtDlpManager.DESKTOP_UA),
                        source = ResolveSource.ARCHIVE
                    )
                }

                val qualities = videos.mapIndexed { idx, v ->
                    val label = when {
                        v.height > 0 -> "${v.height}p (${v.ext.uppercase()})"
                        videos.size == 1 -> "Original (${v.ext.uppercase()})"
                        else -> "${v.formatLabel.take(18)} (${v.ext.uppercase()}) #${idx + 1}"
                    }
                    VideoQuality(
                        label = label,
                        url = v.url,
                        size = if (v.sizeBytes > 0) StorageUtils.formatSize(v.sizeBytes) else "Unknown",
                        type = "video",
                        formatId = "best",
                        isCombined = true,
                        sizeBytes = v.sizeBytes,
                        useDirectUrl = true,
                        headers = mapOf("User-Agent" to YtDlpManager.DESKTOP_UA)
                    )
                }

                val audioQualities = audios.map { a ->
                    VideoQuality(
                        label = "Audio (${a.ext.uppercase()})",
                        url = a.url,
                        size = if (a.sizeBytes > 0) StorageUtils.formatSize(a.sizeBytes) else "Unknown",
                        type = "audio",
                        formatId = "bestaudio",
                        isCombined = true,
                        sizeBytes = a.sizeBytes,
                        useDirectUrl = true,
                        headers = mapOf("User-Agent" to YtDlpManager.DESKTOP_UA)
                    )
                }

                VideoMetadata(
                    title = title,
                    qualities = qualities,
                    audioQualities = audioQualities,
                    thumbnailUrl = bestImageUrl ?: result.thumbnailUrl,
                    isPlaylist = false,
                    sourceUrl = result.pageUrl,
                    streams = streams,
                    durationSec = durationSec,
                    resolvedBy = ResolveSource.ARCHIVE,
                    description = description,
                    year = year,
                    creator = creator,
                    genres = genres,
                    subtitleTracks = subtitleTracks.distinctBy { it.url }.take(6)
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to resolve Archive.org movie ${result.id}: ${e.message}")
            null
        }
    }

    private fun cleanHtmlText(raw: String): String =
        raw.replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), "\n")
            .replace(Regex("<[^>]+>"), " ")
            .replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&nbsp;", " ")
            .replace(Regex("[ \\t]+"), " ")
            .replace(Regex("\\n{3,}"), "\n\n")
            .trim()
            .take(900)

    private fun parseDurationSeconds(raw: String?): Long {
        if (raw.isNullOrBlank()) return 0L
        val trimmed = raw.trim()
        trimmed.toDoubleOrNull()?.let { return it.toLong().coerceAtLeast(0L) }
        val parts = trimmed.split(':').mapNotNull { it.trim().toLongOrNull() }
        return when (parts.size) {
            3 -> parts[0] * 3600 + parts[1] * 60 + parts[2]
            2 -> parts[0] * 60 + parts[1]
            else -> 0L
        }
    }

    private fun JsonElement.asJsonObjectOrNull(): JsonObject? = if (isJsonObject) asJsonObject else null
    private fun JsonElement.asJsonArrayOrNull() = if (isJsonArray) asJsonArray else null
    private fun JsonObject.str(key: String): String? =
        get(key)?.takeIf { it.isJsonPrimitive }?.asString?.takeIf { it.isNotBlank() }

    private fun JsonObject.long(key: String): Long? =
        get(key)?.takeIf { it.isJsonPrimitive }?.let { prim ->
            if (prim.asJsonPrimitive.isNumber) prim.asLong else prim.asString.toLongOrNull()
        }

    private fun JsonObject.strOrFirst(key: String): String? {
        val el = get(key) ?: return null
        return when {
            el.isJsonPrimitive -> el.asString.takeIf { it.isNotBlank() }
            el.isJsonArray -> el.asJsonArray.firstOrNull()?.takeIf { it.isJsonPrimitive }?.asString?.takeIf { it.isNotBlank() }
            else -> null
        }
    }

    private fun JsonObject.strList(key: String): List<String> {
        val el = get(key) ?: return emptyList()
        return when {
            el.isJsonPrimitive -> listOf(el.asString)
            el.isJsonArray -> el.asJsonArray.mapNotNull { if (it.isJsonPrimitive) it.asString else null }
            else -> emptyList()
        }
    }
}
