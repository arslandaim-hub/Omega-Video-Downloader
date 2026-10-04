/*
 * Omega Video Downloader Project Original (2026)
 * arslandaim-hub (GitHub.com/arslandaim-hub)
 * Licenced Under GPL-3.0+
*/
package com.arslandaim.omegavideodownloader

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.*
import java.io.File
import java.util.concurrent.ConcurrentHashMap

class DownloadService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private lateinit var notificationManager: NotificationManager
    private val notificationId = 101
    private val channelId = "download_channel"
    
    companion object {
        const val ACTION_CANCEL_DOWNLOAD = "com.arslandaim.omegavideodownloader.CANCEL_DOWNLOAD"
        const val EXTRA_TASK_ID = "extra_task_id"
    }

    private val activeTasks = ConcurrentHashMap<Long, String>()
    private val activeJobs = ConcurrentHashMap<Long, Job>()
    private var lastForegroundId: Int = -1
    private var lastNotificationUpdateTime = 0L

    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_CANCEL_DOWNLOAD) {
            val taskId = intent.getLongExtra(EXTRA_TASK_ID, -1L)
            if (taskId != -1L) {
                cancelTask(taskId)
            }
            return START_NOT_STICKY
        }

        val url = intent?.getStringExtra("url") ?: return START_NOT_STICKY
        val title = intent.getStringExtra("title") ?: "Video"
        val formatId = intent.getStringExtra("formatId") ?: "best"
        val type = intent.getStringExtra("type") ?: "video"
        val isCombined = intent.getBooleanExtra("isCombined", false)
        val cookiesPath = intent.getStringExtra("cookiesPath")
        val isPlaylist = intent.getBooleanExtra("isPlaylist", false)
        
        val taskId = System.currentTimeMillis()
        activeTasks[taskId] = title
        
        val settingsManager = SettingsManager(applicationContext)
        serviceScope.launch {
            settingsManager.updateLocalDownload(
                id = taskId,
                title = title,
                progress = 0f,
                totalBytes = 0,
                downloadedBytes = 0,
                type = type,
                playlistProgress = if (isPlaylist) "1/?" else null
            )
        }
        
        if (activeTasks.size == 1) {
            startForeground(notificationId, createNotification(title, 0))
            lastForegroundId = notificationId
        } else {
            updateNotification("Multiple downloads in progress...", 0)
        }

        val job = serviceScope.launch {
            if (isPlaylist) {
                handlePlaylistDownload(taskId, url, title, formatId, cookiesPath)
            } else {
                handleSingleDownload(taskId, url, title, formatId, type, isCombined, cookiesPath)
            }
            
            activeTasks.remove(taskId)
            activeJobs.remove(taskId)
            checkAndStopService()
        }

        activeJobs[taskId] = job
        return START_NOT_STICKY
    }

    private suspend fun handleSingleDownload(
        taskId: Long,
        url: String,
        title: String,
        formatId: String,
        type: String,
        isCombined: Boolean,
        cookiesPath: String?
    ) {
        val ext = if (type == "audio") "mp3" else "mp4"
        val tempFile = File(cacheDir, "download_$taskId.$ext")
        val settingsManager = SettingsManager(applicationContext)

        var totalBytes: Long = 0
        val sizeRegex = Regex("(?i)of\\s+([\\d.]+)([KMGT]i?B)")
        var success = false
        var retryCount = 0
        val maxRetries = 3

        while (retryCount < maxRetries && !success && currentCoroutineContext().isActive) {
            if (retryCount > 0) {
                updateNotification("Retrying $title...", 0)
                delay(2000L * retryCount)
            }

            try {
                success = YtDlpManager.downloadVideo(
                    url = url,
                    formatId = formatId,
                    outputFile = tempFile,
                    type = type,
                    isCombined = isCombined,
                    cookiesPath = cookiesPath,
                    processId = taskId.toString()
                ) { progress, _, line ->
                    if (!serviceScope.isActive) return@downloadVideo

                    if (totalBytes == 0L) {
                        sizeRegex.find(line)?.let { match ->
                            val value = match.groupValues[1].toDoubleOrNull() ?: 0.0
                            val unit = match.groupValues[2]
                            totalBytes = when (unit.uppercase()) {
                                "KIB", "KB" -> (value * 1024).toLong()
                                "MIB", "MB" -> (value * 1024 * 1024).toLong()
                                "GIB", "GB" -> (value * 1024 * 1024 * 1024).toLong()
                                "TIB", "TB" -> (value * 1024 * 1024 * 1024 * 1024).toLong()
                                else -> 0L
                            }
                            
                            if (totalBytes > 0 && !StorageUtils.hasEnoughSpace(applicationContext, totalBytes)) {
                                android.util.Log.e("DownloadService", "Low storage warning")
                            }
                        }
                    }

                    val downloaded = if (totalBytes > 0) (totalBytes * (progress / 100f)).toLong() else 0L
                    val currentTime = System.currentTimeMillis()
                    if (currentTime - lastNotificationUpdateTime > 500L) {
                        updateNotification(title, progress.toInt())
                        lastNotificationUpdateTime = currentTime
                    }
                    settingsManager.updateLocalDownload(taskId, title, progress, totalBytes, downloaded, type)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {}

            if (!success) retryCount++
        }

        if (currentCoroutineContext().isActive && success && tempFile.exists()) {
            val finalUri = if (type == "audio") {
                StorageUtils.saveAudioToGallery(applicationContext, tempFile, title)
            } else {
                StorageUtils.saveVideoToGallery(applicationContext, tempFile, title)
            }
            
            if (finalUri != null) {
                settingsManager.addDownloadedVideo(DownloadedVideo(title, finalUri.toString(), System.currentTimeMillis(), type = type))
                showCompletionNotification(title, true)
            } else {
                showCompletionNotification(title, false)
            }
        } else if (currentCoroutineContext().isActive) {
            showCompletionNotification(title, false)
        }
        
        settingsManager.removeLocalDownload(taskId)
        if (tempFile.exists()) tempFile.delete()
    }

    private suspend fun handlePlaylistDownload(
        taskId: Long,
        url: String,
        title: String,
        formatId: String,
        cookiesPath: String?
    ) {
        val settingsManager = SettingsManager(applicationContext)
        val projectDir = File(cacheDir, "playlist_$taskId").apply { mkdirs() }
        
        val playlistId = "playlist_${System.currentTimeMillis()}"
        
        // Add a group entry to act as the "Folder"
        settingsManager.addDownloadedVideo(
            DownloadedVideo(
                title = title,
                localPath = "", // Folder doesn't have a path
                timestamp = System.currentTimeMillis(),
                type = "video",
                playlistId = playlistId,
                isPlaylistGroup = true
            )
        )

        var currentIndex = 1
        var hasMore = true
        val resolution = formatId.filter { it.isDigit() }.ifEmpty { "720" }
        
        while (hasMore && currentCoroutineContext().isActive) {
            val itemFile = File(projectDir, "item_$currentIndex.mp4")
            val itemRequest = YoutubeDLRequest(url).apply {
                addOption("--playlist-items", currentIndex)
                addOption("-o", itemFile.absolutePath)
                addOption("-f", "bestvideo[height<=$resolution]+bestaudio/best[height<=$resolution]")
                addOption("--merge-output-format", "mp4")
                if (cookiesPath != null) addOption("--cookies", cookiesPath)
            }
            
            updateNotification("$title ($currentIndex/?)", 0)
            settingsManager.updateLocalDownload(
                id = taskId,
                title = title,
                progress = 0f,
                totalBytes = 0,
                downloadedBytes = 0,
                type = "video",
                playlistProgress = "$currentIndex/?"
            )
            
            try {
                val success = withContext(Dispatchers.IO) {
                    try {
                        YoutubeDL.getInstance().execute(itemRequest, "${taskId}_$currentIndex") { progress, _, _ ->
                            if (System.currentTimeMillis() - lastNotificationUpdateTime > 500L) {
                                updateNotification("$title ($currentIndex/?)", progress.toInt())
                                lastNotificationUpdateTime = System.currentTimeMillis()
                            }
                            settingsManager.updateLocalDownload(
                                id = taskId,
                                title = title,
                                progress = progress,
                                totalBytes = 0,
                                downloadedBytes = 0,
                                type = "video",
                                playlistProgress = "$currentIndex/?"
                            )
                        }
                        true
                    } catch (_: Exception) {
                        false
                    }
                }
                
                if (success && itemFile.exists() && itemFile.length() > 0) {
                    // Save individual item to gallery
                    val itemTitle = "$title - Part $currentIndex"
                    val finalUri = StorageUtils.saveVideoToGallery(applicationContext, itemFile, itemTitle)
                    
                    if (finalUri != null) {
                        settingsManager.addDownloadedVideo(
                            DownloadedVideo(
                                title = itemTitle,
                                localPath = finalUri.toString(),
                                timestamp = System.currentTimeMillis(),
                                type = "video",
                                playlistId = playlistId,
                                isPlaylistGroup = false
                            )
                        )
                    }
                    
                    itemFile.delete() // Clean up temp part
                    currentIndex++
                } else {
                    hasMore = false
                }
            } catch (e: Exception) {
                hasMore = false
            }
        }
        
        projectDir.deleteRecursively()
        settingsManager.removeLocalDownload(taskId)
        showCompletionNotification(title, currentIndex > 1)
    }

    private fun cancelTask(taskId: Long) {
        activeJobs[taskId]?.cancel()
        activeJobs.remove(taskId)
        activeTasks.remove(taskId)
        
        val settingsManager = SettingsManager(applicationContext)
        settingsManager.removeLocalDownload(taskId)

        try {
            YoutubeDL.getInstance().destroyProcessById(taskId.toString())
        } catch (_: Exception) {}
        
        if (activeTasks.isEmpty()) {
            checkAndStopService()
        } else {
            updateNotification("${activeTasks.size} active downloads", 0)
        }
    }

    private fun checkAndStopService() {
        if (activeTasks.isEmpty()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_REMOVE)
            } else {
                @Suppress("DEPRECATION")
                stopForeground(true)
            }
            stopSelf()
        } else {
            updateNotification("${activeTasks.size} active downloads", 0)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Downloads",
                NotificationManager.IMPORTANCE_LOW
            )
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(title: String, progress: Int): android.app.Notification =
        NotificationCompat.Builder(this, channelId)
            .setContentTitle("Downloading $title")
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOngoing(true)
            .apply {
                if (progress >= 0) {
                    setProgress(100, progress, false)
                } else {
                    setProgress(0, 0, true)
                }
            }
            .build()

    private fun updateNotification(title: String, progress: Int) {
        notificationManager.notify(notificationId, createNotification(title, progress))
    }

    private fun showCompletionNotification(title: String, success: Boolean) {
        val message = if (success) "Download complete" else "Download failed"
        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle(title)
            .setContentText(message)
            .setSmallIcon(if (success) android.R.drawable.stat_sys_download_done else android.R.drawable.stat_notify_error)
            .build()
        notificationManager.notify(System.currentTimeMillis().toInt(), notification)
    }
}
