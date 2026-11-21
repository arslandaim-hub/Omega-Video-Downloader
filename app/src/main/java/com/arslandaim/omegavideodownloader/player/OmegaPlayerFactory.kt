/*
 * Omega Video Downloader Project Original (2026)
 * arslandaim-hub (GitHub.com/arslandaim-hub)
 * Licenced Under GPL-3.0+
*/
package com.arslandaim.omegavideodownloader.player

import android.app.ActivityManager
import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.annotation.OptIn
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.TransferListener
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.drm.DrmSessionManagerProvider
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.exoplayer.upstream.DefaultBandwidthMeter
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy
import com.arslandaim.omegavideodownloader.SettingsManager
import com.arslandaim.omegavideodownloader.YtDlpManager
import com.arslandaim.omegavideodownloader.dataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
import java.io.File
import java.util.concurrent.TimeUnit

/** Shared HTTP client (connection pooling + HTTP/2 multiplexing for segment-heavy streams). */
object OmegaHttp {
    val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectionPool(ConnectionPool(10, 5, TimeUnit.MINUTES))
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .retryOnConnectionFailure(true)
            .build()
    }
}

/**
 * Builds the tuned ExoPlayer used by PlaybackService:
 *  - Multi-connection sliding-window prefetching for progressive movies (MP4/MKV)
 *  - OkHttp + per-stream header injection (Referer/Origin/Cookie/UA) for HLS/DASH
 *  - Manifest-bypassing LRU disk cache (segments & progressive files cached; playlists always fresh)
 *  - Memory-safe LoadControl + adaptive bitrate + decoder fallback
 */
@OptIn(UnstableApi::class)
object OmegaPlayerFactory {
    private const val TAG = "OmegaPlayerFactory"

    @Volatile private var cache: SimpleCache? = null
    @Volatile private var cacheInitStarted = false

    private fun cacheDir(context: Context) = File(context.cacheDir, "stream_cache")

    /** Pre-warms SimpleCache off the main thread during Application startup to prevent PlaybackService ANR. */
    fun initCacheAsync(context: Context, scope: CoroutineScope) {
        if (cacheInitStarted || cache != null) return
        cacheInitStarted = true
        val appContext = context.applicationContext
        scope.launch(Dispatchers.IO) {
            runCatching { cache(appContext) }
        }
    }

    @Synchronized
    fun cache(context: Context): SimpleCache {
        cache?.let { return it }
        val mb = try {
            runBlocking {
                context.applicationContext.dataStore.data.first()[intPreferencesKey("stream_cache_mb")]
                    ?: SettingsManager.DEFAULT_STREAM_CACHE_MB
            }
        } catch (_: Exception) {
            SettingsManager.DEFAULT_STREAM_CACHE_MB
        }
        return SimpleCache(
            cacheDir(context),
            LeastRecentlyUsedCacheEvictor(mb.toLong() * 1024 * 1024),
            StandaloneDatabaseProvider(context.applicationContext)
        ).also { cache = it }
    }

    /** Removes every cached stream (call off the main thread). */
    fun clearCache(context: Context) {
        try {
            val c = cache(context)
            c.keys.toList().forEach { c.removeResource(it) }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to clear stream cache", e)
        }
    }

    fun cacheSizeBytes(context: Context): Long = try { cache(context).cacheSpace } catch (_: Exception) { 0L }

    private fun dataSourceFactory(context: Context): DataSource.Factory {
        val http = OkHttpDataSource.Factory(OmegaHttp.client)
            // Set via default properties (not setUserAgent) so per-stream headers can override it
            .setDefaultRequestProperties(mapOf("User-Agent" to YtDlpManager.DESKTOP_UA))

        val withHeaders = ResolvingDataSource.Factory(http) { spec ->
            val headers = StreamRegistry.headersFor(spec.uri.toString())
            if (headers.isEmpty()) spec else spec.withAdditionalHeaders(headers)
        }

        // Progressive movies use multi-connection prefetching; HLS/DASH use standard OkHttpDataSource
        val progressiveOrHttp = ProgressivePrefetchDataSource.Factory(withHeaders)

        val cached = CacheDataSource.Factory()
            .setCache(cache(context))
            .setUpstreamDataSourceFactory(progressiveOrHttp)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)

        // Bypass SimpleCache for .m3u8 / .mpd manifests so expiring playlists are never served stale
        val smartNetworkFactory = ManifestBypassDataSourceFactory(
            cachedFactory = cached,
            directFactory = withHeaders
        )

