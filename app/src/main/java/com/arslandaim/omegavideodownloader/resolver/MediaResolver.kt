/*
 * Omega Video Downloader Project Original (2026)
 * arslandaim-hub (GitHub.com/arslandaim-hub)
 * Licenced Under GPL-3.0+
*/
package com.arslandaim.omegavideodownloader.resolver

import android.content.Context
import com.arslandaim.omegavideodownloader.CookiesManager
import com.arslandaim.omegavideodownloader.MovieSearchResult
import com.arslandaim.omegavideodownloader.SearchSource
import com.arslandaim.omegavideodownloader.UrlUtils
import com.arslandaim.omegavideodownloader.VideoMetadata
import com.arslandaim.omegavideodownloader.YtDlpManager

sealed class ResolveResult {
    data class Success(val metadata: VideoMetadata) : ResolveResult()
    data class NeedsBrowserSniffer(val url: String, val reason: String?) : ResolveResult()
    data class Error(val message: String) : ResolveResult()
}

/**
 * Coordinates the multi-layer link resolver:
 *  - Layer 0: Archive.org item API & Twitter/X fast JSON API (FxTwitter / VxTwitter + t.co expansion)
 *  - Layer 1: Direct media probe (skipped for known HTML page platforms like YouTube/Instagram)
 *  - Layer 2: yt-dlp extraction (1,800+ supported sites + generic HTML5 extractor + authenticated GraphQL/Spaces)
 *  - Layer 3: Signals [ResolveResult.NeedsBrowserSniffer] for JS/iframe movie websites or gated tweets.
 */
class MediaResolver(context: Context) {
    private val appContext = context.applicationContext

    private fun shouldSkipDirectProbe(url: String): Boolean {
        if (UrlUtils.mediaKindFromUrl(url) != null) return false
        return UrlUtils.hostMatches(
            url,
            "youtube.com", "youtu.be", "music.youtube.com",
            "instagram.com", "facebook.com", "fb.watch",
            "tiktok.com", "twitter.com", "x.com", "t.co",
            "fxtwitter.com", "vxtwitter.com", "fixupx.com", "fixvx.com",
            "twittpr.com", "girlcockx.com", "stupidpenisx.com", "xcancel.com",
            "pscp.tv", "periscope.tv",
            "dailymotion.com", "dai.ly", "vimeo.com", "twitch.tv"
        )
    }

    suspend fun resolve(
        rawInput: String,
        allowBrowserFallback: Boolean,
        onProgress: (String) -> Unit = {}
    ): ResolveResult {
        val rawUrl = UrlUtils.extractUrl(rawInput)
            ?: return ResolveResult.Error("Please enter a valid video or movie URL.")

        // Unwrap t.co shortlinks early so downstream resolvers & platform checks see the true host
        val url = if (UrlUtils.hostMatches(rawUrl, "t.co")) {
            onProgress("Expanding short link...")
            TwitterResolver.expandShortUrl(rawUrl)
        } else {
            rawUrl
        }

        // Layer 0a: Fast path for archive.org/details/<id> item pages via lightweight JSON API
        val archiveMatch = Regex("archive\\.org/details/([^/?#]+)", RegexOption.IGNORE_CASE).find(url)
        if (archiveMatch != null) {
            onProgress("Fetching Archive.org movie files...")
            val archiveId = archiveMatch.groupValues[1]
            val stub = MovieSearchResult(
                id = archiveId,
                title = archiveId,
                pageUrl = url,
                source = SearchSource.ARCHIVE_ORG
            )
            val archiveMeta = MovieSearchEngine.resolveArchiveMovie(stub)
            if (archiveMeta != null && (archiveMeta.streams.isNotEmpty() || archiveMeta.qualities.isNotEmpty())) {
                return ResolveResult.Success(archiveMeta)
            }
        }

        // Layer 0b: Fast Twitter/X API path (videos, GIFs, multi-video carousels, quote tweets, proxy domains)
        if (UrlUtils.isTwitterOrX(url) && !UrlUtils.isTwitterSpaceOrBroadcast(url)) {
            onProgress("Fetching X / Twitter media...")
            val twMeta = TwitterResolver.resolve(url)
            if (twMeta != null && (twMeta.streams.isNotEmpty() || twMeta.qualities.isNotEmpty())) {
                return ResolveResult.Success(twMeta)
            }
        }

        // Layer 1: Direct Probe (skipped for known webpage-only hosts)
        if (!shouldSkipDirectProbe(url)) {
            onProgress("Probing link...")
            val direct = DirectProbe.probe(url)
            if (direct != null && (direct.streams.isNotEmpty() || direct.qualities.isNotEmpty())) {
                return ResolveResult.Success(direct)
            }
        }

        // Layer 2: yt-dlp (supports YouTube, social sites, X Spaces/Broadcasts, protected tweets with cookies, etc.)
        onProgress("Analyzing media sources...")
        val cookiesPath = CookiesManager.getCookiesPath(appContext)
        val ytResult = YtDlpManager.getVideoInfo(appContext, url, cookiesPath)
        val meta = ytResult.metadata
        if (meta != null && (meta.isPlaylist || meta.streams.isNotEmpty() || meta.qualities.isNotEmpty() || meta.audioQualities.isNotEmpty() || meta.mediaItems.isNotEmpty())) {
            return ResolveResult.Success(meta)
        }

        // Layer 3: Fall back to hidden WebView sniffer
        val snifferTarget = if (UrlUtils.isTwitterOrX(url)) UrlUtils.toXUrl(url) else url
        return if (allowBrowserFallback) {
            ResolveResult.NeedsBrowserSniffer(snifferTarget, ytResult.error)
        } else {
            val fallbackHint = if (UrlUtils.isTwitterOrX(url) && !CookiesManager.hasTwitterAuthCookies(appContext)) {
                ytResult.error ?: "This X / Twitter post may be age-restricted or protected. Sign in to X / Twitter or enable Browser Fallback."
            } else {
                ytResult.error ?: "Could not extract video from this link. Enable Hidden Browser Fallback in Settings for movie websites."
            }
            ResolveResult.Error(fallbackHint)
        }
    }
}
