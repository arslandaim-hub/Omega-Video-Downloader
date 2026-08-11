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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object YtDlpManager {
    private const val TAG = "YtDlpManager"
    private var isInitialized = false
    private lateinit var appContext: Context

    fun init(context: Context) {
        if (isInitialized) return
        appContext = context.applicationContext
        try {
            YoutubeDL.getInstance().init(context)
            val ffmpegStatus = FFmpeg.getInstance().init(context)
            Log.d(TAG, "FFmpeg init status: $ffmpegStatus")
            isInitialized = true
            Log.d(TAG, "yt-dlp initialized successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize yt-dlp", e)
        }
    }

    suspend fun updateBinary(context: Context): Result<String> = withContext(Dispatchers.IO) {
        try {
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

    suspend fun getVideoInfo(url: String, cookiesPath: String? = null): VideoMetadata? = withContext(Dispatchers.IO) {
        try {
            // Normalize URL to twitter.com if it's x.com (some extractors prefer the legacy domain)
            val normalizedUrl = if (url.contains("x.com")) url.replace("x.com", "twitter.com") else url
            val request = YoutubeDLRequest(normalizedUrl)
            
            // Bot Bypass Tactics & Specialized headers
            request.addOption("--user-agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36")
            request.addOption("--add-header", "Accept: text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.7")
            request.addOption("--add-header", "Accept-Language: en-US,en;q=0.9")
            request.addOption("--add-header", "Sec-Fetch-Mode: navigate")
            
            if (normalizedUrl.contains("twitter.com") || normalizedUrl.contains("t.co")) {
                request.addOption("--extractor-args", "twitter:api=syndication")
                request.addOption("--add-header", "Referer: https://syndication.twitter.com/")
                request.addOption("--add-header", "Origin: https://syndication.twitter.com")
            } else if (normalizedUrl.contains("instagram.com")) {
                request.addOption("--add-header", "Referer: https://www.instagram.com/")
            } else if (normalizedUrl.contains("youtube.com") || normalizedUrl.contains("youtu.be")) {
                request.addOption("--extractor-args", "youtube:player-client=web,default")
            }

            if (cookiesPath != null && File(cookiesPath).exists()) {
                request.addOption("--cookies", cookiesPath)
            }

            // Standard options for info fetching
            request.addOption("--dump-single-json")
            
            val isPlaylistUrl = url.contains("playlist") || url.contains("list=")
            if (!isPlaylistUrl) {
                request.addOption("--no-playlist")
            } else {
                request.addOption("--flat-playlist")
            }

            request.addOption("--check-formats") 
            request.addOption("--no-check-certificate")
            
            val info = YoutubeDL.getInstance().getInfo(request)
            
            Log.d(TAG, "Fetched info for: ${info.title}, formats: ${info.formats?.size}")

            val playlistCount = if (isPlaylistUrl) {
                // Heuristic: If it's a playlist, try to parse the entry count from the full JSON or title
                // Since VideoInfo doesn't expose it directly in this version, we'll mark it as -1 
                // and let the Service calculate it during sequential download.
                -1 
            } else null

            // Find best audio for combined size calculation
            val bestAudio = info.formats?.filter { 
                (it.acodec != null && it.acodec != "none") && (it.vcodec == null || it.vcodec == "none") 
            }?.maxByOrNull { it.abr.toFloat() }
            val bestAudioSize = bestAudio?.let { if (it.fileSize > 0) it.fileSize else it.fileSizeApproximate } ?: 0L

            // Permissive format filtering:
            // 1. Map to VideoQuality with isCombined info
            val allVideoFormats = info.formats?.filter { 
                ((it.vcodec != null && it.vcodec != "none") || it.height > 0 || (it.formatNote?.contains("p") == true)) &&
                (!isPlaylistUrl || it.height <= 720) // Cap at 720p for playlists
            }?.map {
                val isCombined = (it.acodec != null && it.acodec != "none")
                val resolution = it.height
                val label = if (resolution > 0) "${resolution}p" else it.formatNote ?: it.format ?: "Video"
                
                val rawSize = if (it.fileSize > 0) it.fileSize else it.fileSizeApproximate
                val displaySize = if (rawSize > 0) {
                    val totalSize = if (isCombined) rawSize else rawSize + bestAudioSize
                    formatSize(totalSize)
                } else {
                    "Unknown"
                }

                VideoQuality(
                    label = label,
                    url = it.url ?: "",
                    size = displaySize,
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
                val bitrate = if (it.abr > 0) " - ${it.abr}kbps" else ""
                val codec = it.acodec?.replace("audio/", "") ?: it.ext ?: "audio"
                val label = "Audio$bitrate ($codec)"
                
                VideoQuality(
                    label = label,
                    url = it.url ?: "",
                    size = if (it.fileSize > 0) formatSize(it.fileSize) else if (it.fileSizeApproximate > 0) formatSize(it.fileSizeApproximate) else "Unknown",
                    type = "audio",
                    formatId = it.formatId,
                    isCombined = true 
                )
            }?.distinctBy { it.label } ?: emptyList()

            // Fix: If it's a playlist and no formats found (due to flat-playlist), provide presets
            val finalVideoQualities = if (isPlaylistUrl && videoQualities.isEmpty()) {
                listOf(
                    VideoQuality("720p (Merged)", "", "Variable", "video", "720", true),
                    VideoQuality("480p (Merged)", "", "Variable", "video", "480", true),
                    VideoQuality("360p (Merged)", "", "Variable", "video", "360", true)
                )
            } else videoQualities

            VideoMetadata(
                title = info.title ?: "Video",
                qualities = finalVideoQualities,
                audioQualities = audioQualities,
                thumbnailUrl = info.thumbnail,
                isPlaylist = isPlaylistUrl,
                playlistCount = playlistCount
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
        processId: String? = null,
        onProgress: (Float, Long, String) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val normalizedUrl = if (url.contains("x.com")) url.replace("x.com", "twitter.com") else url
            val request = YoutubeDLRequest(normalizedUrl)
            
            request.addOption("-o", outputFile.absolutePath)
            
            val formatSpec = when {
                type == "audio" -> formatId
                isCombined -> formatId
                else -> "$formatId+bestaudio/best"
            }
            request.addOption("-f", formatSpec)
            request.addOption("--merge-output-format", "mp4")
            
            request.addOption("--no-mtime")
            request.addOption("--retries", "10")
            request.addOption("--fragment-retries", "10")
            request.addOption("--no-part") 
            request.addOption("--no-check-certificate")
            
            request.addOption("--user-agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36")

            if (cookiesPath != null && File(cookiesPath).exists()) {
                request.addOption("--cookies", cookiesPath)
            }

            if (poToken != null && visitorData != null) {
                request.addOption("--extractor-args", "youtube:player-client=web;po_token=web+$poToken;visitor_data=$visitorData")
            }

            YoutubeDL.getInstance().execute(request, processId) { progress, eta, line ->
                onProgress(progress, eta, line)
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Download failed for $url", e)
            false
        }
    }

    suspend fun runFFmpegCommand(commands: List<String>): Boolean = withContext(Dispatchers.IO) {
        if (!isInitialized || !::appContext.isInitialized) return@withContext false
        try {
            val binDir = File(appContext.applicationInfo.nativeLibraryDir)
            val ffmpegBinary = File(binDir, "libffmpeg.so")

            val command = mutableListOf<String>()
            command.add(ffmpegBinary.absolutePath)
            command.addAll(commands)

            val processBuilder = ProcessBuilder(command)

            // Setup environment similar to what YoutubeDL does
            val baseDir = File(appContext.noBackupFilesDir, "youtubedl-android")
            val packagesDir = File(baseDir, "packages")
            val pythonDir = File(packagesDir, "python")
            val ffmpegDir = File(packagesDir, "ffmpeg")
            val aria2cDir = File(packagesDir, "aria2c")

            val ldLibraryPath = "${pythonDir.absolutePath}/usr/lib:${ffmpegDir.absolutePath}/usr/lib:${aria2cDir.absolutePath}/usr/lib"

            processBuilder.environment().apply {
                this["LD_LIBRARY_PATH"] = ldLibraryPath
                this["PATH"] = (System.getenv("PATH") ?: "") + ":" + binDir.absolutePath
            }

            processBuilder.redirectErrorStream(true)
            val process = processBuilder.start()

            // Consume output to prevent hanging
            process.inputStream.bufferedReader().use { reader ->
                reader.forEachLine { line ->
                    Log.v(TAG, "FFmpeg: $line")
                }
            }

            val exitCode = process.waitFor()
            Log.d(TAG, "FFmpeg process exited with code $exitCode")
            exitCode == 0
        } catch (e: Exception) {
            Log.e(TAG, "FFmpeg command failed", e)
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
