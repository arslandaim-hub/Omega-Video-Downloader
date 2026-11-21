/*
 * Omega Video Downloader Project Original (2026)
 * arslandaim-hub (GitHub.com/arslandaim-hub)
 * Licenced Under GPL-3.0+
*/
package com.arslandaim.omegavideodownloader

import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.core.net.toUri
import com.arslandaim.omegavideodownloader.player.OmegaHttp
import com.google.gson.Gson
import kotlinx.coroutines.*
import okhttp3.Call
import okhttp3.ConnectionPool
import okhttp3.Dispatcher
import okhttp3.Protocol
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.net.URI
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.util.Collections
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * High-speed native Kotlin segmented HTTP downloader (IDM/ADM style) for direct progressive
 * movie/video/audio files (Archive.org MP4/MKV, direct `.mp4`/`.mkv`/`.webm` links).
 *
 * Optimizations:
 *  - Dedicated HTTP/1.1 multi-socket pool (avoids HTTP/2 single-TCP-connection multiplexing bottleneck)
 *  - Work-stealing block scheduler (4 MB blocks so fast connections never sit idle behind slow ones)
 *  - Exact-byte checkpointing (`checkpoint.json`) for durable Pause/Resume across restarts
 *  - 512 KB buffered positional FileChannel writes + file pre-allocation + zero-copy MediaStore on Android 10+
 */
object FastHttpDownloader {
    private const val TAG = "FastHttpDownloader"
    private const val BUFFER_SIZE = 512 * 1024
    private const val MIN_SEGMENTED_BYTES = 4L * 1024L * 1024L
    private const val MAX_CHUNK_RETRIES = 5
    private val gson = Gson()

    /**
     * Dedicated OkHttpClient using HTTP/1.1 + isolated ConnectionPool so 6-8 range workers open
     * truly parallel TCP sockets instead of multiplexing onto one HTTP/2 socket.
     */
    private val fastClient by lazy {
        val dispatcher = Dispatcher().apply {
            maxRequests = 64
            maxRequestsPerHost = 16
        }
        OmegaHttp.client.newBuilder()
            .dispatcher(dispatcher)
            .connectionPool(ConnectionPool(16, 2, TimeUnit.MINUTES))
            .protocols(listOf(Protocol.HTTP_1_1))
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(45, TimeUnit.SECONDS)
            .build()
    }

    sealed class Result {
        data class SavedToGallery(val uri: Uri, val sizeBytes: Long) : Result()
        data class DownloadedFile(val file: File) : Result()
        data class LowStorage(val requiredBytes: Long) : Result()
        data class Paused(val totalBytes: Long, val downloadedBytes: Long) : Result()
        data object Cancelled : Result()
        data class FallbackToYtDlp(val reason: String) : Result()
    }

    private data class Checkpoint(
        val finalUrl: String,
        val totalBytes: Long,
        val etag: String?,
        val lastModified: String?,
        val extension: String,
        val pendingUri: String?,
        val blockSize: Long,
        val blockCursors: LongArray
    )

    private data class ProbeInfo(
        val finalUrl: String,
        val totalBytes: Long,
        val supportsRanges: Boolean,
        val extension: String,
        val etag: String?,
        val lastModified: String?
    )

    fun isEligible(url: String, useDirectUrl: Boolean): Boolean {
        if (!useDirectUrl) return false
        if (!url.startsWith("http://") && !url.startsWith("https://")) return false
        val kind = UrlUtils.mediaKindFromUrl(url)
        if (kind == StreamKind.HLS || kind == StreamKind.DASH) return false
        val lower = url.lowercase()
        if (lower.contains(".m3u8") || lower.contains(".mpd") || lower.contains("format=m3u8")) return false
        if (UrlUtils.isYouTube(url)) return false
        return true
    }

    /** Cleans up any preserved partial files & pending MediaStore entry when a paused task is cancelled. */
    fun cleanupTaskFiles(context: Context, workDir: File) {
        try {
            val cpFile = File(workDir, "checkpoint.json")
            if (cpFile.exists()) {
                val cp = runCatching { gson.fromJson(cpFile.readText(), Checkpoint::class.java) }.getOrNull()
                cp?.pendingUri?.let { uriStr ->
                    runCatching { StorageUtils.deletePendingGalleryEntry(context, uriStr.toUri()) }
                }
            }
            workDir.deleteRecursively()
        } catch (e: Exception) {
            Log.w(TAG, "cleanupTaskFiles failed", e)
        }
    }

