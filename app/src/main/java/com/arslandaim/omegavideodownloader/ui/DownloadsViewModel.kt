/*
 * Omega Video Downloader Project Original (2026)
 * arslandaim-hub (GitHub.com/arslandaim-hub)
 * Licenced Under GPL-3.0+
*/
package com.arslandaim.omegavideodownloader.ui

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arslandaim.omegavideodownloader.DownloadedVideo
import com.arslandaim.omegavideodownloader.SettingsManager
import com.arslandaim.omegavideodownloader.StorageUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DownloadsViewModel(
    context: Context,
    private val settingsManager: SettingsManager
) : ViewModel() {

    private val appContext = context.applicationContext

    val downloadedVideos: StateFlow<List<DownloadedVideo>> = settingsManager.downloadedVideos
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    var selectedTab by mutableStateOf("Videos")
    var videoToLock by mutableStateOf<DownloadedVideo?>(null)
    var videoToDelete by mutableStateOf<DownloadedVideo?>(null)
    var openPlaylistId by mutableStateOf<String?>(null)

    fun deleteVideo(video: DownloadedVideo) {
        viewModelScope.launch {
            if (video.isPlaylistGroup) {
                val all = settingsManager.allVideos.first()
                val itemsToDelete = all.filter { it.playlistId == video.playlistId }
                itemsToDelete.forEach { item ->
                    settingsManager.removeDownloadedVideo(item)
                    if (!item.isPlaylistGroup && item.localPath.isNotBlank()) {
                        withContext(Dispatchers.IO) {
                            StorageUtils.deleteMedia(appContext, item.localPath)
                        }
                    }
                }
            } else {
                settingsManager.removeDownloadedVideo(video)
                withContext(Dispatchers.IO) {
                    StorageUtils.deleteMedia(appContext, video.localPath)
                }
            }
            videoToDelete = null
        }
    }

    fun syncHistory() {
        viewModelScope.launch {
            settingsManager.syncHistoryWithStorage()
        }
    }
}
