/*
 * Omega Video Downloader Project Original (2026)
 * arslandaim-hub (GitHub.com/arslandaim-hub)
 * Licenced Under GPL-3.0+
*/
package com.arslandaim.omegavideodownloader

import android.app.Application
import com.arslandaim.omegavideodownloader.player.OmegaPlayerFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Process-wide initialisation. Runs once per process (unlike Activity.onCreate which
 * runs on every recreation), and moves heavy yt-dlp & media cache setup off the main thread.
 */
class OmegaApp : Application() {

    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        OmegaCrashHandler.initialize(this)
        YtDlpManager.initAsync(this, appScope)
        OmegaPlayerFactory.initCacheAsync(this, appScope)
    }
}
