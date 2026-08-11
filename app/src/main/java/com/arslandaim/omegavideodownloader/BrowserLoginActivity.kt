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
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.text.font.FontWeight

class BrowserLoginActivity : ComponentActivity() {

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Ensure WebView data is cleared or managed if needed, but here we want to keep session
        val url = intent.getStringExtra("url") ?: "https://twitter.com/login"
        val platformName = intent.getStringExtra("platform") ?: "Twitter/X"

        setContent {
            var webView by remember { mutableStateOf<WebView?>(null) }
            
            MaterialTheme {
                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = { Text("Login to $platformName", fontWeight = FontWeight.Bold) },
                            navigationIcon = {
                                IconButton(onClick = { finish() }) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                                }
                            },
                            actions = {
                                IconButton(onClick = { webView?.reload() }) {
                                    Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                                }
                            },
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = MaterialTheme.colorScheme.surface,
                                titleContentColor = MaterialTheme.colorScheme.onSurface
                            )
                        )
                    }
                ) { padding ->
                    Box(modifier = Modifier
                        .padding(padding)
                        .fillMaxSize()) {
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
        AndroidView(
            factory = { context ->
                WebView(context).apply {
                    layoutParams = android.view.ViewGroup.LayoutParams(
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.databaseEnabled = true
                    settings.useWideViewPort = true
                    settings.loadWithOverviewMode = true
                    settings.setSupportZoom(true)
                    settings.builtInZoomControls = true
                    settings.displayZoomControls = false
                    settings.userAgentString = "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/116.0.0.0 Mobile Safari/537.36"
                    
                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView?, url: String?) {
                            super.onPageFinished(view, url)
                            url?.let { checkCookies(it) }
                        }
                    }
                    loadUrl(url)
                    onWebViewCreated(this)
                }
            },
            modifier = Modifier.fillMaxSize()
        )
    }

    private fun checkCookies(url: String) {
        val cookieManager = CookieManager.getInstance()
        val cookies = cookieManager.getCookie(url)
        if (cookies != null) {
            var captured = false
            when {
                url.contains("twitter.com") || url.contains("x.com") -> {
                    if (cookies.contains("auth_token")) {
                        CookiesManager.saveCookies(this, cookies, "twitter.com")
                        Toast.makeText(this, "Twitter/X cookies captured!", Toast.LENGTH_SHORT).show()
                        captured = true
                    }
                }
                url.contains("facebook.com") -> {
                    if (cookies.contains("c_user") || cookies.contains("datr")) {
                        CookiesManager.saveCookies(this, cookies, "facebook.com")
                        Toast.makeText(this, "Facebook cookies captured!", Toast.LENGTH_SHORT).show()
                        captured = true
                    }
                }
                url.contains("instagram.com") -> {
                    if (cookies.contains("sessionid")) {
                        CookiesManager.saveCookies(this, cookies, "instagram.com")
                        Toast.makeText(this, "Instagram cookies captured!", Toast.LENGTH_SHORT).show()
                        captured = true
                    }
                }
            }
            
            if (captured) {
                cookieManager.flush()
                finish()
            }
        }
    }
}
