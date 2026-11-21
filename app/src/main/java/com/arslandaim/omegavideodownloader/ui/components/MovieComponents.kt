/*
 * Omega Video Downloader Project Original (2026)
 * arslandaim-hub (GitHub.com/arslandaim-hub)
 * Licenced Under GPL-3.0+
*/
package com.arslandaim.omegavideodownloader.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.request.videoFrameMillis
import com.arslandaim.omegavideodownloader.*
import com.arslandaim.omegavideodownloader.ui.theme.OmegaTheme

@Composable
fun MovieSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearchSubmit: () -> Unit,
    onClear: () -> Unit,
    isSearching: Boolean,
    modifier: Modifier = Modifier
) {
    val omegaColors = OmegaTheme.colors
    Surface(
        color = omegaColors.card,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, omegaColors.borderBright),
        modifier = modifier.fillMaxWidth()
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            placeholder = {
                Text(
                    "Search movies by title...",
                    color = omegaColors.textSecondary.copy(alpha = 0.7f),
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            },
            leadingIcon = {
                Icon(
                    Icons.Default.Search,
                    contentDescription = "Search movies",
                    tint = omegaColors.primary
                )
            },
            trailingIcon = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isSearching) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .padding(end = 10.dp)
                                .size(18.dp),
                            strokeWidth = 2.dp,
                            color = omegaColors.primary
                        )
                    } else if (query.isNotEmpty()) {
                        IconButton(onClick = onClear) {
                            Icon(
                                Icons.Default.Clear,
                                contentDescription = "Clear",
                                tint = omegaColors.textSecondary
                            )
                        }
                        IconButton(onClick = onSearchSubmit) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = "Search",
                                tint = omegaColors.primary
                            )
                        }
                    }
                }
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onSearchSubmit() }),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                focusedBorderColor = Color.Transparent,
                unfocusedBorderColor = Color.Transparent,
                focusedTextColor = omegaColors.textPrimary,
                unfocusedTextColor = omegaColors.textPrimary,
                cursorColor = omegaColors.primary
            ),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
