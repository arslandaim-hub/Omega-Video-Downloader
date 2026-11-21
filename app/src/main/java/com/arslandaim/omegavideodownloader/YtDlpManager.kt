/*
 * Omega Video Downloader Project Original (2026)
 * arslandaim-hub (GitHub.com/arslandaim-hub)
 * Licenced Under GPL-3.0+
*/
package com.arslandaim.omegavideodownloader

import android.content.Context
import android.util.Log
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.yausername.aria2c.Aria2c
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

object YtDlpManager {
    private const val TAG = "YtDlpManager"
    const val DESKTOP_UA =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
    private const val INFO_CACHE_TTL_MS = 5L * 60L * 1000L

    private data class CachedInfo(val metadata: VideoMetadata, val timestampMs: Long)
    private val infoCache = ConcurrentHashMap<String, CachedInfo>()

    private val ready = CompletableDeferred<Boolean>()
    @Volatile private var initStarted = false
    @Volatile private var aria2cReady = false

    /** Non-blocking initialisation (extracts Python/ffmpeg on first run, which can take seconds). */
    fun initAsync(context: Context, scope: CoroutineScope) {
        if (initStarted) return
        initStarted = true
        val appContext = context.applicationContext
        scope.launch(Dispatchers.IO) { ready.complete(initBlocking(appContext)) }
    }

    private fun initBlocking(context: Context): Boolean = try {
        YoutubeDL.getInstance().init(context)
        FFmpeg.getInstance().init(context)
        try {
            Aria2c.init(context)
            aria2cReady = File(context.applicationInfo.nativeLibraryDir, "libaria2c.so").exists()
        } catch (e: Exception) {
            Log.w(TAG, "aria2c unavailable, falling back to native downloader", e)
        }
        Log.d(TAG, "yt-dlp initialized successfully")
        true
    } catch (e: Exception) {
        Log.e(TAG, "Failed to initialize yt-dlp", e)
        false
    }

    /** Suspends until yt-dlp is ready. Safe to call from any component (service, viewmodel...). */
    suspend fun awaitReady(context: Context): Boolean {
        if (!initStarted) {
            synchronized(this) {
                if (!initStarted) {
                    initStarted = true
                    val appContext = context.applicationContext
                    CoroutineScope(Dispatchers.IO).launch { ready.complete(initBlocking(appContext)) }
                }
            }
        }
        return ready.await()
    }