    suspend fun download(
        context: Context,
        url: String,
        title: String,
        type: String,
        workDir: File,
        headers: Map<String, String>,
        isPauseRequested: () -> Boolean = { false },
        onProgress: (progress: Float, totalBytes: Long, downloadedBytes: Long, speedBytesPerSec: Long, etaSec: Long) -> Unit
    ): Result = withContext(Dispatchers.IO) {
        val baseHeaders = buildMap {
            put("User-Agent", YtDlpManager.DESKTOP_UA)
            put("Accept-Encoding", "identity")
            putAll(headers)
        }

        val probe = probeUrl(url, baseHeaders, type)
            ?: return@withContext Result.FallbackToYtDlp("Direct probe unsuitable; falling back to yt-dlp")

        val checkpointFile = File(workDir, "checkpoint.json")
        val existingCp = loadValidCheckpoint(checkpointFile, probe)

        val useDirectMediaStore = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
        if (probe.totalBytes > 0 && existingCp == null) {
            val needed = if (useDirectMediaStore) probe.totalBytes else probe.totalBytes * 2
            if (!StorageUtils.hasEnoughSpace(context, needed)) {
                return@withContext Result.LowStorage(needed)
            }
        }

        val activeCalls = Collections.synchronizedSet(HashSet<Call>())
        val downloaded = AtomicLong(0L)

        var pendingUri: Uri? = null
        var pfd: ParcelFileDescriptor? = null
        var fallbackFile: File? = null
        var raf: RandomAccessFile? = null
        var channel: FileChannel? = null

        try {
            // Try resuming an existing pending MediaStore URI first if checkpoint had one
            if (useDirectMediaStore) {
                val resumeUri = existingCp?.pendingUri?.toUri()
                val targetUri = resumeUri ?: StorageUtils.createPendingGalleryEntry(context, title, probe.extension, type)
                if (targetUri != null) {
                    val desc = try {
                        context.contentResolver.openFileDescriptor(targetUri, "rw")
                    } catch (e: Exception) {
                        Log.w(TAG, "MediaStore rw descriptor unavailable: ${e.message}")
                        if (resumeUri == null) StorageUtils.deletePendingGalleryEntry(context, targetUri)
                        null
                    }
                    if (desc != null) {
                        pendingUri = targetUri
                        pfd = desc
                        channel = FileOutputStream(desc.fileDescriptor).channel
                    }
                }
            }

            if (channel == null) {
                workDir.mkdirs()
                val target = File(workDir, "media.${probe.extension}")
                fallbackFile = target
                raf = RandomAccessFile(target, "rw")
                if (probe.totalBytes > 0L && raf.length() != probe.totalBytes) {
                    runCatching { raf.setLength(probe.totalBytes) }
                }
                channel = raf.channel
            } else {
                workDir.mkdirs()
            }

            val targetChannel = channel
            val connections = chooseConnectionCount(probe)
            val isSegmented = connections > 1 && probe.supportsRanges && probe.totalBytes > 0L

            val blockSize: Long
            val blockCount: Int
            val blockCursors: LongArray

            if (isSegmented) {
                if (existingCp != null && existingCp.blockCursors.isNotEmpty()) {
                    blockSize = existingCp.blockSize
                    blockCount = existingCp.blockCursors.size
                    blockCursors = existingCp.blockCursors.copyOf()
                    var alreadyBytes = 0L
                    for (i in 0 until blockCount) {
                        val bStart = i * blockSize
                        val bEnd = if (i == blockCount - 1) probe.totalBytes - 1 else (bStart + blockSize - 1)
                        val cur = blockCursors[i].coerceIn(bStart, bEnd + 1)
                        blockCursors[i] = cur
                        alreadyBytes += (cur - bStart)
                    }
                    downloaded.set(alreadyBytes)
                } else {
                    blockSize = if (probe.totalBytes >= 64L * 1024L * 1024L) 4L * 1024L * 1024L else 2L * 1024L * 1024L
                    blockCount = ((probe.totalBytes + blockSize - 1) / blockSize).toInt()
                    blockCursors = LongArray(blockCount) { i -> i * blockSize }
                }
            } else {
                blockSize = probe.totalBytes.coerceAtLeast(1L)
                blockCount = 1
                blockCursors = longArrayOf(0L)
            }

            val checkpoint = Checkpoint(
                finalUrl = probe.finalUrl,
                totalBytes = probe.totalBytes,
                etag = probe.etag,
                lastModified = probe.lastModified,
                extension = probe.extension,
                pendingUri = pendingUri?.toString(),
                blockSize = blockSize,
                blockCursors = blockCursors
            )

            fun persistCheckpoint() {
                if (!isSegmented) return
                runCatching {
                    synchronized(checkpointFile) {
                        workDir.mkdirs()
                        checkpointFile.writeText(gson.toJson(checkpoint))
                    }
                }
            }

            if (isSegmented && existingCp == null) {
                persistCheckpoint()
            }

            Log.d(
                TAG,
                "FastHttpDownloader starting ($connections conns, $blockCount blocks, ${StorageUtils.formatSize(probe.totalBytes)}, resumed=${existingCp != null}): ${probe.finalUrl}"
            )

            val progressJob = launch {
                var lastBytes = downloaded.get()
                var lastTime = System.currentTimeMillis()
                var smoothedSpeed = 0.0
                var ticks = 0
                while (isActive) {
                    delay(400L)
                    val now = System.currentTimeMillis()
                    val current = downloaded.get()
                    val dtMs = (now - lastTime).coerceAtLeast(1L)
                    val instantSpeed = ((current - lastBytes).coerceAtLeast(0L) * 1000.0) / dtMs
                    smoothedSpeed = if (smoothedSpeed <= 0.0) instantSpeed else (smoothedSpeed * 0.65 + instantSpeed * 0.35)
                    lastBytes = current
                    lastTime = now

                    val pct = if (probe.totalBytes > 0) ((current * 100f) / probe.totalBytes).coerceIn(0f, 99.9f) else 0f
                    val speedLong = smoothedSpeed.toLong()
                    val eta = if (probe.totalBytes > 0 && speedLong > 0L) {
                        ((probe.totalBytes - current).coerceAtLeast(0L) / speedLong)
                    } else -1L
                    onProgress(pct, probe.totalBytes, current, speedLong, eta)

                    ticks++
                    if (isSegmented && ticks % 5 == 0) {
                        persistCheckpoint()
                    }
                }
            }

            try {
                if (!isSegmented) {
                    downloadSingleStream(
                        url = probe.finalUrl,
                        headers = baseHeaders,
                        channel = targetChannel,
                        downloaded = downloaded,
                        activeCalls = activeCalls,
                        isPauseRequested = isPauseRequested
                    )
                } else {
                    val remainingBlocks = (0 until blockCount).filter { i ->
                        val bEnd = if (i == blockCount - 1) probe.totalBytes - 1 else ((i + 1) * blockSize - 1)
                        blockCursors[i] <= bEnd
                    }
                    val queueIdx = AtomicInteger(0)
                    val workerCount = minOf(connections, remainingBlocks.size.coerceAtLeast(1))

                    coroutineScope {
                        val jobs = (0 until workerCount).map {
                            async(Dispatchers.IO) {
                                val buffer = ByteArray(BUFFER_SIZE)
                                while (true) {
                                    currentCoroutineContext().ensureActive()
                                    if (isPauseRequested()) throw PauseRequestedException()
                                    val posInQueue = queueIdx.getAndIncrement()
                                    if (posInQueue >= remainingBlocks.size) break

                                    val blockIdx = remainingBlocks[posInQueue]
                                    val bEnd = if (blockIdx == blockCount - 1) {
                                        probe.totalBytes - 1
                                    } else {
                                        ((blockIdx + 1) * blockSize - 1)
                                    }
                                    downloadRangeBlock(
                                        url = probe.finalUrl,
                                        headers = baseHeaders,
                                        blockIdx = blockIdx,
                                        rangeEnd = bEnd,
                                        blockCursors = blockCursors,
                                        channel = targetChannel,
                                        downloaded = downloaded,
                                        activeCalls = activeCalls,
                                        buffer = buffer,
                                        isPauseRequested = isPauseRequested
                                    )
                                }
                            }
                        }
                        jobs.awaitAll()
                    }
                }
            } finally {
                progressJob.cancel()
            }

            if (isPauseRequested()) {
                try { targetChannel.force(false) } catch (_: Exception) {}
                persistCheckpoint()
                try { targetChannel.close() } catch (_: Exception) {}
                try { raf?.close() } catch (_: Exception) {}
                try { pfd?.close() } catch (_: Exception) {}
                return@withContext Result.Paused(probe.totalBytes, downloaded.get())
            }

            try { targetChannel.force(false) } catch (_: Exception) {}
            try { targetChannel.close() } catch (_: Exception) {}
            try { raf?.close() } catch (_: Exception) {}
            try { pfd?.close() } catch (_: Exception) {}

            val finalBytes = downloaded.get()
            if (probe.totalBytes > 0 && finalBytes < probe.totalBytes * 0.98) {
                pendingUri?.let { StorageUtils.deletePendingGalleryEntry(context, it) }
                fallbackFile?.delete()
                checkpointFile.delete()
                return@withContext Result.FallbackToYtDlp("Incomplete transfer ($finalBytes / ${probe.totalBytes})")
            }

            checkpointFile.delete()
            onProgress(100f, finalBytes, finalBytes, 0L, 0L)

            val savedUri = pendingUri
            if (savedUri != null) {
                StorageUtils.publishPendingGalleryEntry(context, savedUri)
                return@withContext Result.SavedToGallery(savedUri, finalBytes)
            }
            val file = fallbackFile
            if (file != null && file.exists() && file.length() > 0L) {
                return@withContext Result.DownloadedFile(file)
            }
            Result.FallbackToYtDlp("Output file missing after direct download")
        } catch (_: PauseRequestedException) {
            abortCalls(activeCalls)
            try { channel?.force(false) } catch (_: Exception) {}
            try { channel?.close() } catch (_: Exception) {}
            try { raf?.close() } catch (_: Exception) {}
            try { pfd?.close() } catch (_: Exception) {}
            Result.Paused(probe.totalBytes, downloaded.get())
        } catch (_: CancellationException) {
            abortCalls(activeCalls)
            try { channel?.close() } catch (_: Exception) {}
            try { raf?.close() } catch (_: Exception) {}
            try { pfd?.close() } catch (_: Exception) {}
            if (isPauseRequested()) {
                Result.Paused(probe.totalBytes, downloaded.get())
            } else {
                pendingUri?.let { StorageUtils.deletePendingGalleryEntry(context, it) }
                fallbackFile?.delete()
                checkpointFile.delete()
                Result.Cancelled
            }
        } catch (e: Exception) {
            Log.w(TAG, "FastHttpDownloader failed, falling back to yt-dlp: ${e.message}")
            abortCalls(activeCalls)
            try { channel?.close() } catch (_: Exception) {}
            try { raf?.close() } catch (_: Exception) {}
            try { pfd?.close() } catch (_: Exception) {}
            if (isPauseRequested()) {
                Result.Paused(probe.totalBytes, downloaded.get())
            } else {
                pendingUri?.let { StorageUtils.deletePendingGalleryEntry(context, it) }
                fallbackFile?.delete()
                checkpointFile.delete()
                Result.FallbackToYtDlp(e.message ?: "Direct download error")
            }
        }
    }

