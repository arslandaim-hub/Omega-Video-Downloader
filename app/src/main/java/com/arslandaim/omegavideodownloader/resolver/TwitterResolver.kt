/*
 * Omega Video Downloader Project Original (2026)
 * arslandaim-hub (GitHub.com/arslandaim-hub)
 * Licenced Under GPL-3.0+
*/
package com.arslandaim.omegavideodownloader.resolver

import android.util.Log
import com.arslandaim.omegavideodownloader.ResolveSource
import com.arslandaim.omegavideodownloader.StreamCandidate
import com.arslandaim.omegavideodownloader.StreamKind
import com.arslandaim.omegavideodownloader.UrlUtils
import com.arslandaim.omegavideodownloader.VideoMetadata
import com.arslandaim.omegavideodownloader.VideoQuality
import com.arslandaim.omegavideodownloader.YtDlpManager
import com.arslandaim.omegavideodownloader.player.OmegaHttp
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Fast, zero-Python Layer 0 resolver for Twitter / X status links.
 * Handles:
 *  - `t.co` shortlink unwrapping
 *  - Standard tweet videos (all MP4 variants + adaptive HLS)
 *  - Twitter GIFs (`animated_gif` silent MP4s)
 *  - Multi-video / mixed-media carousel tweets (exposes all items in [VideoMetadata.mediaItems])
 *  - Quote-tweet videos (when the main tweet quotes a tweet containing video)
 *  - Proxy domains (`fxtwitter.com`, `vxtwitter.com`, `fixupx.com`, etc.)
 *
 * Protected/login-walled tweets, Spaces, Broadcasts, and gated 18+ posts fall through cleanly
 * to Layer 2 (yt-dlp with cookies + GraphQL) and Layer 3 (WebView sniffer).
 */
object TwitterResolver {
    private const val TAG = "TwitterResolver"

    private val apiClient by lazy {
        OmegaHttp.client.newBuilder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(12, TimeUnit.SECONDS)
            .build()
    }

    private val resolutionRegex = Regex("""/(\d{2,4})x(\d{2,4})/""")
    private val trailingTcoRegex = Regex("""\s*https?://t\.co/\S+$""")
    private val metaRefreshRegex = Regex("""URL=(https?://[^"'>\s]+)""", RegexOption.IGNORE_CASE)
    private val jsLocationRegex = Regex("""location\.replace\(["'](https?://[^"']+)["']\)""")