    suspend fun updateBinary(context: Context): Result<String> = withContext(Dispatchers.IO) {
        try {
            if (!awaitReady(context)) return@withContext Result.failure(IllegalStateException("yt-dlp is not initialized"))
            val status = YoutubeDL.getInstance().updateYoutubeDL(context)
            if (status == YoutubeDL.UpdateStatus.DONE) {
                Result.success("yt-dlp updated successfully")
            } else {
                Result.success("yt-dlp is already up to date")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update yt-dlp binary", e)
            Result.failure(e)
        }
    }

    /** Applies headers, cookies and per-platform tweaks shared by info + download requests. */
    private fun applyCommonOptions(
        request: YoutubeDLRequest,
        url: String,
        cookiesPath: String?,
        headers: Map<String, String>,
        twitterApiMode: String? = null
    ) {
        val ua = headers.entries.firstOrNull { it.key.equals("User-Agent", true) }?.value ?: DESKTOP_UA
        request.addOption("--user-agent", ua)
        headers.filterKeys { !it.equals("User-Agent", true) }.forEach { (k, v) ->
            request.addOption("--add-header", "$k:$v")
        }
        if (headers.isEmpty()) {
            request.addOption("--add-header", "Accept-Language:en-US,en;q=0.9")
        }

        when {
            UrlUtils.isTwitterOrX(url) && !UrlUtils.isTwitterSpaceOrBroadcast(url) && twitterApiMode != null -> {
                request.addOption("--extractor-args", "twitter:api=$twitterApiMode")
            }
            UrlUtils.hostMatches(url, "instagram.com") && headers.isEmpty() -> {
                request.addOption("--add-header", "Referer:https://www.instagram.com/")
            }
        }

        if (cookiesPath != null && File(cookiesPath).exists()) {
            request.addOption("--cookies", cookiesPath)
        }
        request.addOption("--no-check-certificate")
        request.addOption("--socket-timeout", "20")
    }

    class InfoResult(val metadata: VideoMetadata?, val error: String?)

    /**
     * Extracts metadata + every playable stream using yt-dlp (1800+ site extractors and the generic extractor).
     * NOTE: `--check-formats` was removed - it probed every format over the network and added 10-40s.
     */
    suspend fun getVideoInfo(
        context: Context,
        url: String,
        cookiesPath: String? = null,
        headers: Map<String, String> = emptyMap(),
        bypassCache: Boolean = false
    ): InfoResult = withContext(Dispatchers.IO) {
        if (!awaitReady(context)) return@withContext InfoResult(null, "Downloader engine failed to initialize")
        val normalizedUrl = UrlUtils.normalize(url)
        val cacheKey = "$normalizedUrl|${cookiesPath.orEmpty()}"
        if (!bypassCache && headers.isEmpty()) {
            infoCache[cacheKey]?.let { cached ->
                if (System.currentTimeMillis() - cached.timestampMs < INFO_CACHE_TTL_MS) {
                    return@withContext InfoResult(cached.metadata, null)
                }
            }
        }
        val isTwitter = UrlUtils.isTwitterOrX(normalizedUrl)
        val isSpaceOrBroadcast = UrlUtils.isTwitterSpaceOrBroadcast(normalizedUrl)
        val hasTwitterCookies = isTwitter && CookiesManager.hasTwitterAuthCookies(context)
        val apiAttempts: List<String?> = when {
            !isTwitter || isSpaceOrBroadcast -> listOf(null)
            hasTwitterCookies -> listOf("graphql", "syndication")
            else -> listOf("syndication", "graphql")
        }

        var lastError: String? = null
        for (apiMode in apiAttempts) {
            val processId = "info_${UUID.randomUUID().toString().take(8)}"
            try {
                val request = YoutubeDLRequest(normalizedUrl)
                applyCommonOptions(request, normalizedUrl, cookiesPath, headers, twitterApiMode = apiMode)
                if (UrlUtils.isYouTube(normalizedUrl)) {
                    request.addOption("--extractor-args", "youtube:player_client=android,web")
                }
                request.addOption("--dump-single-json")
                if (!isTwitter) {
                    request.addOption("--flat-playlist")
                }
                request.addOption("--no-warnings")

                val response = YoutubeDL.getInstance().execute(request, processId, null)
                val root = JsonParser.parseString(response.out).asJsonObject
                val parsed = parseInfo(root, normalizedUrl)
                if (infoCache.size > 40) infoCache.clear()
                infoCache[cacheKey] = CachedInfo(parsed, System.currentTimeMillis())
                return@withContext InfoResult(parsed, null)
            } catch (e: CancellationException) {
                runCatching { YoutubeDL.getInstance().destroyProcessById(processId) }
                throw e
            } catch (_: YoutubeDL.CanceledException) {
                return@withContext InfoResult(null, "Cancelled")
            } catch (e: Exception) {
                if (!currentCoroutineContext().isActive) {
                    runCatching { YoutubeDL.getInstance().destroyProcessById(processId) }
                }
                lastError = cleanError(e.message)
                Log.w(TAG, "Error fetching video info for $url (mode=$apiMode): $lastError")
            }
        }
        InfoResult(null, lastError ?: "Failed to extract video information")
    }

    private fun cleanError(message: String?): String {
        val line = message?.lines()?.lastOrNull { it.contains("ERROR", true) } ?: message?.lines()?.firstOrNull()
        return line?.substringAfter("ERROR:")?.trim()?.take(220) ?: "Unknown error"
    }

    private fun parseInfo(root: JsonObject, sourceUrl: String): VideoMetadata {
        val title = root.str("title") ?: "Video"
        val thumbnail = root.str("thumbnail")
            ?: root.arr("thumbnails")?.lastOrNull()?.asJsonObjectOrNull()?.str("url")
        val isPlaylist = root.str("_type") == "playlist"

        if (isPlaylist) {
            val entries = root.arr("entries")?.mapNotNull { it.asJsonObjectOrNull() }.orEmpty()
            val detailedItems = entries.mapIndexedNotNull { idx, entry ->
                val hasResolvedMedia = (entry.arr("formats")?.size() ?: 0) > 0 || entry.str("url") != null
                if (!hasResolvedMedia) return@mapIndexedNotNull null
                val entryUrl = entry.str("webpage_url") ?: sourceUrl
                val parsedEntry = parseInfo(entry, entryUrl)
                val taggedTitle = if (parsedEntry.isGif) {
                    "GIF ${idx + 1} • $title"
                } else {
                    "Video ${idx + 1} • $title"
                }
                parsedEntry.copy(
                    title = if (parsedEntry.title == "Video") taggedTitle else parsedEntry.title,
                    sourceUrl = sourceUrl
                )
            }
            if (detailedItems.size == 1) {
                return detailedItems.first()
            } else if (detailedItems.size > 1) {
                return detailedItems.first().copy(
                    title = title,
                    thumbnailUrl = thumbnail ?: detailedItems.first().thumbnailUrl,
                    mediaItems = detailedItems
                )
            }

            val count = entries.size.takeIf { it > 0 } ?: root.int("playlist_count")
            return VideoMetadata(
                title = title,
                qualities = listOf(
                    VideoQuality("720p", "", "Variable", "video", "720", true),
                    VideoQuality("480p", "", "Variable", "video", "480", true),
                    VideoQuality("360p", "", "Variable", "video", "360", true)
                ),
                thumbnailUrl = thumbnail,
                isPlaylist = true,
                playlistCount = count?.takeIf { it > 0 },
                sourceUrl = sourceUrl
            )
        }

        val topHeaders = root.headers()
        val formats = root.arr("formats")?.mapNotNull { it.asJsonObjectOrNull() }.orEmpty()
            .ifEmpty { listOf(root) } // single-format sites put the url at the top level

        fun JsonObject.hasVideo() = (str("vcodec") ?: "unknown") != "none" && (int("height") ?: 0) >= 0 &&
            !(str("vcodec") == null && str("acodec") != null && str("acodec") != "none" && (int("height") ?: 0) == 0)
        fun JsonObject.hasAudio() = (str("acodec") ?: "unknown") != "none"
        fun JsonObject.sizeBytes() = long("filesize") ?: long("filesize_approx") ?: 0L

        val isYouTube = UrlUtils.isYouTube(sourceUrl)
        val audioOnly = formats.filter { it.str("vcodec") == "none" && it.hasAudio() }
        val bestAudio = audioOnly
            .sortedWith(
                compareByDescending<JsonObject> { it.str("ext") == "m4a" }
                    .thenByDescending { it.double("abr") ?: it.double("tbr") ?: 0.0 }
            )
            .firstOrNull()
        val bestAudioSize = bestAudio?.sizeBytes() ?: 0L

        // ---------- Download qualities ----------
        val videoFormats = formats.filter { it.str("vcodec") != "none" && it.hasVideo() }
        val isTweetGif = videoFormats.any { (it.str("url") ?: "").contains("tweet_video", ignoreCase = true) }
        val videoQualities = videoFormats.map {
            val formatUrl = it.str("url") ?: ""
            val protocol = it.str("protocol") ?: "https"
            val isSilentMp4 = audioOnly.isEmpty() && it.str("acodec") == "none" &&
                (protocol == "https" || protocol == "http") &&
                formatUrl.contains(".mp4", ignoreCase = true)
            val combined = (it.hasAudio() && it.str("acodec") != null) || isSilentMp4
            val height = it.int("height") ?: 0
            val baseLabel = if (height > 0) "${height}p" else it.str("format_note") ?: it.str("format") ?: "Video"
            val label = if (formatUrl.contains("tweet_video", ignoreCase = true)) "GIF • $baseLabel" else baseLabel
            val raw = it.sizeBytes()
            val total = if (raw > 0) (if (combined) raw else raw + bestAudioSize) else 0L
            val isDirectProgressive = combined && !isYouTube &&
                (protocol == "https" || protocol == "http") &&
                formatUrl.startsWith("http", ignoreCase = true) &&
                UrlUtils.mediaKindFromUrl(formatUrl) != StreamKind.HLS &&
                UrlUtils.mediaKindFromUrl(formatUrl) != StreamKind.DASH
            VideoQuality(
                label = label,
                url = formatUrl,
                size = if (total > 0) StorageUtils.formatSize(total) else "Unknown",
                type = "video",
                formatId = it.str("format_id"),
                isCombined = combined,
                sizeBytes = total,
                useDirectUrl = isDirectProgressive,
                headers = if (isDirectProgressive) it.headers().ifEmpty { topHeaders } else emptyMap()
            )
        }.groupBy { it.label }
            .map { (_, list) ->
                list.sortedWith(compareByDescending<VideoQuality> { it.isCombined }.thenByDescending { it.sizeBytes }).first()
            }
            .sortedByDescending { q -> q.label.filter { it.isDigit() }.toIntOrNull() ?: 0 }

        val audioQualities = audioOnly.map {
            val abr = (it.double("abr") ?: 0.0).toInt()
            val codec = it.str("acodec")?.substringBefore('.') ?: it.str("ext") ?: "audio"
            val ext = it.str("ext") ?: codec
            val size = it.sizeBytes()
            val formatUrl = it.str("url") ?: ""
            val protocol = it.str("protocol") ?: "https"
            val isDirectAudio = !isYouTube &&
                (protocol == "https" || protocol == "http") &&
                formatUrl.startsWith("http", ignoreCase = true) &&
                UrlUtils.mediaKindFromUrl(formatUrl) != StreamKind.HLS &&
                UrlUtils.mediaKindFromUrl(formatUrl) != StreamKind.DASH
            VideoQuality(
                label = "Audio${if (abr > 0) " - ${abr}kbps" else ""} ($ext)",
                url = formatUrl,
                size = if (size > 0) StorageUtils.formatSize(size) else "Unknown",
                type = "audio",
                formatId = it.str("format_id"),
                isCombined = true,
                sizeBytes = size,
                useDirectUrl = isDirectAudio,
                headers = if (isDirectAudio) it.headers().ifEmpty { topHeaders } else emptyMap()
            )
        }.distinctBy { it.label }.sortedByDescending { it.sizeBytes }

        // ---------- Streaming candidates ----------
        val adaptiveStreams = mutableListOf<StreamCandidate>()
        val progressiveStreams = mutableListOf<StreamCandidate>()
        val seen = HashSet<String>()
        fun addAdaptive(c: StreamCandidate) { if (seen.add(c.url + (c.audioUrl ?: ""))) adaptiveStreams.add(c) }
        fun addProgressive(c: StreamCandidate) { if (seen.add(c.url + (c.audioUrl ?: ""))) progressiveStreams.add(c) }

        // 1. Adaptive manifests (best for low buffering: ExoPlayer ABR switches quality on the fly)
        formats.forEach { f ->
            val protocol = f.str("protocol") ?: ""
            val manifest = f.str("manifest_url")
            val h = f.headers().ifEmpty { topHeaders }
            when {
                protocol.startsWith("m3u8") -> addAdaptive(
                    StreamCandidate(manifest ?: f.str("url") ?: return@forEach, StreamKind.HLS, "Auto (adaptive)", 0, 0, h)
                )
                protocol.contains("dash") && manifest != null -> addAdaptive(
                    StreamCandidate(manifest, StreamKind.DASH, "Auto (adaptive)", 0, 0, h)
                )
            }
        }
        // 2. Combined progressive files
        formats.filter { f ->
            val p = f.str("protocol") ?: "https"
            (p == "https" || p == "http") && f.str("vcodec") != "none" && f.hasAudio() && f.str("url") != null
        }.sortedByDescending { it.int("height") ?: 0 }.forEach { f ->
            val height = f.int("height") ?: 0
            val streamUrl = f.str("url")!!
            addProgressive(
                StreamCandidate(
                    url = streamUrl,
                    kind = UrlUtils.mediaKindFromUrl(streamUrl) ?: StreamKind.PROGRESSIVE,
                    label = if (height > 0) "${height}p" else "Original",
                    height = height,
                    sizeBytes = f.sizeBytes(),
                    headers = f.headers().ifEmpty { topHeaders }
                )
            )
        }
        // 3. Separate video + audio progressive (YouTube style) -> merged at playback time
        val bestAudioUrl = bestAudio?.takeIf { (it.str("protocol") ?: "https").startsWith("http") }?.str("url")
        if (bestAudioUrl != null) {
            formats.filter { f ->
                val p = f.str("protocol") ?: "https"
                (p == "https" || p == "http") && f.str("acodec") == "none" && f.str("vcodec") != "none" &&
                    (f.int("height") ?: 0) in 144..1080 && f.str("url") != null
            }.groupBy { it.int("height") ?: 0 }
                .map { (_, list) -> list.maxBy { if (it.str("vcodec")?.startsWith("avc") == true) 2.0 else 1.0 } }
                .sortedByDescending { it.int("height") ?: 0 }
                .forEach { f ->
                    val height = f.int("height") ?: 0
                    addProgressive(
                        StreamCandidate(
                            url = f.str("url")!!,
                            kind = StreamKind.PROGRESSIVE,
                            label = "${height}p",
                            height = height,
                            sizeBytes = f.sizeBytes() + bestAudioSize,
                            headers = f.headers().ifEmpty { topHeaders },
                            audioUrl = bestAudioUrl
                        )
                    )
                }
        }
        val streams = (adaptiveStreams + progressiveStreams.sortedByDescending { it.height }).toMutableList()
        // 4. Single url fallback
        if (streams.isEmpty()) {
            root.str("url")?.let { u ->
                streams.add(StreamCandidate(u, UrlUtils.mediaKindFromUrl(u) ?: StreamKind.PROGRESSIVE, "Original", root.int("height") ?: 0, root.sizeBytes(), topHeaders))
            }
        }

        return VideoMetadata(
            title = title,
            qualities = videoQualities,
            audioQualities = audioQualities,
            thumbnailUrl = thumbnail,
            isPlaylist = false,
            playlistCount = null,
            sourceUrl = sourceUrl,
            streams = streams,
            durationSec = root.double("duration")?.toLong() ?: 0L,
            resolvedBy = ResolveSource.YTDLP,
            isGif = isTweetGif
        )
    }

    class DownloadResult(val file: File?, val error: String?)

    /**
     * Downloads [url] into [outputDir] as `<baseName>.<ext>` (real container extension, no more fake .mp3/.mp4).
     * Partial files are kept so a retry resumes instead of restarting from zero.
     */
    suspend fun downloadVideo(
        context: Context,
        url: String,
        formatSpec: String,
        outputDir: File,
        baseName: String,
        type: String = "video",
        cookiesPath: String? = null,
        headers: Map<String, String> = emptyMap(),
        processId: String? = null,
        playlistItem: Int? = null,
        useAria2c: Boolean = false,
        onProgress: (Float, Long, String) -> Unit
    ): DownloadResult = withContext(Dispatchers.IO) {
        if (!awaitReady(context)) return@withContext DownloadResult(null, "Downloader engine failed to initialize")
        try {
            outputDir.mkdirs()
            val normalizedUrl = UrlUtils.normalize(url)
            val request = YoutubeDLRequest(normalizedUrl)
            applyCommonOptions(request, normalizedUrl, cookiesPath, headers)

            request.addOption("-o", File(outputDir, "$baseName.%(ext)s").absolutePath)
            request.addOption("-f", formatSpec)
            if (type != "audio") request.addOption("--merge-output-format", "mp4/mkv")
            if (playlistItem != null) {
                request.addOption("--yes-playlist")
                request.addOption("--playlist-items", playlistItem.toString())
            } else {
                request.addOption("--no-playlist")
            }
            request.addOption("--no-mtime")
            request.addOption("--continue")
            request.addOption("--retries", "10")
            request.addOption("--fragment-retries", "10")
            request.addOption("--buffer-size", "64K")
            // High-concurrency fragment downloading for HLS/DASH streams (Dailymotion, PeerTube, movie CDNs)
            request.addOption("--concurrent-fragments", "8")

            val kind = UrlUtils.mediaKindFromUrl(normalizedUrl)
            val isHlsOrDash = kind == StreamKind.HLS || kind == StreamKind.DASH ||
                UrlUtils.hostMatches(normalizedUrl, "dailymotion.com", "dai.ly")

            if (UrlUtils.isYouTube(normalizedUrl)) {
                request.addOption("--http-chunk-size", "10M")
                request.addOption("--extractor-args", "youtube:player_client=android,web")
            } else if (useAria2c && aria2cReady && !isHlsOrDash) {
                val conns = if (UrlUtils.hostMatches(normalizedUrl, "archive.org")) 6 else 12
                request.addOption("--downloader", "libaria2c.so")
                request.addOption(
                    "--downloader-args",
                    "aria2c:-x $conns -s $conns -k 1M --file-allocation=none --max-tries=5 --retry-wait=2"
                )
            }

            YoutubeDL.getInstance().execute(request, processId) { progress, eta, line ->
                onProgress(progress, eta, line)
            }
            val output = findOutput(outputDir, baseName)
            if (output != null) DownloadResult(output, null) else DownloadResult(null, "Output file not found")
        } catch (_: YoutubeDL.CanceledException) {
            DownloadResult(null, "Cancelled")
        } catch (e: Exception) {
            Log.e(TAG, "Download failed for $url", e)
            DownloadResult(null, cleanError(e.message))
        }
    }

    /**
     * Searches YouTube using yt-dlp's fast flat-playlist JSON mode (no per-video page fetches).
     * Tapping a result later invokes [getVideoInfo] to retrieve full quality options.
     */
    suspend fun searchYouTube(
        context: Context,
        query: String,
        limit: Int = 12
    ): List<MovieSearchResult> = withContext(Dispatchers.IO) {
        if (query.isBlank() || !awaitReady(context)) return@withContext emptyList()
        val searchPid = "ytsearch_${UUID.randomUUID().toString().take(8)}"
        try {
            val request = YoutubeDLRequest("ytsearch$limit:$query").apply {
                addOption("--flat-playlist")
                addOption("--dump-single-json")
                addOption("--no-warnings")
                addOption("--no-check-certificate")
                addOption("--socket-timeout", "15")
                addOption("--user-agent", DESKTOP_UA)
            }
            val response = YoutubeDL.getInstance().execute(request, searchPid, null)
            val out = response.out.trim()
            if (out.isEmpty()) return@withContext emptyList()

            val root = JsonParser.parseString(out).asJsonObjectOrNull() ?: return@withContext emptyList()
            val entries = root.arr("entries") ?: return@withContext emptyList()

            buildList {
                for (el in entries) {
                    val obj = el.asJsonObjectOrNull() ?: continue
                    val id = obj.str("id") ?: continue
                    val title = obj.str("title") ?: continue
                    val pageUrl = obj.str("webpage_url")
                        ?: obj.str("url")?.takeIf { it.startsWith("http") }
                        ?: "https://www.youtube.com/watch?v=$id"
                    val bestFlatThumb = obj.arr("thumbnails")
                        ?.mapNotNull { it.asJsonObjectOrNull() }
                        ?.maxByOrNull { it.int("width") ?: 0 }
                        ?.str("url")
                    val thumb = bestFlatThumb?.takeIf { !it.contains("hqdefault") && !it.contains("mqdefault") }
                        ?: "https://i.ytimg.com/vi/$id/hq720.jpg"
                    val channel = obj.str("channel") ?: obj.str("uploader")
                    val durationSec = obj.double("duration")?.toLong()?.coerceAtLeast(0L) ?: 0L

                    add(
                        MovieSearchResult(
                            id = id,
                            title = title,
                            pageUrl = pageUrl,
                            source = SearchSource.YOUTUBE,
                            thumbnailUrl = thumb,
                            durationSec = durationSec,
                            subtitle = channel
                        )
                    )
                }
            }
        } catch (e: CancellationException) {
            runCatching { YoutubeDL.getInstance().destroyProcessById(searchPid) }
            throw e
        } catch (e: Exception) {
            if (!currentCoroutineContext().isActive) {
                runCatching { YoutubeDL.getInstance().destroyProcessById(searchPid) }
            }
            Log.w(TAG, "YouTube search failed for '$query': ${e.message}")
            emptyList()
        }
    }

    fun findOutput(dir: File, baseName: String): File? =
        dir.listFiles { f ->
            f.isFile && f.name.startsWith("$baseName.") &&
                !f.name.endsWith(".part") && !f.name.endsWith(".ytdl") &&
                !f.name.contains(".part-Frag") && !f.name.endsWith(".temp")
        }?.maxByOrNull { it.length() }?.takeIf { it.length() > 0 }

    // ---------- tiny JSON helpers ----------
    private fun JsonElement.asJsonObjectOrNull(): JsonObject? = if (isJsonObject) asJsonObject else null
    private fun JsonObject.prim(key: String) = get(key)?.takeIf { it.isJsonPrimitive }?.asJsonPrimitive
    private fun JsonObject.str(key: String): String? = prim(key)?.asString?.takeIf { it.isNotBlank() }
    private fun JsonObject.double(key: String): Double? = prim(key)?.takeIf { it.isNumber }?.asDouble
    private fun JsonObject.int(key: String): Int? = double(key)?.toInt()
    private fun JsonObject.long(key: String): Long? = double(key)?.toLong()?.takeIf { it > 0 }
    private fun JsonObject.arr(key: String) = get(key)?.takeIf { it.isJsonArray }?.asJsonArray
    private fun JsonObject.headers(): Map<String, String> =
        get("http_headers")?.asJsonObjectOrNull()?.entrySet()
            ?.filter { it.value.isJsonPrimitive }
            ?.associate { it.key to it.value.asString }
            .orEmpty()
}
