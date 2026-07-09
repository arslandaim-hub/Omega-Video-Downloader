/*
 * Omega Video Downloader Project Original (2026)
 * arslandaim-hub (GitHub.com/arslandaim-hub)
 * Licenced Under GPL-3.0+
*/
package com.arslandaim.omegavideodownloader

import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Environment
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

class LockerManager(private val context: Context) {
    private val lockerDir = File(context.filesDir, "locker")

    init {
        if (!lockerDir.exists()) lockerDir.mkdirs()
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
        val biometricPrompt = BiometricPrompt(activity, executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    onSuccess()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    onError(errString.toString())
                }
            })

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
            val sourceUri = Uri.parse(video.localPath)
            val fileName = video.localPath.substringAfterLast("/")
            val destinationFile = File(lockerDir, fileName)

            // Copy to private storage
            val inputStream = if (sourceUri.scheme == "content") {
                context.contentResolver.openInputStream(sourceUri)
            } else {
                FileInputStream(File(video.localPath))
            }

            inputStream?.use { input ->
                FileOutputStream(destinationFile).use { output ->
                    input.copyTo(output)
                }
            }

            if (destinationFile.exists() && destinationFile.length() > 0) {
                // Delete from public storage
                if (sourceUri.scheme == "content") {
                    context.contentResolver.delete(sourceUri, null, null)
                } else {
                    File(video.localPath).delete()
                }
                
                // Update MediaStore (remove from gallery)
                MediaScannerConnection.scanFile(context, arrayOf(video.localPath), null, null)
                
                destinationFile.absolutePath
            } else {
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    suspend fun unlockFile(video: DownloadedVideo): String? = withContext(Dispatchers.IO) {
        try {
            val sourceFile = File(video.localPath)
            if (!sourceFile.exists()) return@withContext null
            
            val fileName = sourceFile.name
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val destinationFile = File(downloadsDir, fileName)

            // Copy back to public storage
            FileInputStream(sourceFile).use { input ->
                FileOutputStream(destinationFile).use { output ->
                    input.copyTo(output)
                }
            }

            if (destinationFile.exists() && destinationFile.length() > 0) {
                // Delete from private storage
                sourceFile.delete()

                // Update MediaStore (show in gallery)
                MediaScannerConnection.scanFile(context, arrayOf(destinationFile.absolutePath), null, null)

                destinationFile.absolutePath
            } else {
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
