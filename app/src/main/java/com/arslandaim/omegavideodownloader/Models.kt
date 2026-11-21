/*
 * Omega Video Downloader Project Original (2026)
 * arslandaim-hub (GitHub.com/arslandaim-hub)
 * Licenced Under GPL-3.0+
*/
package com.arslandaim.omegavideodownloader

import androidx.compose.runtime.Immutable

/** Delivery format of a playable/downloadable stream. */
enum class StreamKind { HLS, DASH, PROGRESSIVE }

/** Where a stream was discovered. */
enum class ResolveSource { DIRECT, YTDLP, BROWSER, ARCHIVE }

/** Source catalog for name-based movie/video search. */
enum class SearchSource { ARCHIVE_ORG, YOUTUBE, DAILYMOTION, PEERTUBE }

/** Film industry / catalog region for the Movies hub. */
enum class MovieIndustry(val label: String) {
    ALL("All"),
    HOLLYWOOD("Hollywood"),
    BOLLYWOOD("Bollywood"),
    WORLD("World")
}

/** Curated genres for filtering pre-fetched and searched movies. */
enum class MovieGenre(val label: String) {
    ALL("All Genres"),
    ACTION("Action"),
    SCIFI("Sci-Fi"),
    HORROR("Horror"),
    THRILLER("Thriller"),
    COMEDY("Comedy"),
    DRAMA("Drama"),
    ROMANCE("Romance"),
    DOCUMENTARY("Documentary"),
    ANIMATION("Animation")
}

/** Release year bucket filter for the Movies hub. */
enum class MovieYearFilter(val label: String) {
    ALL("All Years"),
    Y2020S("2020s"),
    Y2010S("2010s"),
    Y2000S("2000s"),
    CLASSIC("Classic")
}

/** Sort order for movie browsing & search results. */
enum class MovieSortOrder(val label: String) {
    NEWEST("Newest First"),
    POPULAR("Most Popular"),
    LONGEST("Longest Runtime")
}

/** UI filter for movie/video search. */
enum class SearchFilter(val label: String) {
    FULL_MOVIES("Full Movies"),
    ARCHIVE("Archive.org"),
    YOUTUBE_ANY("YouTube Videos")
}

@Immutable
data class SubtitleTrack(
    val label: String,
    val url: String,
    val mimeType: String,
    val language: String = "en"
)

@Immutable
data class MovieSearchResult(
    val id: String,
    val title: String,
    val pageUrl: String,
    val source: SearchSource,
    val thumbnailUrl: String? = null,
    val year: String? = null,
    val durationSec: Long = 0L,
    val sizeBytes: Long = 0L,
    val subtitle: String? = null,
    val industry: MovieIndustry = MovieIndustry.HOLLYWOOD,
    val genres: List<String> = emptyList(),
    val releaseYear: Int = 0,
    val description: String? = null,
    val popularityScore: Long = 0L
)

/**
 * A single playable stream that the resolver discovered for a link.
 *
 * @param headers HTTP headers (Referer, Origin, Cookie, User-Agent...) that the host requires.
 * @param audioUrl optional separate audio track (e.g. YouTube DASH-style progressive formats).
 */
@Immutable
data class StreamCandidate(
    val url: String,
    val kind: StreamKind,
    val label: String,
    val height: Int = 0,
    val sizeBytes: Long = 0L,
    val headers: Map<String, String> = emptyMap(),
    val audioUrl: String? = null,
    val source: ResolveSource = ResolveSource.YTDLP
)

@Immutable
data class VideoQuality(
    val label: String,
    val url: String,
    val size: String,
    val type: String = "video",
    val formatId: String? = null,
    val isCombined: Boolean = false,
    val sizeBytes: Long = 0L,
    /** When true the download is performed on [url] directly (sniffed/direct streams) instead of the page URL. */
    val useDirectUrl: Boolean = false,
    val headers: Map<String, String> = emptyMap()
)

@Immutable
data class VideoMetadata(
    val title: String,
    val qualities: List<VideoQuality>,
    val audioQualities: List<VideoQuality> = emptyList(),
    val thumbnailUrl: String? = null,
    val isPlaylist: Boolean = false,
    val playlistCount: Int? = null,
    val sourceUrl: String = "",
    val streams: List<StreamCandidate> = emptyList(),
    val durationSec: Long = 0L,
    val resolvedBy: ResolveSource = ResolveSource.YTDLP,
    val description: String? = null,
    val year: String? = null,
    val creator: String? = null,
    val genres: List<String> = emptyList(),
    val subtitleTracks: List<SubtitleTrack> = emptyList(),
    val isGif: Boolean = false,
    val mediaItems: List<VideoMetadata> = emptyList()
)

