/*
 * Omega Video Downloader Project Original (2026)
 * arslandaim-hub (GitHub.com/arslandaim-hub)
 * Licenced Under GPL-3.0+
*/
package com.arslandaim.omegavideodownloader

import java.net.URI

object UrlUtils {

    private val urlRegex = Regex("""https?://[^\s"'<>]+""", RegexOption.IGNORE_CASE)

    /** Extracts the first http(s) URL from arbitrary shared/pasted text. */
    fun extractUrl(text: String): String? {
        val trimmed = text.trim()
        return urlRegex.find(trimmed)?.value?.trimEnd('.', ',', ')', ']')
            ?: if (trimmed.contains('.') && !trimmed.contains(' ')) "https://$trimmed" else null
    }

    fun host(url: String): String? = try {
        URI(url).host?.lowercase()?.removePrefix("www.")
    } catch (_: Exception) {
        null
    }

    fun hostMatches(url: String, vararg domains: String): Boolean {
        val h = host(url) ?: return false
        return domains.any { h == it || h.endsWith(".$it") }
    }

    private val twitterWrapperHosts = setOf(
        "x.com", "www.x.com", "mobile.x.com",
        "twitter.com", "www.twitter.com", "mobile.twitter.com",
        "fxtwitter.com", "www.fxtwitter.com",
        "vxtwitter.com", "www.vxtwitter.com",
        "fixupx.com", "www.fixupx.com",
        "fixvx.com", "www.fixvx.com",
        "twittpr.com", "www.twittpr.com",
        "girlcockx.com", "www.girlcockx.com",
        "stupidpenisx.com", "www.stupidpenisx.com",
        "xcancel.com", "www.xcancel.com"
    )

    private val tweetStatusRegex = Regex(
        """^/(?:([A-Za-z0-9_]{1,30})|i(?:/web)?)/status/(\d+)""",
        RegexOption.IGNORE_CASE
    )

    /**
     * Rewrites x.com and third-party X embed proxy links to twitter.com for consistent extractor routing.
     * Host based, so URLs like dropbox.com are never corrupted.
     */
    fun normalize(url: String): String {
        val trimmed = url.trim()
        return try {
            val uri = URI(trimmed)
            val h = uri.host?.lowercase() ?: return trimmed
            if (h in twitterWrapperHosts && h != "twitter.com") {
                URI(uri.scheme, uri.userInfo, "twitter.com", uri.port, uri.path, uri.query, uri.fragment).toString()
            } else trimmed
        } catch (_: Exception) {
            trimmed
        }
    }

    /** Converts a Twitter/X link to canonical `https://x.com/...` for WebView/Browser flows. */
    fun toXUrl(url: String): String {
        val trimmed = url.trim()
        return try {
            val uri = URI(trimmed)
            val h = uri.host?.lowercase() ?: return trimmed
            if (h in twitterWrapperHosts) {
                URI(uri.scheme ?: "https", uri.userInfo, "x.com", uri.port, uri.path, uri.query, uri.fragment).toString()
            } else trimmed
        } catch (_: Exception) {
            trimmed
        }
    }

    fun isTwitterOrX(url: String): Boolean = hostMatches(
        url,
        "twitter.com", "x.com", "t.co",
        "fxtwitter.com", "vxtwitter.com", "fixupx.com", "fixvx.com",
        "twittpr.com", "girlcockx.com", "stupidpenisx.com", "xcancel.com",
        "pscp.tv", "periscope.tv"
    )

    fun isTwitterSpaceOrBroadcast(url: String): Boolean {
        if (hostMatches(url, "pscp.tv", "periscope.tv")) return true
        if (!isTwitterOrX(url)) return false
        val path = try { URI(url).path?.lowercase() ?: "" } catch (_: Exception) { url.lowercase() }
        return path.contains("/i/spaces/") || path.contains("/i/broadcasts/")
    }

    /** Extracts `(screenNameOrI, tweetId)` from a Twitter/X status URL, or null if not a status link. */
    fun extractTweetTarget(url: String): Pair<String, String>? {
        if (!isTwitterOrX(url)) return null
        return try {
            val path = URI(url).path ?: return null
            val match = tweetStatusRegex.find(path) ?: return null
            val user = match.groupValues[1].ifBlank { "i" }
            val id = match.groupValues[2]
            if (id.isBlank()) null else user to id
        } catch (_: Exception) {
            null
        }
    }

    /** True for URLs whose path looks like a media file or manifest. */
    fun mediaKindFromUrl(url: String): StreamKind? {
        val path = try { URI(url).path?.lowercase() ?: "" } catch (_: Exception) { url.lowercase() }
        return when {
            path.endsWith(".m3u8") || path.contains(".m3u8") -> StreamKind.HLS
            path.endsWith(".mpd") -> StreamKind.DASH
            path.endsWith(".mp4") || path.endsWith(".mkv") || path.endsWith(".webm") ||
                path.endsWith(".mov") || path.endsWith(".m4v") || path.endsWith(".avi") ||
                path.endsWith(".flv") -> StreamKind.PROGRESSIVE
            else -> null
        }
    }

    fun mediaKindFromMime(mime: String?): StreamKind? {
        val m = mime?.lowercase()?.substringBefore(';')?.trim() ?: return null
        return when {
            m == "application/vnd.apple.mpegurl" || m == "application/x-mpegurl" || m == "audio/mpegurl" || m == "audio/x-mpegurl" -> StreamKind.HLS
            m == "application/dash+xml" -> StreamKind.DASH
            m.startsWith("video/") && m != "video/mp2t" -> StreamKind.PROGRESSIVE
            else -> null
        }
    }

    fun isYouTube(url: String) = hostMatches(url, "youtube.com", "youtu.be", "music.youtube.com")
}
