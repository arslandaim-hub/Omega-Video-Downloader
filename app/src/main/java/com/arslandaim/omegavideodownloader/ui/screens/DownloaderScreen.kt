/*
 * Omega Video Downloader Project Original (2026)
 * arslandaim-hub (GitHub.com/arslandaim-hub)
 * Licenced Under GPL-3.0+
*/
package com.arslandaim.omegavideodownloader.ui.screens

import android.Manifest
import android.app.Activity
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import coil.ImageLoader
import coil.compose.AsyncImage
import com.arslandaim.omegavideodownloader.*
import com.arslandaim.omegavideodownloader.ui.DownloaderMode
import com.arslandaim.omegavideodownloader.ui.HomeViewModel
import com.arslandaim.omegavideodownloader.ui.components.MovieSearchResultCard
import com.arslandaim.omegavideodownloader.ui.components.PlatformBadge
import com.arslandaim.omegavideodownloader.ui.components.QualitySelectionSection
import com.arslandaim.omegavideodownloader.ui.components.SearchHistoryCard
import com.arslandaim.omegavideodownloader.ui.theme.OmegaTheme

@Composable
fun DownloaderScreen(
    viewModel: HomeViewModel,
    likedUrls: Set<String>,
    imageLoader: ImageLoader,
    onToggleLikeResult: (MovieSearchResult) -> Unit,
    onToggleLikeMetadata: (VideoMetadata) -> Unit,
    onStreamClick: (String) -> Unit,
    onDownloadStarted: (String) -> Unit
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val omegaColors = OmegaTheme.colors

    var pendingDownloadQuality by remember { mutableStateOf<Pair<VideoQuality, VideoMetadata?>?>(null) }
    var pendingDownloadAll by remember { mutableStateOf<List<VideoMetadata>?>(null) }
    val availableStorageBytes = remember(viewModel.videoMetadata) {
        StorageUtils.getAvailableStorage(context)
    }

    val twitterLoginLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK || CookiesManager.hasTwitterAuthCookies(context)) {
            viewModel.fetchMetadata()
        }
    }

    fun executeDownload(quality: VideoQuality, targetMeta: VideoMetadata? = null) {
        viewModel.startDownload(quality, targetMeta)
        Toast.makeText(context, "Added to Download Queue", Toast.LENGTH_SHORT).show()
        onDownloadStarted(quality.type)
    }

    fun executeDownloadAll(items: List<VideoMetadata>) {
        viewModel.startDownloadAll(items)
        Toast.makeText(context, "Queued ${items.size} items", Toast.LENGTH_SHORT).show()
        onDownloadStarted("video")
    }

    val legacyStorageLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        val pending = pendingDownloadQuality
        val pendingAll = pendingDownloadAll
        pendingDownloadQuality = null
        pendingDownloadAll = null
        if (granted) {
            if (pending != null) executeDownload(pending.first, pending.second)
            else if (pendingAll != null) executeDownloadAll(pendingAll)
        } else {
            Toast.makeText(context, "Storage permission is required on Android 9 and older.", Toast.LENGTH_LONG).show()
        }
    }

    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ ->
        val pending = pendingDownloadQuality
        val pendingAll = pendingDownloadAll
        pendingDownloadQuality = null
        pendingDownloadAll = null
        if (pending != null) executeDownload(pending.first, pending.second)
        else if (pendingAll != null) executeDownloadAll(pendingAll)
    }

    fun requestPermissionsAndDownload(quality: VideoQuality, targetMeta: VideoMetadata? = null) {
        when {
            Build.VERSION.SDK_INT <= Build.VERSION_CODES.P -> {
                val hasWrite = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                ) == PackageManager.PERMISSION_GRANTED
                if (hasWrite) {
                    executeDownload(quality, targetMeta)
                } else {
                    pendingDownloadQuality = quality to targetMeta
                    legacyStorageLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                }
            }
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> {
                val hasNotif = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
                if (hasNotif) {
                    executeDownload(quality, targetMeta)
                } else {
                    pendingDownloadQuality = quality to targetMeta
                    notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
            else -> executeDownload(quality, targetMeta)
        }
    }

    fun requestPermissionsAndDownloadAll(items: List<VideoMetadata>) {
        when {
            Build.VERSION.SDK_INT <= Build.VERSION_CODES.P -> {
                val hasWrite = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                ) == PackageManager.PERMISSION_GRANTED
                if (hasWrite) executeDownloadAll(items)
                else {
                    pendingDownloadAll = items
                    legacyStorageLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                }
            }
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> {
                val hasNotif = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
                if (hasNotif) executeDownloadAll(items)
                else {
                    pendingDownloadAll = items
                    notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
            else -> executeDownloadAll(items)
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
                TextButton(onClick = { viewModel.dismissDisclaimer() }) {
                    Text("Cancel")
                }
            }
        )
    }

    val isBusy = viewModel.isLoading || viewModel.isSearching
    val isLink = viewModel.isLinkInput()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Mode Switcher: Paste Link vs Search YT Videos
        Surface(
            color = omegaColors.card,
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(0.8.dp, omegaColors.border),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                DownloaderMode.entries.forEach { mode ->
                    val active = viewModel.downloaderMode == mode
                    Surface(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            viewModel.selectMode(mode)
                        },
                        color = if (active) omegaColors.primary else Color.Transparent,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(40.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxSize(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = if (mode == DownloaderMode.PASTE_LINK) Icons.Default.Link else Icons.Default.VideoLibrary,
                                contentDescription = null,
                                tint = if (active) Color.White else omegaColors.textSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = mode.label,
                                fontSize = 13.sp,
                                fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                                color = if (active) Color.White else omegaColors.textSecondary
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Smart Paste / Link Extractor Card
        Surface(
            color = omegaColors.card,
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(0.8.dp, omegaColors.borderBright, RoundedCornerShape(24.dp))
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (viewModel.downloaderMode == DownloaderMode.PASTE_LINK) {
                            "Paste Any Video or Music Link"
                        } else {
                            "Search YouTube Videos & Music"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = omegaColors.textPrimary
                    )

                    Surface(
                        color = omegaColors.success.copy(alpha = 0.14f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = "${StorageUtils.formatSize(availableStorageBytes)} free",
                            color = omegaColors.success,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }

                OutlinedTextField(
                    value = viewModel.url,
                    onValueChange = {
                        viewModel.url = it
                        if (viewModel.errorMessage != null) viewModel.errorMessage = null
                    },
                    placeholder = {
                        Text(
                            text = if (viewModel.downloaderMode == DownloaderMode.PASTE_LINK) {
                                "https://youtube.com/... instagram, tiktok, mp4..."
                            } else {
                                "Search songs, tutorials, vlogs, podcasts..."
                            },
                            color = omegaColors.textSecondary.copy(alpha = 0.65f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        imeAction = if (isLink) ImeAction.Go else ImeAction.Search
                    ),
                    keyboardActions = KeyboardActions(
                        onGo = {
                            if (viewModel.url.isNotBlank() && !isBusy) {
                                keyboardController?.hide()
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                viewModel.submitInput()
                            }
                        },
                        onSearch = {
                            if (viewModel.url.isNotBlank() && !isBusy) {
                                keyboardController?.hide()
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                viewModel.submitInput()
                            }
                        }
                    ),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = omegaColors.cardElevated,
                        unfocusedContainerColor = omegaColors.cardElevated.copy(alpha = 0.7f),
                        unfocusedBorderColor = omegaColors.border,
                        focusedBorderColor = omegaColors.primary.copy(alpha = 0.7f),
                        focusedTextColor = omegaColors.textPrimary,
                        unfocusedTextColor = omegaColors.textPrimary,
                        cursorColor = omegaColors.primary
                    ),
                    trailingIcon = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (viewModel.url.isEmpty()) {
                                IconButton(
                                    onClick = {
                                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                        val clip = cm?.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString()?.trim()
                                        if (!clip.isNullOrBlank()) {
                                            val extracted = UrlUtils.extractUrl(clip) ?: clip
                                            viewModel.url = extracted
                                            if (UrlUtils.extractUrl(extracted) != null) {
                                                viewModel.submitInput()
                                            }
                                        } else {
                                            Toast.makeText(context, "Clipboard is empty", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                ) {
                                    Icon(
                                        Icons.Default.ContentPaste,
                                        contentDescription = "1-Tap Paste",
                                        tint = omegaColors.primary
                                    )
                                }
                            } else {
                                IconButton(onClick = { viewModel.clearUrl() }) {
                                    Icon(
                                        Icons.Default.Clear,
                                        contentDescription = "Clear",
                                        tint = omegaColors.textSecondary
                                    )
                                }
                            }
                        }
                    }
                )

                Spacer(modifier = Modifier.height(12.dp))

                viewModel.videoMetadata?.let { metadata ->
                    if (metadata.isPlaylist) {
                        Surface(
                            color = omegaColors.secondary.copy(alpha = 0.14f),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 12.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.AutoMirrored.Filled.PlaylistPlay, null, tint = omegaColors.primary)
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    "Playlist detected (${metadata.playlistCount ?: "multiple"} items). Saved separately (Max 720p).",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = omegaColors.textPrimary,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }

                // Primary Extract / Search Button + In-App Browser Sniffer button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = {
                            if (viewModel.url.isNotBlank() && !isBusy) {
                                keyboardController?.hide()
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                viewModel.submitInput()
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = omegaColors.primary,
                            contentColor = Color.White,
                            disabledContainerColor = omegaColors.primary.copy(alpha = 0.35f)
                        ),
                        shape = RoundedCornerShape(16.dp),
                        enabled = viewModel.url.isNotBlank() && !isBusy
                    ) {
                        if (isBusy) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = viewModel.resolveStatus ?: "Working...",
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        } else {
                            Icon(
                                imageVector = if (isLink || viewModel.downloaderMode == DownloaderMode.PASTE_LINK) {
                                    Icons.Default.Download
                                } else {
                                    Icons.Default.Search
                                },
                                contentDescription = null
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (isLink || viewModel.downloaderMode == DownloaderMode.PASTE_LINK) {
                                    "Extract Qualities"
                                } else {
                                    "Search YT"
                                },
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    OutlinedIconButton(
                        onClick = {
                            keyboardController?.hide()
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            viewModel.openManualBrowser()
                        },
                        modifier = Modifier.size(52.dp),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, omegaColors.primary.copy(alpha = 0.45f))
                    ) {
                        Icon(
                            Icons.Default.Language,
                            contentDescription = "In-App Browser Sniffer",
                            tint = omegaColors.primary
                        )
                    }
                }

                // Controls while resolving or searching
                AnimatedVisibility(visible = isBusy) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (viewModel.snifferUrl != null && !viewModel.forceSnifferVisible) {
                            TextButton(onClick = { viewModel.openManualBrowser() }) {
                                Icon(Icons.Default.Language, null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Show Browser", fontWeight = FontWeight.SemiBold)
                            }
                        } else {
                            Spacer(Modifier.width(1.dp))
                        }
                        TextButton(onClick = { viewModel.cancelResolve() }) {
                            Text("Cancel", color = omegaColors.danger)
                        }
                    }
                }
            }
        }

        // Error Card with Manual Browser Fallback
        AnimatedVisibility(
            visible = viewModel.errorMessage != null && !isBusy,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            viewModel.errorMessage?.let { err ->
                Surface(
                    color = omegaColors.danger.copy(alpha = 0.09f),
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 14.dp)
                        .border(0.8.dp, omegaColors.danger.copy(alpha = 0.3f), RoundedCornerShape(20.dp))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.ErrorOutline, null, tint = omegaColors.danger)
                            Spacer(Modifier.width(10.dp))
                            Text(
                                text = err,
                                style = MaterialTheme.typography.bodySmall,
                                color = omegaColors.textPrimary
                            )
                        }
                        val extractedLink = UrlUtils.extractUrl(viewModel.url)
                        if (extractedLink != null) {
                            Spacer(Modifier.height(10.dp))
                            if (UrlUtils.isTwitterOrX(extractedLink)) {
                                Button(
                                    onClick = {
                                        val loginIntent = Intent(context, BrowserLoginActivity::class.java).apply {
                                            putExtra("url", "https://x.com/i/flow/login")
                                            putExtra("platform", "X / Twitter")
                                        }
                                        twitterLoginLauncher.launch(loginIntent)
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = omegaColors.primary,
                                        contentColor = Color.White
                                    )
                                ) {
                                    Icon(Icons.AutoMirrored.Filled.Login, null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text("Sign in to X / Twitter & Retry", fontWeight = FontWeight.Bold)
                                }
                                Spacer(Modifier.height(8.dp))
                            }
                            OutlinedButton(
                                onClick = { viewModel.openManualBrowser() },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.Language, null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Scan in In-App Browser", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        // Video Search History (with dedicated Pause & Clear controls)
        if (viewModel.downloaderMode == DownloaderMode.SEARCH_VIDEOS &&
            viewModel.videoMetadata == null &&
            viewModel.searchResults.isEmpty() &&
            !isBusy
        ) {
            Spacer(modifier = Modifier.height(16.dp))
            SearchHistoryCard(
                title = "Video Search History",
                history = viewModel.videoSearchHistory,
                isPaused = viewModel.isVideoSearchHistoryPaused,
                onTogglePause = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    viewModel.togglePauseVideoSearchHistory()
                },
                onQueryClick = { query ->
                    keyboardController?.hide()
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    viewModel.searchFromHistory(query)
                },
                onRemoveQuery = { viewModel.removeVideoSearchHistoryItem(it) },
                onClearAll = {
                    viewModel.clearVideoSearchHistory()
                    Toast.makeText(context, "Video search history cleared", Toast.LENGTH_SHORT).show()
                }
            )
        }

        // Recent Extractions Strip (with Clear All button)
        if (viewModel.recentExtractions.isNotEmpty() && viewModel.videoMetadata == null && viewModel.searchResults.isEmpty()) {
            Spacer(modifier = Modifier.height(18.dp))
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 4.dp, bottom = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Recent Extractions (${viewModel.recentExtractions.size})",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = omegaColors.textPrimary
                    )
                    TextButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            viewModel.clearRecentExtractions()
                            Toast.makeText(context, "Recent extractions cleared", Toast.LENGTH_SHORT).show()
                        },
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteSweep,
                            contentDescription = "Clear recent extractions",
                            tint = omegaColors.danger,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Clear",
                            color = omegaColors.danger,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(items = viewModel.recentExtractions, key = { it.sourceUrl }) { recent ->
                        Surface(
                            onClick = { viewModel.openRecentExtraction(recent) },
                            color = omegaColors.card,
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(0.8.dp, omegaColors.border),
                            modifier = Modifier.width(210.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(56.dp, 42.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(omegaColors.cardElevated),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (!recent.thumbnailUrl.isNullOrBlank()) {
                                        AsyncImage(
                                            model = recent.thumbnailUrl,
                                            imageLoader = imageLoader,
                                            contentDescription = recent.title,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    } else {
                                        Icon(Icons.Default.VideoFile, null, tint = omegaColors.primary, modifier = Modifier.size(20.dp))
                                    }
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = recent.title,
                                        color = omegaColors.textPrimary,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                        lineHeight = 15.sp
                                    )
                                    Text(
                                        text = "${recent.qualities.size} video • ${recent.audioQualities.size} audio",
                                        color = omegaColors.textSecondary,
                                        fontSize = 10.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Platform Badges when completely idle
        AnimatedVisibility(
            visible = viewModel.searchResults.isEmpty() && viewModel.videoMetadata == null
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Spacer(modifier = Modifier.height(18.dp))
                Text(
                    "Supported Platforms & Direct Streams",
                    style = MaterialTheme.typography.labelMedium,
                    color = omegaColors.textSecondary,
                    modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("YouTube", "X / Twitter", "Insta", "TikTok").forEach { name ->
                        Box(modifier = Modifier.weight(1f)) {
                            PlatformBadge(name)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Facebook", "Vimeo", "MP4 / MKV", "HLS / m3u8").forEach { name ->
                        Box(modifier = Modifier.weight(1f)) {
                            PlatformBadge(name)
                        }
                    }
                }
            }
        }

        // Search Results List (YouTube Videos / Music)
        AnimatedVisibility(
            visible = viewModel.searchResults.isNotEmpty() && viewModel.videoMetadata == null && !isBusy,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "YouTube Results (${viewModel.searchResults.size})",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = omegaColors.textSecondary,
                    modifier = Modifier.padding(start = 4.dp, bottom = 2.dp)
                )
                viewModel.searchResults.forEach { result ->
                    val isLiked = result.pageUrl.trim() in likedUrls
                    MovieSearchResultCard(
                        result = result,
                        isLiked = isLiked,
                        onLikeClick = {
                            onToggleLikeResult(result)
                            Toast.makeText(
                                context,
                                if (isLiked) "Removed from Watchlist" else "Saved to Watchlist",
                                Toast.LENGTH_SHORT
                            ).show()
                        },
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            viewModel.selectSearchResult(result)
                        }
                    )
                }
            }
        }

        // Quality & Streaming Selection Section
        AnimatedVisibility(
            visible = (viewModel.videoMetadata != null) && !isBusy,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 18.dp)
            ) {
                if (viewModel.searchResults.isNotEmpty() && viewModel.selectedSearchResult != null) {
                    TextButton(
                        onClick = { viewModel.backToSearchResults() },
                        modifier = Modifier.padding(bottom = 6.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Back to Search Results", fontWeight = FontWeight.SemiBold)
                    }
                }

                viewModel.videoMetadata?.let { metadata ->
                    val metaKey = metadata.sourceUrl.trim()
                    val isMetaLiked = metaKey.isNotEmpty() && metaKey in likedUrls
                    QualitySelectionSection(
                        metadata = metadata,
                        isLiked = isMetaLiked,
                        onLikeClick = if (metaKey.isNotEmpty()) {
                            {
                                val selected = viewModel.selectedSearchResult
                                if (selected != null && selected.pageUrl.trim() == metaKey) {
                                    onToggleLikeResult(selected)
                                } else {
                                    onToggleLikeMetadata(metadata)
                                }
                                Toast.makeText(
                                    context,
                                    if (isMetaLiked) "Removed from Watchlist" else "Saved to Watchlist",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        } else null,
                        onStreamClick = { candidate ->
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            viewModel.runWithDisclaimerCheck {
                                val sessionKey = viewModel.prepareStreamSession(candidate)
                                if (sessionKey != null) {
                                    onStreamClick(sessionKey)
                                } else {
                                    Toast.makeText(context, "No streamable source available for this item.", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        onStreamItemClick = { candidate, itemMeta ->
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            viewModel.runWithDisclaimerCheck {
                                val sessionKey = viewModel.prepareStreamSession(candidate, itemMeta)
                                if (sessionKey != null) {
                                    onStreamClick(sessionKey)
                                } else {
                                    Toast.makeText(context, "No streamable source available for this item.", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        onQualitySelected = { quality ->
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            viewModel.runWithDisclaimerCheck {
                                if (quality.sizeBytes > 0L && !StorageUtils.hasEnoughSpace(context, quality.sizeBytes)) {
                                    Toast.makeText(context, "Not enough free device storage", Toast.LENGTH_SHORT).show()
                                } else {
                                    requestPermissionsAndDownload(quality)
                                }
                            }
                        },
                        onQualityItemSelected = { quality, itemMeta ->
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            viewModel.runWithDisclaimerCheck {
                                if (quality.sizeBytes > 0L && !StorageUtils.hasEnoughSpace(context, quality.sizeBytes)) {
                                    Toast.makeText(context, "Not enough free device storage", Toast.LENGTH_SHORT).show()
                                } else {
                                    requestPermissionsAndDownload(quality, itemMeta)
                                }
                            }
                        },
                        onDownloadAll = { items ->
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            viewModel.runWithDisclaimerCheck {
                                requestPermissionsAndDownloadAll(items)
                            }
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(175.dp))
    }
}
