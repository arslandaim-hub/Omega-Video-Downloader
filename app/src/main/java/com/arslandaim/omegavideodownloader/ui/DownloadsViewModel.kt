/*
 * Omega Video Downloader Project Original (2026)
 * arslandaim-hub (GitHub.com/arslandaim-hub)
 * Licenced Under GPL-3.0+
*/
package com.arslandaim.omegavideodownloader.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arslandaim.omegavideodownloader.DownloadedVideo
import com.arslandaim.omegavideodownloader.SettingsManager
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

class DownloadsViewModel(
    private val settingsManager: SettingsManager
) : ViewModel() {

    val downloadedVideos: StateFlow<List<DownloadedVideo>> = settingsManager.downloadedVideos
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    var selectedTab by mutableStateOf("Videos")
    var videoToLock by mutableStateOf<DownloadedVideo?>(null)
    var videoToDelete by mutableStateOf<DownloadedVideo?>(null)
    var openPlaylistId by mutableStateOf<String?>(null)

    fun deleteVideo(video: DownloadedVideo) {
        viewModelScope.launch {
            if (video.isPlaylistGroup) {
                // Delete all videos in this playlist
                val currentVideos = downloadedVideos.value
                val itemsToDelete = currentVideos.filter { it.playlistId == video.playlistId }
                itemsToDelete.forEach { item ->
                    settingsManager.removeDownloadedVideo(item)
                    try {
                        val file = File(item.localPath)
                        if (file.exists()) file.delete()
                    } catch (_: Exception) {}
                }
            } else {
                settingsManager.removeDownloadedVideo(video)
                try {
                    val file = File(video.localPath)
                    if (file.exists()) file.delete()
                } catch (_: Exception) {}
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