fun MovieHeroCarousel(
    movies: List<MovieSearchResult>,
    likedUrls: Set<String>,
    imageLoader: ImageLoader,
    onMovieClick: (MovieSearchResult) -> Unit,
    onToggleLike: (MovieSearchResult) -> Unit,
    modifier: Modifier = Modifier
) {
    if (movies.isEmpty()) return
    val omegaColors = OmegaTheme.colors
    val pagerState = rememberPagerState(pageCount = { movies.size })

    Column(modifier = modifier.fillMaxWidth()) {
        HorizontalPager(
            state = pagerState,
            pageSpacing = 12.dp,
            modifier = Modifier
                .fillMaxWidth()
                .height(216.dp)
        ) { page ->
            val movie = movies[page]
            val isLiked = movie.pageUrl.trim() in likedUrls
            Surface(
                onClick = { onMovieClick(movie) },
                shape = RoundedCornerShape(22.dp),
                color = omegaColors.card,
                border = BorderStroke(1.dp, omegaColors.borderBright),
                modifier = Modifier.fillMaxSize()
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    CinemaThumbnailImage(
                        thumbnailUrl = movie.thumbnailUrl,
                        contentDescription = movie.title,
                        imageLoader = imageLoader,
                        modifier = Modifier.fillMaxSize()
                    )
                    // Dark contrast scrim for poster overlay text
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color.Black.copy(alpha = 0.2f),
                                        Color(0xFF04050A).copy(alpha = 0.65f),
                                        Color(0xFF04050A).copy(alpha = 0.96f)
                                    )
                                )
                            )
                    )

                    // Top badges
                    Row(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Surface(
                                color = omegaColors.primary.copy(alpha = 0.9f),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = movie.industry.label.uppercase(),
                                    color = Color.White,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                            if (movie.releaseYear > 0) {
                                Surface(
                                    color = omegaColors.gold,
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(
                                        text = movie.releaseYear.toString(),
                                        color = Color.Black,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                }
                            }
                        }

                        IconButton(
                            onClick = { onToggleLike(movie) },
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.55f))
                        ) {
                            Icon(
                                imageVector = if (isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                contentDescription = "Watchlist",
                                tint = if (isLiked) omegaColors.watchlistRose else Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    // Bottom title + CTA
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Text(
                            text = movie.title,
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            lineHeight = 22.sp
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (movie.genres.isNotEmpty()) {
                                Text(
                                    text = movie.genres.take(2).joinToString(" • "),
                                    color = Color.White.copy(alpha = 0.78f),
                                    fontSize = 12.sp,
                                    maxLines = 1
                                )
                            }
                            if (movie.durationSec > 0) {
                                Text(
                                    text = "• ${formatRuntimeShort(movie.durationSec)}",
                                    color = omegaColors.secondary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(omegaColors.accentBrush)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.PlayArrow, null, tint = Color.White, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Watch & Download", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }

        if (movies.size > 1) {
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                repeat(movies.size) { idx ->
                    val selected = pagerState.currentPage == idx
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 3.dp)
                            .height(5.dp)
                            .width(if (selected) 18.dp else 5.dp)
                            .clip(CircleShape)
                            .background(
                                if (selected) omegaColors.primary
                                else omegaColors.textSecondary.copy(alpha = 0.35f)
                            )
                    )
                }
            }
        }
    }
}

@Composable
fun MoviePosterCard(
    movie: MovieSearchResult,
    isLiked: Boolean,
    imageLoader: ImageLoader,
    onClick: () -> Unit,
    onToggleLike: () -> Unit,
    modifier: Modifier = Modifier
) {
    val omegaColors = OmegaTheme.colors
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
    ) {
        Surface(
            color = omegaColors.card,
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, omegaColors.border),
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 10f)
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(omegaColors.cardElevated, omegaColors.backgroundTop)
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Movie,
                        contentDescription = null,
                        tint = omegaColors.primary.copy(alpha = 0.45f),
                        modifier = Modifier.size(36.dp)
                    )
                }

                CinemaThumbnailImage(
                    thumbnailUrl = movie.thumbnailUrl,
                    contentDescription = movie.title,
                    imageLoader = imageLoader,
                    modifier = Modifier.fillMaxSize()
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .align(Alignment.BottomCenter)
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, Color.Black.copy(alpha = 0.88f))
                            )
                        )
                )

                // Top Row: Year badge + Like button
                Row(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .fillMaxWidth()
                        .padding(7.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (movie.releaseYear > 0) {
                        Surface(
                            color = omegaColors.gold,
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = movie.releaseYear.toString(),
                                color = Color.Black,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.ExtraBold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    } else {
                        Spacer(Modifier.width(1.dp))
                    }

                    Box(
                        modifier = Modifier
                            .size(26.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.55f))
                            .clickable(onClick = onToggleLike),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = "Save to Watchlist",
                            tint = if (isLiked) omegaColors.watchlistRose else Color.White,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }

                // Bottom Row: Source tag + Runtime
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .padding(7.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val srcLabel = when (movie.source) {
                        SearchSource.ARCHIVE_ORG -> "Archive"
                        SearchSource.YOUTUBE -> "YouTube"
                        SearchSource.DAILYMOTION -> "DM"
                        SearchSource.PEERTUBE -> "Open"
                    }
                    Surface(
                        color = Color.Black.copy(alpha = 0.65f),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = srcLabel,
                            color = omegaColors.secondary,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                        )
                    }

                    if (movie.durationSec > 0) {
                        Text(
                            text = formatRuntimeShort(movie.durationSec),
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = movie.title,
            color = omegaColors.textPrimary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            lineHeight = 16.sp
        )

        val metaSub = listOfNotNull(
            movie.industry.label,
            movie.genres.firstOrNull(),
            formatCompactViews(movie.popularityScore)
        ).joinToString(" • ")

        if (metaSub.isNotEmpty()) {
            Text(
                text = metaSub,
                color = omegaColors.textSecondary,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun MovieWideHistoryCard(
    item: WatchHistoryItem,
    imageLoader: ImageLoader,
    isResuming: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val omegaColors = OmegaTheme.colors
    Surface(
        onClick = onClick,
        color = omegaColors.card,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, omegaColors.border),
        modifier = modifier.width(210.dp)
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(110.dp)
                    .background(omegaColors.cardElevated),
                contentAlignment = Alignment.Center
            ) {
                if (!item.thumbnailUrl.isNullOrBlank()) {
                    CinemaThumbnailImage(
                        thumbnailUrl = item.thumbnailUrl,
                        contentDescription = item.title,
                        imageLoader = imageLoader,
                        modifier = Modifier.fillMaxSize()
                    )
                } else if (!item.isStream) {
                    AsyncImage(
                        model = ImageRequest.Builder(androidx.compose.ui.platform.LocalContext.current)
                            .data(item.sourceUrl)
                            .videoFrameMillis(1000)
                            .build(),
                        imageLoader = imageLoader,
                        contentDescription = item.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        Icons.Default.Movie,
                        null,
                        tint = omegaColors.primary.copy(alpha = 0.55f),
                        modifier = Modifier.size(32.dp)
                    )
                }

                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.65f)),
                    contentAlignment = Alignment.Center
                ) {
                    if (isResuming) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = omegaColors.primary
                        )
                    } else {
                        Icon(Icons.Default.PlayArrow, "Resume", tint = Color.White, modifier = Modifier.size(22.dp))
                    }
                }

                LinearProgressIndicator(
                    progress = { item.progressFraction },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(3.dp),
                    color = omegaColors.primary,
                    trackColor = Color.White.copy(alpha = 0.2f)
                )
            }

            Column(modifier = Modifier.padding(10.dp)) {
                Text(
                    text = item.title,
                    color = omegaColors.textPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val remainingMs = (item.durationMs - item.positionMs).coerceAtLeast(0L)
                val sub = if (item.durationMs > 0L && !item.isCompleted) {
                    "${formatTime(remainingMs)} left"
                } else {
                    formatTime(item.positionMs)
                }
                Text(
                    text = sub,
                    color = omegaColors.textSecondary,
                    fontSize = 11.sp,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun CinemaThumbnailImage(
    thumbnailUrl: String?,
    contentDescription: String,
    imageLoader: ImageLoader,
    modifier: Modifier = Modifier
) {
    var currentUrl by remember(thumbnailUrl) { mutableStateOf(thumbnailUrl) }
    val url = currentUrl ?: return

    AsyncImage(
        model = url,
        imageLoader = imageLoader,
        contentDescription = contentDescription,
        contentScale = ContentScale.Crop,
        onError = {
            val next = nextThumbnailFallback(url)
            currentUrl = if (next != null && next != url) next else null
        },
        modifier = modifier
    )
}

private fun nextThumbnailFallback(url: String?): String? {
    if (url.isNullOrBlank()) return null
    return when {
        url.contains("/maxresdefault.jpg", ignoreCase = true) ->
            url.replace("/maxresdefault.jpg", "/hq720.jpg", ignoreCase = true)
        url.contains("/hq720.jpg", ignoreCase = true) ->
            url.replace("/hq720.jpg", "/hqdefault.jpg", ignoreCase = true)
        url.contains("/sddefault.jpg", ignoreCase = true) ->
            url.replace("/sddefault.jpg", "/hqdefault.jpg", ignoreCase = true)
        url.contains("/hqdefault.jpg", ignoreCase = true) ->
            url.replace("/hqdefault.jpg", "/mqdefault.jpg", ignoreCase = true)
        url.contains("dmcdn.net", ignoreCase = true) && url.contains("/x1080", ignoreCase = true) ->
            url.replace("/x1080", "/x720", ignoreCase = true)
        url.contains("dmcdn.net", ignoreCase = true) && url.contains("/x720", ignoreCase = true) ->
            url.replace("/x720", "/x480", ignoreCase = true)
        else -> null
    }
}

private fun formatCompactViews(views: Long): String? {
    if (views <= 0L) return null
    return when {
        views >= 1_000_000L -> {
            val m = views / 100_000L
            if (m % 10L == 0L) "${m / 10L}M views" else "${m / 10.0}M views"
        }
        views >= 1_000L -> "${views / 1_000L}K views"
        else -> "$views views"
    }
}

fun formatRuntimeShort(durationSec: Long): String {
    if (durationSec <= 0L) return ""
    val h = durationSec / 3600
    val m = (durationSec % 3600) / 60
    return if (h > 0) "${h}h ${m}m" else "${m}m"
}
