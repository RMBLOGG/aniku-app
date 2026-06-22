package com.example.network

import android.content.Context
import android.util.Log
import android.webkit.*
import kotlinx.coroutines.*
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Extractor Blogger via hidden WebView.
 * Blogger video.g butuh Google cookie — OkHttp tidak bisa dapat itu.
 * WebView Android otomatis pakai CookieManager yang share cookie dengan akun Google di device.
 *
 * Flow:
 * 1. Load video.g?token=... di WebView tersembunyi
 * 2. Intercept semua network request yang keluar
 * 3. Kalau ada URL googlevideo.com → itu direct stream URL-nya
 * 4. Return ke ExoPlayer
 */
object BloggerWebViewExtractor {

    private const val TAG = "BloggerWVExtractor"
    private const val TIMEOUT_MS = 15_000L

    /**
     * Resolve Blogger video URL → direct googlevideo.com URL.
     * WAJIB dipanggil dari Main thread (WebView requirement).
     * Return null kalau timeout atau tidak ketemu URL video.
     */
    suspend fun resolve(context: Context, bloggerUrl: String): String? =
        withContext(Dispatchers.Main) {
            suspendCancellableCoroutine { continuation ->
                var webView: WebView? = null
                var resolved = false

                fun cleanup() {
                    webView?.apply {
                        stopLoading()
                        onPause()
                        pauseTimers()
                        destroy()
                    }
                    webView = null
                }

                fun deliver(url: String?) {
                    if (resolved) return
                    resolved = true
                    cleanup()
                    continuation.resume(url)
                }

                // Timeout fallback
                val timeoutJob = CoroutineScope(Dispatchers.Main).launch {
                    delay(TIMEOUT_MS)
                    if (!resolved) {
                        Log.w(TAG, "Timeout waiting for googlevideo URL from $bloggerUrl")
                        deliver(null)
                    }
                }

                continuation.invokeOnCancellation {
                    timeoutJob.cancel()
                    cleanup()
                }

                webView = WebView(context).apply {
                    settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        mediaPlaybackRequiresUserGesture = false
                        mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                        userAgentString = "Mozilla/5.0 (Linux; Android 13; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
                    }

                    // Aktifkan cookie supaya Google session bisa dipakai
                    CookieManager.getInstance().setAcceptCookie(true)
                    CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)

                    webViewClient = object : WebViewClient() {
                        override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                            super.onPageStarted(view, url, favicon)
                            // Mute SEDINI mungkin, sebelum video sempat autoplay & bersuara.
                            // WebView ini cuma alat extract URL, bukan untuk diputar — kalau tidak
                            // dimute, dia akan autoplay sendiri dan dobel dengan ExoPlayer di UI.
                            view?.evaluateJavascript(
                                """
                                (function() {
                                    function muteAll() {
                                        document.querySelectorAll('video, audio').forEach(function(el) {
                                            el.muted = true;
                                            el.volume = 0;
                                        });
                                    }
                                    muteAll();
                                    // Elemen video Blogger sering muncul belakangan (lazy), jadi observe juga.
                                    var observer = new MutationObserver(muteAll);
                                    observer.observe(document.documentElement, { childList: true, subtree: true });
                                })();
                                """.trimIndent(),
                                null
                            )
                        }

                        override fun shouldInterceptRequest(
                            view: WebView?,
                            request: WebResourceRequest?
                        ): WebResourceResponse? {
                            val url = request?.url?.toString() ?: return null

                            // Tangkap URL googlevideo.com — ini direct stream URL dari Blogger
                            if (url.contains("googlevideo.com") && 
                                (url.contains(".mp4") || url.contains("videoplayback"))) {
                                Log.d(TAG, "Intercepted googlevideo URL: ${url.take(100)}")
                                timeoutJob.cancel()
                                deliver(url)
                            }

                            return null // lanjut load normal
                        }

                        override fun onPageFinished(view: WebView?, url: String?) {
                            super.onPageFinished(view, url)
                            Log.d(TAG, "Page finished: $url")
                            // Inject JS untuk cari video src setelah page load
                            view?.evaluateJavascript(
                                """
                                (function() {
                                    document.querySelectorAll('video, audio').forEach(function(el) {
                                        el.muted = true;
                                        el.volume = 0;
                                        el.pause();
                                    });
                                    var videos = document.querySelectorAll('video');
                                    for (var v of videos) {
                                        if (v.src && v.src.includes('googlevideo')) return v.src;
                                        if (v.currentSrc && v.currentSrc.includes('googlevideo')) return v.currentSrc;
                                    }
                                    var sources = document.querySelectorAll('source');
                                    for (var s of sources) {
                                        if (s.src && (s.src.includes('googlevideo') || s.src.includes('.mp4'))) return s.src;
                                    }
                                    return null;
                                })()
                                """.trimIndent()
                            ) { result ->
                                if (result != null && result != "null" && result.isNotBlank()) {
                                    val cleanUrl = result.trim('"')
                                    if (cleanUrl.startsWith("http")) {
                                        Log.d(TAG, "JS found video src: ${cleanUrl.take(100)}")
                                        timeoutJob.cancel()
                                        deliver(cleanUrl)
                                    }
                                }
                            }
                        }

                        override fun onReceivedError(
                            view: WebView?,
                            request: WebResourceRequest?,
                            error: WebResourceError?
                        ) {
                            Log.w(TAG, "WebView error: ${error?.description} for ${request?.url}")
                        }
                    }

                    // Extract origin dari URL untuk Referer header
                    val origin = Regex("""[?&]origin=([^&]+)""").find(bloggerUrl)
                        ?.groupValues?.get(1)?.let { "https://$it" }
                        ?: "https://www.blogger.com"

                    loadUrl(bloggerUrl, mapOf(
                        "Referer" to origin,
                        "Origin" to origin
                    ))
                }
            }
        }
}
