/*
 * Omega Video Downloader Project Original (2026)
 * arslandaim-hub (GitHub.com/arslandaim-hub)
 * Licenced Under GPL-3.0+
*/
package com.arslandaim.omegavideodownloader.ui.screens

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.os.LocaleListCompat
import com.arslandaim.omegavideodownloader.*
import com.arslandaim.omegavideodownloader.player.OmegaPlayerFactory
import com.arslandaim.omegavideodownloader.ui.components.*
import com.arslandaim.omegavideodownloader.ui.theme.OmegaTheme
import com.arslandaim.omegavideodownloader.ui.theme.ThemeColorPreset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SettingsScreen(settingsManager: SettingsManager, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val themeMode by settingsManager.themeMode.collectAsState(initial = "System")
    val themeColorPresetId by settingsManager.themeColorPreset.collectAsState(
        initial = ThemeColorPreset.ELECTRIC_OCEAN.id
    )
    val activeColorPreset = remember(themeColorPresetId) { ThemeColorPreset.fromId(themeColorPresetId) }
    val language by settingsManager.appLanguage.collectAsState(initial = "English (US)")
    val autoUpdateYtDlp by settingsManager.autoUpdateYtDlp.collectAsState(initial = true)
    val streamCacheMb by settingsManager.streamCacheMb.collectAsState(initial = SettingsManager.DEFAULT_STREAM_CACHE_MB)
    val maxConcurrentDownloads by settingsManager.maxConcurrentDownloads.collectAsState(
        initial = SettingsManager.DEFAULT_MAX_CONCURRENT_DOWNLOADS
    )
    val browserFallback by settingsManager.browserFallback.collectAsState(initial = true)
    val wifiOnlyDownloads by settingsManager.wifiOnlyDownloads.collectAsState(initial = false)
    val movieSuggestionsEnabled by settingsManager.movieSuggestionsEnabled.collectAsState(initial = false)

    var cacheSize by remember { mutableStateOf("0.0 MB") }
    var streamCacheUsed by remember { mutableStateOf("0.0 MB") }
    var totalAppSize by remember { mutableStateOf("0.0 MB") }

    val appVersionName = remember {
        try {
            val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            pInfo.versionName ?: "1.7.5"
        } catch (_: Exception) {
            "1.7.5"
        }
    }

    val cookiePickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            scope.launch {
                val success = withContext(Dispatchers.IO) { CookiesManager.importCookies(context, it) }
                Toast.makeText(
                    context,
                    if (success) "Cookies imported successfully" else "Failed to import cookies",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    suspend fun refreshSizes() {
        val (cache, files, streamBytes) = withContext(Dispatchers.IO) {
            Triple(
                StorageUtils.calculateFolderSize(context.cacheDir),
                StorageUtils.calculateFolderSize(context.filesDir),
                OmegaPlayerFactory.cacheSizeBytes(context)
            )
        }
        cacheSize = StorageUtils.formatSize(cache)
        totalAppSize = StorageUtils.formatSize(cache + files)
        streamCacheUsed = StorageUtils.formatSize(streamBytes)
    }

    LaunchedEffect(Unit) {
        refreshSizes()
    }

    val omegaColors = OmegaTheme.colors

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(brush = omegaColors.backgroundBrush)
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Header
            Surface(
                color = omegaColors.surface,
                modifier = Modifier
                    .statusBarsPadding()
                    .fillMaxWidth()
                    .border(
                        0.5.dp,
                        omegaColors.border,
                        RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp)
                    ),
                shape = RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .size(40.dp)
                            .background(omegaColors.cardElevated, RoundedCornerShape(12.dp))
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = omegaColors.textPrimary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(
                        text = "Settings",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Black,
                        color = omegaColors.textPrimary,
                        letterSpacing = (-0.5).sp
                    )
                }
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                // Personalization Section
                item {
                    var showThemeModeDialog by remember { mutableStateOf(false) }
                    var showThemeColorDialog by remember { mutableStateOf(false) }
                    var showLanguageDialog by remember { mutableStateOf(false) }

                    if (showThemeModeDialog) {
                        OptionSelectionDialog(
                            title = "Choose Theme",
                            options = listOf("System", "Light", "Dark"),
                            selectedOption = themeMode,
                            onDismiss = { showThemeModeDialog = false },
                            onOptionSelected = {
                                scope.launch { settingsManager.setThemeMode(it) }
                                showThemeModeDialog = false
                            }
                        )
                    }

                    if (showThemeColorDialog) {
                        ThemeColorSelectionDialog(
                            selectedPresetId = themeColorPresetId,
                            onDismiss = { showThemeColorDialog = false },
                            onPresetSelected = { preset ->
                                scope.launch { settingsManager.setThemeColorPreset(preset.id) }
                                showThemeColorDialog = false
                            }
                        )
                    }

                    if (showLanguageDialog) {
                        OptionSelectionDialog(
                            title = "Choose Language",
                            options = listOf("English (US)", "English (UK)"),
                            selectedOption = language,
                            onDismiss = { showLanguageDialog = false },
                            onOptionSelected = { selectedLang ->
                                scope.launch {
                                    settingsManager.setAppLanguage(selectedLang)
                                    val appLocales = LocaleListCompat.forLanguageTags(
                                        SettingsManager.localeTagFor(selectedLang)
                                    )
                                    AppCompatDelegate.setApplicationLocales(appLocales)
                                }
                                showLanguageDialog = false
                            }
                        )
                    }

                    SettingsSection(title = "Personalization") {
                        SettingsItem(
                            icon = Icons.Default.Palette,
                            title = "Appearance",
                            subtitle = themeMode,
                            onClick = { showThemeModeDialog = true }
                        ) {
                            IconButton(onClick = { showThemeModeDialog = true }) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(omegaColors.cardElevated)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ChevronRight,
                                        contentDescription = null,
                                        tint = omegaColors.primary,
                                        modifier = Modifier.size(30.dp)
                                    )
                                }
                            }
                        }

                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            thickness = 0.5.dp,
                            color = omegaColors.border
                        )

                        SettingsItem(
                            icon = Icons.Default.ColorLens,
                            title = "Theme Color",
                            subtitle = "${activeColorPreset.displayName} • ${activeColorPreset.colorPairLabel}",
                            onClick = { showThemeColorDialog = true }
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                DuoToneSwatch(preset = activeColorPreset, size = 28.dp)
                                IconButton(onClick = { showThemeColorDialog = true }) {
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(CircleShape)
                                            .background(omegaColors.cardElevated)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.ChevronRight,
                                            contentDescription = null,
                                            tint = omegaColors.primary,
                                            modifier = Modifier.size(30.dp)
                                        )
                                    }
                                }
                            }
                        }

                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            thickness = 0.5.dp,
                            color = omegaColors.border
                        )

                        SettingsItem(
                            icon = Icons.Default.Update,
                            title = "Auto-update yt-dlp",
                            subtitle = if (autoUpdateYtDlp) "Enabled" else "Disabled"
                        ) {
                            Switch(
                                checked = autoUpdateYtDlp,
                                onCheckedChange = { scope.launch { settingsManager.setAutoUpdateYtDlp(it) } }
                            )
                        }

                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            thickness = 0.5.dp,
                            color = omegaColors.border
                        )

                        SettingsItem(
                            icon = Icons.Default.CloudDownload,
                            title = "Check for yt-dlp Updates",
                            subtitle = "Manual binary update"
                        ) {
                            var isUpdating by remember { mutableStateOf(false) }
                            IconButton(
                                onClick = {
                                    if (!isUpdating) {
                                        isUpdating = true
                                        scope.launch {
                                            val result = YtDlpManager.updateBinary(context)
                                            result.onSuccess { msg ->
                                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                            }.onFailure { err ->
                                                Toast.makeText(context, "Update failed: ${err.message}", Toast.LENGTH_LONG).show()
                                            }
                                            isUpdating = false
                                        }
                                    }
                                },
                                enabled = !isUpdating
                            ) {
                                if (isUpdating) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(24.dp),
                                        strokeWidth = 2.dp,
                                        color = omegaColors.primary
                                    )
                                } else {
                                    Icon(Icons.Default.Refresh, contentDescription = "Update", tint = omegaColors.primary)
                                }
                            }
                        }

                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            thickness = 0.5.dp,
                            color = omegaColors.border
                        )

                        SettingsItem(
                            icon = Icons.Default.Translate,
                            title = "Language",
                            subtitle = language,
                            onClick = { showLanguageDialog = true }
                        ) {
                            IconButton(onClick = { showLanguageDialog = true }) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(omegaColors.cardElevated)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ChevronRight,
                                        contentDescription = null,
                                        tint = omegaColors.primary,
                                        modifier = Modifier.size(30.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // Streaming & Movie Links Section
                item {
                    val cacheSizeOptions = listOf(
                        "128 MB" to 128,
                        "256 MB" to 256,
                        "512 MB" to 512,
                        "1 GB" to 1024,
                        "2 GB" to 2048
                    )
                    val currentCacheLabel = cacheSizeOptions.firstOrNull { it.second == streamCacheMb }?.first
                        ?: "$streamCacheMb MB"

                    var showCacheDialog by remember { mutableStateOf(false) }
                    var showConcurrencyDialog by remember { mutableStateOf(false) }

                    if (showCacheDialog) {
                        OptionSelectionDialog(
                            title = "Stream Cache Limit",
                            options = cacheSizeOptions.map { it.first },
                            selectedOption = currentCacheLabel,
                            onDismiss = { showCacheDialog = false },
                            onOptionSelected = { pickedLabel ->
                                val mb = cacheSizeOptions.firstOrNull { it.first == pickedLabel }?.second
                                    ?: SettingsManager.DEFAULT_STREAM_CACHE_MB
                                scope.launch { settingsManager.setStreamCacheMb(mb) }
                                showCacheDialog = false
                                Toast.makeText(context, "Stream cache set to $pickedLabel", Toast.LENGTH_SHORT).show()
                            }
                        )
                    }

                    if (showConcurrencyDialog) {
                        OptionSelectionDialog(
                            title = "Max Concurrent Downloads",
                            options = listOf("1", "2", "3"),
                            selectedOption = maxConcurrentDownloads.toString(),
                            onDismiss = { showConcurrencyDialog = false },
                            onOptionSelected = { picked ->
                                val count = picked.toIntOrNull()
                                    ?: SettingsManager.DEFAULT_MAX_CONCURRENT_DOWNLOADS
                                scope.launch { settingsManager.setMaxConcurrentDownloads(count) }
                                showConcurrencyDialog = false
                                Toast.makeText(
                                    context,
                                    "Max concurrent downloads set to $count",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        )
                    }

                    SettingsSection(title = "Streaming & Movie Links") {
                        SettingsItem(
                            icon = Icons.Default.Speed,
                            title = "Stream Cache Size",
                            subtitle = "$currentCacheLabel (applies on restart)",
                            onClick = { showCacheDialog = true }
                        ) {
                            IconButton(onClick = { showCacheDialog = true }) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(omegaColors.cardElevated)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ChevronRight,
                                        contentDescription = null,
                                        tint = omegaColors.primary,
                                        modifier = Modifier.size(30.dp)
                                    )
                                }
                            }
                        }

                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            thickness = 0.5.dp,
                            color = omegaColors.border
                        )

                        SettingsItem(
                            icon = Icons.Default.CleaningServices,
                            title = "Clear Stream Cache",
                            subtitle = "Currently using $streamCacheUsed"
                        ) {
                            var isClearing by remember { mutableStateOf(false) }
                            TextButton(
                                onClick = {
                                    if (!isClearing) {
                                        isClearing = true
                                        scope.launch {
                                            withContext(Dispatchers.IO) {
                                                OmegaPlayerFactory.clearCache(context)
                                            }
                                            refreshSizes()
                                            isClearing = false
                                            Toast.makeText(context, "Stream cache cleared", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                },
                                enabled = !isClearing
                            ) {
                                Text(
                                    if (isClearing) "Clearing..." else "Clear",
                                    fontWeight = FontWeight.Bold,
                                    color = omegaColors.primary
                                )
                            }
                        }

                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            thickness = 0.5.dp,
                            color = omegaColors.border
                        )

                        SettingsItem(
                            icon = Icons.Default.Language,
                            title = "Hidden Browser Fallback",
                            subtitle = if (browserFallback) "Scans movie pages silently; opens only on CAPTCHA" else "Disabled"
                        ) {
                            Switch(
                                checked = browserFallback,
                                onCheckedChange = { scope.launch { settingsManager.setBrowserFallback(it) } }
                            )
                        }

                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            thickness = 0.5.dp,
                            color = omegaColors.border
                        )

                        SettingsItem(
                            icon = Icons.Default.Download,
                            title = "Max Concurrent Downloads",
                            subtitle = "$maxConcurrentDownloads active download${if (maxConcurrentDownloads == 1) "" else "s"} at once",
                            onClick = { showConcurrencyDialog = true }
                        ) {
                            IconButton(onClick = { showConcurrencyDialog = true }) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(omegaColors.cardElevated)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ChevronRight,
                                        contentDescription = null,
                                        tint = omegaColors.primary,
                                        modifier = Modifier.size(30.dp)
                                    )
                                }
                            }
                        }

                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            thickness = 0.5.dp,
                            color = omegaColors.border
                        )

                        SettingsItem(
                            icon = Icons.Default.Wifi,
                            title = "Wi-Fi Only Downloads",
                            subtitle = if (wifiOnlyDownloads) "Only downloads on unmetered Wi-Fi" else "Downloads on Wi-Fi & mobile data"
                        ) {
                            Switch(
                                checked = wifiOnlyDownloads,
                                onCheckedChange = { scope.launch { settingsManager.setWifiOnlyDownloads(it) } }
                            )
                        }

                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            thickness = 0.5.dp,
                            color = omegaColors.border
                        )

                        SettingsItem(
                            icon = Icons.Default.AutoAwesome,
                            title = "Movie Suggestions",
                            subtitle = if (movieSuggestionsEnabled) "Showing suggested movies on Movies tab" else "Off by default • Search anytime on Movies tab"
                        ) {
                            Switch(
                                checked = movieSuggestionsEnabled,
                                onCheckedChange = { scope.launch { settingsManager.setMovieSuggestionsEnabled(it) } }
                            )
                        }
                    }
                }

                // Security Section
                item {
                    val useBiometric by settingsManager.useBiometric.collectAsState(initial = false)
                    val isLockerSet by settingsManager.isLockerSet.collectAsState(initial = false)
                    val lockerManager = remember { LockerManager(context) }
                    val isFingerprintAvailable = remember { lockerManager.isFingerprintSupported() }
                    var showChangePin by remember { mutableStateOf(false) }

                    if (showChangePin) {
                        ChangePinDialog(
                            settingsManager = settingsManager,
                            onDismiss = { showChangePin = false },
                            onSuccess = { showChangePin = false }
                        )
                    }

                    val pauseVideoSearchHistory by settingsManager.pauseVideoSearchHistory.collectAsState(initial = false)
                    val pauseMovieSearchHistory by settingsManager.pauseMovieSearchHistory.collectAsState(initial = false)
                    val pauseContinueWatching by settingsManager.pauseContinueWatching.collectAsState(initial = false)
                    var hasTwitterAuth by remember { mutableStateOf(CookiesManager.hasTwitterAuthCookies(context)) }
                    val xLoginLauncher = rememberLauncherForActivityResult(
                        ActivityResultContracts.StartActivityForResult()
                    ) {
                        hasTwitterAuth = CookiesManager.hasTwitterAuthCookies(context)
                    }

                    SettingsSection(title = "Security & Privacy") {
                        SettingsItem(
                            icon = Icons.Default.PauseCircleOutline,
                            title = "Pause Video Search History",
                            subtitle = if (pauseVideoSearchHistory) "Video searches are not being saved" else "Save recent video searches"
                        ) {
                            Switch(
                                checked = pauseVideoSearchHistory,
                                onCheckedChange = { scope.launch { settingsManager.setPauseVideoSearchHistory(it) } }
                            )
                        }

                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            thickness = 0.5.dp,
                            color = omegaColors.border
                        )

                        SettingsItem(
                            icon = Icons.Default.MovieFilter,
                            title = "Pause Movie Search History",
                            subtitle = if (pauseMovieSearchHistory) "Movie searches are not being saved" else "Save recent movie searches"
                        ) {
                            Switch(
                                checked = pauseMovieSearchHistory,
                                onCheckedChange = { scope.launch { settingsManager.setPauseMovieSearchHistory(it) } }
                            )
                        }

                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            thickness = 0.5.dp,
                            color = omegaColors.border
                        )

                        SettingsItem(
                            icon = Icons.Default.HistoryToggleOff,
                            title = "Pause Continue Watching",
                            subtitle = if (pauseContinueWatching) "Playback progress is not being saved" else "Save watch progress for Continue Watching"
                        ) {
                            Switch(
                                checked = pauseContinueWatching,
                                onCheckedChange = { scope.launch { settingsManager.setPauseContinueWatching(it) } }
                            )
                        }

                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            thickness = 0.5.dp,
                            color = omegaColors.border
                        )

                        SettingsItem(
                            icon = Icons.AutoMirrored.Filled.Login,
                            title = "Connect X / Twitter Account",
                            subtitle = if (hasTwitterAuth) {
                                "Signed in — protected & 18+ posts unlocked"
                            } else {
                                "Optional sign-in for protected & age-restricted tweets"
                            },
                            onClick = {
                                xLoginLauncher.launch(
                                    Intent(context, BrowserLoginActivity::class.java).apply {
                                        putExtra("url", "https://x.com/i/flow/login")
                                        putExtra("platform", "X / Twitter")
                                    }
                                )
                            }
                        ) {
                            TextButton(
                                onClick = {
                                    xLoginLauncher.launch(
                                        Intent(context, BrowserLoginActivity::class.java).apply {
                                            putExtra("url", "https://x.com/i/flow/login")
                                            putExtra("platform", "X / Twitter")
                                        }
                                    )
                                }
                            ) {
                                Text(
                                    text = if (hasTwitterAuth) "Reconnect" else "Sign In",
                                    fontWeight = FontWeight.Bold,
                                    color = omegaColors.primary
                                )
                            }
                        }

                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            thickness = 0.5.dp,
                            color = omegaColors.border
                        )

                        SettingsItem(
                            icon = Icons.Default.Cookie,
                            title = "Import Cookies",
                            subtitle = "Use cookies.txt to bypass bot detection"
                        ) {
                            Row {
                                TextButton(onClick = {
                                    CookiesManager.clearCookies(context)
                                    hasTwitterAuth = false
                                    Toast.makeText(context, "Cookies cleared", Toast.LENGTH_SHORT).show()
                                }) {
                                    Text("Clear", color = omegaColors.danger)
                                }
                                TextButton(onClick = { cookiePickerLauncher.launch("*/*") }) {
                                    Text("Import", fontWeight = FontWeight.Bold, color = omegaColors.primary)
                                }
                            }
                        }

                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            thickness = 0.5.dp,
                            color = omegaColors.border
                        )

                        SettingsItem(
                            icon = Icons.Default.LockReset,
                            title = "Change Locker PIN",
                            subtitle = if (isLockerSet) "Update your security credentials" else "Set up a locker first"
                        ) {
                            TextButton(
                                onClick = { showChangePin = true },
                                enabled = isLockerSet
                            ) {
                                Text("Change", fontWeight = FontWeight.Bold, color = omegaColors.secondary)
                            }
                        }

                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            thickness = 0.5.dp,
                            color = omegaColors.border
                        )

                        SettingsItem(
                            icon = Icons.Default.Fingerprint,
                            title = "Fingerprint Unlock",
                            subtitle = if (isFingerprintAvailable) "Protect locker with biometric" else "Not supported on this device"
                        ) {
                            var localBiometricState by remember { mutableStateOf(useBiometric) }

                            LaunchedEffect(useBiometric) {
                                localBiometricState = useBiometric
                            }

                            Switch(
                                checked = localBiometricState,
                                onCheckedChange = { checked ->
                                    localBiometricState = checked
                                    scope.launch { settingsManager.setUseBiometric(checked) }
                                },
                                enabled = isFingerprintAvailable,
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = omegaColors.primary
                                )
                            )
                        }
                    }
                }

                // Storage Section
                item {
                    SettingsSection(title = "Storage & Data") {
                        SettingsItem(
                            icon = Icons.Default.Storage,
                            title = "Total Storage Used",
                            subtitle = totalAppSize
                        ) {}

                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            thickness = 0.5.dp,
                            color = omegaColors.border
                        )

                        SettingsItem(
                            icon = Icons.Default.Cached,
                            title = "Cache Size",
                            subtitle = cacheSize
                        ) {}
                    }
                }

                // Feedback & Support Section
                item {
                    SettingsSection(title = "Help & Feedback") {
                        SettingsItem(
                            icon = Icons.Default.BugReport,
                            title = "Report a bug",
                            subtitle = "Please share logs through Email"
                        ) {
                            IconButton(onClick = {
                                scope.launch {
                                    val currentLog = OmegaCrashHandler.getCrashReport(context)
                                    if (currentLog != null) {
                                        val intent = Intent(Intent.ACTION_SENDTO).apply {
                                            data = Uri.parse("mailto:onlyforandroiddev@gmail.com")
                                            putExtra(Intent.EXTRA_SUBJECT, "Omega Bug Report")
                                            putExtra(Intent.EXTRA_TEXT, currentLog)
                                        }
                                        try {
                                            context.startActivity(intent)
                                        } catch (_: Exception) {
                                            Toast.makeText(context, "No email app found", Toast.LENGTH_SHORT).show()
                                        }
                                    } else {
                                        Toast.makeText(context, "No Logs to share", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(omegaColors.cardElevated)
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.Send,
                                        contentDescription = null,
                                        tint = omegaColors.primary,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }
                        }

                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            thickness = 0.5.dp,
                            color = omegaColors.border
                        )

                        SettingsItem(
                            icon = Icons.Default.VolunteerActivism,
                            title = "Support Developer",
                            subtitle = "Help sustain open-source projects"
                        ) {
                            IconButton(onClick = {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://patreon.com/ArslanDaim77"))
                                context.startActivity(intent)
                            }) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(omegaColors.cardElevated)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Support,
                                        contentDescription = null,
                                        tint = omegaColors.primary,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // About Section
                item {
                    var isAboutExpanded by remember { mutableStateOf(false) }
                    SettingsSection(title = "About") {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Surface(
                                onClick = { isAboutExpanded = !isAboutExpanded },
                                color = Color.Transparent,
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            Icons.Default.Info,
                                            null,
                                            tint = omegaColors.primary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Text(
                                            "About Developer",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = omegaColors.textPrimary
                                        )
                                    }
                                    Icon(
                                        imageVector = if (isAboutExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                        contentDescription = null,
                                        tint = omegaColors.textSecondary
                                    )
                                }
                            }

                            AnimatedVisibility(
                                visible = isAboutExpanded,
                                enter = expandVertically() + fadeIn(),
                                exit = shrinkVertically() + fadeOut()
                            ) {
                                Column {
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text(
                                        text = "Developed by Arsalan Daim Shar",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = omegaColors.textSecondary,
                                        lineHeight = 20.sp
                                    )

                                    Spacer(modifier = Modifier.height(16.dp))

                                    Button(
                                        onClick = {
                                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/arslandaim-hub/Omega-Video-Downloader"))
                                            context.startActivity(intent)
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = omegaColors.primary.copy(alpha = 0.14f),
                                            contentColor = omegaColors.primary
                                        ),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Icon(Icons.Default.Code, null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("View Source Code", fontWeight = FontWeight.Bold)
                                    }

                                    Spacer(modifier = Modifier.height(16.dp))
                                    Surface(
                                        color = omegaColors.cardElevated,
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                            horizontalArrangement = Arrangement.Absolute.Center,
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Text(
                                                "Version",
                                                style = MaterialTheme.typography.labelLarge,
                                                color = omegaColors.textPrimary,
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                "$appVersionName (Beta)",
                                                style = MaterialTheme.typography.labelLarge,
                                                fontWeight = FontWeight.Bold,
                                                color = omegaColors.primary
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                item { Spacer(modifier = Modifier.height(20.dp)) }
            }
        }
    }
}
