/*
 * Omega Video Downloader Project Original (2026)
 * arslandaim-hub (GitHub.com/arslandaim-hub)
 * Licenced Under GPL-3.0+
*/
package com.arslandaim.omegavideodownloader.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.arslandaim.omegavideodownloader.ui.HomeViewModel
import com.arslandaim.omegavideodownloader.ui.components.PlatformBadge
import com.arslandaim.omegavideodownloader.ui.components.QualitySelectionSection

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onDownloadStarted: (String) -> Unit
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.entries.all { it.value }
        if (allGranted) {
            viewModel.selectedQuality?.let {
                viewModel.startDownload(it)
                onDownloadStarted(it.type)
            }
        } else {
            Toast.makeText(context, "Storage permission is required to save downloads.", Toast.LENGTH_LONG).show()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.weight(0.1f))

        Surface(
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f),
            shape = RoundedCornerShape(28.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(0.5.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f), RoundedCornerShape(28.dp))
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "Video Link",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.align(Alignment.Start).padding(bottom = 12.dp, start = 4.dp)
                )

                OutlinedTextField(
                    value = viewModel.url,
                    onValueChange = { viewModel.url = it },
                    placeholder = { Text("Paste URL here...", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f),
                        unfocusedContainerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f),
                        unfocusedBorderColor = Color.Transparent,
                        focusedBorderColor = Color(0xFF1877F2).copy(alpha = 0.5f),
                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                    ),
                    trailingIcon = {
                        if (viewModel.url.isNotEmpty()) {
                            IconButton(onClick = { viewModel.clearUrl() }) {
                                Icon(Icons.Default.Clear, "Clear", tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
                            }
                        }
                    }
                )

                Spacer(modifier = Modifier.height(20.dp))

                viewModel.videoMetadata?.let { metadata ->
                    if (metadata.isPlaylist) {
                        Surface(
                            color = Color(0xFFFF5722).copy(alpha = 0.1f),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.History,
                                    contentDescription = null, 
                                    tint = Color(0xFFFF5722)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    "Playlist detected. Items will be merged into one file (Max 720p).",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFFFF5722)
                                )
                            }
                        }
                    }
                }

                Button(
                    onClick = {
                        if (viewModel.url.isNotEmpty() && !viewModel.isLoading) {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            viewModel.fetchMetadata()
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF1877F2),
                        contentColor = Color.White,
                        disabledContainerColor = Color(0xFF1877F2).copy(alpha = 0.3f)
                    ),
                    shape = RoundedCornerShape(16.dp),
                    enabled = viewModel.url.isNotEmpty() && !viewModel.isLoading
                ) {
                    if (viewModel.isLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White, strokeWidth = 2.dp)
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.PlayArrow, null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Download Now", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val platforms = listOf("YouTube", "Facebook", "Twitter", "Insta")
            platforms.forEach { name ->
                Box(modifier = Modifier.weight(1f)) {
                    PlatformBadge(name)
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        AnimatedVisibility(
            visible = (viewModel.videoMetadata != null) && !viewModel.isLoading,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            viewModel.videoMetadata?.let { metadata ->
                QualitySelectionSection(
                    metadata = metadata,
                ) { quality ->
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    
                    val permissions = mutableListOf<String>()
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        permissions.add(Manifest.permission.READ_MEDIA_VIDEO)
                        permissions.add(Manifest.permission.READ_MEDIA_AUDIO)
                        permissions.add(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
                            permissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                        }
                        permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
                    }

                    val toRequest = permissions.filter { 
                        ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED 
                    }

                    if (toRequest.isEmpty()) {
                        viewModel.startDownload(quality)
                        onDownloadStarted(quality.type)
                    } else {
                        viewModel.selectedQuality = quality
                        permissionLauncher.launch(toRequest.toTypedArray())
                    }
                }
            }
        }
        Spacer(modifier = Modifier.weight(0.1f))
        Spacer(modifier = Modifier.height(80.dp))
    }
}
