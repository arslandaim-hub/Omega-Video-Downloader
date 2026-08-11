/*
 * Omega Video Downloader Project Original (2026)
 * arslandaim-hub (GitHub.com/arslandaim-hub)
 * Licenced Under GPL-3.0+
*/
package com.arslandaim.omegavideodownloader.data

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.os.Build
import com.arslandaim.omegavideodownloader.ActiveDownload
import com.arslandaim.omegavideodownloader.CookiesManager
import com.arslandaim.omegavideodownloader.DownloadService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class DownloadRepository(private val context: Context) {

    private val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

    fun startDownload(
        pageUrl: String, 
        title: String, 
        type: String = "video", 
        formatId: String? = null, 
        isCombined: Boolean = false,
        isPlaylist: Boolean = false
    ) {
        val cookiesPath = CookiesManager.getCookiesPath(context)
        val intent = Intent(context, DownloadService::class.java).apply {
            putExtra("url", pageUrl)
            putExtra("title", title)
            putExtra("type", type)
            putExtra("formatId", formatId)
            putExtra("isCombined", isCombined)
            putExtra("cookiesPath", cookiesPath)
            putExtra("isPlaylist", isPlaylist)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }

    fun cancelDownload(id: Long) {
        downloadManager.remove(id)
        val cancelIntent = Intent(context, DownloadService::class.java).apply {
            action = DownloadService.ACTION_CANCEL_DOWNLOAD
            putExtra(DownloadService.EXTRA_TASK_ID, id)
        }
        context.startService(cancelIntent)
    }

    suspend fun queryActiveDownloads(): List<ActiveDownload> = withContext(Dispatchers.IO) {
        val activeList = mutableListOf<ActiveDownload>()
        val query = DownloadManager.Query()
        downloadManager.query(query)?.use { cursor ->
            val idCol = cursor.getColumnIndex(DownloadManager.COLUMN_ID)
            val statusCol = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
            val titleCol = cursor.getColumnIndex(DownloadManager.COLUMN_TITLE)
            val totalCol = cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
            val downloadedCol = cursor.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
            val descCol = cursor.getColumnIndex(DownloadManager.COLUMN_DESCRIPTION)

            while (cursor.moveToNext()) {
                val status = cursor.getInt(statusCol)
                if ((status == DownloadManager.STATUS_RUNNING) || (status == DownloadManager.STATUS_PENDING) || (status == DownloadManager.STATUS_PAUSED)) {
                    val id = cursor.getLong(idCol)
                    val title = cursor.getString(titleCol) ?: "Video"
                    val total = cursor.getLong(totalCol)
                    val downloaded = cursor.getLong(downloadedCol)
                    val progress = if (total > 0) downloaded.toFloat() / total else 0f
                    val desc = cursor.getString(descCol) ?: ""
                    val type = if (desc.contains("audio", ignoreCase = true)) "audio" else "video"

                    activeList.add(
                        ActiveDownload(
                            id = id,
                            title = title,
                            progress = progress,
                            totalBytes = total,
                            downloadedBytes = downloaded,
                            status = when (status) {
                                DownloadManager.STATUS_PENDING -> "Pending"
                                DownloadManager.STATUS_PAUSED -> "Paused"
                                else -> "Downloading"
                            },
                            thumbnailUrl = null,
                            type = type
                        )
                    )
                }
            }
        }
        activeList
    }
}
