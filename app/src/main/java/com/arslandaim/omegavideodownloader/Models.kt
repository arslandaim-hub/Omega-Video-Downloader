/*
 * Omega Video Downloader Project Original (2026)
 * arslandaim-hub (GitHub.com/arslandaim-hub)
 * Licenced Under GPL-3.0+
*/
package com.arslandaim.omegavideodownloader

import androidx.compose.runtime.Immutable

@Immutable
data class VideoQuality(
    val label: String,
    val url: String,
    val size: String,
    val type: String = "video",
    val formatId: String? = null,
    val isCombined: Boolean = false
)

@Immutable
data class VideoMetadata(
    val title: String,
    val qualities: List<VideoQuality>,
    val audioQualities: List<VideoQuality> = emptyList(),
    val thumbnailUrl: String? = null,
)

@Immutable
data class ActiveDownload(
    val id: Long,
    val title: String,
    val progress: Float,
    val totalBytes: Long,
    val downloadedBytes: Long,
    val status: String,
    val thumbnailUrl: String? = null,
    val type: String = "video",
)

data class DownloadedVideo(
    val title: String,
    val localPath: String,
    val timestamp: Long,
    val thumbnailUrl: String? = null,
    val isLocked: Boolean = false,
    val type: String = "video",
)
