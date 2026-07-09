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
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.compose.runtime.Immutable
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

@Immutable

class SettingsManager(private val context: Context) {

    private val gson = Gson()
    
    val activeDownloads: StateFlow<List<ActiveDownload>> = _activeDownloadsFlow
    val currentlyPlayingPath: StateFlow<String?> = _currentlyPlayingPath

    companion object {
        private val _dmActiveDownloads = MutableStateFlow<Map<Long, ActiveDownload>>(emptyMap())
        private val _localActiveDownloads = MutableStateFlow<Map<Long, ActiveDownload>>(emptyMap())
        private val _currentlyPlayingPath = MutableStateFlow<String?>(null)
        
        private val sharedScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

        private val _activeDownloadsFlow: StateFlow<List<ActiveDownload>> = _dmActiveDownloads
            .combine(_localActiveDownloads) { dm, local -> (dm.values + local.values).toList() }
            .stateIn(sharedScope, SharingStarted.Eagerly, emptyList())

        private val THEME_KEY = stringPreferencesKey("theme_mode")
        private val LANGUAGE_KEY = stringPreferencesKey("app_language")
        private val DOWNLOADS_KEY = stringPreferencesKey("downloaded_videos")
        private val LOCKER_PIN_KEY = stringPreferencesKey("locker_pin")
        private val SECURITY_QUESTION_KEY = stringPreferencesKey("security_question")
        private val SECURITY_ANSWER_KEY = stringPreferencesKey("security_answer")
        private val USE_BIOMETRIC_KEY = booleanPreferencesKey("use_biometric")
    }

    private fun hashString(input: String): String {
        val bytes = input.lowercase().trim().toByteArray()
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest(bytes)
        return digest.fold("") { str, it -> str + "%02x".format(it) }
    }