        // DefaultDataSource serves content:// and file:// (downloaded items) directly, http(s) via smartNetworkFactory
        return DefaultDataSource.Factory(context, smartNetworkFactory)
    }

    fun create(context: Context): ExoPlayer {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val isLowRam = am?.isLowRamDevice == true
        val targetBufferBytes = if (isLowRam) 32 * 1024 * 1024 else 64 * 1024 * 1024

        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                /* minBufferMs = */ 20_000,
                /* maxBufferMs = */ 60_000,
                /* bufferForPlaybackMs = */ 1_500,
                /* bufferForPlaybackAfterRebufferMs = */ 4_000
            )
            .setTargetBufferBytes(targetBufferBytes)
            .setBackBuffer(20_000, true)
            .setPrioritizeTimeOverSizeThresholds(false)
            .build()

        val mediaSourceFactory = OmegaMediaSourceFactory(
            DefaultMediaSourceFactory(dataSourceFactory(context))
                .setLoadErrorHandlingPolicy(DefaultLoadErrorHandlingPolicy(6))
        )

        val renderers = DefaultRenderersFactory(context)
            .setEnableDecoderFallback(true)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)

        val trackSelector = DefaultTrackSelector(context).apply {
            setParameters(
                buildUponParameters()
                    .setAllowVideoMixedMimeTypeAdaptiveness(true)
                    .setExceedRendererCapabilitiesIfNecessary(true)
            )
        }

        return ExoPlayer.Builder(context, renderers)
            .setMediaSourceFactory(mediaSourceFactory)
            .setLoadControl(loadControl)
            .setTrackSelector(trackSelector)
            .setBandwidthMeter(DefaultBandwidthMeter.getSingletonInstance(context))
            .setAudioAttributes(AudioAttributes.DEFAULT, true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .setHandleAudioBecomingNoisy(true)
            .setSeekBackIncrementMs(10_000)
            .setSeekForwardIncrementMs(10_000)
            .build()
    }
}

/**
 * Routes HLS (`.m3u8`) and DASH (`.mpd`) manifest requests directly to [directFactory] so
 * expiring signed playlists are never served stale from `SimpleCache`, while media segments
 * and progressive MP4/MKV streams flow through [cachedFactory].
 */
@OptIn(UnstableApi::class)
private class ManifestBypassDataSourceFactory(
    private val cachedFactory: DataSource.Factory,
    private val directFactory: DataSource.Factory
) : DataSource.Factory {
    override fun createDataSource(): DataSource = object : DataSource {
        private var active: DataSource? = null
        private val listeners = mutableListOf<TransferListener>()

        private fun isManifest(uri: Uri): Boolean {
            val path = uri.path?.lowercase().orEmpty()
            val full = uri.toString().lowercase()
            return path.endsWith(".m3u8") || path.endsWith(".mpd") ||
                full.contains(".m3u8?") || full.contains(".mpd?")
        }

        override fun addTransferListener(transferListener: TransferListener) {
            listeners.add(transferListener)
            active?.addTransferListener(transferListener)
        }

        override fun open(dataSpec: DataSpec): Long {
            val target = if (isManifest(dataSpec.uri)) directFactory.createDataSource() else cachedFactory.createDataSource()
            listeners.forEach { target.addTransferListener(it) }
            active = target
            return target.open(dataSpec)
        }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
            active?.read(buffer, offset, length) ?: C.RESULT_END_OF_INPUT

        override fun getUri(): Uri? = active?.uri

        override fun getResponseHeaders(): Map<String, List<String>> = active?.responseHeaders.orEmpty()

        override fun close() {
            try {
                active?.close()
            } finally {
                active = null
            }
        }
    }
}

/** Merges a separate audio track (YouTube-style split formats) into the video at playback time. */
@OptIn(UnstableApi::class)
private class OmegaMediaSourceFactory(private val delegate: DefaultMediaSourceFactory) : MediaSource.Factory {

    override fun setDrmSessionManagerProvider(drmSessionManagerProvider: DrmSessionManagerProvider): MediaSource.Factory {
        delegate.setDrmSessionManagerProvider(drmSessionManagerProvider)
        return this
    }

    override fun setLoadErrorHandlingPolicy(loadErrorHandlingPolicy: LoadErrorHandlingPolicy): MediaSource.Factory {
        delegate.setLoadErrorHandlingPolicy(loadErrorHandlingPolicy)
        return this
    }

    override fun getSupportedTypes(): IntArray = delegate.supportedTypes

    override fun createMediaSource(mediaItem: MediaItem): MediaSource {
        val item = StreamRegistry.restore(mediaItem)
        val video = delegate.createMediaSource(item)
        val audioUrl = item.localConfiguration?.uri?.toString()?.let { StreamRegistry.audioFor(it) } ?: return video
        val audio = delegate.createMediaSource(MediaItem.fromUri(audioUrl))
        return MergingMediaSource(video, audio)
    }
}
