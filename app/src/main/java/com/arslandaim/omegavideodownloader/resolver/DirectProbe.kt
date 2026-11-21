/*
 * Omega Video Downloader Project Original (2026)
 * arslandaim-hub (GitHub.com/arslandaim-hub)
 * Licenced Under GPL-3.0+
*/
package com.arslandaim.omegavideodownloader.resolver

import android.util.Log
import com.arslandaim.omegavideodownloader.ResolveSource
import com.arslandaim.omegavideodownloader.StorageUtils
import com.arslandaim.omegavideodownloader.StreamCandidate
import com.arslandaim.omegavideodownloader.StreamKind
import com.arslandaim.omegavideodownloader.UrlUtils
import com.arslandaim.omegavideodownloader.VideoMetadata
import com.arslandaim.omegavideodownloader.VideoQuality
import com.arslandaim.omegavideodownloader.YtDlpManager
import com.arslandaim.omegavideodownloader.player.OmegaHttp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import okhttp3.Response
import java.net.URI
import java.net.URLDecoder

/**
 * Layer 1 of the resolver pipeline: fast HTTP HEAD (with Range GET fallback)
 * to instantly detect direct video files (.mp4, .mkv, .webm) and adaptive manifests (.m3u8, .mpd)
 * without launching Python / yt-dlp.
 */
object DirectProbe {
    private const val TAG = "DirectProbe"

    suspend fun probe(
        url: String,
        headers: Map<String, String> = emptyMap()
    ): VideoMetadata? = withContext(Dispatchers.IO) {
        val kindFromUrl = UrlUtils.mediaKindFromUrl(url)
        try {
            val baseHeaders = buildMap {
                put("User-Agent", YtDlpManager.DESKTOP_UA)
                putAll(headers)
            }

            // Try HEAD first; many CDNs reject HEAD (403/405/501) so fall back to a 2-byte Range GET
            val headResp = execute(url, baseHeaders, head = true)
            val resp = if (headResp != null && headResp.isSuccessful) {
                headResp
            } else {
                headResp?.close()
                execute(url, baseHeaders + ("Range" to "bytes=0-1"), head = false)
            }

            if (resp == null) {
                // If URL clearly looks like a direct media link, still return it even if probe timed out
                return@withContext kindFromUrl?.let { buildMetadata(url, it, 0L, null, headers) }
            }

            resp.use { r ->
                val finalUrl = r.request.url.toString()
                val contentType = r.header("Content-Type")
                val detectedKind = UrlUtils.mediaKindFromMime(contentType)
                    ?: UrlUtils.mediaKindFromUrl(finalUrl)
                    ?: kindFromUrl

                if (detectedKind == null) return@withContext null

                // Reject HTML pages masquerading under a video-like URL
                if (contentType != null && contentType.contains("text/html", ignoreCase = true)) {
                    return@withContext null
                }

                val sizeBytes = parseLength(r)
                val title = parseFileName(r.header("Content-Disposition"), finalUrl)
                val hlsVariants = if (detectedKind == StreamKind.HLS) {
                    fetchHlsVariants(finalUrl, baseHeaders)
                } else emptyList()

                buildMetadata(finalUrl, detectedKind, sizeBytes, title, headers, hlsVariants)
            }
        } catch (e: Exception) {
            Log.d(TAG, "Direct probe skipped for $url: ${e.message}")
            kindFromUrl?.let { buildMetadata(url, it, 0L, null, headers) }
        }
    }

    private fun execute(url: String, headers: Map<String, String>, head: Boolean): Response? = try {
        val builder = Request.Builder().url(url)
        headers.forEach { (k, v) -> builder.header(k, v) }
        if (head) builder.head() else builder.get()
        OmegaHttp.client.newCall(builder.build()).execute()
    } catch (_: Exception) {
        null
    }

    private fun parseLength(response: Response): Long {
        response.header("Content-Range")?.substringAfter('/')?.toLongOrNull()?.let { if (it > 0) return it }
        return response.header("Content-Length")?.toLongOrNull()?.takeIf { it > 1024 } ?: 0L
    }

