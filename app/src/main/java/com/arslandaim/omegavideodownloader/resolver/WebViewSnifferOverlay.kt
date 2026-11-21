/*
 * Omega Video Downloader Project Original (2026)
 * arslandaim-hub (GitHub.com/arslandaim-hub)
 * Licenced Under GPL-3.0+
*/
package com.arslandaim.omegavideodownloader.resolver

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import com.arslandaim.omegavideodownloader.*
import com.arslandaim.omegavideodownloader.ui.theme.OmegaTheme
import kotlinx.coroutines.delay
import java.io.ByteArrayInputStream
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Layer 3 of the movie resolver: an ad-blocking WebView sniffer that stays COMPLETELY HIDDEN
 * while automatically discovering `.m3u8`, `.mpd`, `.mp4`, `.mkv`, `.webm` streams loaded by
 * JavaScript / embedded players, and ONLY slides open if the website presents a CAPTCHA /
 * Cloudflare verification challenge (or the user explicitly opens it).
 */
@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WebViewSnifferOverlay(
    url: String,
    forceVisible: Boolean,
    onCaptchaDetected: () -> Unit,
    onStreamsFound: (VideoMetadata) -> Unit,
    onTimeout: (List<StreamCandidate>) -> Unit,
    onDismiss: () -> Unit
) {
    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    var isVisible by remember(url) { mutableStateOf(forceVisible) }
    var captchaPrompt by remember(url) { mutableStateOf(false) }
    var pageTitle by remember(url) { mutableStateOf<String?>(null) }
    var pageThumb by remember(url) { mutableStateOf<String?>(null) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var isPageLoading by remember(url) { mutableStateOf(true) }

    val captured = remember(url) { CopyOnWriteArrayList<StreamCandidate>() }
    val capturedUrls = remember(url) { java.util.Collections.synchronizedSet(HashSet<String>()) }
    var capturedCount by remember(url) { mutableIntStateOf(0) }
    var finished by remember(url) { mutableStateOf(false) }

    LaunchedEffect(forceVisible) {
        if (forceVisible) isVisible = true
    }

    fun buildMetadataFromCaptured(): VideoMetadata {
        val list = captured.toList()
            .sortedWith(
                compareBy<StreamCandidate> {
                    when (it.kind) {
                        StreamKind.HLS -> 0
                        StreamKind.DASH -> 1
                        StreamKind.PROGRESSIVE -> 2
                    }
                }.thenByDescending { it.height }
            )
        val title = pageTitle
            ?.replace(Regex("""\s*[-|•]\s*(Watch|Stream|Free|Online|HD|Movies).*$""", RegexOption.IGNORE_CASE), "")
            ?.trim()
            ?.takeIf { it.isNotBlank() && !it.contains("just a moment", true) }
            ?: UrlUtils.host(url)
            ?: "Movie Stream"

        val qualities = list.mapIndexed { idx, s ->
            val tag = when (s.kind) {
                StreamKind.HLS -> "HLS"
                StreamKind.DASH -> "DASH"
                StreamKind.PROGRESSIVE -> "Direct"
            }
            val label = if (s.height > 0) "${s.height}p ($tag)" else "${s.label} #${idx + 1}"
            VideoQuality(
                label = label,
                url = s.url,
                size = if (s.sizeBytes > 0) StorageUtils.formatSize(s.sizeBytes) else "Variable",
                type = "video",
                formatId = "best",
                isCombined = true,
                sizeBytes = s.sizeBytes,
                useDirectUrl = true,
                headers = s.headers
            )
        }

        return VideoMetadata(
            title = title,
            qualities = qualities,
            thumbnailUrl = pageThumb,
            isPlaylist = false,
            sourceUrl = url,
            streams = list,
            resolvedBy = ResolveSource.BROWSER
        )
    }

    fun completeWithStreams() {
        if (finished || captured.isEmpty()) return
        finished = true
        onStreamsFound(buildMetadataFromCaptured())
    }

    fun recordCandidate(streamUrl: String, kind: StreamKind, rawHeaders: Map<String, String>) {
        if (finished || !isLikelyVideoStream(streamUrl, kind)) return
        if (!capturedUrls.add(streamUrl)) return

        val cookie = try { CookieManager.getInstance().getCookie(streamUrl) } catch (_: Exception) { null }
        val pageCookie = try { CookieManager.getInstance().getCookie(url) } catch (_: Exception) { null }
        val mergedHeaders = buildMap {
            put("User-Agent", YtDlpManager.DESKTOP_UA)
            put("Referer", rawHeaders.entries.firstOrNull { it.key.equals("Referer", true) }?.value ?: url)
            rawHeaders.entries.firstOrNull { it.key.equals("Origin", true) }?.let { put("Origin", it.value) }
            val effectiveCookie = cookie ?: pageCookie
            if (!effectiveCookie.isNullOrBlank()) put("Cookie", effectiveCookie)
        }

        val heightHint = Regex("""(\d{3,4})p""", RegexOption.IGNORE_CASE).find(streamUrl)?.groupValues?.getOrNull(1)?.toIntOrNull()
            ?.takeIf { it in 240..2160 } ?: 0
        val label = when {
            heightHint > 0 -> "${heightHint}p"
            kind == StreamKind.HLS -> "Auto (Adaptive HLS)"
            kind == StreamKind.DASH -> "Auto (Adaptive DASH)"
            else -> "Direct Video"
        }

        captured.add(
            StreamCandidate(
                url = streamUrl,
                kind = kind,
                label = label,
                height = heightHint,
                headers = mergedHeaders,
                source = ResolveSource.BROWSER
            )
        )
        mainHandler.post {
            capturedCount = captured.size
        }
    }

    // Auto-complete shortly after the first stream is captured (grace window catches master manifests & title)
    LaunchedEffect(capturedCount) {
        if (capturedCount > 0 && !finished) {
            delay(if (isVisible) 900L else 1400L)
            completeWithStreams()
        }
    }

    // Hidden timeout: if nothing found and no captcha appeared within 20s, report timeout
    LaunchedEffect(url, isVisible) {
        if (!isVisible) {
            delay(20_000L)
            if (!finished && !isVisible) {
                if (captured.isNotEmpty()) {
                    completeWithStreams()
                } else {
                    onTimeout(emptyList())
                }
            }
        }
    }

    BackHandler(enabled = isVisible) {
        onDismiss()
    }

    DisposableEffect(url) {
        onDispose {
            webViewRef?.apply {
                stopLoading()
                loadUrl("about:blank")
                removeJavascriptInterface("OmegaSniffer")
                webChromeClient = null
                destroy()
            }
            webViewRef = null
        }
    }

    val containerModifier = if (isVisible) {
        Modifier
            .fillMaxSize()
            .zIndex(50f)
            .background(MaterialTheme.colorScheme.background)
    } else {
        // Hidden: keep a real layout size (360x640) so JS players render & trigger streams,
        // but 0 alpha and parked behind the main UI (zIndex -10f) so it intercepts zero touches.
        Modifier
            .size(360.dp, 640.dp)
            .zIndex(-10f)
            .alpha(0f)
    }

    Box(modifier = containerModifier) {
        Column(modifier = Modifier.fillMaxSize()) {
            if (isVisible) {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = if (captchaPrompt) "Verification Required" else "Movie Browser",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = if (captchaPrompt) "Complete the check & tap Play if needed"
                                else "Tap Play on the video to capture stream",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = "Close")
                        }
                    },
                    actions = {
                        IconButton(onClick = { webViewRef?.reload() }) {
                            Icon(Icons.Default.Refresh, contentDescription = "Reload")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )
                if (isPageLoading) {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth().height(2.dp),
                        color = OmegaTheme.colors.primary
                    )
                }
                if (captchaPrompt) {
                    Surface(
                        color = Color(0xFFFF9800).copy(alpha = 0.14f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Security, null, tint = Color(0xFFFF9800), modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "Security check detected. Solve it below — Omega will auto-capture the video.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }

            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                AndroidView(
                    factory = { ctx ->
                        val startHost = UrlUtils.host(url)
                        WebView(ctx).apply {
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                            CookieManager.getInstance().setAcceptCookie(true)
                            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)

                            settings.apply {
                                javaScriptEnabled = true
                                domStorageEnabled = true
                                mediaPlaybackRequiresUserGesture = false
                                useWideViewPort = true
                                loadWithOverviewMode = true
                                javaScriptCanOpenWindowsAutomatically = false
                                setSupportMultipleWindows(false)
                                mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                                userAgentString = YtDlpManager.DESKTOP_UA
                            }

                            val bridge = object {
                                @JavascriptInterface
                                fun onCaptchaFound() {
                                    mainHandler.post {
                                        if (!finished && !isVisible) {
                                            captchaPrompt = true
                                            isVisible = true
                                            onCaptchaDetected()
                                        }
                                    }
                                }

                                @JavascriptInterface
                                fun onMeta(title: String?, thumb: String?) {
                                    mainHandler.post {
                                        if (!title.isNullOrBlank()) pageTitle = title
                                        if (!thumb.isNullOrBlank()) pageThumb = thumb
                                    }
                                }

                                @JavascriptInterface
                                fun onMediaUrl(mediaUrl: String) {
                                    val kind = UrlUtils.mediaKindFromUrl(mediaUrl) ?: StreamKind.PROGRESSIVE
                                    recordCandidate(mediaUrl, kind, emptyMap())
                                }
                            }
                            addJavascriptInterface(bridge, "OmegaSniffer")

                            webChromeClient = object : WebChromeClient() {
                                override fun onReceivedTitle(view: WebView?, title: String?) {
                                    if (!title.isNullOrBlank()) {
                                        pageTitle = title
                                        if (looksLikeChallengeTitle(title) && !isVisible) {
                                            captchaPrompt = true
                                            isVisible = true
                                            onCaptchaDetected()
                                        }
                                    }
                                }
                            }

                            webViewClient = object : WebViewClient() {
                                override fun onPageStarted(view: WebView?, pageUrl: String?, favicon: Bitmap?) {
                                    isPageLoading = true
                                    view?.evaluateJavascript(NETWORK_HOOK_JS, null)
                                }

                                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                                    val target = request?.url?.toString() ?: return false
                                    if (!target.startsWith("http://") && !target.startsWith("https://")) return true
                                    if (isAdDomain(target)) return true
                                    // Block surprise popup redirects to unrelated domains during hidden auto-click
                                    val targetHost = UrlUtils.host(target)
                                    val allowTwitterCrossHost = UrlUtils.isTwitterOrX(url) &&
                                        (UrlUtils.isTwitterOrX(target) || UrlUtils.hostMatches(target, "twimg.com"))
                                    if (!isVisible && request.isForMainFrame && startHost != null && targetHost != null &&
                                        !allowTwitterCrossHost &&
                                        !targetHost.endsWith(startHost) && !startHost.endsWith(targetHost) &&
                                        !looksLikeChallengeUrl(target)
                                    ) {
                                        return true
                                    }
                                    return false
                                }

                                override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
                                    val reqUrl = request?.url?.toString() ?: return null
                                    if (isAdDomain(reqUrl)) {
                                        return WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream(ByteArray(0)))
                                    }
                                    if (looksLikeChallengeUrl(reqUrl)) {
                                        mainHandler.post {
                                            if (!finished && !isVisible) {
                                                captchaPrompt = true
                                                isVisible = true
                                                onCaptchaDetected()
                                            }
                                        }
                                    }
                                    detectStreamKind(reqUrl)?.let { kind ->
                                        recordCandidate(reqUrl, kind, request.requestHeaders.orEmpty())
                                    }
                                    return null
                                }

                                override fun onPageFinished(view: WebView?, pageUrl: String?) {
                                    isPageLoading = false
                                    view?.evaluateJavascript(NETWORK_HOOK_JS, null)
                                    val allowAutoClick = !isVisible
                                    view?.evaluateJavascript(SNIFFER_JS.replace("__AUTO_CLICK__", allowAutoClick.toString()), null)
                                    // Run a second pass after 2.5s for late-loading JS players
                                    mainHandler.postDelayed({
                                        if (!finished && webViewRef === view) {
                                            view?.evaluateJavascript(SNIFFER_JS.replace("__AUTO_CLICK__", (!isVisible).toString()), null)
                                        }
                                    }, 2500L)
                                }

                                override fun onRenderProcessGone(view: WebView?, detail: RenderProcessGoneDetail?): Boolean {
                                    Log.w("WebViewSniffer", "WebView renderer crashed (didCrash=${if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) detail?.didCrash() else "unknown"})")
                                    mainHandler.post {
                                        if (!finished) {
                                            if (captured.isNotEmpty()) completeWithStreams()
                                            else onTimeout(emptyList())
                                        }
                                    }
                                    return true
                                }
                            }

                            webViewRef = this
                            loadUrl(url)
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )

                if (isVisible && capturedCount > 0) {
                    ExtendedFloatingActionButton(
                        onClick = { completeWithStreams() },
                        containerColor = OmegaTheme.colors.primary,
                        contentColor = Color.White,
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .navigationBarsPadding()
                            .padding(bottom = 20.dp)
                    ) {
                        Icon(Icons.Default.PlayArrow, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Use $capturedCount detected stream${if (capturedCount > 1) "s" else ""}", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

private val AD_HOSTS = listOf(
    "doubleclick.net", "googlesyndication.com", "googleadservices.com", "adservice.google",
    "popads.net", "popcash.net", "propellerads.com", "exoclick.com", "adsterra.com",
    "juicyads.com", "trafficjunky.net", "mgid.com", "taboola.com", "outbrain.com",
    "histats.com", "mc.yandex.ru", "facebook.net", "scorecardresearch.com",
    "adnxs.com", "pubmatic.com", "rubiconproject.com", "openx.net", "criteo.com",
    "onclickalgo.com", "acscdn.com", "dtscout.com", "bidgear.com", "monetag.com"
)

private fun isAdDomain(url: String): Boolean {
    val h = UrlUtils.host(url) ?: return false
    return AD_HOSTS.any { h == it || h.endsWith(".$it") }
}

private fun looksLikeChallengeUrl(url: String): Boolean {
    val lower = url.lowercase()
    return lower.contains("challenges.cloudflare.com") ||
        lower.contains("/cdn-cgi/challenge-platform") ||
        lower.contains("google.com/recaptcha") ||
        lower.contains("hcaptcha.com/1/api") ||
        lower.contains("turnstile")
}

private fun looksLikeChallengeTitle(title: String): Boolean {
    val lower = title.lowercase()
    return lower.contains("just a moment") ||
        lower.contains("attention required") ||
        lower.contains("verify you are human") ||
        lower.contains("checking your browser") ||
        lower.contains("security check") ||
        lower.contains("ddos-guard")
}

private fun detectStreamKind(url: String): StreamKind? {
    val lower = url.lowercase()
    // Ignore tiny media segments, subtitles, images, fonts and tracking pixels
    if (lower.endsWith(".ts") || lower.contains(".ts?") ||
        lower.endsWith(".m4s") || lower.contains(".m4s?") ||
        lower.endsWith(".vtt") || lower.endsWith(".srt") ||
        lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".png") || lower.endsWith(".webp") ||
        lower.endsWith(".js") || lower.endsWith(".css") || lower.endsWith(".woff2") ||
        lower.startsWith("blob:") || lower.startsWith("data:")
    ) return null

    UrlUtils.mediaKindFromUrl(url)?.let { return it }

    // Catch extensionless manifest/video endpoints (common on movie CDNs & video.twimg.com)
    return when {
        lower.contains(".m3u8") || lower.contains("format=m3u8") || lower.contains("/hls/master") -> StreamKind.HLS
        lower.contains(".mpd") || lower.contains("format=mpd") -> StreamKind.DASH
        lower.contains("video.twimg.com/") && lower.contains(".mp4") -> StreamKind.PROGRESSIVE
        lower.contains("mime=video") || lower.contains("videoplayback") -> StreamKind.PROGRESSIVE
        else -> null
    }
}

private fun isLikelyVideoStream(url: String, kind: StreamKind): Boolean {
    if (!url.startsWith("http://") && !url.startsWith("https://")) return false
    if (isAdDomain(url)) return false
    val lower = url.lowercase()
    // Skip tiny UI/preview clips or ad prerolls
    if (lower.contains("preroll") || lower.contains("vast") || lower.contains("vpaid") ||
        lower.contains("sprite") || lower.contains("thumb") || lower.contains("poster") ||
        lower.contains("trailer_bg")
    ) return false
    return kind == StreamKind.HLS || kind == StreamKind.DASH || lower.length > 16
}

/**
 * Lightweight JS probe injected into the hidden WebView:
 *  - Reports page title & og:image thumbnail
 *  - Detects Cloudflare / Turnstile / reCAPTCHA / hCaptcha widgets
 *  - Reads <video> / <source> direct URLs
 *  - Gently triggers play buttons on embedded players while hidden so lazy streams start loading
 */
private const val SNIFFER_JS = """
(function() {
  try {
    var title = document.title || '';
    var ogImg = document.querySelector('meta[property="og:image"], meta[name="twitter:image"]');
    OmegaSniffer.onMeta(title, ogImg ? ogImg.content : '');

    var bodyText = (document.body && document.body.innerText ? document.body.innerText.slice(0, 1200) : '').toLowerCase();
    var hasCaptcha = !!(
      document.querySelector('#cf-challenge-running, .cf-turnstile, iframe[src*="challenges.cloudflare.com"], iframe[src*="recaptcha"], iframe[src*="hcaptcha"], .g-recaptcha, .h-captcha') ||
      bodyText.indexOf('verify you are human') !== -1 ||
      bodyText.indexOf('checking your browser') !== -1 ||
      bodyText.indexOf('enable javascript and cookies to continue') !== -1
    );
    if (hasCaptcha) {
      OmegaSniffer.onCaptchaFound();
      return;
    }

    var videos = document.querySelectorAll('video, video source');
    for (var i = 0; i < videos.length; i++) {
      var src = videos[i].currentSrc || videos[i].src || videos[i].getAttribute('src');
      if (src && src.indexOf('http') === 0) {
        OmegaSniffer.onMediaUrl(src);
      }
    }

    if (__AUTO_CLICK__) {
      var selectors = [
        '[data-testid="tweetPhoto"] button',
        'div[role="button"] span',
        '.vjs-big-play-button', '.jw-icon-display', '.plyr__control--overlaid',
        '.play-button', '.btn-play', 'button[aria-label*="Play" i]', 'video'
      ];
      for (var j = 0; j < selectors.length; j++) {
        var el = document.querySelector(selectors[j]);
        if (el) {
          if (selectors[j] === 'div[role="button"] span') {
            var txt = (el.innerText || '').trim().toLowerCase();
            if (txt !== 'view' && txt !== 'show') continue;
          }
          try { if (el.tagName === 'VIDEO' && el.play) el.play(); else el.click(); } catch(e) {}
          break;
        }
      }
    }
  } catch (e) {}
})();
"""

private const val NETWORK_HOOK_JS = """
(function() {
  try {
    if (window.__omegaHooked) return;
    window.__omegaHooked = true;
    var check = function(u) {
      if (typeof u === 'string' && (
          u.indexOf('.m3u8') !== -1 ||
          u.indexOf('.mpd') !== -1 ||
          u.indexOf('mime=video') !== -1 ||
          (u.indexOf('video.twimg.com/') !== -1 && u.indexOf('.mp4') !== -1)
      )) {
        try { OmegaSniffer.onMediaUrl(u); } catch(e) {}
      }
    };
    if (window.fetch) {
      var origFetch = window.fetch;
      window.fetch = function(input) {
        try { check(typeof input === 'string' ? input : (input && input.url)); } catch(e) {}
        return origFetch.apply(this, arguments);
      };
    }
    if (window.XMLHttpRequest && window.XMLHttpRequest.prototype) {
      var origOpen = window.XMLHttpRequest.prototype.open;
      window.XMLHttpRequest.prototype.open = function(method, url) {
        try { check(url); } catch(e) {}
        return origOpen.apply(this, arguments);
      };
    }
  } catch (e) {}
})();
"""