    private fun loadValidCheckpoint(checkpointFile: File, probe: ProbeInfo): Checkpoint? {
        if (!checkpointFile.exists()) return null
        return try {
            val cp = gson.fromJson(checkpointFile.readText(), Checkpoint::class.java) ?: return null
            if (cp.totalBytes <= 0L || cp.totalBytes != probe.totalBytes) return null
            if (cp.etag != null && probe.etag != null && cp.etag != probe.etag) return null
            if (cp.lastModified != null && probe.lastModified != null && cp.lastModified != probe.lastModified) return null
            cp
        } catch (_: Exception) {
            null
        }
    }

    private fun chooseConnectionCount(probe: ProbeInfo): Int {
        if (!probe.supportsRanges || probe.totalBytes < MIN_SEGMENTED_BYTES) return 1
        if (UrlUtils.hostMatches(probe.finalUrl, "archive.org")) return 6
        return 8
    }

    private fun probeUrl(url: String, headers: Map<String, String>, type: String): ProbeInfo? {
        return try {
            // Fast HEAD first so servers don't open a streaming body
            val headReq = Request.Builder().url(url).apply {
                headers.forEach { (k, v) -> header(k, v) }
            }.head().build()

            val headResp = runCatching { fastClient.newCall(headReq).execute() }.getOrNull()
            val headInfo = headResp?.use { resp ->
                if (resp.isSuccessful) {
                    val ct = resp.header("Content-Type")?.lowercase().orEmpty()
                    if (ct.contains("text/html") || ct.contains("mpegurl") || ct.contains("dash+xml")) {
                        return@probeUrl null
                    }
                    val totalBytes = resp.header("Content-Length")?.toLongOrNull()?.takeIf { it > 1024 } ?: 0L
                    val acceptsRanges = resp.header("Accept-Ranges")?.equals("bytes", ignoreCase = true) == true
                    if (totalBytes > 0L && acceptsRanges) {
                        val finalUrl = resp.request.url.toString()
                        return@use ProbeInfo(
                            finalUrl = finalUrl,
                            totalBytes = totalBytes,
                            supportsRanges = true,
                            extension = resolveExtension(finalUrl, ct, type),
                            etag = resp.header("ETag"),
                            lastModified = resp.header("Last-Modified")
                        )
                    }
                }
                null
            }
            if (headInfo != null) return headInfo

            // Fallback: 2-byte Range GET (handles servers that reject HEAD or omit Accept-Ranges on HEAD)
            val rangeReq = Request.Builder().url(url).apply {
                headers.forEach { (k, v) -> header(k, v) }
                header("Range", "bytes=0-1")
            }.get().build()

            fastClient.newCall(rangeReq).execute().use { resp ->
                if (!resp.isSuccessful && resp.code != 206) return null
                val contentType = resp.header("Content-Type")?.lowercase().orEmpty()
                if (contentType.contains("text/html") ||
                    contentType.contains("mpegurl") ||
                    contentType.contains("dash+xml")
                ) return null

                val finalUrl = resp.request.url.toString()
                val supportsRanges = resp.code == 206 ||
                    resp.header("Accept-Ranges")?.equals("bytes", ignoreCase = true) == true
                val totalBytes = resp.header("Content-Range")?.substringAfter('/')?.toLongOrNull()
                    ?: resp.header("Content-Length")?.toLongOrNull()?.takeIf { resp.code == 200 && it > 1024 }
                    ?: 0L

                val ext = resolveExtension(finalUrl, contentType, type)
                ProbeInfo(
                    finalUrl = finalUrl,
                    totalBytes = totalBytes,
                    supportsRanges = supportsRanges && totalBytes > 0L,
                    extension = ext,
                    etag = resp.header("ETag"),
                    lastModified = resp.header("Last-Modified")
                )
            }
        } catch (e: Exception) {
            Log.d(TAG, "Probe failed for $url: ${e.message}")
            null
        }
    }

