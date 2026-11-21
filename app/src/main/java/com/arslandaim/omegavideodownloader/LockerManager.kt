/*
 * Omega Video Downloader Project Original (2026)
 * arslandaim-hub (GitHub.com/arslandaim-hub)
 * Licenced Under GPL-3.0+
*/
package com.arslandaim.omegavideodownloader

import android.content.Context
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

class LockerManager(context: Context) {
    private val context = context.applicationContext
    private val lockerDir = File(this.context.filesDir, "locker").apply { mkdirs() }

    companion object {
        private const val TAG = "LockerManager"
        private const val COPY_BUFFER = 256 * 1024
    }

    fun isFingerprintSupported(): Boolean {
        val biometricManager = BiometricManager.from(context)
        return biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS
    }

    fun showBiometricPrompt(
        activity: AppCompatActivity,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val executor = ContextCompat.getMainExecutor(context)
        val biometricPrompt = BiometricPrompt(
            activity,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    onSuccess()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    onError(errString.toString())
                }
            }
        )

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Locker Authentication")
            .setSubtitle("Use your biometric to access private videos")
            .setNegativeButtonText("Use PIN")
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
            .build()

        biometricPrompt.authenticate(promptInfo)
    }

    suspend fun lockFile(video: DownloadedVideo): String? = withContext(Dispatchers.IO) {
        try {
            if (!lockerDir.exists()) lockerDir.mkdirs()
            val sourceUri = video.localPath.toUri()
            val rawName = StorageUtils.displayName(context, video.localPath)
                ?: "${StorageUtils.sanitizeFileName(video.title)}.${if (video.type == "audio") "m4a" else "mp4"}"
            val ext = rawName.substringAfterLast('.', if (video.type == "audio") "m4a" else "mp4")
            val uniqueName = "${StorageUtils.sanitizeFileName(video.title)}_${System.currentTimeMillis()}.$ext"
            val destinationFile = File(lockerDir, uniqueName)

            val inputStream = if (sourceUri.scheme == "content") {
                context.contentResolver.openInputStream(sourceUri)
            } else {
                val path = if (sourceUri.scheme == "file") sourceUri.path else video.localPath
                path?.let { File(it) }?.takeIf { it.exists() }?.let { FileInputStream(it) }
            } ?: return@withContext null

            inputStream.use { input ->
                FileOutputStream(destinationFile).use { output ->
                    input.copyTo(output, COPY_BUFFER)
                }
            }

            if (destinationFile.exists() && destinationFile.length() > 0) {
                StorageUtils.deleteMedia(context, video.localPath)
                destinationFile.absolutePath
            } else {
                destinationFile.delete()
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to lock file: ${video.localPath}", e)
            null
        }
    }

    /**
     * Restores a locked file back to the public Gallery via MediaStore (Scoped Storage safe on Android 10+).
     */
    suspend fun unlockFile(video: DownloadedVideo): String? = withContext(Dispatchers.IO) {
        try {
            val sourceFile = File(video.localPath)
            if (!sourceFile.exists() || sourceFile.length() == 0L) return@withContext null

            val restoredUri = StorageUtils.saveToGallery(context, sourceFile, video.title, video.type)
            if (restoredUri != null) {
                sourceFile.delete()
                restoredUri.toString()
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to unlock file: ${video.localPath}", e)
            null
        }
    }
}
