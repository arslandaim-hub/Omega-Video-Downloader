/*
 * Omega Video Downloader Project Original (2026)
 * arslandaim-hub (GitHub.com/arslandaim-hub)
 * Licenced Under GPL-3.0+
*/
package com.arslandaim.omegavideodownloader.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
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
    with(sharedTransitionScope) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .sharedElement(
                    rememberSharedContentState(key = "video_container_$videoUrl"),
                    animatedVisibilityScope = animatedVisibilityScope,
                    boundsTransform = { _, _ ->
                        spring(
                            stiffness = Spring.StiffnessVeryLow,
                            dampingRatio = Spring.DampingRatioLowBouncy
                        )
                    }
                )
        ) {
            VideoPlayer(videoUrl = videoUrl, settingsManager = settingsManager, onBack = onBack)
        }
    }
}
