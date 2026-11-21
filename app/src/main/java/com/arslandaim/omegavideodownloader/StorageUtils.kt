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
import android.os.StatFs
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.util.Log
import android.webkit.MimeTypeMap
import androidx.core.net.toUri
import java.io.File
import java.io.FileInputStream
import java.util.Locale

object StorageUtils {
    private const val TAG = "StorageUtils"
    private const val COPY_BUFFER = 256 * 1024

    /** Working directory for in-progress downloads. Not purged by the OS like cacheDir. */
    fun downloadWorkDir(context: Context): File =
        File(context.getExternalFilesDir(null) ?: context.filesDir, "downloads").apply { mkdirs() }

    fun sanitizeFileName(title: String): String =
        title.replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]"), "_").trim().take(120).ifBlank { "Omega" }

    fun mimeForExtension(ext: String, type: String): String {
        val e = ext.lowercase(Locale.US)
        return when (e) {
            "mp4", "m4v" -> if (type == "audio") "audio/mp4" else "video/mp4"
            "mkv" -> "video/x-matroska"
            "webm" -> if (type == "audio") "audio/webm" else "video/webm"
            "mov" -> "video/quicktime"
            "m4a", "aac" -> "audio/mp4"
            "mp3" -> "audio/mpeg"
            "opus", "ogg", "oga" -> "audio/ogg"
            "flac" -> "audio/flac"
            "wav" -> "audio/wav"
            else -> MimeTypeMap.getSingleton().getMimeTypeFromExtension(e)
                ?: if (type == "audio") "audio/mp4" else "video/mp4"
        }
    }

    /**
     * Publishes [file] to the shared gallery (Movies/OmegaDownloader or Music/OmegaDownloader),
     * keeping its real container extension and MIME type.
     */
    fun saveToGallery(context: Context, file: File, title: String, type: String): Uri? {
        if (!file.exists() || file.length() == 0L) {
            Log.e(TAG, "Source file does not exist: ${file.absolutePath}")
            return null
        }
        val ext = file.extension.ifBlank { if (type == "audio") "m4a" else "mp4" }
        val mime = mimeForExtension(ext, type)
        val isAudio = type == "audio" || mime.startsWith("audio/")
        val fileName = "${sanitizeFileName(title)}_${System.currentTimeMillis()}.$ext"

        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, mime)
            put(MediaStore.MediaColumns.TITLE, title)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val root = if (isAudio) Environment.DIRECTORY_MUSIC else Environment.DIRECTORY_MOVIES
                put(MediaStore.MediaColumns.RELATIVE_PATH, "$root/OmegaDownloader")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
        }

        val resolver = context.contentResolver
        val collection = when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && isAudio -> MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            isAudio -> MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
            else -> MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        }

        val itemUri = try {
            resolver.insert(collection, values)
        } catch (e: Exception) {
            Log.e(TAG, "MediaStore insert failed", e)
            null
        } ?: return null

        return try {
            resolver.openOutputStream(itemUri)?.use { out ->
                FileInputStream(file).use { input -> input.copyTo(out, COPY_BUFFER) }
            } ?: throw IllegalStateException("Failed to open output stream")

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                resolver.update(itemUri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
            } else {
                getPathFromUri(context, itemUri)?.let { MediaScannerConnection.scanFile(context, arrayOf(it), arrayOf(mime), null) }
            }
            itemUri
        } catch (e: Exception) {
            Log.e(TAG, "Error saving to gallery", e)
            try { resolver.delete(itemUri, null, null) } catch (_: Exception) {}
            null
        }
    }

    /**
     * Android 10+ (API 29+): Creates a pending MediaStore entry so [FastHttpDownloader] can
     * write directly to the final gallery file with zero post-download copy and 1x storage space.
     */
    fun createPendingGalleryEntry(context: Context, title: String, ext: String, type: String): Uri? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
        val safeExt = ext.ifBlank { if (type == "audio") "m4a" else "mp4" }
        val mime = mimeForExtension(safeExt, type)
        val isAudio = type == "audio" || mime.startsWith("audio/")
        val fileName = "${sanitizeFileName(title)}_${System.currentTimeMillis()}.$safeExt"
        val root = if (isAudio) Environment.DIRECTORY_MUSIC else Environment.DIRECTORY_MOVIES
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, mime)
            put(MediaStore.MediaColumns.TITLE, title)
            put(MediaStore.MediaColumns.RELATIVE_PATH, "$root/OmegaDownloader")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val collection = if (isAudio) {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        }
        return try {
            context.contentResolver.insert(collection, values)
        } catch (e: Exception) {
            Log.w(TAG, "Could not create pending MediaStore entry", e)
            null
        }
    }

    fun publishPendingGalleryEntry(context: Context, uri: Uri) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                context.contentResolver.update(
                    uri,
                    ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) },
                    null,
                    null
                )
            } catch (e: Exception) {
                Log.w(TAG, "Failed to publish pending MediaStore entry", e)
            }
        }
    }

    fun deletePendingGalleryEntry(context: Context, uri: Uri) {
        try {
            context.contentResolver.delete(uri, null, null)
        } catch (_: Exception) {}
    }

    @Suppress("DEPRECATION")
    private fun getPathFromUri(context: Context, uri: Uri): String? = try {
        context.contentResolver.query(uri, arrayOf(MediaStore.MediaColumns.DATA), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    } catch (_: Exception) {
        null
    }

    /** Display name (with extension) of a content:// or file path. */
    fun displayName(context: Context, path: String): String? {
        val uri = path.toUri()
        if (uri.scheme == "content") {
            try {
                context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                    if (c.moveToFirst()) return c.getString(0)
                }
            } catch (_: Exception) {}
            return null
        }
        return File(uri.path ?: path).name
    }

    /** Deletes a media item whether it is a content:// URI, file:// URI or a plain path. */
    fun deleteMedia(context: Context, path: String): Boolean {
        if (path.isBlank()) return false
        return try {
            val uri = path.toUri()
            when (uri.scheme) {
                "content" -> context.contentResolver.delete(uri, null, null) > 0
                "file" -> uri.path?.let { File(it).delete() } ?: false
                else -> File(path).delete()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete $path", e)
            false
        }
    }

    fun calculateFolderSize(file: File): Long {
        var length = 0L
        file.listFiles()?.forEach { f -> length += if (f.isFile) f.length() else calculateFolderSize(f) }
        return length
    }

    fun formatSize(bytes: Long): String {
        if (bytes <= 0) return "0.0 MB"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt().coerceIn(0, units.lastIndex)
        return String.format(Locale.US, "%.1f %s", bytes / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
    }

    fun formatSpeed(bytesPerSec: Long): String {
        if (bytesPerSec <= 0L) return ""
        return "${formatSize(bytesPerSec)}/s"
    }

    fun formatEta(seconds: Long): String {
        if (seconds < 0L) return ""
        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        val s = seconds % 60
        return when {
            h > 0 -> "${h}h ${m}m"
            m > 0 -> "${m}m ${s}s"
            else -> "${s}s"
        }
    }

    /** Free space on the volume used for downloads (external app dir, same volume as the gallery). */
    fun getAvailableStorage(context: Context): Long = try {
        StatFs(downloadWorkDir(context).path).availableBytes
    } catch (_: Exception) {
        StatFs(context.filesDir.path).availableBytes
    }

    fun hasEnoughSpace(context: Context, requiredBytes: Long): Boolean {
        val buffer = 50 * 1024 * 1024L
        return getAvailableStorage(context) > (requiredBytes + buffer)
    }
}