    private suspend fun downloadRangeBlock(
        url: String,
        headers: Map<String, String>,
        blockIdx: Int,
        rangeEnd: Long,
        blockCursors: LongArray,
        channel: FileChannel,
        downloaded: AtomicLong,
        activeCalls: MutableSet<Call>,
        buffer: ByteArray,
        isPauseRequested: () -> Boolean
    ) {
        var cursor = blockCursors[blockIdx]
        var attempt = 0

        while (cursor <= rangeEnd) {
            currentCoroutineContext().ensureActive()
            if (isPauseRequested()) throw PauseRequestedException()

            val req = Request.Builder().url(url).apply {
                headers.forEach { (k, v) -> header(k, v) }
                header("Range", "bytes=$cursor-$rangeEnd")
            }.get().build()

            val call = fastClient.newCall(req)
            activeCalls.add(call)
            try {
                call.execute().use { resp ->
                    if (resp.code == 429 || resp.code == 503) {
                        throw RateLimitException("HTTP ${resp.code}")
                    }
                    if (resp.code != 206 && !(resp.code == 200 && cursor == 0L)) {
                        throw IllegalStateException("Unexpected HTTP ${resp.code} for range $cursor-$rangeEnd")
                    }
                    val input = resp.body.byteStream()
                    while (cursor <= rangeEnd) {
                        currentCoroutineContext().ensureActive()
                        if (isPauseRequested()) throw PauseRequestedException()
                        val maxToRead = minOf(buffer.size.toLong(), (rangeEnd - cursor + 1)).toInt()
                        val read = input.read(buffer, 0, maxToRead)
                        if (read == -1) break
                        writeFullyAt(channel, buffer, read, cursor)
                        cursor += read
                        blockCursors[blockIdx] = cursor
                        downloaded.addAndGet(read.toLong())
                    }
                }
                if (cursor <= rangeEnd) {
                    throw IllegalStateException("Premature EOF at $cursor / $rangeEnd")
                }
            } catch (e: PauseRequestedException) {
                call.cancel()
                throw e
            } catch (e: CancellationException) {
                call.cancel()
                throw e
            } catch (e: Exception) {
                if (isPauseRequested()) throw PauseRequestedException()
                attempt++
                if (attempt >= MAX_CHUNK_RETRIES) throw e
                val backoff = if (e is RateLimitException) 1500L * attempt else 700L * attempt
                delay(backoff)
            } finally {
                activeCalls.remove(call)
            }
        }
    }

