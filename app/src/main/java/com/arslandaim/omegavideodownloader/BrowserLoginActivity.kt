/*
 * Omega Video Downloader Project Original (2026)
 * arslandaim-hub (GitHub.com/arslandaim-hub)
 * Licenced Under GPL-3.0+
*/
package com.arslandaim.omegavideodownloader

import android.annotation.SuppressLint
import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.viewinterop.AndroidView

class BrowserLoginActivity : ComponentActivity() {

    private var currentPageUrl: String = "https://x.com/i/flow/login"

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val url = intent.getStringExtra("url") ?: "https://x.com/i/flow/login"
        val platformName = intent.getStringExtra("platform") ?: "X / Twitter"
        currentPageUrl = url

        setContent {
            var webView by remember { mutableStateOf<WebView?>(null) }

            MaterialTheme {
                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = { Text("Sign in to $platformName", fontWeight = FontWeight.Bold) },
                            navigationIcon = {
                                IconButton(onClick = { finish() }) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                                }
                            },
                            actions = {
                                IconButton(onClick = { webView?.reload() }) {
                                    Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                                }
                                TextButton(
                                    onClick = {
                                        if (!tryCaptureCookies(currentPageUrl, requireCt0 = false)) {
                                            Toast.makeText(
                                                this@BrowserLoginActivity,
                                                "Complete sign-in first so session cookies appear.",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    }
                                ) {
                                    Icon(Icons.Default.Check, contentDescription = null)
                                    Text(" Done", fontWeight = FontWeight.Bold)
                                }
                            },
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = MaterialTheme.colorScheme.surface,
                                titleContentColor = MaterialTheme.colorScheme.onSurface
                            )
                        )
                    }
                ) { padding ->
                    Box(
                        modifier = Modifier
                            .padding(padding)
                            .fillMaxSize()
                    ) {
                        BrowserView(url) { wv ->
                            webView = wv
                        }
                    }
                }
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    @Composable
    fun BrowserView(url: String, onWebViewCreated: (WebView) -> Unit) {
        var instance by remember { mutableStateOf<WebView?>(null) }
        DisposableEffect(Unit) {
            onDispose {
                instance?.apply {
                    stopLoading()
                    webViewClient = WebViewClient()
                    destroy()
                }
            }
        }
        AndroidView(
            factory = { context ->
                WebView(context).apply {
                    layoutParams = android.view.ViewGroup.LayoutParams(
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    CookieManager.getInstance().setAcceptCookie(true)
                    CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.databaseEnabled = true
                    settings.useWideViewPort = true
                    settings.loadWithOverviewMode = true
                    settings.setSupportZoom(true)
                    settings.builtInZoomControls = true
                    settings.displayZoomControls = false
                    settings.userAgentString =
                        "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"

                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView?, finishedUrl: String?) {
                            super.onPageFinished(view, finishedUrl)
                            finishedUrl?.let {
                                currentPageUrl = it
                                tryCaptureCookies(it, requireCt0 = true)
                            }
                        }
                    }
                    loadUrl(url)
                    instance = this
                    onWebViewCreated(this)
                }
            },
            modifier = Modifier.fillMaxSize()
        )
    }

    private fun tryCaptureCookies(url: String, requireCt0: Boolean): Boolean {
        val cookieManager = CookieManager.getInstance()
        var captured = false

        when {
            url.contains("twitter.com", true) || url.contains("x.com", true) -> {
                val xCookies = cookieManager.getCookie("https://x.com").orEmpty()
                val twitterCookies = cookieManager.getCookie("https://twitter.com").orEmpty()
                val pageCookies = cookieManager.getCookie(url).orEmpty()
                val combined = listOf(twitterCookies, pageCookies, xCookies)
                    .filter { it.isNotBlank() }
                    .joinToString("; ")
                val hasAuth = combined.contains("auth_token=")
                val hasCt0 = combined.contains("ct0=")
                if (hasAuth && (!requireCt0 || hasCt0)) {
                    CookiesManager.saveCookiesForDomains(
                        this,
                        combined,
                        listOf(".x.com", ".twitter.com")
                    )
                    Toast.makeText(this, "X / Twitter session saved!", Toast.LENGTH_SHORT).show()
                    captured = true
                }
            }
            url.contains("facebook.com", true) -> {
                val cookies = cookieManager.getCookie(url).orEmpty()
                if (cookies.contains("c_user") || cookies.contains("datr")) {
                    CookiesManager.saveCookies(this, cookies, "facebook.com")
                    Toast.makeText(this, "Facebook cookies captured!", Toast.LENGTH_SHORT).show()
                    captured = true
                }
            }
            url.contains("instagram.com", true) -> {
                val cookies = cookieManager.getCookie(url).orEmpty()
                if (cookies.contains("sessionid")) {
                    CookiesManager.saveCookies(this, cookies, "instagram.com")
                    Toast.makeText(this, "Instagram cookies captured!", Toast.LENGTH_SHORT).show()
                    captured = true
                }
            }
        }

        if (captured) {
            cookieManager.flush()
            setResult(RESULT_OK)
            finish()
        }
        return captured
    }
}