@Immutable
data class ActiveDownload(
    val id: Long,
    val title: String,
    val progress: Float,
    val totalBytes: Long,
    val downloadedBytes: Long,
    val status: String,
    val thumbnailUrl: String? = null,
    val type: String = "video",
    val playlistProgress: String? = null, // e.g., "3/10"
    val speedBytesPerSec: Long = 0L,
    val etaSeconds: Long = -1L,
    val isPaused: Boolean = false,
    val canPause: Boolean = false
)

data class ResumableDownloadTask(
    val id: Long,
    val url: String,
    val title: String,
    val type: String = "video",
    val formatId: String? = null,
    val isCombined: Boolean = false,
    val useDirectUrl: Boolean = false,
    val cookiesPath: String? = null,
    val headers: Map<String, String> = emptyMap(),
    val totalBytes: Long = 0L,
    val downloadedBytes: Long = 0L,
    val createdAt: Long = System.currentTimeMillis()
)

data class DownloadedVideo(
    val title: String,
    val localPath: String,
    val timestamp: Long,
    val thumbnailUrl: String? = null,
    val isLocked: Boolean = false,
    val type: String = "video",
    val playlistId: String? = null,
    val isPlaylistGroup: Boolean = false,
    val durationMs: Long = 0L,
    val isMovie: Boolean = false,
    val subtitleUrl: String? = null
)

/** Persisted watch history entry supporting resume-where-left-off for both streams and local files. */
@Immutable
data class WatchHistoryItem(
    val id: String,
    val title: String,
    val thumbnailUrl: String? = null,
    val isStream: Boolean = false,
    /** Local content/file URI for downloads, or original page/stream URL for online streams. */
    val sourceUrl: String,
    /** Last resolved direct stream URL (optional fast hint). */
    val lastStreamUrl: String? = null,
    val qualityLabel: String? = null,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val lastWatchedAt: Long = System.currentTimeMillis()
) {
    val progressFraction: Float
        get() = if (durationMs > 0L) (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f) else 0f

    val isCompleted: Boolean
        get() = durationMs > 0L && (progressFraction >= 0.95f || (durationMs - positionMs) <= 15_000L)

    /** Safe seek target when resuming: restarts from 0 if barely started (< 3s) or already completed. */
    val resumePositionMs: Long
        get() = if (positionMs >= 3_000L && !isCompleted) positionMs else 0L

    companion object {
        fun localId(localPath: String): String = "local:${localPath.trim()}"
        fun streamId(pageUrl: String): String = "stream:${pageUrl.trim()}"
    }
}

/** Represents the currently active video session shared seamlessly between Full-Screen and Mini Player. */
@Immutable
data class ActiveVideoSession(
    val historyId: String,
    /** Local path/URI or `stream:<sessionId>` key in StreamRegistry. */
    val videoUrl: String,
    val title: String,
    val thumbnailUrl: String? = null,
    val sourceUrl: String,
    val isStream: Boolean = false,
    val isLocked: Boolean = false,
    val initialPositionMs: Long = 0L
)

/** A searched/discovered movie or video saved by the user via the Like (Heart) button. */
@Immutable
data class LikedVideo(
    val id: String,
    val title: String,
    val pageUrl: String,
    val source: SearchSource = SearchSource.ARCHIVE_ORG,
    val thumbnailUrl: String? = null,
    val year: String? = null,
    val durationSec: Long = 0L,
    val sizeBytes: Long = 0L,
    val subtitle: String? = null,
    val likedAt: Long = System.currentTimeMillis()
) {
    fun toMovieSearchResult(): MovieSearchResult = MovieSearchResult(
        id = id,
        title = title,
        pageUrl = pageUrl,
        source = source,
        thumbnailUrl = thumbnailUrl,
        year = year,
        durationSec = durationSec,
        sizeBytes = sizeBytes,
        subtitle = subtitle
    )

    companion object {
        fun fromSearchResult(result: MovieSearchResult): LikedVideo = LikedVideo(
            id = result.id,
            title = result.title,
            pageUrl = result.pageUrl.trim(),
            source = result.source,
            thumbnailUrl = result.thumbnailUrl,
            year = result.year,
            durationSec = result.durationSec,
            sizeBytes = result.sizeBytes,
            subtitle = result.subtitle,
            likedAt = System.currentTimeMillis()
        )
    }
}