    private fun parseFileName(disposition: String?, url: String): String {
        val fromHeader = disposition?.let {
            Regex("""filename\*?=(?:UTF-8''|")?([^";\r\n]+)""", RegexOption.IGNORE_CASE)
                .find(it)?.groupValues?.getOrNull(1)
        }
        val raw = fromHeader ?: try {
            URI(url).path?.substringAfterLast('/')
        } catch (_: Exception) {
            null
        }
        val decoded = try {
            URLDecoder.decode(raw ?: "", "UTF-8")
        } catch (_: Exception) {
            raw ?: ""
        }
        val cleaned = decoded.substringBeforeLast('.').replace(Regex("[._-]+"), " ").trim()
        return cleaned.ifBlank { UrlUtils.host(url) ?: "Direct Video" }
    }

    /** Parses variants from a master HLS (.m3u8) playlist so users can pick a resolution if desired. */
    private fun fetchHlsVariants(masterUrl: String, headers: Map<String, String>): List<Pair<Int, String>> {
        return try {
            val req = Request.Builder().url(masterUrl).apply {
                headers.forEach { (k, v) -> header(k, v) }
            }.get().build()
            OmegaHttp.client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return emptyList()
                val body = resp.body.string().take(256 * 1024)
                if (!body.contains("#EXT-X-STREAM-INF")) return emptyList()

                val baseUri = URI(masterUrl)
                val lines = body.lines()
                val variants = mutableListOf<Pair<Int, String>>()
                var pendingHeight: Int? = null
                for (rawLine in lines) {
                    val line = rawLine.trim()
                    if (line.startsWith("#EXT-X-STREAM-INF:")) {
                        pendingHeight = Regex("""RESOLUTION=\d+x(\d+)""", RegexOption.IGNORE_CASE)
                            .find(line)?.groupValues?.getOrNull(1)?.toIntOrNull()
                    } else if (line.isNotEmpty() && !line.startsWith("#") && pendingHeight != null) {
                        val resolved = try { baseUri.resolve(line).toString() } catch (_: Exception) { line }
                        variants.add(pendingHeight to resolved)
                        pendingHeight = null
                    }
                }
                variants.distinctBy { it.first }.sortedByDescending { it.first }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun buildMetadata(
        url: String,
        kind: StreamKind,
        sizeBytes: Long,
        title: String?,
        headers: Map<String, String>,
        hlsVariants: List<Pair<Int, String>> = emptyList()
    ): VideoMetadata {
        val cleanTitle = title?.ifBlank { null } ?: "Direct Stream"
        val sizeStr = if (sizeBytes > 0) StorageUtils.formatSize(sizeBytes) else "Unknown"

        val primaryLabel = when (kind) {
            StreamKind.HLS -> "Auto (Adaptive HLS)"
            StreamKind.DASH -> "Auto (Adaptive DASH)"
            StreamKind.PROGRESSIVE -> "Direct Video"
        }

        val streams = buildList {
            add(
                StreamCandidate(
                    url = url,
                    kind = kind,
                    label = primaryLabel,
                    height = 0,
                    sizeBytes = sizeBytes,
                    headers = headers,
                    source = ResolveSource.DIRECT
                )
            )
            hlsVariants.forEach { (h, variantUrl) ->
                add(
                    StreamCandidate(
                        url = variantUrl,
                        kind = StreamKind.HLS,
                        label = "${h}p",
                        height = h,
                        sizeBytes = 0L,
                        headers = headers,
                        source = ResolveSource.DIRECT
                    )
                )
            }
        }

        val qualities = buildList {
            if (hlsVariants.isNotEmpty()) {
                hlsVariants.forEach { (h, variantUrl) ->
                    add(
                        VideoQuality(
                            label = "${h}p (HLS)",
                            url = variantUrl,
                            size = "Variable",
                            type = "video",
                            formatId = "best",
                            isCombined = true,
                            useDirectUrl = true,
                            headers = headers
                        )
                    )
                }
            } else {
                add(
                    VideoQuality(
                        label = when (kind) {
                            StreamKind.HLS -> "Best Quality (HLS)"
                            StreamKind.DASH -> "Best Quality (DASH)"
                            StreamKind.PROGRESSIVE -> "Original Quality"
                        },
                        url = url,
                        size = sizeStr,
                        type = "video",
                        formatId = "best",
                        isCombined = true,
                        sizeBytes = sizeBytes,
                        useDirectUrl = true,
                        headers = headers
                    )
                )
            }
        }

        return VideoMetadata(
            title = cleanTitle,
            qualities = qualities,
            thumbnailUrl = null,
            isPlaylist = false,
            sourceUrl = url,
            streams = streams,
            resolvedBy = ResolveSource.DIRECT
        )
    }
}