    val isLockerSet: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[LOCKER_PIN_KEY] != null
    }

    val useBiometric: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[USE_BIOMETRIC_KEY] ?: false
    }

    val securityQuestion: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[SECURITY_QUESTION_KEY]
    }

    suspend fun setLockerPin(pin: String, question: String, answer: String) = withContext(Dispatchers.IO) {
        context.dataStore.edit { preferences ->
            preferences[LOCKER_PIN_KEY] = hashString(pin)
            preferences[SECURITY_QUESTION_KEY] = question
            preferences[SECURITY_ANSWER_KEY] = hashString(answer)
        }
    }

    suspend fun verifyLockerPin(pin: String): Boolean = withContext(Dispatchers.Default) {
        val hashedInput = hashString(pin)
        var isMatch = false
        context.dataStore.edit { preferences ->
            isMatch = preferences[LOCKER_PIN_KEY] == hashedInput
        }
        isMatch
    }

    suspend fun verifySecurityAnswer(answer: String): Boolean = withContext(Dispatchers.Default) {
        val hashedInput = hashString(answer)
        var isMatch = false
        context.dataStore.edit { preferences ->
            isMatch = preferences[SECURITY_ANSWER_KEY] == hashedInput
        }
        isMatch
    }

    suspend fun updateLockerPin(newPin: String) = withContext(Dispatchers.IO) {
        context.dataStore.edit { preferences ->
            preferences[LOCKER_PIN_KEY] = hashString(newPin)
        }
    }

    suspend fun setUseBiometric(enabled: Boolean) = withContext(Dispatchers.IO) {
        context.dataStore.edit { preferences ->
            preferences[USE_BIOMETRIC_KEY] = enabled
        }
    }

    suspend fun updateVideoLockStatus(video: DownloadedVideo, isLocked: Boolean, newPath: String) = withContext(Dispatchers.IO) {
        context.dataStore.edit { preferences ->
            val json = preferences[DOWNLOADS_KEY] ?: "[]"
            val type = object : TypeToken<List<DownloadedVideo>>() {}.type
            val currentList = gson.fromJson<List<DownloadedVideo>>(json, type)?.toMutableList() ?: mutableListOf()
            
            val index = currentList.indexOfFirst { it.timestamp == video.timestamp && it.localPath == video.localPath }
            if (index != -1) {
                currentList[index] = video.copy(isLocked = isLocked, localPath = newPath)
                preferences[DOWNLOADS_KEY] = gson.toJson(currentList)
            }
        }
    }

    private fun checkFileExists(uriString: String): Boolean {
        if (uriString.isEmpty()) return false
        return try {
            val uri = uriString.toUri()
            when (uri.scheme) {
                "content" -> {
                    try {
                        context.contentResolver.query(uri, arrayOf(android.provider.BaseColumns._ID), null, null, null)?.use { cursor ->
                            val exists = cursor.count > 0
                            if (!exists) android.util.Log.w("SettingsManager", "Content URI does not exist: $uriString")
                            exists
                        } ?: false
                    } catch (e: Exception) {
                        android.util.Log.e("SettingsManager", "Error checking content URI: $uriString", e)
                        false
                    }
                }
                "file" -> {
                    val exists = uri.path?.let { java.io.File(it).exists() } ?: false
                    if (!exists) android.util.Log.w("SettingsManager", "File path does not exist: ${uri.path}")
                    exists
                }
                else -> {
                    val exists = java.io.File(uriString).exists()
                    if (!exists) android.util.Log.w("SettingsManager", "Plain path does not exist: $uriString")
                    exists
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("SettingsManager", "Exception in checkFileExists for $uriString", e)
            false
        }
    }

    val themeMode: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[THEME_KEY] ?: "System"
    }

    val appLanguage: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[LANGUAGE_KEY] ?: "English (US)"
    }

    val downloadedVideos: Flow<List<DownloadedVideo>> = context.dataStore.data.map { preferences ->
        val json = preferences[DOWNLOADS_KEY] ?: "[]"
        try {
            val type = object : TypeToken<List<DownloadedVideo>>() {}.type
            val list = gson.fromJson<List<DownloadedVideo>>(json, type) ?: emptyList()
            // Only show UNLOCKED videos that exist
            list.filter { !it.isLocked && checkFileExists(it.localPath) }
        } catch (_: Exception) {
            emptyList()
        }
    }.flowOn(Dispatchers.IO)

    val lockerVideos: Flow<List<DownloadedVideo>> = context.dataStore.data.map { preferences ->
        val json = preferences[DOWNLOADS_KEY] ?: "[]"
        try {
            val type = object : TypeToken<List<DownloadedVideo>>() {}.type
            val list = gson.fromJson<List<DownloadedVideo>>(json, type) ?: emptyList()
            // Only show LOCKED videos that exist
            list.filter { it.isLocked && checkFileExists(it.localPath) }
        } catch (_: Exception) {
            emptyList()
        }
    }.flowOn(Dispatchers.IO)

    suspend fun setThemeMode(mode: String) = withContext(Dispatchers.IO) {
        context.dataStore.edit { preferences ->
            preferences[THEME_KEY] = mode
        }
    }

    suspend fun setAppLanguage(language: String) = withContext(Dispatchers.IO) {
        context.dataStore.edit { preferences ->
            preferences[LANGUAGE_KEY] = language
        }
    }

    suspend fun addDownloadedVideo(video: DownloadedVideo) = withContext(Dispatchers.Default) {
        context.dataStore.edit { preferences ->
            val json = preferences[DOWNLOADS_KEY] ?: "[]"
            val type = object : TypeToken<List<DownloadedVideo>>() {}.type
            val currentList = gson.fromJson<List<DownloadedVideo>>(json, type)?.toMutableList() ?: mutableListOf()
            
            currentList.add(0, video)
            
            val limitedList = if (currentList.size > 100) currentList.take(100) else currentList
            preferences[DOWNLOADS_KEY] = gson.toJson(limitedList)
        }
    }

    suspend fun removeDownloadedVideo(video: DownloadedVideo) = withContext(Dispatchers.IO) {
        context.dataStore.edit { preferences ->
            val json = preferences[DOWNLOADS_KEY] ?: "[]"
            val type = object : TypeToken<List<DownloadedVideo>>() {}.type
            val currentList = gson.fromJson<List<DownloadedVideo>>(json, type)?.toMutableList() ?: mutableListOf()
            
            currentList.removeIf { (it.timestamp == video.timestamp) && (it.localPath == video.localPath) }
            preferences[DOWNLOADS_KEY] = gson.toJson(currentList)
        }
    }

    fun updateActiveDownloads(downloads: List<ActiveDownload>) {
        _dmActiveDownloads.update { current ->
            val next = current.toMutableMap()
            // Clean up old ones
            val newIds = downloads.map { it.id }.toSet()
            current.keys.filter { it !in newIds }.forEach { next.remove(it) }
            // Add/Update
            downloads.forEach { next[it.id] = it }
            next
        }
    }

    fun updateLocalDownload(id: Long, title: String, progress: Float, totalBytes: Long, downloadedBytes: Long, type: String = "video") {
        _localActiveDownloads.update { current ->
            val next = current.toMutableMap()
            next[id] = ActiveDownload(
                id = id,
                title = title,
                progress = progress / 100f, // Normalize to 0..1
                totalBytes = totalBytes,
                downloadedBytes = downloadedBytes,
                status = "Downloading",
                thumbnailUrl = null,
                type = type
            )
            next
        }
    }

    fun removeLocalDownload(id: Long) {
        _localActiveDownloads.update { current ->
            val next = current.toMutableMap()
            next.remove(id)
            next
        }
    }

    fun setCurrentlyPlaying(path: String?) {
        _currentlyPlayingPath.value = path
    }

    suspend fun syncHistoryWithStorage() = withContext(Dispatchers.IO) {
        context.dataStore.edit { preferences ->
            val json = preferences[DOWNLOADS_KEY] ?: "[]"
            val type = object : TypeToken<List<DownloadedVideo>>() {}.type
            val currentList = gson.fromJson<List<DownloadedVideo>>(json, type) ?: emptyList()
            
            val now = System.currentTimeMillis()
            val validList = currentList.filter { 
                // Don't remove fresh items (less than 5 minutes old) to avoid race conditions with MediaStore indexing
                val isVeryFresh = (now - it.timestamp) < 300000
                isVeryFresh || checkFileExists(it.localPath)
            }
            if (validList.size != currentList.size) {
                preferences[DOWNLOADS_KEY] = gson.toJson(validList)
            }
        }
    }

    suspend fun clearPublicHistory() = withContext(Dispatchers.IO) {
        context.dataStore.edit { preferences ->
            val json = preferences[DOWNLOADS_KEY] ?: "[]"
            try {
                val type = object : TypeToken<List<DownloadedVideo>>() {}.type
                val currentList = gson.fromJson<List<DownloadedVideo>>(json, type) ?: emptyList()
                
                // Keep only the locked videos
                val lockerOnlyList = currentList.filter { it.isLocked }
                preferences[DOWNLOADS_KEY] = gson.toJson(lockerOnlyList)
            } catch (_: Exception) {
                // If parsing fails, we don't want to accidentally wipe the locker if it might exist
            }
        }
    }

    suspend fun clearHistory() = withContext(Dispatchers.IO) {
        context.dataStore.edit { preferences ->
            preferences[DOWNLOADS_KEY] = "[]"
        }
    }
}
