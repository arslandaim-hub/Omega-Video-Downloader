/*
 * Omega Video Downloader Project Original (2026)
 * arslandaim-hub (GitHub.com/arslandaim-hub)
 * Licenced Under GPL-3.0+
*/
package com.arslandaim.omegavideodownloader.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import coil.ImageLoader
import coil.compose.AsyncImage
import com.arslandaim.omegavideodownloader.*
import com.arslandaim.omegavideodownloader.ui.MoviesViewModel
import com.arslandaim.omegavideodownloader.ui.components.formatRuntimeShort
import com.arslandaim.omegavideodownloader.ui.theme.OmegaTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MovieDetailScreen(
    movie: MovieSearchResult,
    viewModel: MoviesViewModel,
    watchHistory: List<WatchHistoryItem>,
    isLiked: Boolean,
    imageLoader: ImageLoader,
    onToggleLike: () -> Unit,
    onStreamClick: (String) -> Unit,
    onDownloadStarted: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val omegaColors = OmegaTheme.colors
    val metadata = viewModel.resolvedMetadata
    val isResolving = viewModel.isResolvingDetail
    val error = viewModel.detailError

    var showDownloadSheet by remember { mutableStateOf(false) }
    var expandSynopsis by remember { mutableStateOf(false) }
    var pendingDownloadQuality by remember { mutableStateOf<VideoQuality?>(null) }

    val availableStorageBytes = remember(showDownloadSheet) {
        StorageUtils.getAvailableStorage(context)
    }

    val historyEntry = remember(watchHistory, movie.pageUrl) {
        val id = WatchHistoryItem.streamId(movie.pageUrl)
        watchHistory.firstOrNull { it.id == id }
    }

    BackHandler(onBack = onBack)

    fun executeMovieDownload(quality: VideoQuality) {
        viewModel.startMovieDownload(quality)
        Toast.makeText(context, "Movie download added to Queue", Toast.LENGTH_SHORT).show()
        showDownloadSheet = false
        onDownloadStarted()
    }

    val legacyStorageLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        val q = pendingDownloadQuality
        pendingDownloadQuality = null
        if (granted && q != null) executeMovieDownload(q)
        else Toast.makeText(context, "Storage permission is required on Android 9 and older.", Toast.LENGTH_LONG).show()
    }

    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ ->
        val q = pendingDownloadQuality
        pendingDownloadQuality = null
        q?.let { executeMovieDownload(it) }
    }

    fun requestPermissionsAndDownload(quality: VideoQuality) {
        when {
            Build.VERSION.SDK_INT <= Build.VERSION_CODES.P -> {
                val hasWrite = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                ) == PackageManager.PERMISSION_GRANTED
                if (hasWrite) executeMovieDownload(quality)
                else {
                    pendingDownloadQuality = quality
                    legacyStorageLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                }
            }
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> {
                val hasNotif = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
                if (hasNotif) executeMovieDownload(quality)
                else {
                    pendingDownloadQuality = quality
                    notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
            else -> executeMovieDownload(quality)
        }
    }

    if (viewModel.showDisclaimerDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissDisclaimer() },
            title = { Text("Content Responsibility", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "Omega Video Downloader supports publicly accessible, non-DRM streams. " +
                        "Please make sure you have the right or permission to stream or download any content you access."
                )
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.confirmDisclaimer() },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = omegaColors.primary,
                        contentColor = Color.White
                    )
                ) {
                    Text("I Understand", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissDisclaimer() }) { Text("Cancel") }
            }
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(omegaColors.backgroundBrush)
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 140.dp)
        ) {
            // Backdrop + Poster Header
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(310.dp)
                ) {
                    val thumb = metadata?.thumbnailUrl ?: movie.thumbnailUrl
                    if (!thumb.isNullOrBlank()) {
                        AsyncImage(
                            model = thumb,
                            imageLoader = imageLoader,
                            contentDescription = movie.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(omegaColors.cardElevated),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Movie,
                                null,
                                tint = omegaColors.primary.copy(alpha = 0.4f),
                                modifier = Modifier.size(64.dp)
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    listOf(
                                        Color.Black.copy(alpha = 0.4f),
                                        omegaColors.backgroundTop.copy(alpha = 0.7f),
                                        omegaColors.backgroundTop
                                    )
                                )
                            )
                    )

                    // Top Navigation Row
                    Row(
                        modifier = Modifier
                            .statusBarsPadding()
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.55f))
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White)
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            IconButton(
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onToggleLike()
                                },
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(Color.Black.copy(alpha = 0.55f))
                            ) {
                                Icon(
                                    imageVector = if (isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                    contentDescription = "Watchlist",
                                    tint = if (isLiked) omegaColors.watchlistRose else Color.White
                                )
                            }
                            IconButton(
                                onClick = {
                                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_SUBJECT, movie.title)
                                        putExtra(Intent.EXTRA_TEXT, "${movie.title}\n${movie.pageUrl}")
                                    }
                                    context.startActivity(Intent.createChooser(shareIntent, "Share Movie"))
                                },
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(Color.Black.copy(alpha = 0.55f))
                            ) {
                                Icon(Icons.Default.Share, "Share", tint = Color.White)
                            }
                        }
                    }

                    // Bottom Title & Metadata Strip
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 10.dp)
                    ) {
                        val displayTitle = metadata?.title ?: movie.title
                        Text(
                            text = displayTitle,
                            color = omegaColors.textPrimary,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Black,
                            lineHeight = 27.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val yr = metadata?.year ?: movie.year
                            if (!yr.isNullOrBlank()) {
                                Surface(color = omegaColors.gold, shape = RoundedCornerShape(6.dp)) {
                                    Text(
                                        yr,
                                        color = Color.Black,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            val dur = metadata?.durationSec?.takeIf { it > 0 } ?: movie.durationSec
                            if (dur > 0) {
                                Text(
                                    formatRuntimeShort(dur),
                                    color = omegaColors.textPrimary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            Surface(
                                color = omegaColors.primary.copy(alpha = 0.18f),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    movie.industry.label,
                                    color = omegaColors.primary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                )
                            }
                            val subs = metadata?.subtitleTracks.orEmpty()
                            if (subs.isNotEmpty()) {
                                Surface(
                                    color = omegaColors.success.copy(alpha = 0.18f),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        "CC (${subs.size})",
                                        color = omegaColors.success,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Genres + Creator + Synopsis
            item {
                Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                    val genres = metadata?.genres?.ifEmpty { movie.genres } ?: movie.genres
                    if (genres.isNotEmpty()) {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(genres) { g ->
                                Surface(
                                    color = omegaColors.card,
                                    shape = RoundedCornerShape(8.dp),
                                    border = BorderStroke(1.dp, omegaColors.border)
                                ) {
                                    Text(
                                        g,
                                        color = omegaColors.textSecondary,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                    }

                    val creator = metadata?.creator ?: movie.subtitle
                    if (!creator.isNullOrBlank()) {
                        Text(
                            text = "Source / Creator: $creator",
                            color = omegaColors.textSecondary,
                            fontSize = 12.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    val synopsis = metadata?.description ?: movie.description
                    if (!synopsis.isNullOrBlank()) {
                        Text(
                            text = synopsis,
                            color = omegaColors.textPrimary.copy(alpha = 0.85f),
                            fontSize = 13.sp,
                            lineHeight = 19.sp,
                            maxLines = if (expandSynopsis) Int.MAX_VALUE else 4,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (synopsis.length > 180) {
                            TextButton(
                                onClick = { expandSynopsis = !expandSynopsis },
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Text(
                                    if (expandSynopsis) "Show less" else "Read full synopsis",
                                    color = omegaColors.primary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }

            // Action Buttons (Stream Now / Resume + Download Offline)
            item {
                Spacer(modifier = Modifier.height(16.dp))
                Column(
                    modifier = Modifier.padding(horizontal = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (isResolving) {
                        Surface(
                            color = omegaColors.card,
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(1.dp, omegaColors.border),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(18.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(22.dp),
                                    strokeWidth = 2.5.dp,
                                    color = omegaColors.primary
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = viewModel.detailResolveStatus ?: "Preparing movie streams...",
                                    color = omegaColors.textPrimary,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.sp
                                )
                            }
                        }
                    } else if (error != null) {
                        Surface(
                            color = omegaColors.danger.copy(alpha = 0.1f),
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(1.dp, omegaColors.danger.copy(alpha = 0.35f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(error, color = omegaColors.textPrimary, fontSize = 13.sp)
                                Spacer(modifier = Modifier.height(10.dp))
                                Button(
                                    onClick = { viewModel.retryMovieDetail() },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = omegaColors.primary,
                                        contentColor = Color.White
                                    )
                                ) {
                                    Text("Retry Extraction")
                                }
                            }
                        }
                    } else if (metadata != null) {
                        if (metadata.streams.isNotEmpty()) {
                            val resumeLabel = if (historyEntry != null && historyEntry.resumePositionMs >= 3000L) {
                                "Resume Movie (${formatTime(historyEntry.resumePositionMs)})"
                            } else {
                                "Stream Movie (${metadata.streams.first().label})"
                            }
                            Button(
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    viewModel.runWithDisclaimerCheck {
                                        val key = viewModel.prepareStreamSession(metadata.streams.first())
                                        if (key != null) onStreamClick(key)
                                        else Toast.makeText(context, "No stream source available", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(54.dp),
                                shape = RoundedCornerShape(16.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = omegaColors.primary,
                                    contentColor = Color.White
                                )
                            ) {
                                Icon(Icons.Default.PlayArrow, null, modifier = Modifier.size(22.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(resumeLabel, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
                            }
                        }

                        if (metadata.qualities.isNotEmpty()) {
                            OutlinedButton(
                                onClick = { showDownloadSheet = true },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(50.dp),
                                shape = RoundedCornerShape(16.dp),
                                border = BorderStroke(1.dp, omegaColors.primary),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = omegaColors.primary)
                            ) {
                                Icon(Icons.Default.Download, null, modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    "Download Offline (${metadata.qualities.size} qualities)",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                            }
                        }
                    }
                }
            }

            // Stream Quality & Subtitle Info Section
            if (metadata != null && metadata.streams.size > 1) {
                item {
                    Spacer(modifier = Modifier.height(22.dp))
                    Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                        Text(
                            "Stream Sources",
                            color = omegaColors.textPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        metadata.streams.forEach { candidate ->
                            Surface(
                                onClick = {
                                    viewModel.runWithDisclaimerCheck {
                                        val key = viewModel.prepareStreamSession(candidate)
                                        if (key != null) onStreamClick(key)
                                    }
                                },
                                color = omegaColors.card,
                                shape = RoundedCornerShape(14.dp),
                                border = BorderStroke(1.dp, omegaColors.border),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(14.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.LiveTv, null, tint = omegaColors.primary, modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(10.dp))
                                        Text(candidate.label, color = omegaColors.textPrimary, fontWeight = FontWeight.SemiBold)
                                    }
                                    if (candidate.sizeBytes > 0) {
                                        Text(
                                            StorageUtils.formatSize(candidate.sizeBytes),
                                            color = omegaColors.textSecondary,
                                            fontSize = 12.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Download Quality + Free Storage Sheet
    if (showDownloadSheet && metadata != null) {
        ModalBottomSheet(
            onDismissRequest = { showDownloadSheet = false },
            containerColor = omegaColors.cardElevated,
            contentColor = omegaColors.textPrimary
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 12.dp)
            ) {
                Text("Download Movie Offline", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "Device Free Space: ${StorageUtils.formatSize(availableStorageBytes)}",
                    color = omegaColors.success,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
                if (metadata.subtitleTracks.isNotEmpty()) {
                    Text(
                        "Includes ${metadata.subtitleTracks.size} subtitle track(s) when streaming",
                        color = omegaColors.gold,
                        fontSize = 11.sp
                    )
                }
                Spacer(modifier = Modifier.height(14.dp))

                metadata.qualities.forEach { quality ->
                    val fits = quality.sizeBytes <= 0L || StorageUtils.hasEnoughSpace(context, quality.sizeBytes)
                    Surface(
                        onClick = {
                            if (!fits) {
                                Toast.makeText(context, "Not enough free device storage", Toast.LENGTH_SHORT).show()
                            } else {
                                viewModel.runWithDisclaimerCheck {
                                    requestPermissionsAndDownload(quality)
                                }
                            }
                        },
                        color = omegaColors.card,
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(
                            1.dp,
                            if (fits) omegaColors.borderBright else omegaColors.danger.copy(alpha = 0.45f)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(quality.label, color = omegaColors.textPrimary, fontWeight = FontWeight.Bold)
                                if (quality.useDirectUrl) {
                                    Text(
                                        "Turbo Resumable",
                                        color = omegaColors.success,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (quality.size.isNotBlank() && quality.size != "Unknown") {
                                    Text(
                                        quality.size,
                                        color = if (fits) omegaColors.textSecondary else omegaColors.danger,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(Modifier.width(10.dp))
                                }
                                Icon(Icons.Default.Download, null, tint = omegaColors.primary)
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}
