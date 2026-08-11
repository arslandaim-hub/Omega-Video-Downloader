/*
 * Omega Video Downloader Project Original (2026)
 * arslandaim-hub (GitHub.com/arslandaim-hub)
 * Licenced Under GPL-3.0+
*/
package com.arslandaim.omegavideodownloader.ui.screens

import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.ImageLoader
import com.arslandaim.omegavideodownloader.*
import com.arslandaim.omegavideodownloader.ui.LockerViewModel
import com.arslandaim.omegavideodownloader.ui.MainViewModel
import com.arslandaim.omegavideodownloader.ui.components.DownloadItem
import com.arslandaim.omegavideodownloader.ui.components.FilterButton

@OptIn(ExperimentalSharedTransitionApi::class, ExperimentalMaterial3Api::class)
@Composable
fun LockerScreen(
    viewModel: LockerViewModel,
    mainViewModel: MainViewModel,
    imageLoader: ImageLoader,
    lockerManager: LockerManager,
    activity: AppCompatActivity,
    onCancel: () -> Unit,
    onVideoClick: (DownloadedVideo) -> Unit,
    onDeleteVideo: (DownloadedVideo) -> Unit,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope
) {
    val context = LocalContext.current
    val isAuthorized = viewModel.isAuthorized
    val lockerVideos by viewModel.lockerVideos.collectAsState()
    val isLockerSet by viewModel.isLockerSet.collectAsState()
    val useBiometric by viewModel.useBiometric.collectAsState()
    val currentlyPlaying by mainViewModel.currentlyPlayingPath.collectAsState()

    if (!isAuthorized) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Surface(
                modifier = Modifier.padding(32.dp),
                shape = RoundedCornerShape(32.dp),
                color = MaterialTheme.colorScheme.surfaceColorAtElevation(8.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier.size(80.dp).background(Color(0xFFFF5722).copy(alpha = 0.1f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Lock, null, modifier = Modifier.size(40.dp), tint = Color(0xFFFF5722))
                    }
                    
                    Spacer(modifier = Modifier.height(24.dp))
                    Text("Locked Videos", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                    Text("Hide your important videos & audio here", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f), textAlign = TextAlign.Center)
                    Spacer(modifier = Modifier.height(32.dp))
                    
                    if (!isLockerSet) {
                        Text("Please download a video/audio first to setup a lock PIN.", color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyMedium)
                        Spacer(modifier = Modifier.height(24.dp))
                        Button(onClick = onCancel, shape = RoundedCornerShape(16.dp), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary, contentColor = MaterialTheme.colorScheme.onSecondary)) {
                            Text("Go Back Home")
                        }
                    } else if (!viewModel.isInputVisible) {
                        Button(
                            onClick = { 
                                if (useBiometric && lockerManager.isFingerprintSupported()) {
                                    lockerManager.showBiometricPrompt(
                                        activity = activity,
                                        onSuccess = { viewModel.isAuthorized = true },
                                        onError = { viewModel.isInputVisible = true }
                                    )
                                } else {
                                    viewModel.isInputVisible = true 
                                }
                            },
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth(),
                            contentPadding = PaddingValues(16.dp)
                        ) {
                            Text("Unlock", fontWeight = FontWeight.Bold)
                        }
                    } else {
                        Text("Enter 4-Digit PIN", style = MaterialTheme.typography.labelLarge, color = Color(0xFFFF5722), fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(16.dp))
                        OutlinedTextField(
                            value = viewModel.pinValue,
                            onValueChange = { 
                                if (it.length <= 4 && it.all { c -> c.isDigit() }) {
                                    viewModel.pinValue = it
                                    if (it.length == 4) {
                                        viewModel.verifyPin(it, onSuccess = {}, onError = { Toast.makeText(context, "Incorrect PIN", Toast.LENGTH_SHORT).show() })
                                    }
                                }
                            },
                            modifier = Modifier.width(200.dp),
                            shape = RoundedCornerShape(16.dp),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                            visualTransformation = PasswordVisualTransformation(),
                            singleLine = true,
                            textStyle = LocalTextStyle.current.copy(textAlign = TextAlign.Center, fontSize = 20.sp, letterSpacing = 8.sp),
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color(0xFFFF5722), unfocusedBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f))
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        TextButton(onClick = { viewModel.isInputVisible = false; viewModel.pinValue = "" }) {
                            Text("Hide Input", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
                        }
                    }
                }
            }
        }
    } else {
        Column(modifier = Modifier.fillMaxSize().padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.LockOpen, null, tint = Color(0xFFFF5722), modifier = Modifier.size(24.dp))
                Spacer(modifier = Modifier.width(12.dp))
                Text("Private Locker", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Color(0xFFFF5722))
            }
            Text("Locked files are moved to private storage", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f), modifier = Modifier.padding(start = 36.dp))

            viewModel.videoToUnlock?.let { video ->
                AlertDialog(
                    onDismissRequest = { viewModel.videoToUnlock = null },
                    icon = { Icon(Icons.Default.LockOpen, null, tint = Color(0xFFFF5722)) },
                    title = { Text("Unlock Item?", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold) },
                    text = { Text("Do you want to move \"${video.title}\" back to the Public Gallery?", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center) },
                    confirmButton = {
                        TextButton(onClick = {
                            viewModel.toggleLock(video) {}
                            viewModel.videoToUnlock = null
                        }) {
                            Text("Unlock", fontWeight = FontWeight.ExtraBold, color = Color(0xFFFF5722))
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { viewModel.videoToUnlock = null }) {
                            Text("Cancel", fontWeight = FontWeight.Bold)
                        }
                    }
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Row(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FilterButton(text = "Videos", isSelected = viewModel.selectedTab == "Videos", onClick = { viewModel.selectedTab = "Videos" })
                FilterButton(text = "Music", isSelected = viewModel.selectedTab == "Music", onClick = { viewModel.selectedTab = "Music" })
            }

            val currentType = if (viewModel.selectedTab == "Videos") "video" else "audio"
            val filteredLocker = lockerVideos.filter { 
                it.type == currentType && (it.playlistId == null || it.isPlaylistGroup)
            }

            Box(modifier = Modifier.fillMaxSize()) {
                if (filteredLocker.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(imageVector = if (viewModel.selectedTab == "Videos") Icons.Default.FolderOpen else Icons.Default.MusicNote, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f), modifier = Modifier.size(64.dp))
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(if (viewModel.selectedTab == "Videos") "No locked videos" else "No locked music", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
                        }
                    }
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(4.dp), contentPadding = PaddingValues(bottom = 80.dp)) {
                        items(items = filteredLocker, key = { it.timestamp + it.localPath.hashCode() }) { video ->
                            Box(modifier = Modifier.animateItem()) {
                                DownloadItem(
                                    video = video,
                                    isCurrentlyPlaying = currentlyPlaying == video.localPath,
                                    imageLoader = imageLoader,
                                    onDelete = { onDeleteVideo(video) },
                                    onLock = { viewModel.videoToUnlock = video },
                                    sharedTransitionScope = sharedTransitionScope,
                                    animatedVisibilityScope = animatedVisibilityScope,
                                    onClick = { 
                                        if (video.isPlaylistGroup) {
                                            viewModel.openPlaylistId = video.playlistId
                                        } else {
                                            onVideoClick(video)
                                        }
                                    },
                                    isInsideLocker = true
                                )
                            }
                        }
                    }
                }

                // Playlist Detail Sub-view (Locker)
                androidx.compose.animation.AnimatedVisibility(
                    visible = viewModel.openPlaylistId != null,
                    enter = slideInHorizontally(initialOffsetX = { it }) + fadeIn(),
                    exit = slideOutHorizontally(targetOffsetX = { it }) + fadeOut()
                ) {
                    PlaylistDetailView(
                        playlistId = viewModel.openPlaylistId,
                        downloadedVideos = lockerVideos,
                        currentlyPlaying = currentlyPlaying,
                        imageLoader = imageLoader,
                        onBack = { viewModel.openPlaylistId = null },
                        onVideoClick = onVideoClick,
                        onDeleteVideo = { onDeleteVideo(it) },
                        onLockVideo = { viewModel.videoToUnlock = it },
                        sharedTransitionScope = sharedTransitionScope
                    )
                }
            }
        }
    }
}
