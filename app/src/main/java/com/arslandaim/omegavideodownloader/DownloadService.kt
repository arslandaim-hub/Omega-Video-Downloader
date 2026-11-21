/*
 * Omega Video Downloader Project Original (2026)
 * arslandaim-hub (GitHub.com/arslandaim-hub)
 * Licenced Under GPL-3.0+
*/
package com.arslandaim.omegavideodownloader

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.yausername.youtubedl_android.YoutubeDL
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

class DownloadService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private lateinit var notificationManager: NotificationManager
    private lateinit var settingsManager: SettingsManager
    private val summaryNotificationId = 101
    private val channelId = "download_channel"
    private val notifGroupKey = "omega_downloads_group"

    companion object {
        private const val TAG = "DownloadService"
        const val ACTION_CANCEL_DOWNLOAD = "com.arslandaim.omegavideodownloader.CANCEL_DOWNLOAD"
        const val ACTION_PAUSE_DOWNLOAD = "com.arslandaim.omegavideodownloader.PAUSE_DOWNLOAD"
        const val ACTION_RESUME_DOWNLOAD = "com.arslandaim.omegavideodownloader.RESUME_DOWNLOAD"
        const val EXTRA_TASK_ID = "extra_task_id"

        const val EXTRA_URL = "url"
        const val EXTRA_TITLE = "title"
        const val EXTRA_FORMAT_ID = "formatId"
        const val EXTRA_TYPE = "type"
        const val EXTRA_IS_COMBINED = "isCombined"
        const val EXTRA_COOKIES = "cookiesPath"
        const val EXTRA_IS_PLAYLIST = "isPlaylist"
        const val EXTRA_PLAYLIST_COUNT = "playlistCount"
        const val EXTRA_HEADERS = "headers"
        const val EXTRA_USE_DIRECT_URL = "useDirectUrl"
        const val EXTRA_IS_MOVIE = "isMovie"
        const val EXTRA_DURATION_MS = "durationMs"
        const val EXTRA_SUBTITLE_URL = "subtitleUrl"

        private val taskIdCounter = AtomicLong(System.currentTimeMillis())
        private fun nextTaskId(): Long = taskIdCounter.incrementAndGet()
    }

    private data class MovieExtraInfo(
        val isMovie: Boolean = false,
        val durationMs: Long = 0L,
        val subtitleUrl: String? = null
    )

    private val activeTasks = ConcurrentHashMap<Long, String>()
    private val activeJobs = ConcurrentHashMap<Long, Job>()
    private val activeProcessIds = ConcurrentHashMap<Long, String>()
    private val pausedTaskIds = ConcurrentHashMap.newKeySet<Long>()
    private val taskSpecs = ConcurrentHashMap<Long, ResumableDownloadTask>()
    private val taskMovieExtras = ConcurrentHashMap<Long, MovieExtraInfo>()
    private val lastNotifUpdateByTask = ConcurrentHashMap<Long, Long>()
    private val lastUiUpdateByTask = ConcurrentHashMap<Long, Long>()
    private val runningWorkers = MutableStateFlow(0)
    private val slotLock = Any()

    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        settingsManager = SettingsManager(applicationContext)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_CANCEL_DOWNLOAD -> {
                val taskId = intent.getLongExtra(EXTRA_TASK_ID, -1L)
                if (taskId != -1L) cancelTask(taskId)
                if (activeTasks.isEmpty()) checkAndStopService()
                return START_NOT_STICKY
            }
            ACTION_PAUSE_DOWNLOAD -> {
                val taskId = intent.getLongExtra(EXTRA_TASK_ID, -1L)
                if (taskId != -1L) pauseTask(taskId)
                return START_NOT_STICKY
            }
            ACTION_RESUME_DOWNLOAD -> {
                val taskId = intent.getLongExtra(EXTRA_TASK_ID, -1L)
                if (taskId != -1L) {
                    goForeground(createSummaryNotification("Resuming download..."))
                    serviceScope.launch {
                        val saved = settingsManager.getPausedDownload(taskId)
                        if (saved == null) {
                            withContext(Dispatchers.Main) { checkAndStopService() }
                            return@launch
                        }
                        settingsManager.removePausedDownload(taskId)
                        enqueueSingleTask(
                            taskId = saved.id,
                            url = saved.url,
                            title = saved.title,
                            formatId = saved.formatId,
                            type = saved.type,
                            isCombined = saved.isCombined,
                            useDirectUrl = saved.useDirectUrl,
                            cookiesPath = saved.cookiesPath,
                            headers = saved.headers,
                            initialTotal = saved.totalBytes,
                            initialDownloaded = saved.downloadedBytes
                        )
                    }
                } else {
                    goForeground(createSummaryNotification("Preparing..."))
                    checkAndStopService()
                }
                return START_NOT_STICKY
            }
        }

        val url = intent?.getStringExtra(EXTRA_URL)
        if (url == null) {
            goForeground(createSummaryNotification("Preparing..."))
            checkAndStopService()
            return START_NOT_STICKY
        }
        val title = intent.getStringExtra(EXTRA_TITLE) ?: "Video"
        val formatId = intent.getStringExtra(EXTRA_FORMAT_ID)
        val type = intent.getStringExtra(EXTRA_TYPE) ?: "video"
        val isCombined = intent.getBooleanExtra(EXTRA_IS_COMBINED, false)
        val cookiesPath = intent.getStringExtra(EXTRA_COOKIES)
        val isPlaylist = intent.getBooleanExtra(EXTRA_IS_PLAYLIST, false)
        val playlistCount = intent.getIntExtra(EXTRA_PLAYLIST_COUNT, -1)
        val useDirectUrl = intent.getBooleanExtra(EXTRA_USE_DIRECT_URL, false)
        val isMovie = intent.getBooleanExtra(EXTRA_IS_MOVIE, false)
        val durationMs = intent.getLongExtra(EXTRA_DURATION_MS, 0L)
        val subtitleUrl = intent.getStringExtra(EXTRA_SUBTITLE_URL)
        @Suppress("DEPRECATION", "UNCHECKED_CAST")
        val headers = (intent.getSerializableExtra(EXTRA_HEADERS) as? HashMap<String, String>) ?: emptyMap()

        val taskId = nextTaskId()
        taskMovieExtras[taskId] = MovieExtraInfo(isMovie, durationMs, subtitleUrl)

        if (isPlaylist) {
            activeTasks[taskId] = title
            settingsManager.updateLocalDownload(
                id = taskId, title = title, progress = 0f, totalBytes = 0, downloadedBytes = 0, type = type,
                playlistProgress = "1/${if (playlistCount > 0) playlistCount else "?"}",
                status = "Starting"
            )
            goForeground(createSummaryNotification())
            val job = serviceScope.launch {
                try {
                    acquireDownloadSlot(taskId, title, type, canPause = false)
                    handlePlaylistDownload(taskId, url, title, formatId ?: "720", cookiesPath, playlistCount)
                } finally {
                    releaseDownloadSlot()
                    cleanupTaskRuntime(taskId)
                }
            }
            activeJobs[taskId] = job
        } else {
            goForeground(createSummaryNotification())
            enqueueSingleTask(
                taskId = taskId,
                url = url,
                title = title,
                formatId = formatId,
                type = type,
                isCombined = isCombined,
                useDirectUrl = useDirectUrl,
                cookiesPath = cookiesPath,
                headers = headers
            )
        }
        return START_NOT_STICKY
    }

    private fun enqueueSingleTask(
        taskId: Long,
        url: String,
        title: String,
        formatId: String?,
        type: String,
        isCombined: Boolean,
        useDirectUrl: Boolean,
        cookiesPath: String?,
        headers: Map<String, String>,
        initialTotal: Long = 0L,
        initialDownloaded: Long = 0L
    ) {
        val canPause = FastHttpDownloader.isEligible(url, useDirectUrl)
        pausedTaskIds.remove(taskId)
        activeTasks[taskId] = title
        taskSpecs[taskId] = ResumableDownloadTask(
            id = taskId,
            url = url,
            title = title,
            type = type,
            formatId = formatId,
            isCombined = isCombined,
            useDirectUrl = useDirectUrl,
            cookiesPath = cookiesPath,
            headers = headers,
            totalBytes = initialTotal,
            downloadedBytes = initialDownloaded
        )

        val initPct = if (initialTotal > 0L) (initialDownloaded * 100f / initialTotal).coerceIn(0f, 99.9f) else 0f
        settingsManager.updateLocalDownload(
            id = taskId,
            title = title,
            progress = initPct,
            totalBytes = initialTotal,
            downloadedBytes = initialDownloaded,
            type = type,
            status = "Queued",
            canPause = canPause
        )
        updateTaskNotification(title, initPct.toInt(), taskId, "Queued...", canPause)
        updateSummaryNotification()

        val job = serviceScope.launch {
            var slotAcquired = false
            try {
                acquireDownloadSlot(taskId, title, type, canPause)
                slotAcquired = true
                handleSingleDownload(taskId, url, title, formatId, type, isCombined, useDirectUrl, cookiesPath, headers)
            } finally {
                if (slotAcquired) releaseDownloadSlot()
                cleanupTaskRuntime(taskId)
            }
        }
        activeJobs[taskId] = job
    }

    private fun isOnWifiOrUnmetered(): Boolean {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return true
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) ||
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
    }

    private suspend fun acquireDownloadSlot(taskId: Long, title: String, type: String, canPause: Boolean) {
        while (currentCoroutineContext().isActive && !pausedTaskIds.contains(taskId)) {
            val wifiOnly = settingsManager.wifiOnlyDownloads.first()
            if (wifiOnly && !isOnWifiOrUnmetered()) {
                settingsManager.updateLocalDownload(
                    id = taskId,
                    title = title,
                    progress = taskSpecs[taskId]?.let {
                        if (it.totalBytes > 0) (it.downloadedBytes * 100f / it.totalBytes) else 0f
                    } ?: 0f,
                    totalBytes = taskSpecs[taskId]?.totalBytes ?: 0L,
                    downloadedBytes = taskSpecs[taskId]?.downloadedBytes ?: 0L,
                    type = type,
                    status = "Waiting for Wi-Fi",
                    canPause = canPause
                )
                updateTaskNotification(title, -1, taskId, "Waiting for Wi-Fi...", canPause)
                delay(2000L)
                continue
            }
            val maxConcurrent = settingsManager.maxConcurrentDownloads.first()
            synchronized(slotLock) {
                if (runningWorkers.value < maxConcurrent) {
                    runningWorkers.value = runningWorkers.value + 1
                    return
                }
            }
            settingsManager.updateLocalDownload(
                id = taskId,
                title = title,
                progress = taskSpecs[taskId]?.let {
                    if (it.totalBytes > 0) (it.downloadedBytes * 100f / it.totalBytes) else 0f
                } ?: 0f,
                totalBytes = taskSpecs[taskId]?.totalBytes ?: 0L,
                downloadedBytes = taskSpecs[taskId]?.downloadedBytes ?: 0L,
                type = type,
                status = "Queued",
                canPause = canPause
            )
            delay(350L)
        }
        currentCoroutineContext().ensureActive()
    }

    private fun releaseDownloadSlot() {
        synchronized(slotLock) {
            runningWorkers.value = (runningWorkers.value - 1).coerceAtLeast(0)
        }
    }

    private suspend fun cleanupTaskRuntime(taskId: Long) {
        activeTasks.remove(taskId)
        activeJobs.remove(taskId)
        activeProcessIds.remove(taskId)
        lastNotifUpdateByTask.remove(taskId)
        lastUiUpdateByTask.remove(taskId)
        taskSpecs.remove(taskId)
        taskMovieExtras.remove(taskId)
        settingsManager.removeLocalDownload(taskId)
        notificationManager.cancel(taskNotificationId(taskId))
        withContext(NonCancellable + Dispatchers.Main) { checkAndStopService() }
    }

    private fun goForeground(notification: Notification) {
        try {
            ServiceCompat.startForeground(
                this, summaryNotificationId, notification,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else 0
            )
        } catch (e: Exception) {
            Log.e(TAG, "startForeground failed", e)
        }
    }

    private fun buildFormatSpec(type: String, formatId: String?, isCombined: Boolean): String = when {
        type == "audio" -> formatId?.let { "$it/bestaudio/best" } ?: "bestaudio/best"
        formatId.isNullOrBlank() || formatId == "best" -> "bestvideo*+bestaudio/best"
        isCombined -> "$formatId/best"
        else -> "$formatId+bestaudio[ext=m4a]/$formatId+bestaudio/$formatId/best"
    }

    private fun parseSizeValue(numStr: String, unitStr: String): Long {
        val value = numStr.toDoubleOrNull() ?: return 0L
        val mul = when (unitStr.uppercase().firstOrNull()) {
            'K' -> 1024.0
            'M' -> 1024.0 * 1024
            'G' -> 1024.0 * 1024 * 1024
            'T' -> 1024.0 * 1024 * 1024 * 1024
            else -> 1.0
        }
        return (value * mul).toLong()
    }

    private fun parseTotalBytes(line: String): Long {
        Regex("(?i)of\\s+~?\\s*([\\d.]+)\\s*([KMGT]i?B)").find(line)?.let {
            return parseSizeValue(it.groupValues[1], it.groupValues[2])
        }
        Regex("(?i)[\\d.]+[KMGT]?i?B/([\\d.]+)([KMGT]i?B)").find(line)?.let {
            return parseSizeValue(it.groupValues[1], it.groupValues[2])
        }
        return 0L
    }

    private fun parseSpeedBytesPerSec(line: String): Long {
        Regex("(?i)at\\s+([\\d.]+)\\s*([KMGT]i?B)/s").find(line)?.let {
            return parseSizeValue(it.groupValues[1], it.groupValues[2])
        }
        Regex("(?i)DL:([\\d.]+)([KMGT]?i?B)").find(line)?.let {
            return parseSizeValue(it.groupValues[1], it.groupValues[2])
        }
        return 0L
    }

    private fun formatProgressDetail(progress: Int, speedBytesPerSec: Long, etaSec: Long): String {
        val parts = buildList {
            if (progress >= 0) add("$progress%")
            val spd = StorageUtils.formatSpeed(speedBytesPerSec)
            if (spd.isNotEmpty()) add(spd)
            val eta = StorageUtils.formatEta(etaSec)
            if (eta.isNotEmpty() && etaSec > 0) add("$eta left")
        }
        return if (parts.isEmpty()) "Working..." else parts.joinToString(" • ")
    }

    private fun shouldThrottle(map: ConcurrentHashMap<Long, Long>, taskId: Long, intervalMs: Long): Boolean {
        val now = System.currentTimeMillis()
        val prev = map[taskId] ?: 0L
        if (now - prev < intervalMs) return true
        map[taskId] = now
        return false
    }

    private suspend fun handleSingleDownload(
        taskId: Long,
        url: String,
        title: String,
        formatId: String?,
        type: String,
        isCombined: Boolean,
        useDirectUrl: Boolean,
        cookiesPath: String?,
        headers: Map<String, String>
    ) {
        val workDir = File(StorageUtils.downloadWorkDir(this), "task_$taskId")

        // Fast path: native HTTP/1.1 multi-connection work-stealing downloader with pause/resume checkpoints
        if (FastHttpDownloader.isEligible(url, useDirectUrl)) {
            when (
                val fast = FastHttpDownloader.download(
                    context = applicationContext,
                    url = url,
                    title = title,
                    type = type,
                    workDir = workDir,
                    headers = headers,
                    isPauseRequested = { pausedTaskIds.contains(taskId) }
                ) { progress, total, downloaded, speed, eta ->
                    taskSpecs[taskId]?.let { spec ->
                        taskSpecs[taskId] = spec.copy(totalBytes = total, downloadedBytes = downloaded)
                    }
                    if (!shouldThrottle(lastNotifUpdateByTask, taskId, 650L)) {
                        val detail = formatProgressDetail(progress.toInt(), speed, eta)
                        updateTaskNotification(title, progress.toInt(), taskId, detail, canPause = true)
                    }
                    if (progress >= 100f || !shouldThrottle(lastUiUpdateByTask, taskId, 250L)) {
                        settingsManager.updateLocalDownload(
                            id = taskId,
                            title = title,
                            progress = progress,
                            totalBytes = total,
                            downloadedBytes = downloaded,
                            type = type,
                            speedBytesPerSec = speed,
                            etaSeconds = eta,
                            canPause = true
                        )
                    }
                }
            ) {
                is FastHttpDownloader.Result.SavedToGallery -> {
                    val movieExtra = taskMovieExtras[taskId]
                    settingsManager.addDownloadedVideo(
                        DownloadedVideo(
                            title = title,
                            localPath = fast.uri.toString(),
                            timestamp = System.currentTimeMillis(),
                            type = type,
                            durationMs = movieExtra?.durationMs ?: 0L,
                            isMovie = movieExtra?.isMovie ?: false,
                            subtitleUrl = movieExtra?.subtitleUrl
                        )
                    )
                    showCompletionNotification(title, true, null)
                    workDir.deleteRecursively()
                    return
                }
                is FastHttpDownloader.Result.DownloadedFile -> {
                    settingsManager.updateLocalDownload(taskId, title, 100f, fast.file.length(), fast.file.length(), type, status = "Saving")
                    updateTaskNotification("Saving $title", -1, taskId, null, canPause = false)
                    val finalUri = if (StorageUtils.hasEnoughSpace(applicationContext, fast.file.length())) {
                        StorageUtils.saveToGallery(applicationContext, fast.file, title, type)
                    } else null
                    if (finalUri != null) {
                        val movieExtra = taskMovieExtras[taskId]
                        settingsManager.addDownloadedVideo(
                            DownloadedVideo(
                                title = title,
                                localPath = finalUri.toString(),
                                timestamp = System.currentTimeMillis(),
                                type = type,
                                durationMs = movieExtra?.durationMs ?: 0L,
                                isMovie = movieExtra?.isMovie ?: false,
                                subtitleUrl = movieExtra?.subtitleUrl
                            )
                        )
                        showCompletionNotification(title, true, null)
                    } else {
                        showCompletionNotification(title, false, "Not enough storage to save the file")
                    }
                    workDir.deleteRecursively()
                    return
                }
                is FastHttpDownloader.Result.Paused -> {
                    val existing = taskSpecs[taskId]
                    if (existing != null) {
                        settingsManager.savePausedDownload(
                            existing.copy(totalBytes = fast.totalBytes, downloadedBytes = fast.downloadedBytes)
                        )
                    }
                    return
                }
                is FastHttpDownloader.Result.LowStorage -> {
                    showCompletionNotification(title, false, "Not enough storage space")
                    FastHttpDownloader.cleanupTaskFiles(applicationContext, workDir)
                    return
                }
                is FastHttpDownloader.Result.Cancelled -> {
                    FastHttpDownloader.cleanupTaskFiles(applicationContext, workDir)
                    return
                }
                is FastHttpDownloader.Result.FallbackToYtDlp -> {
                    Log.d(TAG, "Falling back to yt-dlp for $title: ${fast.reason}")
                }
            }
        }

        // Standard accelerated path: yt-dlp with libaria2c.so / concurrent HLS fragments
        val formatSpec = buildFormatSpec(type, formatId, isCombined)
        var totalBytes = 0L
        var result: YtDlpManager.DownloadResult? = null
        var lowStorage = false
        val maxAttempts = 3

        for (attempt in 0 until maxAttempts) {
            if (!currentCoroutineContext().isActive || lowStorage) break
            if (attempt > 0) {
                updateTaskNotification("Retrying $title (${attempt + 1}/$maxAttempts)", -1, taskId, null, canPause = false)
                delay(1500L * attempt)
            }
            val processId = "${taskId}_$attempt"
            activeProcessIds[taskId] = processId

            result = YtDlpManager.downloadVideo(
                context = applicationContext,
                url = url,
                formatSpec = formatSpec,
                outputDir = workDir,
                baseName = "media",
                type = type,
                cookiesPath = cookiesPath,
                headers = headers,
                processId = processId,
                useAria2c = attempt < 2 // keep multi-connection aria2c on first retry; fallback on final attempt
            ) { progress, eta, line ->
                val parsedTotal = parseTotalBytes(line)
                if (parsedTotal > totalBytes) {
                    totalBytes = parsedTotal
                    if (totalBytes > 0 && !StorageUtils.hasEnoughSpace(applicationContext, totalBytes * 2)) {
                        lowStorage = true
                        try { YoutubeDL.getInstance().destroyProcessById(processId) } catch (_: Exception) {}
                    }
                }
                val speed = parseSpeedBytesPerSec(line)
                val safeProgress = progress.coerceIn(0f, 100f)
                val downloaded = if (totalBytes > 0) (totalBytes * (safeProgress / 100f)).toLong() else 0L
                if (!shouldThrottle(lastNotifUpdateByTask, taskId, 650L)) {
                    val detail = formatProgressDetail(safeProgress.toInt(), speed, eta)
                    updateTaskNotification(title, safeProgress.toInt(), taskId, detail, canPause = false)
                }
                if (safeProgress >= 100f || !shouldThrottle(lastUiUpdateByTask, taskId, 250L)) {
                    settingsManager.updateLocalDownload(
                        id = taskId,
                        title = title,
                        progress = safeProgress,
                        totalBytes = totalBytes,
                        downloadedBytes = downloaded,
                        type = type,
                        speedBytesPerSec = speed,
                        etaSeconds = eta
                    )
                }
            }
            if (result.file != null || result.error == "Cancelled") break
        }

        val file = result?.file
        if (!currentCoroutineContext().isActive) {
            workDir.deleteRecursively()
            return
        }

        if (file != null) {
            settingsManager.updateLocalDownload(taskId, title, 100f, file.length(), file.length(), type, status = "Saving")
            updateTaskNotification("Saving $title", -1, taskId, null, canPause = false)
            val finalUri = if (StorageUtils.hasEnoughSpace(applicationContext, file.length())) {
                StorageUtils.saveToGallery(applicationContext, file, title, type)
            } else null
            if (finalUri != null) {
                val movieExtra = taskMovieExtras[taskId]
                settingsManager.addDownloadedVideo(
                    DownloadedVideo(
                        title = title,
                        localPath = finalUri.toString(),
                        timestamp = System.currentTimeMillis(),
                        type = type,
                        durationMs = movieExtra?.durationMs ?: 0L,
                        isMovie = movieExtra?.isMovie ?: false,
                        subtitleUrl = movieExtra?.subtitleUrl
                    )
                )
                showCompletionNotification(title, true, null)
            } else {
                showCompletionNotification(title, false, "Not enough storage to save the file")
            }
        } else {
            val reason = if (lowStorage) "Not enough storage space" else result?.error
            if (reason != "Cancelled") showCompletionNotification(title, false, reason)
        }
        workDir.deleteRecursively()
    }

    private suspend fun handlePlaylistDownload(
        taskId: Long,
        url: String,
        title: String,
        formatId: String,
        cookiesPath: String?,
        playlistCount: Int
    ) {
        val workDir = File(StorageUtils.downloadWorkDir(this), "playlist_$taskId")
        val playlistId = "playlist_$taskId"
        val resolution = formatId.filter { it.isDigit() }.ifEmpty { "720" }
        val formatSpec = "bestvideo[height<=$resolution]+bestaudio/best[height<=$resolution]/best"
        val countLabel = if (playlistCount > 0) playlistCount.toString() else "?"

        settingsManager.addDownloadedVideo(
            DownloadedVideo(title, "", System.currentTimeMillis(), type = "video", playlistId = playlistId, isPlaylistGroup = true)
        )

        var index = 1
        var saved = 0
        var consecutiveFailures = 0
        while (currentCoroutineContext().isActive) {
            if (playlistCount > 0 && index > playlistCount) break
            val progressLabel = "$index/$countLabel"
            updateTaskNotification("$title ($progressLabel)", 0, taskId, null, canPause = false)
            settingsManager.updateLocalDownload(taskId, title, 0f, 0, 0, "video", progressLabel)

            val processId = "${taskId}_$index"
            activeProcessIds[taskId] = processId
            val result = YtDlpManager.downloadVideo(
                context = applicationContext,
                url = url,
                formatSpec = formatSpec,
                outputDir = workDir,
                baseName = "item_$index",
                type = "video",
                cookiesPath = cookiesPath,
                processId = processId,
                playlistItem = index,
                useAria2c = true
            ) { progress, eta, line ->
                val speed = parseSpeedBytesPerSec(line)
                if (!shouldThrottle(lastNotifUpdateByTask, taskId, 650L)) {
                    val detail = formatProgressDetail(progress.toInt(), speed, eta)
                    updateTaskNotification("$title ($progressLabel)", progress.toInt(), taskId, detail, canPause = false)
                }
                if (progress >= 100f || !shouldThrottle(lastUiUpdateByTask, taskId, 250L)) {
                    settingsManager.updateLocalDownload(
                        id = taskId,
                        title = title,
                        progress = progress,
                        totalBytes = 0,
                        downloadedBytes = 0,
                        type = "video",
                        playlistProgress = progressLabel,
                        speedBytesPerSec = speed,
                        etaSeconds = eta
                    )
                }
            }
            if (result.error == "Cancelled" || !currentCoroutineContext().isActive) break

            val file = result.file
            if (file != null) {
                consecutiveFailures = 0
                val itemTitle = "$title - Part $index"
                StorageUtils.saveToGallery(applicationContext, file, itemTitle, "video")?.let { uri ->
                    settingsManager.addDownloadedVideo(
                        DownloadedVideo(itemTitle, uri.toString(), System.currentTimeMillis(), type = "video", playlistId = playlistId)
                    )
                    saved++
                }
                file.delete()
            } else {
                consecutiveFailures++
                if (playlistCount <= 0 || consecutiveFailures >= 3) break
            }
            index++
        }

        workDir.deleteRecursively()
        if (currentCoroutineContext().isActive) {
            showCompletionNotification(title, saved > 0, if (saved > 0) "$saved items saved" else "No items could be downloaded")
        }
    }

    private fun pauseTask(taskId: Long) {
        val spec = taskSpecs[taskId]
        if (spec != null && FastHttpDownloader.isEligible(spec.url, spec.useDirectUrl)) {
            pausedTaskIds.add(taskId)
            serviceScope.launch {
                settingsManager.savePausedDownload(spec)
            }
            activeJobs[taskId]?.cancel()
        }
    }

    private fun cancelTask(taskId: Long) {
        pausedTaskIds.remove(taskId)
        activeProcessIds[taskId]?.let { pid ->
            try { YoutubeDL.getInstance().destroyProcessById(pid) } catch (_: Exception) {}
        }
        activeJobs[taskId]?.cancel()
        serviceScope.launch {
            settingsManager.removePausedDownload(taskId)
            FastHttpDownloader.cleanupTaskFiles(applicationContext, File(StorageUtils.downloadWorkDir(applicationContext), "task_$taskId"))
        }
        settingsManager.removeLocalDownload(taskId)
        notificationManager.cancel(taskNotificationId(taskId))
    }

    private fun checkAndStopService() {
        if (activeTasks.isEmpty()) {
            notificationManager.cancel(summaryNotificationId)
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            stopSelf()
        } else {
            updateSummaryNotification()
        }
    }

    /** Android 15+: dataSync foreground services are limited to 6h/day. Pause resumable tasks cleanly. */
    override fun onTimeout(startId: Int, fgsType: Int) {
        Log.w(TAG, "Foreground service timeout reached, pausing/stopping downloads")
        activeTasks.keys.toList().forEach { id ->
            val spec = taskSpecs[id]
            if (spec != null && FastHttpDownloader.isEligible(spec.url, spec.useDirectUrl)) {
                pauseTask(id)
            } else {
                cancelTask(id)
            }
        }
        showCompletionNotification("Downloads paused", false, "Android time limit reached. Resume from Downloads.")
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        activeProcessIds.values.forEach { pid ->
            try { YoutubeDL.getInstance().destroyProcessById(pid) } catch (_: Exception) {}
        }
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun taskNotificationId(taskId: Long): Int = 2000 + (taskId and 0x7FFF).toInt()

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notificationManager.createNotificationChannel(
                NotificationChannel(channelId, "Downloads", NotificationManager.IMPORTANCE_LOW)
            )
        }
    }

    private fun contentIntent(): PendingIntent = PendingIntent.getActivity(
        this, 0, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    private fun createSummaryNotification(text: String? = null): Notification {
        val count = activeTasks.size
        val summaryTitle = when {
            count <= 0 -> "Preparing downloads..."
            count == 1 -> activeTasks.values.firstOrNull() ?: "Downloading..."
            else -> "$count downloads active"
        }
        return NotificationCompat.Builder(this, channelId)
            .setContentTitle(summaryTitle)
            .setContentText(text ?: if (count > 1) "Tap to view progress" else "Download in progress")
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setGroup(notifGroupKey)
            .setGroupSummary(true)
            .setContentIntent(contentIntent())
            .build()
    }

    private fun updateSummaryNotification() {
        if (activeTasks.isNotEmpty()) {
            notificationManager.notify(summaryNotificationId, createSummaryNotification())
        }
    }

    private fun updateTaskNotification(
        title: String,
        progress: Int,
        taskId: Long,
        detailText: String?,
        canPause: Boolean
    ) {
        val builder = NotificationCompat.Builder(this, channelId)
            .setContentTitle(title)
            .setContentText(detailText ?: if (progress >= 0) "$progress%" else "Working...")
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setGroup(notifGroupKey)
            .setContentIntent(contentIntent())
            .setProgress(100, progress.coerceAtLeast(0), progress < 0)

        if (canPause) {
            val pause = PendingIntent.getService(
                this,
                (taskId.toInt() shl 1) or 1,
                Intent(this, DownloadService::class.java)
                    .setAction(ACTION_PAUSE_DOWNLOAD).putExtra(EXTRA_TASK_ID, taskId),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            builder.addAction(android.R.drawable.ic_media_pause, "Pause", pause)
        }

        val cancel = PendingIntent.getService(
            this,
            taskId.toInt() shl 1,
            Intent(this, DownloadService::class.java)
                .setAction(ACTION_CANCEL_DOWNLOAD).putExtra(EXTRA_TASK_ID, taskId),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        builder.addAction(android.R.drawable.ic_menu_close_clear_cancel, "Cancel", cancel)
        notificationManager.notify(taskNotificationId(taskId), builder.build())
    }

    private fun showCompletionNotification(title: String, success: Boolean, detail: String?) {
        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle(title)
            .setContentText(detail ?: if (success) "Download complete" else "Download failed")
            .setSmallIcon(if (success) android.R.drawable.stat_sys_download_done else android.R.drawable.stat_notify_error)
            .setContentIntent(contentIntent())
            .setAutoCancel(true)
            .build()
        notificationManager.notify((System.currentTimeMillis() and 0x7FFFFFFF).toInt(), notification)
    }
}
