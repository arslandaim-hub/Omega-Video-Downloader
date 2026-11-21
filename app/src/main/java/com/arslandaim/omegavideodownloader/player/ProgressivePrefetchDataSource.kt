/*
 * Omega Video Downloader Project Original (2026)
 * arslandaim-hub (GitHub.com/arslandaim-hub)
 * Licenced Under GPL-3.0+
*/
package com.arslandaim.omegavideodownloader.player

import android.net.Uri
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import com.arslandaim.omegavideodownloader.StreamKind
import com.arslandaim.omegavideodownloader.UrlUtils
import kotlinx.coroutines.*
import okhttp3.Call
import okhttp3.ConnectionPool
import okhttp3.Dispatcher
import okhttp3.Protocol
import okhttp3.Request
import java.io.EOFException
import java.io.IOException
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

/**
 * Multi-connection sliding-window HTTP/1.1 prefetch [DataSource] for progressive movies (MP4/MKV/WebM).
 *
 * Why: Rate-limited progressive hosts (such as Archive.org) throttle single TCP connections to
 * ~300-600 KB/s, causing rebuffering on 1080p movies. When a progressive stream supports byte-range
 * requests (`Accept-Ranges: bytes`, length >= 4 MB), this DataSource keeps an 8 MB sliding window
 * prefetched across 4 parallel HTTP/1.1 sockets and automatically falls back to [fallbackFactory]
 * for HLS/DASH manifests, tiny files, or non-range servers.
 */
