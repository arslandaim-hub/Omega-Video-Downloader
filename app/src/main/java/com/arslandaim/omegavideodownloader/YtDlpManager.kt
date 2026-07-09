/*
 * Omega Video Downloader Project Original (2026)
 * arslandaim-hub (GitHub.com/arslandaim-hub)
 * Licenced Under GPL-3.0+
*/
package com.arslandaim.omegavideodownloader

import android.content.Context
import android.util.Log
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import com.yausername.ffmpeg.FFmpeg
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

object YtDlpManager {
    private const val TAG = "YtDlpManager"
    private var isInitialized = false

    fun init(context: Context) {
        if (isInitialized) return
        try {
            YoutubeDL.getInstance().init(context)
            FFmpeg.getInstance().init(context)
            
            // Background update of yt-dlp binary
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    YoutubeDL.getInstance().updateYoutubeDL(context)
                    Log.d(TAG, "yt-dlp binary updated")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to update yt-dlp binary", e)
                }
            }
            
            isInitialized = true
            Log.d(TAG, "yt-dlp initialized successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize yt-dlp", e)
        }
    }

    suspend fun getVideoInfo(url: String, cookiesPath: String? = null): VideoMetadata? = withContext(Dispatchers.IO) {
        try {
            // Normalize URL to twitter.com if it's x.com (some extractors prefer the legacy domain)
            val normalizedUrl = if (url.contains("x.com")) url.replace("x.com", "twitter.com") else url
            val request = YoutubeDLRequest(normalizedUrl)
            
            // Bot Bypass Tactics (Modern 2024/2025 headers)
            if (normalizedUrl.contains("twitter.com") || normalizedUrl.contains("t.co")) {
                request.addOption("--extractor-args", "twitter:api=syndication")
                request.addOption("--user-agent", "Mozilla/5.0 (iPhone; CPU iPhone OS 17_4 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.4 Mobile/15E148 Safari/604.1")
                // Specialized headers for Twitter Syndication
                request.addOption("--add-header", "Referer: https://syndication.twitter.com/")
                request.addOption("--add-header", "Origin: https://syndication.twitter.com")
            } else {
                request.addOption("--user-agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36")
                request.addOption("--add-header", "Accept-Language: en-US,en;q=0.9")
                request.addOption("--add-header", "Sec-Ch-Ua: \"Chromium\";v=\"122\", \"Not(A:Brand\";v=\"24\", \"Google Chrome\";v=\"122\"")
                request.addOption("--add-header", "Sec-Ch-Ua-Mobile: ?0")
                request.addOption("--add-header", "Sec-Ch-Ua-Platform: \"Windows\"")
            }
            
            // Platform Specific Fixes
            if (normalizedUrl.contains("twitter.com") || normalizedUrl.contains("t.co")) {
                // Handled above in headers/UA
            } else if (normalizedUrl.contains("instagram.com")) {
                // Instagram is extremely sensitive to cookies and user-agents
            } else if (normalizedUrl.contains("youtube.com") || normalizedUrl.contains("youtu.be")) {
                // Remove some headers that might conflict with YouTube's own player clients
                request.addOption("--extractor-args", "youtube:player-client=web,default")
            }

            if (cookiesPath != null && File(cookiesPath).exists()) {
                request.addOption("--cookies", cookiesPath)
            } else {
                Log.w(TAG, "No cookies provided for $normalizedUrl. This might result in 403 Forbidden.")
            }

            // Standard options for info fetching
            request.addOption("--dump-single-json")
            request.addOption("--no-playlist")
            request.addOption("--check-formats") // Ensures URLs are valid
            
            val info = YoutubeDL.getInstance().getInfo(request)
            
            Log.d(TAG, "Fetched info for: ${info.title}, formats: ${info.formats?.size}")

            // Enhanced format filtering:
            // 1. Map to VideoQuality with isCombined info
            // 2. Prioritize formats that already have audio (combined) to avoid merging if possible
            // 3. For video-only, keep the best ones for each resolution
            val allVideoFormats = info.formats?.filter { 
                it.vcodec != null && it.vcodec != "none" 
            }?.map {
                val isCombined = it.acodec != null && it.acodec != "none"
                val resolution = it.height
                val label = if (resolution > 0) "${resolution}p" else it.formatNote ?: it.format ?: "Video"
                
                VideoQuality(
                    label = label,
                    url = it.url ?: "",
                    size = if (it.fileSize > 0) formatSize(it.fileSize) else if (it.fileSizeApproximate > 0) formatSize(it.fileSizeApproximate) else "Unknown",
                    type = "video",
                    formatId = it.formatId,
                    isCombined = isCombined
                )
            } ?: emptyList()

            // Group by resolution/label and pick the best one (prefer combined, then by size)
            val videoQualities = allVideoFormats.groupBy { it.label }
                .map { entry ->
                    entry.value.sortedWith(
                        compareByDescending<VideoQuality> { it.isCombined }
                        .thenByDescending { it.size.substringBefore(" ").toDoubleOrNull() ?: 0.0 }
                    ).first()
                }.sortedByDescending { 
                    it.label.filter { c -> c.isDigit() }.toIntOrNull() ?: 0 
                }

            val audioQualities = info.formats?.filter { 
                (it.acodec != null && it.acodec != "none") && (it.vcodec == null || it.vcodec == "none")
            }?.map {
                VideoQuality(
                    label = it.formatNote ?: it.format ?: "Audio Only",
                    url = it.url ?: "",
                    size = if (it.fileSize > 0) formatSize(it.fileSize) else if (it.fileSizeApproximate > 0) formatSize(it.fileSizeApproximate) else "Unknown",
                    type = "audio",
                    formatId = it.formatId,
                    isCombined = true // Audio only is "complete" for its type
                )
            }?.distinctBy { it.label } ?: emptyList()

            VideoMetadata(
                title = info.title ?: "Video",
                qualities = videoQualities,
                audioQualities = audioQualities,
                thumbnailUrl = info.thumbnail
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching video info for $url", e)
            null
        }
    }

    suspend fun downloadVideo(
        url: String,
        formatId: String,
        outputFile: File,
        type: String = "video",
        isCombined: Boolean = false,
        cookiesPath: String? = null,
        poToken: String? = null,
        visitorData: String? = null,
        onProgress: (Float, Long, String) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            // Normalize URL
            val normalizedUrl = if (url.contains("x.com")) url.replace("x.com", "twitter.com") else url
            val request = YoutubeDLRequest(normalizedUrl)
            
            // Output setup:
            request.addOption("-o", outputFile.absolutePath)
            
            // Critical Fix for Audio:
            // If it's a video and NOT combined, we must use bestvideo+bestaudio.
            // Even if we have a formatId, it might be video-only.
            val formatSpec = when {
                type == "audio" -> formatId
                isCombined -> formatId
                else -> "$formatId+bestaudio/best"
            }
            request.addOption("-f", formatSpec)
            request.addOption("--merge-output-format", "mp4")
            
            // Added stability options
            request.addOption("--no-mtime")
            request.addOption("--retries", "10")
            request.addOption("--fragment-retries", "10")
            request.addOption("--no-part") // Ensure output is directly written to outputFile path
            
            // Bot Bypass & Session
            if (normalizedUrl.contains("twitter.com") || normalizedUrl.contains("t.co")) {
                request.addOption("--extractor-args", "twitter:api=syndication")
                request.addOption("--user-agent", "Mozilla/5.0 (iPhone; CPU iPhone OS 17_4 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.4 Mobile/15E148 Safari/604.1")
                request.addOption("--add-header", "Referer: https://syndication.twitter.com/")
                request.addOption("--add-header", "Origin: https://syndication.twitter.com")
            } else {
                request.addOption("--user-agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36")
                request.addOption("--add-header", "Accept-Language: en-US,en;q=0.9")
            }

            if (cookiesPath != null && File(cookiesPath).exists()) {
                request.addOption("--cookies", cookiesPath)
            }

            // PO Token Handling
            if (poToken != null && visitorData != null) {
                request.addOption("--extractor-args", "youtube:player-client=web;po_token=web+$poToken;visitor_data=$visitorData")
            }

            YoutubeDL.getInstance().execute(request) { progress, eta, line ->
                onProgress(progress, eta, line)
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Download failed for $url", e)
            false
        }
    }

    private fun formatSize(bytes: Long): String {
        if (bytes <= 0) return "0.0 MB"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt()
        return String.format(java.util.Locale.US, "%.1f %s", bytes / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
    }
}
