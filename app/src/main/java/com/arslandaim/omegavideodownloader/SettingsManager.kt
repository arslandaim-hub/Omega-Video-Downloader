/*
 * Omega Video Downloader Project Original (2026)
 * arslandaim-hub (GitHub.com/arslandaim-hub)
 * Licenced Under GPL-3.0+
*/
package com.arslandaim.omegavideodownloader

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.core.net.toUri
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.withContext
import java.security.MessageDigest

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsManager(context: Context) {

    private val context = context.applicationContext
    private val gson = Gson()
    private val listType = object : TypeToken<List<DownloadedVideo>>() {}.type
    private val watchHistoryType = object : TypeToken<List<WatchHistoryItem>>() {}.type
    private val likedVideosType = object : TypeToken<List<LikedVideo>>() {}.type
    private val pausedDownloadsType = object : TypeToken<List<ResumableDownloadTask>>() {}.type
    private val stringListType = object : TypeToken<List<String>>() {}.type

    val currentlyPlayingPath: StateFlow<String?> = _currentlyPlayingPath

    companion object {
        private val _dmActiveDownloads = MutableStateFlow<Map<Long, ActiveDownload>>(emptyMap())
        private val _localActiveDownloads = MutableStateFlow<Map<Long, ActiveDownload>>(emptyMap())
        private val _currentlyPlayingPath = MutableStateFlow<String?>(null)

        private val sharedScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

        private const val MAX_PUBLIC_HISTORY = 200
        private const val MAX_WATCH_HISTORY = 100
        const val DEFAULT_STREAM_CACHE_MB = 512
        const val DEFAULT_MAX_CONCURRENT_DOWNLOADS = 2

        private val THEME_KEY = stringPreferencesKey("theme_mode")
        private val THEME_COLOR_KEY = stringPreferencesKey("theme_color_preset")
        private val LANGUAGE_KEY = stringPreferencesKey("app_language")
        private val AUTO_UPDATE_YTDLP_KEY = booleanPreferencesKey("auto_update_ytdlp")
        private val LAST_YTDLP_UPDATE_KEY = longPreferencesKey("last_ytdlp_update")
        private val DOWNLOADS_KEY = stringPreferencesKey("downloaded_videos")
        private val WATCH_HISTORY_KEY = stringPreferencesKey("watch_history")
        private val LIKED_VIDEOS_KEY = stringPreferencesKey("liked_videos")
        private val PAUSED_DOWNLOADS_KEY = stringPreferencesKey("paused_downloads")
        private val LOCKER_PIN_KEY = stringPreferencesKey("locker_pin")
        private val SECURITY_QUESTION_KEY = stringPreferencesKey("security_question")
        private val SECURITY_ANSWER_KEY = stringPreferencesKey("security_answer")
        private val USE_BIOMETRIC_KEY = booleanPreferencesKey("use_biometric")
        private val STREAM_CACHE_MB_KEY = intPreferencesKey("stream_cache_mb")
        private val MAX_CONCURRENT_DOWNLOADS_KEY = intPreferencesKey("max_concurrent_downloads")
        private val BROWSER_FALLBACK_KEY = booleanPreferencesKey("browser_fallback")
        private val DISCLAIMER_ACCEPTED_KEY = booleanPreferencesKey("stream_disclaimer_accepted")
        private val LAST_ACTIVE_TAB_KEY = intPreferencesKey("last_active_tab")
        private val WIFI_ONLY_DOWNLOADS_KEY = booleanPreferencesKey("wifi_only_downloads")
        private val MOVIE_SUGGESTIONS_ENABLED_KEY = booleanPreferencesKey("movie_suggestions_enabled")
        private val VIDEO_SEARCH_HISTORY_KEY = stringPreferencesKey("video_search_history")
        private val MOVIE_SEARCH_HISTORY_KEY = stringPreferencesKey("movie_search_history")
        private val PAUSE_VIDEO_SEARCH_HISTORY_KEY = booleanPreferencesKey("pause_video_search_history")
        private val PAUSE_MOVIE_SEARCH_HISTORY_KEY = booleanPreferencesKey("pause_movie_search_history")
        private val PAUSE_CONTINUE_WATCHING_KEY = booleanPreferencesKey("pause_continue_watching")
        private const val MAX_SEARCH_HISTORY = 20

        /** Maps a language setting to a BCP-47 locale tag. */
        fun localeTagFor(language: String): String = when (language) {
            "English (UK)" -> "en-GB"
            "Arabic" -> "ar"
            else -> "en-US"
        }
    }

    private fun hashString(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(input.lowercase().trim().toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }

    private fun parse(json: String?): List<DownloadedVideo> = try {
        gson.fromJson<List<DownloadedVideo>>(json ?: "[]", listType) ?: emptyList()
    } catch (_: Exception) {
        emptyList()
    }

    private fun parseWatchHistory(json: String?): List<WatchHistoryItem> = try {
        gson.fromJson<List<WatchHistoryItem>>(json ?: "[]", watchHistoryType) ?: emptyList()
    } catch (_: Exception) {
        emptyList()
    }

    private fun parseLikedVideos(json: String?): List<LikedVideo> = try {
        gson.fromJson<List<LikedVideo>>(json ?: "[]", likedVideosType) ?: emptyList()
    } catch (_: Exception) {
        emptyList()
    }

    private fun parsePausedDownloads(json: String?): List<ResumableDownloadTask> = try {
        gson.fromJson<List<ResumableDownloadTask>>(json ?: "[]", pausedDownloadsType) ?: emptyList()
    } catch (_: Exception) {
        emptyList()
    }

    private fun parseStringList(json: String?): List<String> = try {
        gson.fromJson<List<String>>(json ?: "[]", stringListType) ?: emptyList()
    } catch (_: Exception) {
        emptyList()
    }

    // ---------------- Locker ----------------

    val isLockerSet: Flow<Boolean> = context.dataStore.data.map { it[LOCKER_PIN_KEY] != null }.distinctUntilChanged()
    val useBiometric: Flow<Boolean> = context.dataStore.data.map { it[USE_BIOMETRIC_KEY] ?: false }.distinctUntilChanged()
    val securityQuestion: Flow<String?> = context.dataStore.data.map { it[SECURITY_QUESTION_KEY] }.distinctUntilChanged()

    suspend fun setLockerPin(pin: String, question: String, answer: String) = withContext(Dispatchers.IO) {
        context.dataStore.edit { preferences ->
            preferences[LOCKER_PIN_KEY] = hashString(pin)
            preferences[SECURITY_QUESTION_KEY] = question
            preferences[SECURITY_ANSWER_KEY] = hashString(answer)
        }
    }

    // Read-only checks (previously used edit{} which performed a disk write transaction)
    suspend fun verifyLockerPin(pin: String): Boolean = withContext(Dispatchers.IO) {
        context.dataStore.data.first()[LOCKER_PIN_KEY] == hashString(pin)
    }

    suspend fun verifySecurityAnswer(answer: String): Boolean = withContext(Dispatchers.IO) {
        context.dataStore.data.first()[SECURITY_ANSWER_KEY] == hashString(answer)
    }

    suspend fun updateLockerPin(newPin: String) = withContext(Dispatchers.IO) {
        context.dataStore.edit { it[LOCKER_PIN_KEY] = hashString(newPin) }
    }

    suspend fun setUseBiometric(enabled: Boolean) = withContext(Dispatchers.IO) {
        context.dataStore.edit { it[USE_BIOMETRIC_KEY] = enabled }
    }

    suspend fun updateVideoLockStatus(video: DownloadedVideo, isLocked: Boolean, newPath: String) = withContext(Dispatchers.IO) {
        context.dataStore.edit { preferences ->
            val list = parse(preferences[DOWNLOADS_KEY]).toMutableList()
            val index = list.indexOfFirst { it.timestamp == video.timestamp && it.localPath == video.localPath }
            if (index != -1) {
                list[index] = video.copy(isLocked = isLocked, localPath = newPath)
                preferences[DOWNLOADS_KEY] = gson.toJson(list)
            }
        }
    }

    // ---------------- History ----------------

    private fun checkFileExists(uriString: String): Boolean {
        if (uriString.isEmpty()) return false
        return try {
            val uri = uriString.toUri()
            when (uri.scheme) {
                "content" -> try {
                    context.contentResolver.query(uri, arrayOf(android.provider.BaseColumns._ID), null, null, null)
                        ?.use { it.count > 0 } ?: false
                } catch (_: Exception) {
                    false
                }
                "file" -> uri.path?.let { java.io.File(it).exists() } ?: false
                else -> java.io.File(uriString).exists()
            }
        } catch (_: Exception) {
            false
        }
    }

    /** Single parse of the history JSON shared by every derived flow. */
    val allVideos: Flow<List<DownloadedVideo>> = context.dataStore.data
        .map { it[DOWNLOADS_KEY] }
        .distinctUntilChanged()
        .map { parse(it) }
        .flowOn(Dispatchers.Default)

    /**
     * Keeps items whose file exists. Playlist groups have no file of their own: they are kept
     * while at least one of their children is visible (previously they were always dropped,
     * which hid every downloaded playlist).
     */
    private fun visible(list: List<DownloadedVideo>, locked: Boolean): List<DownloadedVideo> {
        val items = list.filter { it.isLocked == locked && !it.isPlaylistGroup && checkFileExists(it.localPath) }
        val livePlaylists = items.mapNotNullTo(HashSet()) { it.playlistId }
        val now = System.currentTimeMillis()
        return list.filter { v ->
            if (v.isLocked != locked) return@filter false
            if (v.isPlaylistGroup) v.playlistId in livePlaylists || (now - v.timestamp) < 6 * 60 * 60 * 1000L
            else items.contains(v)
        }
    }

    val downloadedVideos: Flow<List<DownloadedVideo>> = allVideos.map { visible(it, locked = false) }.flowOn(Dispatchers.IO)
    val lockerVideos: Flow<List<DownloadedVideo>> = allVideos.map { visible(it, locked = true) }.flowOn(Dispatchers.IO)

    suspend fun addDownloadedVideo(video: DownloadedVideo) = withContext(Dispatchers.IO) {
        context.dataStore.edit { preferences ->
            val list = parse(preferences[DOWNLOADS_KEY]).toMutableList()
            list.add(0, video)
            // Never drop locked entries (their files live only in private storage)
            val locked = list.filter { it.isLocked }
            val public = list.filter { !it.isLocked }.take(MAX_PUBLIC_HISTORY)
            val keep = (public + locked).toHashSet()
            preferences[DOWNLOADS_KEY] = gson.toJson(list.filter { it in keep })
        }
    }

    suspend fun removeDownloadedVideo(video: DownloadedVideo) = withContext(Dispatchers.IO) {
        context.dataStore.edit { preferences ->
            val list = parse(preferences[DOWNLOADS_KEY]).toMutableList()
            list.removeAll { it.timestamp == video.timestamp && it.localPath == video.localPath }
            preferences[DOWNLOADS_KEY] = gson.toJson(list)
        }
    }

    suspend fun syncHistoryWithStorage() = withContext(Dispatchers.IO) {
        context.dataStore.edit { preferences ->
            val list = parse(preferences[DOWNLOADS_KEY])
            val now = System.currentTimeMillis()
            val fresh = { v: DownloadedVideo -> (now - v.timestamp) < 300_000 }
            val validItems = list.filter { !it.isPlaylistGroup && (fresh(it) || checkFileExists(it.localPath)) }
            val livePlaylists = validItems.mapNotNullTo(HashSet()) { it.playlistId }
            val valid = list.filter { v ->
                if (v.isPlaylistGroup) fresh(v) || v.playlistId in livePlaylists else validItems.contains(v)
            }
            if (valid.size != list.size) preferences[DOWNLOADS_KEY] = gson.toJson(valid)
        }
    }

    suspend fun clearPublicHistory() = withContext(Dispatchers.IO) {
        context.dataStore.edit { preferences ->
            val list = parse(preferences[DOWNLOADS_KEY])
            preferences[DOWNLOADS_KEY] = gson.toJson(list.filter { it.isLocked })
        }
    }

    suspend fun clearHistory() = withContext(Dispatchers.IO) {
        context.dataStore.edit { it[DOWNLOADS_KEY] = "[]" }
    }

    // ---------------- Watch History ----------------

    fun doesMediaFileExist(uriString: String): Boolean = checkFileExists(uriString)

    val watchHistory: Flow<List<WatchHistoryItem>> = context.dataStore.data
        .map { it[WATCH_HISTORY_KEY] }
        .distinctUntilChanged()
        .map { json -> parseWatchHistory(json).sortedByDescending { it.lastWatchedAt } }
        .flowOn(Dispatchers.Default)

    suspend fun getWatchHistoryItem(id: String): WatchHistoryItem? = withContext(Dispatchers.IO) {
        val raw = context.dataStore.data.first()[WATCH_HISTORY_KEY]
        parseWatchHistory(raw).firstOrNull { it.id == id }
    }

    suspend fun getResumePosition(id: String): Long = withContext(Dispatchers.IO) {
        getWatchHistoryItem(id)?.resumePositionMs ?: 0L
    }

    val pauseContinueWatching: Flow<Boolean> = context.dataStore.data
        .map { it[PAUSE_CONTINUE_WATCHING_KEY] ?: false }
        .distinctUntilChanged()

    suspend fun setPauseContinueWatching(paused: Boolean) = withContext(Dispatchers.IO) {
        context.dataStore.edit { it[PAUSE_CONTINUE_WATCHING_KEY] = paused }
    }

    suspend fun upsertWatchHistory(item: WatchHistoryItem) = withContext(Dispatchers.IO) {
        context.dataStore.edit { preferences ->
            if (preferences[PAUSE_CONTINUE_WATCHING_KEY] == true) return@edit
            val list = parseWatchHistory(preferences[WATCH_HISTORY_KEY]).toMutableList()
            val existingIdx = list.indexOfFirst { it.id == item.id }
            val merged = if (existingIdx != -1) {
                val prev = list[existingIdx]
                item.copy(
                    title = item.title.ifBlank { prev.title },
                    thumbnailUrl = item.thumbnailUrl ?: prev.thumbnailUrl,
                    lastStreamUrl = item.lastStreamUrl ?: prev.lastStreamUrl,
                    qualityLabel = item.qualityLabel ?: prev.qualityLabel,
                    durationMs = if (item.durationMs > 0L) item.durationMs else prev.durationMs
                ).also { list.removeAt(existingIdx) }
            } else {
                item
            }
            list.add(0, merged)
            preferences[WATCH_HISTORY_KEY] = gson.toJson(list.take(MAX_WATCH_HISTORY))
        }
    }

    suspend fun removeWatchHistoryItem(id: String) = withContext(Dispatchers.IO) {
        context.dataStore.edit { preferences ->
            val list = parseWatchHistory(preferences[WATCH_HISTORY_KEY]).filterNot { it.id == id }
            preferences[WATCH_HISTORY_KEY] = gson.toJson(list)
        }
    }

    suspend fun clearWatchHistory() = withContext(Dispatchers.IO) {
        context.dataStore.edit { it[WATCH_HISTORY_KEY] = "[]" }
    }

    // ---------------- Liked Videos ----------------

    val likedVideos: Flow<List<LikedVideo>> = context.dataStore.data
        .map { it[LIKED_VIDEOS_KEY] }
        .distinctUntilChanged()
        .map { json -> parseLikedVideos(json).sortedByDescending { it.likedAt } }
        .flowOn(Dispatchers.Default)

    /** Toggles liked status for [video]. Returns true if added, false if removed. */
    suspend fun toggleLikedVideo(video: LikedVideo): Boolean = withContext(Dispatchers.IO) {
        var added = false
        val targetUrl = video.pageUrl.trim()
        context.dataStore.edit { preferences ->
            val list = parseLikedVideos(preferences[LIKED_VIDEOS_KEY]).toMutableList()
            val existingIdx = list.indexOfFirst { it.pageUrl.trim() == targetUrl }
            if (existingIdx != -1) {
                list.removeAt(existingIdx)
                added = false
            } else {
                list.add(0, video.copy(pageUrl = targetUrl, likedAt = System.currentTimeMillis()))
                added = true
            }
            preferences[LIKED_VIDEOS_KEY] = gson.toJson(list)
        }
        added
    }

    suspend fun removeLikedVideo(pageUrl: String) = withContext(Dispatchers.IO) {
        val targetUrl = pageUrl.trim()
        context.dataStore.edit { preferences ->
            val list = parseLikedVideos(preferences[LIKED_VIDEOS_KEY])
                .filterNot { it.pageUrl.trim() == targetUrl }
            preferences[LIKED_VIDEOS_KEY] = gson.toJson(list)
        }
    }

    // ---------------- App settings ----------------

    val themeMode: Flow<String> = context.dataStore.data.map { it[THEME_KEY] ?: "System" }.distinctUntilChanged()
    val themeColorPreset: Flow<String> = context.dataStore.data
        .map { it[THEME_COLOR_KEY] ?: com.arslandaim.omegavideodownloader.ui.theme.ThemeColorPreset.ELECTRIC_OCEAN.id }
        .distinctUntilChanged()
    val appLanguage: Flow<String> = context.dataStore.data.map { it[LANGUAGE_KEY] ?: "English (US)" }.distinctUntilChanged()
    val autoUpdateYtDlp: Flow<Boolean> = context.dataStore.data.map { it[AUTO_UPDATE_YTDLP_KEY] ?: true }.distinctUntilChanged()
    val lastYtDlpUpdate: Flow<Long> = context.dataStore.data.map { it[LAST_YTDLP_UPDATE_KEY] ?: 0L }
    val streamCacheMb: Flow<Int> = context.dataStore.data.map { it[STREAM_CACHE_MB_KEY] ?: DEFAULT_STREAM_CACHE_MB }.distinctUntilChanged()
    val maxConcurrentDownloads: Flow<Int> = context.dataStore.data
        .map { (it[MAX_CONCURRENT_DOWNLOADS_KEY] ?: DEFAULT_MAX_CONCURRENT_DOWNLOADS).coerceIn(1, 3) }
        .distinctUntilChanged()
    val browserFallback: Flow<Boolean> = context.dataStore.data.map { it[BROWSER_FALLBACK_KEY] ?: true }.distinctUntilChanged()
    val disclaimerAccepted: Flow<Boolean> = context.dataStore.data.map { it[DISCLAIMER_ACCEPTED_KEY] ?: false }.distinctUntilChanged()
    val lastActiveTab: Flow<Int> = context.dataStore.data
        .map { (it[LAST_ACTIVE_TAB_KEY] ?: 0).coerceIn(0, 2) }
        .distinctUntilChanged()
    val wifiOnlyDownloads: Flow<Boolean> = context.dataStore.data
        .map { it[WIFI_ONLY_DOWNLOADS_KEY] ?: false }
        .distinctUntilChanged()
    val movieSuggestionsEnabled: Flow<Boolean> = context.dataStore.data
        .map { it[MOVIE_SUGGESTIONS_ENABLED_KEY] ?: false }
        .distinctUntilChanged()

    suspend fun setThemeMode(mode: String) = withContext(Dispatchers.IO) { context.dataStore.edit { it[THEME_KEY] = mode } }
    suspend fun setThemeColorPreset(presetId: String) = withContext(Dispatchers.IO) { context.dataStore.edit { it[THEME_COLOR_KEY] = presetId } }
    suspend fun setAppLanguage(language: String) = withContext(Dispatchers.IO) { context.dataStore.edit { it[LANGUAGE_KEY] = language } }
    suspend fun setAutoUpdateYtDlp(enabled: Boolean) = withContext(Dispatchers.IO) { context.dataStore.edit { it[AUTO_UPDATE_YTDLP_KEY] = enabled } }
    suspend fun setLastYtDlpUpdate(time: Long) = withContext(Dispatchers.IO) { context.dataStore.edit { it[LAST_YTDLP_UPDATE_KEY] = time } }
    suspend fun setStreamCacheMb(mb: Int) = withContext(Dispatchers.IO) { context.dataStore.edit { it[STREAM_CACHE_MB_KEY] = mb } }
    suspend fun setMaxConcurrentDownloads(count: Int) = withContext(Dispatchers.IO) {
        context.dataStore.edit { it[MAX_CONCURRENT_DOWNLOADS_KEY] = count.coerceIn(1, 3) }
    }
    suspend fun setBrowserFallback(enabled: Boolean) = withContext(Dispatchers.IO) { context.dataStore.edit { it[BROWSER_FALLBACK_KEY] = enabled } }
    suspend fun setDisclaimerAccepted() = withContext(Dispatchers.IO) { context.dataStore.edit { it[DISCLAIMER_ACCEPTED_KEY] = true } }
    suspend fun setLastActiveTab(index: Int) = withContext(Dispatchers.IO) {
        context.dataStore.edit { it[LAST_ACTIVE_TAB_KEY] = index.coerceIn(0, 2) }
    }
    suspend fun setWifiOnlyDownloads(enabled: Boolean) = withContext(Dispatchers.IO) {
        context.dataStore.edit { it[WIFI_ONLY_DOWNLOADS_KEY] = enabled }
    }
    suspend fun setMovieSuggestionsEnabled(enabled: Boolean) = withContext(Dispatchers.IO) {
        context.dataStore.edit { it[MOVIE_SUGGESTIONS_ENABLED_KEY] = enabled }
    }

    // ---------------- Search History (Videos & Movies) ----------------

    val videoSearchHistory: Flow<List<String>> = context.dataStore.data
        .map { parseStringList(it[VIDEO_SEARCH_HISTORY_KEY]) }
        .distinctUntilChanged()
        .flowOn(Dispatchers.Default)

    val movieSearchHistory: Flow<List<String>> = context.dataStore.data
        .map { parseStringList(it[MOVIE_SEARCH_HISTORY_KEY]) }
        .distinctUntilChanged()
        .flowOn(Dispatchers.Default)

    val pauseVideoSearchHistory: Flow<Boolean> = context.dataStore.data
        .map { it[PAUSE_VIDEO_SEARCH_HISTORY_KEY] ?: false }
        .distinctUntilChanged()

    val pauseMovieSearchHistory: Flow<Boolean> = context.dataStore.data
        .map { it[PAUSE_MOVIE_SEARCH_HISTORY_KEY] ?: false }
        .distinctUntilChanged()

    suspend fun setPauseVideoSearchHistory(paused: Boolean) = withContext(Dispatchers.IO) {
        context.dataStore.edit { it[PAUSE_VIDEO_SEARCH_HISTORY_KEY] = paused }
    }

    suspend fun setPauseMovieSearchHistory(paused: Boolean) = withContext(Dispatchers.IO) {
        context.dataStore.edit { it[PAUSE_MOVIE_SEARCH_HISTORY_KEY] = paused }
    }

    suspend fun addVideoSearchQuery(query: String) = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return@withContext
        context.dataStore.edit { prefs ->
            if (prefs[PAUSE_VIDEO_SEARCH_HISTORY_KEY] == true) return@edit
            val updated = (listOf(trimmed) + parseStringList(prefs[VIDEO_SEARCH_HISTORY_KEY])
                .filterNot { it.equals(trimmed, ignoreCase = true) })
                .take(MAX_SEARCH_HISTORY)
            prefs[VIDEO_SEARCH_HISTORY_KEY] = gson.toJson(updated)
        }
    }

    suspend fun removeVideoSearchQuery(query: String) = withContext(Dispatchers.IO) {
        context.dataStore.edit { prefs ->
            val updated = parseStringList(prefs[VIDEO_SEARCH_HISTORY_KEY])
                .filterNot { it.equals(query.trim(), ignoreCase = true) }
            prefs[VIDEO_SEARCH_HISTORY_KEY] = gson.toJson(updated)
        }
    }

    suspend fun clearVideoSearchHistory() = withContext(Dispatchers.IO) {
        context.dataStore.edit { it[VIDEO_SEARCH_HISTORY_KEY] = "[]" }
    }

    suspend fun addMovieSearchQuery(query: String) = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return@withContext
        context.dataStore.edit { prefs ->
            if (prefs[PAUSE_MOVIE_SEARCH_HISTORY_KEY] == true) return@edit
            val updated = (listOf(trimmed) + parseStringList(prefs[MOVIE_SEARCH_HISTORY_KEY])
                .filterNot { it.equals(trimmed, ignoreCase = true) })
                .take(MAX_SEARCH_HISTORY)
            prefs[MOVIE_SEARCH_HISTORY_KEY] = gson.toJson(updated)
        }
    }

    suspend fun removeMovieSearchQuery(query: String) = withContext(Dispatchers.IO) {
        context.dataStore.edit { prefs ->
            val updated = parseStringList(prefs[MOVIE_SEARCH_HISTORY_KEY])
                .filterNot { it.equals(query.trim(), ignoreCase = true) }
            prefs[MOVIE_SEARCH_HISTORY_KEY] = gson.toJson(updated)
        }
    }

    suspend fun clearMovieSearchHistory() = withContext(Dispatchers.IO) {
        context.dataStore.edit { it[MOVIE_SEARCH_HISTORY_KEY] = "[]" }
    }

    // ---------------- Paused / Resumable Downloads ----------------

    val pausedDownloads: Flow<List<ResumableDownloadTask>> = context.dataStore.data
        .map { it[PAUSED_DOWNLOADS_KEY] }
        .distinctUntilChanged()
        .map { parsePausedDownloads(it) }
        .flowOn(Dispatchers.Default)

    val activeDownloads: StateFlow<List<ActiveDownload>> by lazy {
        combine(_dmActiveDownloads, _localActiveDownloads, pausedDownloads) { dm, local, paused ->
            val pausedItems = paused
                .filter { it.id !in local }
                .map { task ->
                    val frac = if (task.totalBytes > 0L) (task.downloadedBytes.toFloat() / task.totalBytes.toFloat()).coerceIn(0f, 1f) else 0f
                    ActiveDownload(
                        id = task.id,
                        title = task.title,
                        progress = frac,
                        totalBytes = task.totalBytes,
                        downloadedBytes = task.downloadedBytes,
                        status = "Paused",
                        type = task.type,
                        isPaused = true,
                        canPause = true
                    )
                }
            (dm.values + local.values + pausedItems).toList()
        }.stateIn(sharedScope, SharingStarted.Eagerly, emptyList())
    }

    suspend fun savePausedDownload(task: ResumableDownloadTask) = withContext(Dispatchers.IO) {
        context.dataStore.edit { preferences ->
            val list = parsePausedDownloads(preferences[PAUSED_DOWNLOADS_KEY]).toMutableList()
            list.removeAll { it.id == task.id }
            list.add(0, task)
            preferences[PAUSED_DOWNLOADS_KEY] = gson.toJson(list.take(25))
        }
    }

    suspend fun getPausedDownload(id: Long): ResumableDownloadTask? = withContext(Dispatchers.IO) {
        val raw = context.dataStore.data.first()[PAUSED_DOWNLOADS_KEY]
        parsePausedDownloads(raw).firstOrNull { it.id == id }
    }

    suspend fun removePausedDownload(id: Long) = withContext(Dispatchers.IO) {
        context.dataStore.edit { preferences ->
            val list = parsePausedDownloads(preferences[PAUSED_DOWNLOADS_KEY]).filterNot { it.id == id }
            preferences[PAUSED_DOWNLOADS_KEY] = gson.toJson(list)
        }
    }

    // ---------------- Active downloads (in-memory) ----------------

    fun updateLocalDownload(
        id: Long,
        title: String,
        progress: Float,
        totalBytes: Long,
        downloadedBytes: Long,
        type: String = "video",
        playlistProgress: String? = null,
        status: String = "Downloading",
        speedBytesPerSec: Long = 0L,
        etaSeconds: Long = -1L,
        isPaused: Boolean = false,
        canPause: Boolean = false
    ) {
        _localActiveDownloads.update { current ->
            current + (id to ActiveDownload(
                id = id,
                title = title,
                progress = (progress / 100f).coerceIn(0f, 1f),
                totalBytes = totalBytes,
                downloadedBytes = downloadedBytes,
                status = status,
                thumbnailUrl = null,
                type = type,
                playlistProgress = playlistProgress,
                speedBytesPerSec = speedBytesPerSec,
                etaSeconds = etaSeconds,
                isPaused = isPaused,
                canPause = canPause
            ))
        }
    }

    fun removeLocalDownload(id: Long) {
        _localActiveDownloads.update { it - id }
    }

    fun setCurrentlyPlaying(path: String?) {
        _currentlyPlayingPath.value = path
    }
}
