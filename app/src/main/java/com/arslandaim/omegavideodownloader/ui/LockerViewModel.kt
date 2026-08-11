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
import com.arslandaim.omegavideodownloader.LockerManager
import com.arslandaim.omegavideodownloader.SettingsManager
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class LockerViewModel(
    private val settingsManager: SettingsManager,
    private val lockerManager: LockerManager
) : ViewModel() {

    val lockerVideos: StateFlow<List<DownloadedVideo>> = settingsManager.lockerVideos
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val isLockerSet: StateFlow<Boolean> = settingsManager.isLockerSet
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val useBiometric: StateFlow<Boolean> = settingsManager.useBiometric
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    var isAuthorized by mutableStateOf(false)
    var isInputVisible by mutableStateOf(false)
    var pinValue by mutableStateOf("")
    var selectedTab by mutableStateOf("Videos")
    var videoToUnlock by mutableStateOf<DownloadedVideo?>(null)
    var openPlaylistId by mutableStateOf<String?>(null)

    fun verifyPin(pin: String, onSuccess: () -> Unit, onError: () -> Unit) {
        viewModelScope.launch {
            if (settingsManager.verifyLockerPin(pin)) {
                isAuthorized = true
                isInputVisible = false
                pinValue = ""
                onSuccess()
            } else {
                pinValue = ""
                onError()
            }
        }
    }

    fun toggleLock(video: DownloadedVideo, onComplete: (Boolean) -> Unit) {
        viewModelScope.launch {
            if (video.isPlaylistGroup) {
                // Lock/Unlock all items in this playlist
                val currentVideos = settingsManager.downloadedVideos.first()
                val lockerVideos = settingsManager.lockerVideos.first()
                val allVideos = if (video.isLocked) lockerVideos else currentVideos
                val itemsToToggle = allVideos.filter { it.playlistId == video.playlistId && !it.isPlaylistGroup }
                
                var allSuccess = true
                itemsToToggle.forEach { item ->
                    val newPath = if (video.isLocked) lockerManager.unlockFile(item) else lockerManager.lockFile(item)
                    if (newPath != null) {
                        settingsManager.updateVideoLockStatus(item, !video.isLocked, newPath)
                    } else {
                        allSuccess = false
                    }
                }
                
                // Finally update the group itself
                settingsManager.updateVideoLockStatus(video, !video.isLocked, "")
                onComplete(allSuccess)
            } else {
                if (video.isLocked) {
                    val newPath = lockerManager.unlockFile(video)
                    if (newPath != null) {
                        settingsManager.updateVideoLockStatus(video, false, newPath)
                        onComplete(true)
                    } else {
                        onComplete(false)
                    }
                } else {
                    val newPath = lockerManager.lockFile(video)
                    if (newPath != null) {
                        settingsManager.updateVideoLockStatus(video, true, newPath)
                        onComplete(true)
                    } else {
                        onComplete(false)
                    }
                }
            }
        }
    }

    fun resetAuth() {
        isAuthorized = false
        isInputVisible = false
        pinValue = ""
    }
}
