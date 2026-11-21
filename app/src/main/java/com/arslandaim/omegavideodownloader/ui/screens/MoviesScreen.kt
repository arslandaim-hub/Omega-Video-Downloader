/*
 * Omega Video Downloader Project Original (2026)
 * arslandaim-hub (GitHub.com/arslandaim-hub)
 * Licenced Under GPL-3.0+
*/
package com.arslandaim.omegavideodownloader.ui.screens

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.ImageLoader
import com.arslandaim.omegavideodownloader.*
import com.arslandaim.omegavideodownloader.ui.MoviesViewModel
import com.arslandaim.omegavideodownloader.ui.components.*
import com.arslandaim.omegavideodownloader.ui.theme.OmegaTheme

@Composable
fun MoviesScreen(
    viewModel: MoviesViewModel,
    watchHistory: List<WatchHistoryItem>,
    likedUrls: Set<String>,
    resumingHistoryId: String?,
    imageLoader: ImageLoader,
    onMovieClick: (MovieSearchResult) -> Unit,
    onToggleLike: (MovieSearchResult) -> Unit,
    onResumeHistoryItem: (WatchHistoryItem) -> Unit
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val omegaColors = OmegaTheme.colors

    val displayedMovies = viewModel.displayedMovies
    val isViewingSearch = viewModel.hasExecutedSearch && viewModel.searchQuery.isNotBlank()
    val suggestionsEnabled = viewModel.suggestionsEnabled

    val continueWatchingMovies = remember(watchHistory) {
        watchHistory.filter { it.isStream || it.durationMs >= 20 * 60 * 1000L }.take(10)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(omegaColors.backgroundBrush)
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 185.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            // 1. Search Bar on top
            item {
                MovieSearchBar(
                    query = viewModel.searchQuery,
                    onQueryChange = { viewModel.searchQuery = it },
                    onSearchSubmit = {
                        keyboardController?.hide()
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        viewModel.submitSearch()
                    },
                    onClear = {
                        keyboardController?.hide()
                        viewModel.clearSearch()
                    },
                    isSearching = viewModel.isSearching
                )
            }

            // 2. Movie Search History chips (Pause toggle is in Settings)
            if (!isViewingSearch && !viewModel.isSearching && viewModel.movieSearchHistory.isNotEmpty()) {
                item {
                    SearchHistoryCard(
                        title = "Movie Search History",
                        history = viewModel.movieSearchHistory,
                        isPaused = viewModel.isMovieSearchHistoryPaused,
                        showPauseToggle = false,
                        onTogglePause = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            viewModel.togglePauseMovieSearchHistory()
                        },
                        onQueryClick = { query ->
                            keyboardController?.hide()
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            viewModel.searchFromHistory(query)
                        },
                        onRemoveQuery = { viewModel.removeMovieSearchHistoryItem(it) },
                        onClearAll = {
                            viewModel.clearMovieSearchHistory()
                            Toast.makeText(context, "Movie search history cleared", Toast.LENGTH_SHORT).show()
                        }
                    )
                }
            }

            // Live inline search indicator
            if (viewModel.isSearching) {
                item {
                    Surface(
                        color = omegaColors.card,
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, omegaColors.primary.copy(alpha = 0.35f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = omegaColors.primary
                                )
                                Text(
                                    text = "Searching movies for \"${viewModel.searchQuery.trim()}\"...",
                                    color = omegaColors.textPrimary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            LinearProgressIndicator(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(2.dp),
                                color = omegaColors.primary,
                                trackColor = omegaColors.border
                            )
                        }
                    }
                }
            }

            // Continue Watching strip
            if (!isViewingSearch && continueWatchingMovies.isNotEmpty()) {
                item {
                    CinemaSectionHeader(
                        title = "Continue Watching",
                        badge = "${continueWatchingMovies.size}",
                        accentColor = omegaColors.primary
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(items = continueWatchingMovies, key = { it.id }) { historyItem ->
                            MovieWideHistoryCard(
                                item = historyItem,
                                imageLoader = imageLoader,
                                isResuming = resumingHistoryId == historyItem.id,
                                onClick = { onResumeHistoryItem(historyItem) }
                            )
                        }
                    }
                }
            }

            // Movie Suggestions section (only when toggle is ON)
            if (!isViewingSearch && suggestionsEnabled) {
                if (viewModel.isLoadingCatalog && viewModel.prefetchedMovies.isEmpty()) {
                    item {
                        Surface(
                            color = omegaColors.card,
                            shape = RoundedCornerShape(20.dp),
                            border = BorderStroke(1.dp, omegaColors.border),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 48.dp, horizontal = 24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                CircularProgressIndicator(color = omegaColors.primary)
                                Spacer(modifier = Modifier.height(14.dp))
                                Text(
                                    "Loading Movie Suggestions...",
                                    color = omegaColors.textPrimary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    "Fetching full movies across open catalogs",
                                    color = omegaColors.textSecondary,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                }

                viewModel.catalogError?.let { err ->
                    if (viewModel.prefetchedMovies.isEmpty()) {
                        item {
                            Surface(
                                color = omegaColors.card,
                                shape = RoundedCornerShape(18.dp),
                                border = BorderStroke(1.dp, omegaColors.danger.copy(alpha = 0.4f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(18.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(err, color = omegaColors.textPrimary, fontSize = 13.sp)
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Button(
                                        onClick = { viewModel.loadCatalog(forceRefresh = true) },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = omegaColors.primary,
                                            contentColor = Color.White
                                        )
                                    ) {
                                        Icon(Icons.Default.Refresh, null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text("Retry", fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }

                if (viewModel.prefetchedMovies.isNotEmpty() && viewModel.searchQuery.isBlank()) {
                    if (viewModel.featuredSpotlight.isNotEmpty()) {
                        item {
                            MovieHeroCarousel(
                                movies = viewModel.featuredSpotlight,
                                likedUrls = likedUrls,
                                imageLoader = imageLoader,
                                onMovieClick = onMovieClick,
                                onToggleLike = { movie ->
                                    val added = movie.pageUrl.trim() !in likedUrls
                                    onToggleLike(movie)
                                    Toast.makeText(
                                        context,
                                        if (added) "Added to Watchlist" else "Removed from Watchlist",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            )
                        }
                    }

                    if (viewModel.newestReleases.isNotEmpty()) {
                        item {
                            CinemaShelfRow(
                                title = "Newest Releases",
                                badge = "NEW ON TOP",
                                accentColor = omegaColors.secondary,
                                movies = viewModel.newestReleases,
                                likedUrls = likedUrls,
                                imageLoader = imageLoader,
                                onMovieClick = onMovieClick,
                                onToggleLike = onToggleLike
                            )
                        }
                    }

                    if (viewModel.hollywoodCatalog.isNotEmpty()) {
                        item {
                            CinemaShelfRow(
                                title = "Hollywood Cinema",
                                badge = "${viewModel.hollywoodCatalog.size}",
                                accentColor = omegaColors.primary,
                                movies = viewModel.hollywoodCatalog,
                                likedUrls = likedUrls,
                                imageLoader = imageLoader,
                                onMovieClick = onMovieClick,
                                onToggleLike = onToggleLike
                            )
                        }
                    }

                    if (viewModel.bollywoodCatalog.isNotEmpty()) {
                        item {
                            CinemaShelfRow(
                                title = "Bollywood & Hindi Movies",
                                badge = "${viewModel.bollywoodCatalog.size}",
                                accentColor = omegaColors.secondary,
                                movies = viewModel.bollywoodCatalog,
                                likedUrls = likedUrls,
                                imageLoader = imageLoader,
                                onMovieClick = onMovieClick,
                                onToggleLike = onToggleLike
                            )
                        }
                    }
                }
            } else if (!isViewingSearch && !viewModel.isSearching) {
                // Clean state when Movie Suggestions is OFF (default)
                item {
                    Surface(
                        color = omegaColors.card,
                        shape = RoundedCornerShape(18.dp),
                        border = BorderStroke(1.dp, omegaColors.border),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 28.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.Movie,
                                contentDescription = null,
                                tint = omegaColors.primary.copy(alpha = 0.75f),
                                modifier = Modifier.size(34.dp)
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "Movie Suggestions are Off",
                                color = omegaColors.textPrimary,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = if (viewModel.searchQuery.isNotBlank()) {
                                    "Press Search on your keyboard to find \"${viewModel.searchQuery.trim()}\"."
                                } else {
                                    "Search for a movie above, or enable Movie Suggestions in Settings."
                                },
                                color = omegaColors.textSecondary,
                                fontSize = 12.sp
                            )
                            if (viewModel.searchQuery.isNotBlank()) {
                                Spacer(modifier = Modifier.height(12.dp))
                                Button(
                                    onClick = {
                                        keyboardController?.hide()
                                        viewModel.submitSearch()
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = omegaColors.primary,
                                        contentColor = Color.White
                                    )
                                ) {
                                    Icon(Icons.Default.Search, null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Search Movies", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }

            // Movie Grid (Search Results OR Suggested Movies when toggle is ON)
            if (displayedMovies.isNotEmpty()) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CinemaSectionHeader(
                            title = if (isViewingSearch) "Search Results" else "All Suggested Movies",
                            badge = "${displayedMovies.size}",
                            accentColor = omegaColors.primary
                        )
                        if (isViewingSearch) {
                            TextButton(onClick = { viewModel.clearSearch() }) {
                                Text("Clear", color = omegaColors.primary, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                val rows = displayedMovies.chunked(2)
                items(
                    items = rows,
                    key = { row -> row.joinToString("|") { "${it.source}:${it.id}" } }
                ) { pair ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        pair.forEach { movie ->
                            val isLiked = movie.pageUrl.trim() in likedUrls
                            MoviePosterCard(
                                movie = movie,
                                isLiked = isLiked,
                                imageLoader = imageLoader,
                                onClick = { onMovieClick(movie) },
                                onToggleLike = { onToggleLike(movie) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        if (pair.size == 1) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            } else if (isViewingSearch && !viewModel.isSearching) {
                item {
                    Surface(
                        color = omegaColors.card,
                        shape = RoundedCornerShape(18.dp),
                        border = BorderStroke(1.dp, omegaColors.border),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(Icons.Default.SearchOff, null, tint = omegaColors.textSecondary, modifier = Modifier.size(36.dp))
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = viewModel.searchError ?: "No movies found.",
                                color = omegaColors.textPrimary,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Button(
                                    onClick = { viewModel.submitSearch() },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = omegaColors.primary,
                                        contentColor = Color.White
                                    )
                                ) {
                                    Text("Retry Search")
                                }
                                OutlinedButton(onClick = { viewModel.clearSearch() }) {
                                    Text("Clear", color = omegaColors.primary)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CinemaSectionHeader(
    title: String,
    badge: String?,
    accentColor: Color
) {
    val omegaColors = OmegaTheme.colors
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .width(4.dp)
                .height(18.dp)
                .background(omegaColors.accentBrush, RoundedCornerShape(2.dp))
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = title,
            color = omegaColors.textPrimary,
            fontSize = 16.sp,
            fontWeight = FontWeight.ExtraBold
        )
        if (!badge.isNullOrBlank()) {
            Spacer(modifier = Modifier.width(8.dp))
            Surface(
                color = accentColor.copy(alpha = 0.16f),
                shape = RoundedCornerShape(6.dp)
            ) {
                Text(
                    text = badge,
                    color = if (omegaColors.isDark) accentColor else omegaColors.primary,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }
    }
}

@Composable
private fun CinemaShelfRow(
    title: String,
    badge: String?,
    accentColor: Color,
    movies: List<MovieSearchResult>,
    likedUrls: Set<String>,
    imageLoader: ImageLoader,
    onMovieClick: (MovieSearchResult) -> Unit,
    onToggleLike: (MovieSearchResult) -> Unit
) {
    Column {
        CinemaSectionHeader(title = title, badge = badge, accentColor = accentColor)
        Spacer(modifier = Modifier.height(10.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(items = movies, key = { "${it.source}:${it.id}" }) { movie ->
                MoviePosterCard(
                    movie = movie,
                    isLiked = movie.pageUrl.trim() in likedUrls,
                    imageLoader = imageLoader,
                    onClick = { onMovieClick(movie) },
                    onToggleLike = { onToggleLike(movie) },
                    modifier = Modifier.width(204.dp)
                )
            }
        }
    }
}
