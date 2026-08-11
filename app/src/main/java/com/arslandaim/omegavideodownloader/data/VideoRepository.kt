/*
 * Omega Video Downloader Project Original (2026)
 * arslandaim-hub (GitHub.com/arslandaim-hub)
 * Licenced Under GPL-3.0+
*/
package com.arslandaim.omegavideodownloader.data

import android.content.Context
import com.arslandaim.omegavideodownloader.CookiesManager
import com.arslandaim.omegavideodownloader.VideoMetadata
import com.arslandaim.omegavideodownloader.YtDlpManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class VideoRepository(private val context: Context) {

    suspend fun fetchVideoMetadata(videoUrl: String): VideoMetadata? = withContext(Dispatchers.IO) {
        val cookiesPath = CookiesManager.getCookiesPath(context)
        YtDlpManager.getVideoInfo(videoUrl, cookiesPath)
    }

    suspend fun updateYtDlpBinary(): Result<String> = withContext(Dispatchers.IO) {
        YtDlpManager.updateBinary(context)
    }
}
