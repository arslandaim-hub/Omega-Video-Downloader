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
import com.arslandaim.omegavideodownloader.VideoMetadata
import com.arslandaim.omegavideodownloader.VideoQuality
import com.arslandaim.omegavideodownloader.data.DownloadRepository
import com.arslandaim.omegavideodownloader.data.VideoRepository
import kotlinx.coroutines.launch

class HomeViewModel(
    private val videoRepository: VideoRepository,
    private val downloadRepository: DownloadRepository
) : ViewModel() {

    var url by mutableStateOf("")
    var videoMetadata by mutableStateOf<VideoMetadata?>(null)
    var isLoading by mutableStateOf(false)
    var selectedQuality by mutableStateOf<VideoQuality?>(null)

    fun fetchMetadata() {
        if (url.isNotEmpty() && !isLoading) {
            isLoading = true
            videoMetadata = null
            viewModelScope.launch {
                videoMetadata = videoRepository.fetchVideoMetadata(url)
                isLoading = false
            }
        }
    }

    fun startDownload(quality: VideoQuality) {
        selectedQuality = quality
        videoMetadata?.let { metadata ->
            downloadRepository.startDownload(
                pageUrl = url,
                title = metadata.title,
                type = quality.type,
                formatId = quality.formatId,
                isCombined = quality.isCombined,
                isPlaylist = metadata.isPlaylist
            )
        }
    }

    fun clearUrl() {
        url = ""
    }
}