    private suspend fun downloadSingleStream(
        url: String,
        headers: Map<String, String>,
        channel: FileChannel,
        downloaded: AtomicLong,
        activeCalls: MutableSet<Call>,
        isPauseRequested: () -> Boolean
    ) {
        val req = Request.Builder().url(url).apply {
            headers.forEach { (k, v) -> header(k, v) }
        }.get().build()
        val call = fastClient.newCall(req)
        activeCalls.add(call)
        try {
            call.execute().use { resp ->
                if (!resp.isSuccessful) throw IllegalStateException("HTTP ${resp.code}")
                val input = resp.body.byteStream()
                val buffer = ByteArray(BUFFER_SIZE)
                var pos = 0L
                while (true) {
                    currentCoroutineContext().ensureActive()
                    if (isPauseRequested()) throw PauseRequestedException()
                    val read = input.read(buffer)
                    if (read == -1) break
                    writeFullyAt(channel, buffer, read, pos)
                    pos += read
                    downloaded.addAndGet(read.toLong())
                }
            }
        } catch (e: PauseRequestedException) {
            call.cancel()
            throw e
        } catch (e: CancellationException) {
            call.cancel()
            throw e
        } finally {
            activeCalls.remove(call)
        }
    }

    /** Positional FileChannel write is thread-safe for non-overlapping ranges on Android/POSIX. */
    private fun writeFullyAt(channel: FileChannel, bytes: ByteArray, length: Int, startOffset: Long) {
        val bb = ByteBuffer.wrap(bytes, 0, length)
        var pos = startOffset
        while (bb.hasRemaining()) {
            val written = channel.write(bb, pos)
            if (written <= 0) throw IllegalStateException("Zero bytes written to FileChannel")
            pos += written
        }
    }

    private fun resolveExtension(url: String, contentType: String, type: String): String {
        val pathExt = try {
            URI(url).path?.substringAfterLast('/')?.substringAfterLast('.', "")?.lowercase().orEmpty()
        } catch (_: Exception) {
            ""
        }
        val known = setOf("mp4", "mkv", "webm", "m4v", "mov", "ogv", "m4a", "mp3", "ogg", "opus", "flac")
        if (pathExt in known) return pathExt
        return when {
            contentType.contains("matroska") -> "mkv"
            contentType.contains("webm") -> "webm"
            contentType.contains("quicktime") -> "mov"
            contentType.contains("mpeg") && type == "audio" -> "mp3"
            contentType.contains("mp4") && type == "audio" -> "m4a"
            contentType.contains("ogg") -> if (type == "audio") "ogg" else "ogv"
            else -> if (type == "audio") "m4a" else "mp4"
        }
    }

    private fun abortCalls(calls: MutableSet<Call>) {
        val snapshot = synchronized(calls) { calls.toList() }
        snapshot.forEach { try { it.cancel() } catch (_: Exception) {} }
    }

    private class RateLimitException(message: String) : Exception(message)
    private class PauseRequestedException : CancellationException("Paused")
}
