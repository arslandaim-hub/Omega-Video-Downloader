/*
 * Omega Video Downloader Project Original (2026)
 * arslandaim-hub (GitHub.com/arslandaim-hub)
 * Licenced Under GPL-3.0+
*/
package com.arslandaim.omegavideodownloader

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.io.FileInputStream

object StorageUtils {

    fun saveVideoToGallery(context: Context, file: File, title: String): Uri? {
        val fileName = "${title.replace(Regex("[\\\\/:*?\"<>|]"), "_")}_${System.currentTimeMillis()}.mp4"
        val contentValues = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/OmegaDownloader")
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }
        }

        val resolver = context.contentResolver
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        }

        if (!file.exists()) {
            android.util.Log.e("StorageUtils", "Source file does not exist: ${file.absolutePath}")
            return null
        }

        val itemUri = resolver.insert(collection, contentValues) ?: run {
            android.util.Log.e("StorageUtils", "Failed to insert into MediaStore")
            return null
        }

        try {
            resolver.openOutputStream(itemUri)?.use { outputStream ->
                FileInputStream(file).use { inputStream ->
                    inputStream.copyTo(outputStream)
                }
            } ?: throw Exception("Failed to open output stream")

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                contentValues.clear()
                contentValues.put(MediaStore.Video.Media.IS_PENDING, 0)
                resolver.update(itemUri, contentValues, null, null)
            }

            // Force Media Scanner with actual path if possible
            val path = getPathFromUri(context, itemUri)
            android.util.Log.d("StorageUtils", "Scanning file: $path")
            MediaScannerConnection.scanFile(context, arrayOf(path ?: itemUri.toString()), null, null)
            
            return itemUri
        } catch (e: Exception) {
            android.util.Log.e("StorageUtils", "Error saving video to gallery", e)
            resolver.delete(itemUri, null, null)
            return null
        }
    }

    private fun getPathFromUri(context: Context, uri: Uri): String? {
        val projection = arrayOf(MediaStore.MediaColumns.DATA)
        return context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val columnIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATA)
                cursor.getString(columnIndex)
            } else null
        }
    }

    fun saveAudioToGallery(context: Context, file: File, title: String): Uri? {
        val fileName = "${title.replace(Regex("[\\\\/:*?\"<>|]"), "_")}_${System.currentTimeMillis()}.mp3"
        val contentValues = ContentValues().apply {
            put(MediaStore.Audio.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Audio.Media.MIME_TYPE, "audio/mpeg")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Audio.Media.RELATIVE_PATH, Environment.DIRECTORY_MUSIC + "/OmegaDownloader")
                put(MediaStore.Audio.Media.IS_PENDING, 1)
            }
        }

        val resolver = context.contentResolver
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }

        if (!file.exists()) {
            android.util.Log.e("StorageUtils", "Source audio file does not exist: ${file.absolutePath}")
            return null
        }

        val itemUri = resolver.insert(collection, contentValues) ?: run {
            android.util.Log.e("StorageUtils", "Failed to insert audio into MediaStore")
            return null
        }

        try {
            resolver.openOutputStream(itemUri)?.use { outputStream ->
                FileInputStream(file).use { inputStream ->
                    inputStream.copyTo(outputStream)
                }
            } ?: throw Exception("Failed to open audio output stream")

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                contentValues.clear()
                contentValues.put(MediaStore.Audio.Media.IS_PENDING, 0)
                resolver.update(itemUri, contentValues, null, null)
            }

            // Force Media Scanner
            val path = getPathFromUri(context, itemUri)
            android.util.Log.d("StorageUtils", "Scanning audio file: $path")
            MediaScannerConnection.scanFile(context, arrayOf(path ?: itemUri.toString()), null, null)

            return itemUri
        } catch (e: Exception) {
            android.util.Log.e("StorageUtils", "Error saving audio to gallery", e)
            resolver.delete(itemUri, null, null)
            return null
        }
    }
}
