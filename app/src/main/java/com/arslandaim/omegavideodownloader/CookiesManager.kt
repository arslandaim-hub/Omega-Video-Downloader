/*
 * Omega Video Downloader Project Original (2026)
 * arslandaim-hub (GitHub.com/arslandaim-hub)
 * Licenced Under GPL-3.0+
*/
package com.arslandaim.omegavideodownloader

import android.content.Context
import android.net.Uri
import java.io.File
import java.io.FileOutputStream

object CookiesManager {
    private const val COOKIES_FILE_NAME = "cookies.txt"

    fun getCookiesPath(context: Context): String? {
        val file = File(context.filesDir, COOKIES_FILE_NAME)
        return if (file.exists()) file.absolutePath else null
    }

    fun importCookies(context: Context, uri: Uri): Boolean {
        return try {
            val contentResolver = context.contentResolver
            contentResolver.openInputStream(uri)?.use { input ->
                val outputFile = File(context.filesDir, COOKIES_FILE_NAME)
                FileOutputStream(outputFile).use { output ->
                    input.copyTo(output)
                }
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun clearCookies(context: Context) {
        val file = File(context.filesDir, COOKIES_FILE_NAME)
        if (file.exists()) file.delete()
    }
}
