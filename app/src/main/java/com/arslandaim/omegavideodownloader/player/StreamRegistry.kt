/*
 * Omega Video Downloader Project Original (2026)
 * arslandaim-hub (GitHub.com/arslandaim-hub)
 * Licenced Under GPL-3.0+
*/
package com.arslandaim.omegavideodownloader.player

import androidx.core.net.toUri
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import com.arslandaim.omegavideodownloader.StreamCandidate
import com.arslandaim.omegavideodownloader.StreamKind
import com.arslandaim.omegavideodownloader.SubtitleTrack
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * In-process registry for online streams. MediaItems travel to the PlaybackService through a
 * MediaController, which cannot carry HTTP headers, so the service-side data source looks them up here.
 */
object StreamRegistry {

    const val SESSION_PREFIX = "stream:"
    private const val MAX_SESSIONS = 20
    private const val MAX_URL_ENTRIES = 120

    data class Session(
        val id: String,
        val title: String,
        val pageUrl: String,
        val candidates: List<StreamCandidate>,
        val thumbnailUrl: String? = null,
        val subtitleTracks: List<SubtitleTrack> = emptyList()
    )

    private val sessions = ConcurrentHashMap<String, Session>()
    private val headersByUrl = ConcurrentHashMap<String, Map<String, String>>()
    private val audioByUrl = ConcurrentHashMap<String, String>()
    private val mimeByUrl = ConcurrentHashMap<String, String>()
    private val subtitlesByUrl = ConcurrentHashMap<String, List<SubtitleTrack>>()

    /** Referer/Origin/UA/Cookie/Authorization of the active stream; applied to HLS/DASH segments. */
    @Volatile var activeHeaders: Map<String, String> = emptyMap()
        private set

    fun createSession(
        title: String,
        pageUrl: String,
        candidates: List<StreamCandidate>,
        thumbnailUrl: String?,
        subtitleTracks: List<SubtitleTrack> = emptyList()
    ): String {
        val id = UUID.randomUUID().toString().take(12)
        sessions[id] = Session(id, title, pageUrl, candidates, thumbnailUrl, subtitleTracks)
        if (sessions.size > MAX_SESSIONS) {
            sessions.keys.firstOrNull { it != id }?.let { evictId ->
                sessions.remove(evictId)?.candidates?.forEach { c ->
                    headersByUrl.remove(c.url)
                    audioByUrl.remove(c.url)
                    mimeByUrl.remove(c.url)
                    subtitlesByUrl.remove(c.url)
                    c.audioUrl?.let { headersByUrl.remove(it) }
                }
            }
        }
        return SESSION_PREFIX + id
    }

    fun session(key: String): Session? = sessions[key.removePrefix(SESSION_PREFIX)]

    fun findSessionByPageUrl(pageUrl: String): String? {
        val target = pageUrl.trim()
        if (target.isEmpty()) return null
        val match = sessions.values.firstOrNull {
            it.pageUrl.trim() == target && it.candidates.isNotEmpty()
        } ?: return null
        return SESSION_PREFIX + match.id
    }

    fun replaceCandidates(key: String, candidates: List<StreamCandidate>) {
        val id = key.removePrefix(SESSION_PREFIX)
        sessions[id]?.let { sessions[id] = it.copy(candidates = candidates) }
    }

    fun headersFor(url: String): Map<String, String> = headersByUrl[url] ?: activeHeaders
    fun audioFor(url: String): String? = audioByUrl[url]
    fun mimeFor(url: String): String? = mimeByUrl[url]

    private fun toSubtitleConfigs(tracks: List<SubtitleTrack>): List<MediaItem.SubtitleConfiguration> =
        tracks.mapIndexed { idx, sub ->
            MediaItem.SubtitleConfiguration.Builder(sub.url.toUri())
                .setMimeType(sub.mimeType)
                .setLanguage(sub.language)
                .setLabel(sub.label)
                .setSelectionFlags(if (idx == 0) C.SELECTION_FLAG_DEFAULT else 0)
                .build()
        }

    /** Registers a candidate's side data and builds the MediaItem to hand to the MediaController. */
    fun prepare(
        candidate: StreamCandidate,
        title: String,
        subtitles: List<SubtitleTrack> = emptyList()
    ): MediaItem {
        pruneUrlMapsIfNeeded()
        val headers = candidate.headers
        if (headers.isNotEmpty()) {
            headersByUrl[candidate.url] = headers
            candidate.audioUrl?.let { headersByUrl[it] = headers }
        }
        activeHeaders = headers.filterKeys { k ->
            k.equals("Referer", true) ||
                k.equals("Origin", true) ||
                k.equals("User-Agent", true) ||
                k.equals("Cookie", true) ||
                k.equals("Authorization", true)
        }
        candidate.audioUrl?.let { audioByUrl[candidate.url] = it }
        val mime = when (candidate.kind) {
            StreamKind.HLS -> MimeTypes.APPLICATION_M3U8
            StreamKind.DASH -> MimeTypes.APPLICATION_MPD
            StreamKind.PROGRESSIVE -> null
        }
        mime?.let { mimeByUrl[candidate.url] = it }
        if (subtitles.isNotEmpty()) {
            subtitlesByUrl[candidate.url] = subtitles
        } else {
            subtitlesByUrl.remove(candidate.url)
        }

        val builder = MediaItem.Builder()
            .setUri(candidate.url)
            .setMediaId(candidate.url)
            .setMimeType(mime)
            .setRequestMetadata(MediaItem.RequestMetadata.Builder().setMediaUri(candidate.url.toUri()).build())
            .setMediaMetadata(MediaMetadata.Builder().setTitle(title).setDisplayTitle(title).build())
        if (subtitles.isNotEmpty()) {
            builder.setSubtitleConfigurations(toSubtitleConfigs(subtitles))
        }
        return builder.build()
    }

    /** Rebuilds a MediaItem whose local configuration was stripped while crossing the session boundary. */
    fun restore(item: MediaItem): MediaItem {
        if (item.localConfiguration != null) return item
        val uri = item.requestMetadata.mediaUri ?: return item
        val urlStr = uri.toString()
        val subs = subtitlesByUrl[urlStr].orEmpty()
        val builder = item.buildUpon()
            .setUri(uri)
            .setMimeType(mimeFor(urlStr))
        if (subs.isNotEmpty()) {
            builder.setSubtitleConfigurations(toSubtitleConfigs(subs))
        }
        return builder.build()
    }

    private fun pruneUrlMapsIfNeeded() {
        if (headersByUrl.size > MAX_URL_ENTRIES) {
            val activeUrls = sessions.values.flatMapTo(HashSet()) { s ->
                s.candidates.flatMap { listOfNotNull(it.url, it.audioUrl) }
            }
            headersByUrl.keys.retainAll(activeUrls)
            audioByUrl.keys.retainAll(activeUrls)
            mimeByUrl.keys.retainAll(activeUrls)
            subtitlesByUrl.keys.retainAll(activeUrls)
        }
    }
}
