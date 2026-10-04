/*
 * Omega Video Downloader Project Original (2026)
 * arslandaim-hub (GitHub.com/arslandaim-hub)
 * Licenced Under GPL-3.0+
*/
package com.arslandaim.omegavideodownloader.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.arslandaim.omegavideodownloader.SettingsManager
import com.arslandaim.omegavideodownloader.VideoPlayer

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun VideoPlayerScreen(
    videoUrl: String,
    settingsManager: SettingsManager,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    onBack: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        VideoPlayer(videoUrl = videoUrl, settingsManager = settingsManager, onBack = onBack)
    }
}