@OptIn(UnstableApi::class)
class ProgressivePrefetchDataSource(
    private val fallbackFactory: DataSource.Factory
) : BaseDataSource(/* isNetwork = */ true) {

    companion object {
        private const val TAG = "ProgressivePrefetch"
        private const val BLOCK_SIZE = 1024L * 1024L // 1 MB blocks
        private const val PREFETCH_AHEAD_BLOCKS = 8   // 8 MB sliding read-ahead
        private const val WORKER_COUNT = 4
        private const val MIN_PREFETCH_BYTES = 4L * 1024L * 1024L
        private const val MAX_BLOCK_RETRIES = 3

        private val prefetchClient by lazy {
            val dispatcher = Dispatcher().apply {
                maxRequests = 32
                maxRequestsPerHost = 12
            }
            OmegaHttp.client.newBuilder()
                .dispatcher(dispatcher)
                .connectionPool(ConnectionPool(12, 2, TimeUnit.MINUTES))
                .protocols(listOf(Protocol.HTTP_1_1))
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build()
        }

        private data class ProbeCacheEntry(
            val finalUrl: String,
            val totalBytes: Long,
            val supportsRanges: Boolean,
            val probedAt: Long
        )

        private val probeCache = ConcurrentHashMap<String, ProbeCacheEntry>()
    }

    class Factory(private val fallbackFactory: DataSource.Factory) : DataSource.Factory {
        override fun createDataSource(): DataSource = ProgressivePrefetchDataSource(fallbackFactory)
    }

    private var delegateSource: DataSource? = null
    private var currentUri: Uri? = null
    private var activeUrl: String = ""
    private var activeHeaders: Map<String, String> = emptyMap()
    private var totalBytes: Long = 0L
    private var readCursor: Long = 0L
    private var remainingBytes: Long = C.LENGTH_UNSET.toLong()
    private var opened = false

    private var prefetchScope: CoroutineScope? = null
    private val blocks = ConcurrentHashMap<Long, CompletableDeferred<ByteArray>>()
    private val activeCalls = Collections.synchronizedSet(HashSet<Call>())
    private val nextBlockToSchedule = AtomicLong(0L)
    private var totalBlockCount: Long = 0L

    override fun open(dataSpec: DataSpec): Long {
        val uri = dataSpec.uri
        val url = uri.toString()
        currentUri = uri

        if (!isProgressiveEligible(url)) {
            return openFallback(dataSpec)
        }

        val headers = mergeHeaders(url, dataSpec.httpRequestHeaders)
        val probe = probeRangeSupport(url, headers)
        if (probe == null || !probe.supportsRanges || probe.totalBytes < MIN_PREFETCH_BYTES) {
            return openFallback(dataSpec)
        }

        val startPos = dataSpec.position.coerceAtLeast(0L)
        if (startPos >= probe.totalBytes) {
            throw EOFException("Position $startPos beyond content length ${probe.totalBytes}")
        }

        activeUrl = probe.finalUrl
        activeHeaders = headers
        totalBytes = probe.totalBytes
        totalBlockCount = (totalBytes + BLOCK_SIZE - 1) / BLOCK_SIZE
        readCursor = startPos

        val availableFromPos = totalBytes - startPos
        val requestedLen = dataSpec.length
        remainingBytes = if (requestedLen == C.LENGTH_UNSET.toLong()) {
            availableFromPos
        } else {
            minOf(requestedLen, availableFromPos)
        }

        transferInitializing(dataSpec)
        startPrefetchWorkers(startPos / BLOCK_SIZE)
        opened = true
        transferStarted(dataSpec)

        return if (requestedLen == C.LENGTH_UNSET.toLong()) {
            availableFromPos
        } else {
            remainingBytes
        }
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        delegateSource?.let { return it.read(buffer, offset, length) }
        if (length == 0) return 0
        if (remainingBytes == 0L) return C.RESULT_END_OF_INPUT

        val maxRead = if (remainingBytes == C.LENGTH_UNSET.toLong()) {
            length
        } else {
            minOf(length.toLong(), remainingBytes).toInt()
        }
        if (maxRead <= 0) return C.RESULT_END_OF_INPUT

        val blockIdx = readCursor / BLOCK_SIZE
        if (blockIdx >= totalBlockCount) return C.RESULT_END_OF_INPUT

        val blockData = try {
            runBlocking {
                ensureWindowScheduled(blockIdx)
                val deferred = blocks[blockIdx] ?: scheduleBlock(blockIdx)
                deferred.await()
            }
        } catch (e: IOException) {
            throw e
        } catch (e: Exception) {
            throw IOException("Prefetch read failed at offset $readCursor", e)
        }

        val inBlockOffset = (readCursor % BLOCK_SIZE).toInt()
        val availableInBlock = blockData.size - inBlockOffset
        if (availableInBlock <= 0) return C.RESULT_END_OF_INPUT

        val toCopy = minOf(maxRead, availableInBlock)
        System.arraycopy(blockData, inBlockOffset, buffer, offset, toCopy)

        readCursor += toCopy
        if (remainingBytes != C.LENGTH_UNSET.toLong()) {
            remainingBytes -= toCopy
        }
        bytesTransferred(toCopy)

        // Evict consumed blocks behind cursor - 1 to keep memory bounded (~10 MB)
        val evictBefore = (readCursor / BLOCK_SIZE) - 1L
        if (evictBefore > 0L) {
            blocks.keys.removeIf { it < evictBefore }
        }
        ensureWindowScheduled(readCursor / BLOCK_SIZE)

        return toCopy
    }

    override fun getUri(): Uri? = delegateSource?.uri ?: currentUri

    override fun close() {
        delegateSource?.let {
            try {
                it.close()
            } finally {
                delegateSource = null
            }
            return
        }
        if (opened) {
            opened = false
            stopWorkers()
            transferEnded()
        }
        currentUri = null
    }

    private fun openFallback(dataSpec: DataSpec): Long {
        val ds = fallbackFactory.createDataSource()
        delegateSource = ds
        return ds.open(dataSpec)
    }

    private fun isProgressiveEligible(url: String): Boolean {
        if (!url.startsWith("http://") && !url.startsWith("https://")) return false
        val mime = StreamRegistry.mimeFor(url)
        if (mime != null) return false // HLS or DASH
        val kind = UrlUtils.mediaKindFromUrl(url)
        if (kind == StreamKind.HLS || kind == StreamKind.DASH) return false
        val lower = url.lowercase()
        if (lower.contains(".m3u8") || lower.contains(".mpd") || lower.contains(".ts") || lower.contains(".m4s") ||
            lower.endsWith(".srt") || lower.endsWith(".vtt") || lower.endsWith(".ass") || lower.endsWith(".ttml")
        ) {
            return false
        }
        return true
    }

    private fun mergeHeaders(url: String, specHeaders: Map<String, String>): Map<String, String> {
        val regHeaders = StreamRegistry.headersFor(url)
        if (regHeaders.isEmpty() && specHeaders.isEmpty()) return emptyMap()
        return buildMap {
            putAll(regHeaders)
            putAll(specHeaders)
        }
    }

    private fun probeRangeSupport(url: String, headers: Map<String, String>): ProbeCacheEntry? {
        val now = System.currentTimeMillis()
        probeCache[url]?.let { cached ->
            if (now - cached.probedAt < 5 * 60 * 1000L) return cached
        }
        return try {
            val req = Request.Builder().url(url).apply {
                headers.forEach { (k, v) -> header(k, v) }
            }.head().build()

            val entry = prefetchClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use null
                val ct = resp.header("Content-Type")?.lowercase().orEmpty()
                if (ct.contains("mpegurl") || ct.contains("dash+xml") || ct.contains("text/html")) {
                    return@use null
                }
                val length = resp.header("Content-Length")?.toLongOrNull() ?: 0L
                val ranges = resp.header("Accept-Ranges")?.equals("bytes", ignoreCase = true) == true
                ProbeCacheEntry(
                    finalUrl = resp.request.url.toString(),
                    totalBytes = length,
                    supportsRanges = ranges && length > 0L,
                    probedAt = now
                )
            }
            if (entry != null) {
                if (probeCache.size > 40) probeCache.clear()
                probeCache[url] = entry
            }
            entry
        } catch (e: Exception) {
            Log.d(TAG, "HEAD probe skipped for $url: ${e.message}")
            null
        }
    }

    private fun startPrefetchWorkers(startBlock: Long) {
        stopWorkers()
        nextBlockToSchedule.set(startBlock)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        prefetchScope = scope
        ensureWindowScheduled(startBlock)
        repeat(WORKER_COUNT) {
            scope.launch {
                while (isActive) {
                    val windowLimit = minOf(totalBlockCount - 1, (readCursor / BLOCK_SIZE) + PREFETCH_AHEAD_BLOCKS)
                    val candidateIdx = nextBlockToSchedule.get()
                    if (candidateIdx > windowLimit || candidateIdx >= totalBlockCount) {
                        delay(40L)
                        continue
                    }
                    if (!nextBlockToSchedule.compareAndSet(candidateIdx, candidateIdx + 1)) continue
                    val deferred = blocks[candidateIdx] ?: continue
                    if (deferred.isCompleted) continue
                    try {
                        val bytes = fetchBlock(candidateIdx)
                        deferred.complete(bytes)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        deferred.completeExceptionally(e)
                        blocks.remove(candidateIdx, deferred)
                    }
                }
            }
        }
    }

    private fun ensureWindowScheduled(currentBlock: Long) {
        val end = minOf(totalBlockCount - 1, currentBlock + PREFETCH_AHEAD_BLOCKS)
        for (idx in currentBlock..end) {
            scheduleBlock(idx)
        }
    }

    private fun scheduleBlock(blockIdx: Long): CompletableDeferred<ByteArray> =
        blocks.computeIfAbsent(blockIdx) { CompletableDeferred() }

    private suspend fun fetchBlock(blockIdx: Long): ByteArray {
        val start = blockIdx * BLOCK_SIZE
        val end = minOf(totalBytes - 1, start + BLOCK_SIZE - 1)
        val expectedSize = (end - start + 1).toInt()
        var lastError: Exception? = null

        for (attempt in 0 until MAX_BLOCK_RETRIES) {
            currentCoroutineContext().ensureActive()
            val req = Request.Builder().url(activeUrl).apply {
                activeHeaders.forEach { (k, v) -> header(k, v) }
                header("Range", "bytes=$start-$end")
            }.get().build()

            val call = prefetchClient.newCall(req)
            activeCalls.add(call)
            try {
                return call.execute().use { resp ->
                    if (resp.code != 206 && !(resp.code == 200 && start == 0L)) {
                        throw IOException("HTTP ${resp.code} for range $start-$end")
                    }
                    val out = ByteArray(expectedSize)
                    val input = resp.body.byteStream()
                    var totalRead = 0
                    while (totalRead < expectedSize) {
                        currentCoroutineContext().ensureActive()
                        val read = input.read(out, totalRead, expectedSize - totalRead)
                        if (read == -1) break
                        totalRead += read
                    }
                    if (totalRead < expectedSize) out.copyOf(totalRead) else out
                }
            } catch (e: CancellationException) {
                call.cancel()
                throw e
            } catch (e: Exception) {
                lastError = e
                delay(300L * (attempt + 1))
            } finally {
                activeCalls.remove(call)
            }
        }
        throw lastError ?: IOException("Failed to fetch block $blockIdx")
    }

    private fun stopWorkers() {
        prefetchScope?.cancel()
        prefetchScope = null
        val snapshot = synchronized(activeCalls) { activeCalls.toList() }
        snapshot.forEach { try { it.cancel() } catch (_: Exception) {} }
        activeCalls.clear()
        blocks.clear()
    }
}
