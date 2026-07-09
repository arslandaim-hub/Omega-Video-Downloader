/*
 * Omega Video Downloader Project Original (2026)
 * arslandaim-hub (GitHub.com/arslandaim-hub)
 * Licenced Under GPL-3.0+
*/
package com.arslandaim.omegavideodownloader

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*
import java.io.File

class DownloadService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private lateinit var notificationManager: NotificationManager
    private val notificationId = 101
    private val channelId = "download_channel"
    
    // Track active tasks to manage foreground service lifecycle
    private val activeTasks = java.util.concurrent.ConcurrentHashMap<Long, String>()
    private var lastForegroundId: Int = -1

    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val url = intent?.getStringExtra("url") ?: return START_NOT_STICKY
        val title = intent.getStringExtra("title") ?: "Video"
        val formatId = intent.getStringExtra("formatId") ?: "best"
        val type = intent.getStringExtra("type") ?: "video"
        val isCombined = intent.getBooleanExtra("isCombined", false)
        val cookiesPath = intent.getStringExtra("cookiesPath")
        
        val taskId = System.currentTimeMillis()
        val settingsManager = SettingsManager(applicationContext)
        
        activeTasks[taskId] = title
        
        // Ensure we are in foreground
        if (activeTasks.size == 1) {
            startForeground(notificationId, createNotification(title, 0))
            lastForegroundId = notificationId
        } else {
            updateNotification("Multiple downloads in progress...", 0)
        }

        serviceScope.launch {
            // Use proper extension for the temp file to avoid yt-dlp appending its own
            val ext = if (type == "audio") "mp3" else "mp4"
            val tempFile = File(cacheDir, "download_${taskId}.$ext")
            
            android.util.Log.d("DownloadService", "Starting download for $title to ${tempFile.absolutePath}")

            var totalBytes: Long = 0
            val sizeRegex = Regex("(?i)of\\s+([\\d.]+)([KMGT]i?B)")

            val success = try {
                YtDlpManager.downloadVideo(
                    url = url,
                    formatId = formatId,
                    outputFile = tempFile,
                    type = type,
                    isCombined = isCombined,
                    cookiesPath = cookiesPath
                ) { progress, _, line ->
                    // Try to extract total size from line if not already captured
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
                        }
                    }

                    val downloaded = if (totalBytes > 0) (totalBytes * (progress / 100f)).toLong() else 0L

                    if (activeTasks.size == 1) {
                        updateNotification(title, progress.toInt())
                    } else {
                        updateNotification("${activeTasks.size} active downloads", progress.toInt())
                    }
                    settingsManager.updateLocalDownload(taskId, title, progress, totalBytes, downloaded, type)
                }
            } catch (e: Exception) {
                android.util.Log.e("DownloadService", "YtDlp execution error", e)
                false
            }

            if (success && tempFile.exists()) {
                android.util.Log.d("DownloadService", "Download success, saving to gallery: ${tempFile.length()} bytes")
                val finalUri = if (type == "audio") {
                    StorageUtils.saveAudioToGallery(applicationContext, tempFile, title)
                } else {
                    StorageUtils.saveVideoToGallery(applicationContext, tempFile, title)
                }
                
                if (finalUri != null) {
                    android.util.Log.d("DownloadService", "Saved to gallery: $finalUri")
                    settingsManager.addDownloadedVideo(
                        DownloadedVideo(
                            title = title,
                            localPath = finalUri.toString(),
                            timestamp = System.currentTimeMillis(),
                            type = type
                        )
                    )
                    showCompletionNotification(title, true)
                } else {
                    android.util.Log.e("DownloadService", "Failed to save to gallery")
                    showCompletionNotification(title, false)
                }
            } else {
                android.util.Log.e("DownloadService", "Download failed or file missing: exists=${tempFile.exists()}")
                showCompletionNotification(title, false)
            }
            
            settingsManager.removeLocalDownload(taskId)
            if (tempFile.exists()) tempFile.delete()
            
            activeTasks.remove(taskId)
            
            if (activeTasks.isEmpty()) {
                android.util.Log.d("DownloadService", "All tasks finished, stopping service")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                } else {
                    @Suppress("DEPRECATION")
                    stopForeground(true)
                }
                stopSelf()
            } else {
                android.util.Log.d("DownloadService", "Tasks remaining: ${activeTasks.size}")
                updateNotification("${activeTasks.size} active downloads", 0)
            }
        }

        return START_NOT_STICKY
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

    private fun createNotification(title: String, progress: Int) =
        NotificationCompat.Builder(this, channelId)
            .setContentTitle("Downloading $title")
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setProgress(100, progress, false)
            .setOngoing(true)
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
