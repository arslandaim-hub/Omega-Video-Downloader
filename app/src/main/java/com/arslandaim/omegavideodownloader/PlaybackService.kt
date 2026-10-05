/*
 * Omega Video Downloader Project Original (2026)
 * arslandaim-hub (GitHub.com/arslandaim-hub)
 * Licenced Under GPL-3.0+
*/
package com.arslandaim.omegavideodownloader

import android.R
import android.app.PendingIntent
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionError
import androidx.media3.session.SessionResult
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

class PlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null
    private val COMMAND_CLOSE = SessionCommand("CLOSE", Bundle.EMPTY)

    companion object {
        private const val TAG = "PlaybackService"
        private var instance: PlaybackService? = null

        var isAppInForeground: Boolean = false
            set(value) {
                if (field != value) {
                    field = value
                    Log.d(TAG, "isAppInForeground changed to $value")
                    instance?.updateNotification()
                }
            }

        var isBackgroundPlayEnabled: Boolean = false
            set(value) {
                if (field != value) {
                    field = value
                    Log.d(TAG, "isBackgroundPlayEnabled changed to $value")
                    instance?.updateNotification()
                }
            }
    }

    @OptIn(UnstableApi::class)
    private fun updateNotification() {
        Log.d(TAG, "updateNotification: Triggering update via setMediaButtonPreferences")
        // This is a common trick to force a notification refresh in Media3
        mediaSession?.let { it.setMediaButtonPreferences(it.mediaButtonPreferences) }
    }

    @OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()
        instance = this
        
        val player = ExoPlayer.Builder(this)
            .setAudioAttributes(AudioAttributes.DEFAULT, true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .setHandleAudioBecomingNoisy(true)
            .build()

        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 
            0, 
            intent, 
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val callback = object : MediaSession.Callback {
            override fun onConnect(
                session: MediaSession,
                controller: MediaSession.ControllerInfo
            ): MediaSession.ConnectionResult {
                val sessionCommands = MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon()
                    .add(COMMAND_CLOSE)
                    .build()
                val playerCommands = MediaSession.ConnectionResult.DEFAULT_PLAYER_COMMANDS.buildUpon()
                    .add(Player.COMMAND_SET_REPEAT_MODE)
                    .add(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM)
                    .add(Player.COMMAND_SEEK_TO_NEXT)
                    .add(Player.COMMAND_SEEK_TO_PREVIOUS)
                    .build()
                return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                    .setAvailableSessionCommands(sessionCommands)
                    .setAvailablePlayerCommands(playerCommands)
                    .build()
            }

            override fun onCustomCommand(
                session: MediaSession,
                controller: MediaSession.ControllerInfo,
                customCommand: SessionCommand,
                args: Bundle
            ): ListenableFuture<SessionResult> {
                if (customCommand.customAction == "CLOSE") {
                    session.player.stop()
                    stopSelf()
                    return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                }
                return Futures.immediateFuture(SessionResult(SessionError.ERROR_NOT_SUPPORTED))
            }
        }

        mediaSession = MediaSession.Builder(this, player)
            .setSessionActivity(pendingIntent)
            .setCallback(callback)
            .build()

        val closeButton = CommandButton.Builder()
            .setDisplayName("Close")
            .setIconResId(android.R.drawable.ic_menu_close_clear_cancel)
            .setSessionCommand(COMMAND_CLOSE)
            .build()

        val prevButton = CommandButton.Builder()
            .setDisplayName("Previous")
            .setIconResId(R.drawable.ic_media_previous)
            .setPlayerCommand(Player.COMMAND_SEEK_TO_PREVIOUS)
            .build()

        val nextButton = CommandButton.Builder()
            .setDisplayName("Next")
            .setIconResId(R.drawable.ic_media_next)
            .setPlayerCommand(Player.COMMAND_SEEK_TO_NEXT)
            .build()

        val repeatButton = CommandButton.Builder()
            .setDisplayName("Repeat")
            .setIconResId(R.drawable.ic_menu_revert)
            .setPlayerCommand(Player.COMMAND_SET_REPEAT_MODE)
            .build()

        mediaSession?.setMediaButtonPreferences(
            ImmutableList.of(repeatButton, prevButton, nextButton, closeButton)
        )
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    @OptIn(UnstableApi::class)
    override fun onUpdateNotification(session: MediaSession, startInForegroundRequired: Boolean) {
        // Log.d(TAG, "onUpdateNotification: foreground=$isAppInForeground, bgPlayEnabled=$isBackgroundPlayEnabled")
        // Rule: ONLY show if NOT in foreground AND background play is ENABLED
        // if (isAppInForeground || !isBackgroundPlayEnabled) {
        //    Log.d(TAG, "onUpdateNotification: Suppressing notification")
        //    return
        // }
        // Log.d(TAG, "onUpdateNotification: Showing notification")
        super.onUpdateNotification(session, startInForegroundRequired)
    }

    override fun onDestroy() {
        instance = null
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        super.onDestroy()
    }
}