    /** Expands `t.co` shortlinks via HTTP redirect / HTML meta-refresh inspection. */
    fun expandShortUrl(url: String): String {
        if (!UrlUtils.hostMatches(url, "t.co")) return url
        return try {
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", YtDlpManager.DESKTOP_UA)
                .get()
                .build()
            apiClient.newCall(req).execute().use { resp ->
                val redirected = resp.request.url.toString()
                if (!UrlUtils.hostMatches(redirected, "t.co")) {
                    return redirected
                }
                val body = resp.body.string().take(4096)
                metaRefreshRegex.find(body)?.groupValues?.getOrNull(1)
                    ?: jsLocationRegex.find(body)?.groupValues?.getOrNull(1)
                    ?: redirected
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to unwrap t.co link: $url", e)
            url
        }
    }

    fun resolve(url: String): VideoMetadata? {
        if (UrlUtils.isTwitterSpaceOrBroadcast(url)) return null
        val (user, tweetId) = UrlUtils.extractTweetTarget(url) ?: return null
        val canonicalUrl = UrlUtils.normalize(url)

        fetchText("https://api.fxtwitter.com/$user/status/$tweetId")?.let { json ->
            parseFxTwitterJson(json, canonicalUrl)?.let { return it }
        }

        fetchText("https://api.vxtwitter.com/$user/status/$tweetId")?.let { json ->
            parseVxTwitterJson(json, canonicalUrl)?.let { return it }
        }

        return null
    }

    private fun fetchText(endpoint: String): String? = try {
        val req = Request.Builder()
            .url(endpoint)
            .header("User-Agent", "OmegaVideoDownloader/1.0 (+https://github.com/arslandaim-hub)")
            .header("Accept", "application/json")
            .get()
            .build()
        apiClient.newCall(req).execute().use { resp ->
            if (resp.isSuccessful) resp.body?.string() else null
        }
    } catch (e: Exception) {
        Log.w(TAG, "API request failed for $endpoint: ${e.message}")
        null
    }

    internal fun parseFxTwitterJson(json: String, sourceUrl: String): VideoMetadata? {
        val root = runCatching { JsonParser.parseString(json).asJsonObjectOrNull() }.getOrNull() ?: return null
        val tweet = root.obj("tweet") ?: return null

        val screenName = tweet.obj("author")?.str("screen_name")
        val rawText = tweet.str("text")?.replace(trailingTcoRegex, "")?.trim().orEmpty()
        val baseTitle = buildTitle(rawText, screenName)

        // Check main tweet media first; fall back to quoted tweet media if main has no video/GIF
        val mainMedia = extractFxMediaList(tweet.obj("media"))
        val effectiveMedia = if (mainMedia.isNotEmpty()) {
            mainMedia
        } else {
            extractFxMediaList(tweet.obj("quote")?.obj("media"))
        }
        if (effectiveMedia.isEmpty()) return null

        val headers = mapOf("User-Agent" to YtDlpManager.DESKTOP_UA)
        val items = effectiveMedia.mapIndexedNotNull { idx, mediaObj ->
            val typeStr = mediaObj.str("type")?.lowercase().orEmpty()
            val isGif = typeStr == "gif" || typeStr == "animated_gif"
            val thumb = mediaObj.str("thumbnail_url")
            val durationSec = (mediaObj.double("duration") ?: 0.0).toLong().coerceAtLeast(0L)
            val fallbackWidth = mediaObj.int("width") ?: 0
            val fallbackHeight = mediaObj.int("height") ?: 0

            data class VariantEntry(
                val url: String,
                val height: Int,
                val bitrate: Long,
                val isHls: Boolean
            )

            val variants = mediaObj.arr("variants")?.mapNotNull { it.asJsonObjectOrNull() }.orEmpty()
            val parsedVariants = mutableListOf<VariantEntry>()

            for (v in variants) {
                val vUrl = v.str("url") ?: continue
                val ct = v.str("content_type")?.lowercase().orEmpty()
                val isHls = ct.contains("mpegurl") || vUrl.contains(".m3u8", ignoreCase = true)
                val bitrate = v.long("bitrate") ?: 0L
                val dimMatch = resolutionRegex.find(vUrl)
                val h = dimMatch?.groupValues?.getOrNull(2)?.toIntOrNull() ?: fallbackHeight
                parsedVariants.add(VariantEntry(vUrl, h, bitrate, isHls))
            }

            // Ensure top-level media.url is always included if variants was omitted
            val directUrl = mediaObj.str("url")
            if (!directUrl.isNullOrBlank() && parsedVariants.none { it.url == directUrl }) {
                val isHls = directUrl.contains(".m3u8", ignoreCase = true)
                val h = resolutionRegex.find(directUrl)?.groupValues?.getOrNull(2)?.toIntOrNull()
                    ?: fallbackHeight
                parsedVariants.add(VariantEntry(directUrl, h, if (isHls) 0L else Long.MAX_VALUE / 2, isHls))
            }

            if (parsedVariants.isEmpty()) return@mapIndexedNotNull null

            val mp4Variants = parsedVariants.filter { !it.isHls }
                .sortedWith(compareByDescending<VariantEntry> { it.height }.thenByDescending { it.bitrate })
            val hlsVariants = parsedVariants.filter { it.isHls }

            val qualities = mutableListOf<VideoQuality>()
            val seenLabels = HashSet<String>()
            for (v in mp4Variants) {
                val resLabel = when {
                    isGif && v.height > 0 -> "GIF • ${v.height}p (MP4)"
                    isGif -> "GIF (MP4)"
                    v.height > 0 -> "${v.height}p (MP4)"
                    v.bitrate > 0 -> "${(v.bitrate / 1000L)}kbps (MP4)"
                    else -> "Original (MP4)"
                }
                if (seenLabels.add(resLabel)) {
                    qualities.add(
                        VideoQuality(
                            label = resLabel,
                            url = v.url,
                            size = "Unknown",
                            type = "video",
                            formatId = "best",
                            isCombined = true,
                            useDirectUrl = true,
                            headers = headers
                        )
                    )
                }
            }
            if (qualities.isEmpty() && hlsVariants.isNotEmpty()) {
                qualities.add(
                    VideoQuality(
                        label = "Auto (HLS)",
                        url = hlsVariants.first().url,
                        size = "Variable",
                        type = "video",
                        formatId = "best",
                        isCombined = true,
                        useDirectUrl = false,
                        headers = headers
                    )
                )
            }

            val streams = buildList {
                hlsVariants.firstOrNull()?.let { hls ->
                    add(
                        StreamCandidate(
                            url = hls.url,
                            kind = StreamKind.HLS,
                            label = "Auto (Adaptive)",
                            height = fallbackHeight.coerceAtLeast(fallbackWidth),
                            headers = headers,
                            source = ResolveSource.DIRECT
                        )
                    )
                }
                for (mp4 in mp4Variants) {
                    add(
                        StreamCandidate(
                            url = mp4.url,
                            kind = StreamKind.PROGRESSIVE,
                            label = if (mp4.height > 0) "${mp4.height}p" else if (isGif) "GIF" else "MP4",
                            height = mp4.height,
                            headers = headers,
                            source = ResolveSource.DIRECT
                        )
                    )
                }
            }

            val itemTitle = if (effectiveMedia.size == 1) {
                baseTitle
            } else {
                val kindTag = if (isGif) "GIF" else "Video"
                "$kindTag ${idx + 1} • $baseTitle"
            }

            VideoMetadata(
                title = itemTitle,
                qualities = qualities,
                thumbnailUrl = thumb,
                sourceUrl = sourceUrl,
                streams = streams,
                durationSec = durationSec,
                resolvedBy = ResolveSource.DIRECT,
                creator = screenName?.let { "@$it" },
                isGif = isGif
            )
        }

        if (items.isEmpty()) return null
        return if (items.size == 1) {
            items.first()
        } else {
            items.first().copy(
                title = baseTitle,
                mediaItems = items
            )
        }
    }

    internal fun parseVxTwitterJson(json: String, sourceUrl: String): VideoMetadata? {
        val root = runCatching { JsonParser.parseString(json).asJsonObjectOrNull() }.getOrNull() ?: return null
        val screenName = root.str("user_screen_name")
        val rawText = root.str("text")?.replace(trailingTcoRegex, "")?.trim().orEmpty()
        val baseTitle = buildTitle(rawText, screenName)

        val mainMedia = extractVxMediaList(root.arr("media_extended"))
        val effectiveMedia = if (mainMedia.isNotEmpty()) {
            mainMedia
        } else {
            extractVxMediaList(root.obj("qrt")?.arr("media_extended"))
        }
        if (effectiveMedia.isEmpty()) return null

        val headers = mapOf("User-Agent" to YtDlpManager.DESKTOP_UA)
        val items = effectiveMedia.mapIndexedNotNull { idx, mediaObj ->
            val url = mediaObj.str("url") ?: return@mapIndexedNotNull null
            val typeStr = mediaObj.str("type")?.lowercase().orEmpty()
            val isGif = typeStr == "gif" || typeStr == "animated_gif"
            val thumb = mediaObj.str("thumbnail_url")
            val durationSec = ((mediaObj.long("duration_millis") ?: 0L) / 1000L).coerceAtLeast(0L)
            val sizeObj = mediaObj.obj("size")
            val height = sizeObj?.int("height")
                ?: resolutionRegex.find(url)?.groupValues?.getOrNull(2)?.toIntOrNull()
                ?: 0
            val isHls = url.contains(".m3u8", ignoreCase = true)

            val label = when {
                isGif && height > 0 -> "GIF • ${height}p (MP4)"
                isGif -> "GIF (MP4)"
                isHls -> "Auto (HLS)"
                height > 0 -> "${height}p (MP4)"
                else -> "Original (MP4)"
            }

            val quality = VideoQuality(
                label = label,
                url = url,
                size = if (isHls) "Variable" else "Unknown",
                type = "video",
                formatId = "best",
                isCombined = true,
                useDirectUrl = !isHls,
                headers = headers
            )

            val stream = StreamCandidate(
                url = url,
                kind = if (isHls) StreamKind.HLS else StreamKind.PROGRESSIVE,
                label = if (height > 0) "${height}p" else if (isGif) "GIF" else "Original",
                height = height,
                headers = headers,
                source = ResolveSource.DIRECT
            )

            val itemTitle = if (effectiveMedia.size == 1) {
                baseTitle
            } else {
                val kindTag = if (isGif) "GIF" else "Video"
                "$kindTag ${idx + 1} • $baseTitle"
            }

            VideoMetadata(
                title = itemTitle,
                qualities = listOf(quality),
                thumbnailUrl = thumb,
                sourceUrl = sourceUrl,
                streams = listOf(stream),
                durationSec = durationSec,
                resolvedBy = ResolveSource.DIRECT,
                creator = screenName?.let { "@$it" },
                isGif = isGif
            )
        }

        if (items.isEmpty()) return null
        return if (items.size == 1) {
            items.first()
        } else {
            items.first().copy(
                title = baseTitle,
                mediaItems = items
            )
        }
    }

    private fun extractFxMediaList(mediaObj: JsonObject?): List<JsonObject> {
        if (mediaObj == null) return emptyList()
        val fromVideos = mediaObj.arr("videos")?.mapNotNull { it.asJsonObjectOrNull() }.orEmpty()
        if (fromVideos.isNotEmpty()) return fromVideos
        return mediaObj.arr("all")
            ?.mapNotNull { it.asJsonObjectOrNull() }
            ?.filter {
                val t = it.str("type")?.lowercase()
                t == "video" || t == "gif" || t == "animated_gif"
            }
            .orEmpty()
    }

    private fun extractVxMediaList(arr: JsonArray?): List<JsonObject> =
        arr?.mapNotNull { it.asJsonObjectOrNull() }
            ?.filter {
                val t = it.str("type")?.lowercase()
                t == "video" || t == "gif" || t == "animated_gif"
            }
            .orEmpty()

    private fun buildTitle(rawText: String, screenName: String?): String {
        val cleaned = rawText.replace(Regex("""\s+"""), " ").trim()
        return when {
            cleaned.isNotEmpty() && !screenName.isNullOrBlank() ->
                "@$screenName: ${cleaned.take(72)}${if (cleaned.length > 72) "…" else ""}"
            cleaned.isNotEmpty() ->
                "${cleaned.take(80)}${if (cleaned.length > 80) "…" else ""}"
            !screenName.isNullOrBlank() -> "X Video by @$screenName"
            else -> "X / Twitter Video"
        }
    }

    private fun JsonElement.asJsonObjectOrNull(): JsonObject? = if (isJsonObject) asJsonObject else null
    private fun JsonObject.obj(key: String): JsonObject? = get(key)?.asJsonObjectOrNull()
    private fun JsonObject.arr(key: String): JsonArray? = get(key)?.takeIf { it.isJsonArray }?.asJsonArray
    private fun JsonObject.prim(key: String) = get(key)?.takeIf { it.isJsonPrimitive }?.asJsonPrimitive
    private fun JsonObject.str(key: String): String? = prim(key)?.asString?.takeIf { it.isNotBlank() }
    private fun JsonObject.double(key: String): Double? = prim(key)?.takeIf { it.isNumber }?.asDouble
    private fun JsonObject.int(key: String): Int? = double(key)?.toInt()
    private fun JsonObject.long(key: String): Long? = double(key)?.toLong()
}
