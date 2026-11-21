/*
 * Omega Video Downloader Project Original (2026)
 * arslandaim-hub (GitHub.com/arslandaim-hub)
 * Licenced Under GPL-3.0+
*/
package com.arslandaim.omegavideodownloader.data

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.arslandaim.omegavideodownloader.CookiesManager
import com.arslandaim.omegavideodownloader.DownloadService
import com.arslandaim.omegavideodownloader.FastHttpDownloader
import com.arslandaim.omegavideodownloader.SettingsManager
import com.arslandaim.omegavideodownloader.StorageUtils
import com.arslandaim.omegavideodownloader.VideoMetadata
import com.arslandaim.omegavideodownloader.VideoQuality
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File

class DownloadRepository(context: Context) {

    private val context = context.applicationContext
    private val settingsManager = SettingsManager(this.context)
    private val repoScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Starts a download. Streams discovered by the direct probe / browser sniffer / direct CDN links
     * are downloaded from the media URL itself (with required headers); everything else from pageUrl.
     */
    fun startDownload(
        metadata: VideoMetadata,
        quality: VideoQuality,
        pageUrl: String,
        isMovie: Boolean? = null
    ) {
        val downloadUrl = if (quality.useDirectUrl && quality.url.isNotBlank()) quality.url else pageUrl
        val inferredIsMovie = isMovie ?: (
            quality.type == "video" &&
                (metadata.durationSec >= 2400L || metadata.resolvedBy == com.arslandaim.omegavideodownloader.ResolveSource.ARCHIVE)
            )
        val intent = Intent(context, DownloadService::class.java).apply {
            putExtra(DownloadService.EXTRA_URL, downloadUrl)
            putExtra(DownloadService.EXTRA_TITLE, metadata.title)
            putExtra(DownloadService.EXTRA_TYPE, quality.type)
            putExtra(DownloadService.EXTRA_FORMAT_ID, quality.formatId)
            putExtra(DownloadService.EXTRA_IS_COMBINED, quality.isCombined)
            putExtra(DownloadService.EXTRA_COOKIES, CookiesManager.getCookiesPath(context))
            putExtra(DownloadService.EXTRA_IS_PLAYLIST, metadata.isPlaylist)
            putExtra(DownloadService.EXTRA_PLAYLIST_COUNT, metadata.playlistCount ?: -1)
            putExtra(DownloadService.EXTRA_USE_DIRECT_URL, quality.useDirectUrl)
            putExtra(DownloadService.EXTRA_IS_MOVIE, inferredIsMovie)
            putExtra(DownloadService.EXTRA_DURATION_MS, metadata.durationSec * 1000L)
            metadata.subtitleTracks.firstOrNull()?.url?.let { subUrl ->
                putExtra(DownloadService.EXTRA_SUBTITLE_URL, subUrl)
            }
            if (quality.headers.isNotEmpty()) putExtra(DownloadService.EXTRA_HEADERS, HashMap(quality.headers))
        }
        ContextCompat.startForegroundService(context, intent)
    }

    fun pauseDownload(id: Long) {
        val pauseIntent = Intent(context, DownloadService::class.java).apply {
            action = DownloadService.ACTION_PAUSE_DOWNLOAD
            putExtra(DownloadService.EXTRA_TASK_ID, id)
        }
        try {
            context.startService(pauseIntent)
        } catch (_: Exception) {
            // Service not running
        }
    }

    fun resumeDownload(id: Long) {
        val resumeIntent = Intent(context, DownloadService::class.java).apply {
            action = DownloadService.ACTION_RESUME_DOWNLOAD
            putExtra(DownloadService.EXTRA_TASK_ID, id)
        }
        ContextCompat.startForegroundService(context, resumeIntent)
    }

    fun cancelDownload(id: Long) {
        val cancelIntent = Intent(context, DownloadService::class.java).apply {
            action = DownloadService.ACTION_CANCEL_DOWNLOAD
            putExtra(DownloadService.EXTRA_TASK_ID, id)
        }
        try {
            context.startService(cancelIntent)
        } catch (_: Exception) {
            // Service not running - clean up any persisted paused task directly
        }
        repoScope.launch {
            if (settingsManager.getPausedDownload(id) != null) {
                settingsManager.removePausedDownload(id)
                val workDir = File(StorageUtils.downloadWorkDir(context), "task_$id")
                FastHttpDownloader.cleanupTaskFiles(context, workDir)
            }
        }
    }
}
