/*
 * Omega Video Downloader Project Original (2026)
 * arslandaim-hub (GitHub.com/arslandaim-hub)
 * Licenced Under GPL-3.0+
*/
package com.arslandaim.omegavideodownloader.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arslandaim.omegavideodownloader.ActiveDownload
import com.arslandaim.omegavideodownloader.SettingsManager
import com.arslandaim.omegavideodownloader.data.DownloadRepository
import com.arslandaim.omegavideodownloader.data.VideoRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.seconds

class MainViewModel(
    private val settingsManager: SettingsManager,
    private val downloadRepository: DownloadRepository,
    private val videoRepository: VideoRepository
) : ViewModel() {

    val themeMode: StateFlow<String> = settingsManager.themeMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "System")

    val appLanguage: StateFlow<String> = settingsManager.appLanguage
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "English (US)")

    val activeDownloads: StateFlow<List<ActiveDownload>> = settingsManager.activeDownloads
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val currentlyPlayingPath: StateFlow<String?> = settingsManager.currentlyPlayingPath
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    init {
        autoUpdateYtDlp()
        startDownloadMonitoring()
    }

    private fun autoUpdateYtDlp() {
        viewModelScope.launch(Dispatchers.IO) {
            val autoUpdate = settingsManager.autoUpdateYtDlp.first()
            if (autoUpdate) {
                videoRepository.updateYtDlpBinary()
            }
        }
    }

    private fun startDownloadMonitoring() {
        viewModelScope.launch(Dispatchers.IO) {
            while (true) {
                val activeList = downloadRepository.queryActiveDownloads()
                settingsManager.updateActiveDownloads(activeList)
                delay(1.seconds)
            }
        }
    }

    fun setCurrentlyPlaying(path: String?) {
        settingsManager.setCurrentlyPlaying(path)
    }

    fun cancelDownload(id: Long) {
        downloadRepository.cancelDownload(id)
    }
}
