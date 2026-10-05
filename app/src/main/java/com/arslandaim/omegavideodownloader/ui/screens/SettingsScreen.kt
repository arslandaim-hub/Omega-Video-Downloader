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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.border
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.os.LocaleListCompat
import com.arslandaim.omegavideodownloader.*
import com.arslandaim.omegavideodownloader.ui.components.*
import com.arslandaim.omegavideodownloader.ui.theme.glassBackground
import com.arslandaim.omegavideodownloader.ui.theme.glassBorder
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(settingsManager: SettingsManager, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val themeMode by settingsManager.themeMode.collectAsState(initial = "System")
    val language by settingsManager.appLanguage.collectAsState(initial = "English (US)")
    val autoUpdateYtDlp by settingsManager.autoUpdateYtDlp.collectAsState(initial = false)

    var cacheSize by remember { mutableStateOf("0.0 MB") }
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
            val success = CookiesManager.importCookies(context, it)
            if (success) {
                Toast.makeText(context, "Cookies imported successfully", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "Failed to import cookies", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun refreshSizes() {
        val cache = StorageUtils.calculateFolderSize(context.cacheDir)
        val files = StorageUtils.calculateFolderSize(context.filesDir)
        cacheSize = StorageUtils.formatSize(cache)
        totalAppSize = StorageUtils.formatSize(cache + files)
    }

    LaunchedEffect(Unit) {
        refreshSizes()
    }

    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = if (isDark) {
                        listOf(Color(0xFF161823), Color.Black)
                    } else {
                        listOf(Color(0xFFF0F2F5), Color(0xFFE3E6EA))
                    }
                )
            )
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Header
            Surface(
                color = MaterialTheme.colorScheme.glassBackground,
                modifier = Modifier
                    .statusBarsPadding()
                    .fillMaxWidth()
                    .border(
                        0.5.dp, 
                        MaterialTheme.colorScheme.glassBorder, 
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
                            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f), RoundedCornerShape(12.dp))
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack, 
                            contentDescription = "Back", 
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(
                        text = "Settings",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.onSurface,
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
                    SettingsSection(title = "Personalization") {
                        SettingsItem(
                            icon = Icons.Default.Palette,
                            title = "Appearance",
                            subtitle = themeMode
                        ) {
                            var showDialog by remember { mutableStateOf(value = false) }
                            if (showDialog) {
                                OptionSelectionDialog(
                                    title = "Choose Theme",
                                    options = listOf("System", "Light", "Dark"),
                                    selectedOption = themeMode,
                                    onDismiss = { showDialog = false },
                                    onOptionSelected = {
                                        scope.launch { settingsManager.setThemeMode(it) }
                                        showDialog = false
                                    }
                                )
                            }
                            IconButton(onClick = { showDialog = true }) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ChevronRight,
                                        contentDescription = null,
                                        tint = Color(0xFF1877F2),
                                        modifier = Modifier.size(30.dp)
                                    )
                                }
                            }
                        }

                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            thickness = 0.5.dp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)
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
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)
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
                                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                                } else {
                                    Icon(Icons.Default.Refresh, contentDescription = "Update", tint = Color(0xFF1877F2))
                                }
                            }
                        }

                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            thickness = 0.5.dp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)
                        )

                        SettingsItem(
                            icon = Icons.Default.Translate,
                            title = "Language",
                            subtitle = language
                        ) {
                            var showDialog by remember { mutableStateOf(value = false) }
                            if (showDialog) {
                                OptionSelectionDialog(
                                    title = "Choose Language",
                                    options = listOf("English (US)", "English (UK)"),
                                    selectedOption = language,
                                    onDismiss = { showDialog = false },
                                    onOptionSelected = { selectedLang ->
                                        scope.launch {
                                            settingsManager.setAppLanguage(selectedLang)
                                            val localeTag = when (selectedLang) {
                                                "English (UK)" -> "en-GB"
                                                else -> "en-US"
                                            }
                                            val appLocales = LocaleListCompat.forLanguageTags(localeTag)
                                            AppCompatDelegate.setApplicationLocales(appLocales)
                                        }
                                        showDialog = false
                                    }
                                )
                            }
                            IconButton(onClick = { showDialog = true }) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ChevronRight,
                                        contentDescription = null,
                                        tint = Color(0xFF1877F2),
                                        modifier = Modifier.size(30.dp)
                                    )
                                }
                            }
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

                    SettingsSection(title = "Security & Privacy") {
                        SettingsItem(
                            icon = Icons.Default.Cookie,
                            title = "Import Cookies",
                            subtitle = "Use cookies.txt to bypass bot detection"
                        ) {
                            Row {
                                TextButton(onClick = { 
                                    CookiesManager.clearCookies(context)
                                    Toast.makeText(context, "Cookies cleared", Toast.LENGTH_SHORT).show()
                                }) {
                                    Text("Clear", color = Color.Red)
                                }
                                TextButton(onClick = { cookiePickerLauncher.launch("*/*") }) {
                                    Text("Import", fontWeight = FontWeight.Bold, color = Color(0xFF1877F2))
                                }
                            }
                        }

                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            thickness = 0.5.dp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)
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
                                Text("Change",fontWeight = FontWeight.Bold, color = Color(0xFFFF5722))
                            }
                        }

                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            thickness = 0.5.dp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)
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
                                    checkedThumbColor = Color(0xFF1877F2),
                                    checkedTrackColor = Color.LightGray,
                                    uncheckedTrackColor = Color.LightGray,
                                    disabledUncheckedTrackColor = Color.LightGray.copy(alpha = 0.5f),
                                    disabledCheckedTrackColor = Color.LightGray.copy(alpha = 0.5f)
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
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)
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
                            subtitle = "Please share logs through Gmail"
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
                                        } catch (e: Exception) {
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
                                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.Send,
                                        contentDescription = null,
                                        tint = Color(0xFF1877F2),
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }
                        }

                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            thickness = 0.5.dp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)
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
                                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Support,
                                        contentDescription = null,
                                        tint = Color(0xFF1877F2),
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
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            Icons.Default.Info,
                                            null,
                                            tint = Color(0xFF1877F2),
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Text(
                                            "About Developer",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                    Icon(
                                        imageVector = if (isAboutExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
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
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
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
                                            containerColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.1f),
                                            contentColor = Color(0xFF1877F2)
                                        ),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Icon(Icons.Default.Code, null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("View Source Code", fontWeight = FontWeight.Bold)
                                    }
                                    
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Surface(
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f),
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
                                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                "$appVersionName (Beta)",
                                                style = MaterialTheme.typography.labelLarge,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFF1877F2)
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
