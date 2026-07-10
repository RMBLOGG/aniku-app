package com.example.network

import android.content.Context
import android.util.Log
import android.webkit.*
import kotlinx.coroutines.*
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

/**
 * Extractor rubyvidhub.com / streamruby.net via hidden WebView.
 *
 * KENAPA WEBVIEW (bukan regex/OkHttp statis kayak extractPackedJwPlayer):
 * halaman embed-nya emang JWPlayer packed-JS standar dan `file:` (URL
 * master.m3u8 bertoken) BISA diambil via regex statis - tapi begitu URL
 * itu di-fetch tanpa cookie sesi asli, CDN-nya (streamruby.net) balikin
 * 403 Forbidden. Dikonfirmasi manual: curl dengan Referer+User-Agent yang
 * PERSIS sama kayak yang dipakai browser tetap 403, artinya validasi
 * token di CDN ini terikat ke cookie sesi yang di-set server pas halaman
 * embed di-load pertama kali - bukan cuma cocokin header Referer/UA.
 *
 * Makanya di sini kita biarin WebView beneran navigasi ke embedUrl (biar
 * cookie sesi ke-set natural lewat network stack WebView sendiri, bukan
 * kita fetch manual pakai OkHttp terpisah kayak AbyssWebViewExtractor),
 * lalu begitu halaman selesai load kita paksa `jwplayer().play()` supaya
 * request manifest/segment video asli ke-trigger tanpa perlu klik overlay
 * iklan/ADBlock-check manual - dan kita nguping request itu dari
 * shouldInterceptRequest.
 */
object StreamRubyWebViewExtractor {

    private const val TAG = "StreamRubyWVExtractor"
    private const val TIMEOUT_MS = 20_000L

    private const val DESKTOP_UA =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

    /**
     * Sama prinsipnya kayak AUTOPLAY_SCRIPT di AbyssWebViewExtractor - manggil
     * jwplayer().play() langsung begitu instance-nya siap, bypass overlay
     * "Disable ADBlock to watch the video!" yang keliatan di HTML embed
     * rubyvidhub (murni otomatisasi klik-play, bukan bypass proteksi apapun).
     */
    private const val AUTOPLAY_JS = """
        (function() {
            var tries = 0;
            var iv = setInterval(function() {
                tries++;
                try {
                    if (typeof jwplayer !== 'undefined') {
                        var p = jwplayer();
                        if (p && typeof p.play === 'function') {
                            p.play();
                            if (p.getState && p.getState() === 'playing') {
                                clearInterval(iv);
                            }
                        }
                    }
                } catch (e) {}
                if (tries > 36) clearInterval(iv); // ~18 detik @ 500ms
            }, 500);
        })();
    """

    // Domain infrastruktur/iklan yang harus diabaikan supaya gak ketuker
    // sama request manifest/segment video asli.
    private val IGNORED_HOST_FRAGMENTS = listOf(
        "googletagmanager.com",
        "google-analytics.com",
        "googlesyndication.com",
        "doubleclick.net",
        "cloudflare.com",
        "cdnjs.cloudflare.com",
        "jquery",
        "fuckadblock",
        "noadblocker"
    )

    private fun isLikelyVideoUrl(url: String): Boolean {
        val lower = url.lowercase()
        if (IGNORED_HOST_FRAGMENTS.any { lower.contains(it) }) return false
        return lower.contains(".m3u8") ||
            (lower.contains(".mp4") && !lower.contains(".min.js"))
    }

    /**
     * Resolve embed rubyvidhub/streamruby -> direct stream URL (m3u8/mp4)
     * buat ExoPlayer, LENGKAP dengan cookie sesi yang valid (dikasih ke
     * ResolvedStream.headers sebagai header "Cookie" manual, karena
     * ExoPlayer/OkHttpDataSource kita gak share CookieJar sama WebView).
     * WAJIB dipanggil dari Main thread (WebView requirement).
     * Return null kalau timeout / gak ketemu (caller fallback ke WebView biasa).
     */
    suspend fun resolve(context: Context, embedUrl: String, referer: String? = null): ResolvedStream? =
        withContext(Dispatchers.Main) {
            suspendCancellableCoroutine { continuation ->
                var webView: WebView? = null
                var resolved = false

                fun cleanup() {
                    webView?.apply {
                        stopLoading()
                        destroy()
                    }
                    webView = null
                }

                fun deliver(stream: ResolvedStream?) {
                    if (resolved) return
                    resolved = true
                    cleanup()
                    continuation.resume(stream)
                }

                val timeoutJob = CoroutineScope(Dispatchers.Main).launch {
                    delay(TIMEOUT_MS)
                    if (!resolved) {
                        Log.w(TAG, "Timeout waiting for stream URL from $embedUrl")
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
                        userAgentString = DESKTOP_UA
                    }

                    // Cookie sesi dari embed page ini persis yang bikin CDN
                    // nolak/nerima token - WAJIB nyala, beda dari client OkHttp
                    // statis (VideoExtractor.client) yang emang gak punya cookie jar.
                    CookieManager.getInstance().setAcceptCookie(true)
                    CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)

                    settings.javaScriptCanOpenWindowsAutomatically = false
                    webChromeClient = object : WebChromeClient() {
                        override fun onCreateWindow(
                            view: WebView?,
                            isDialog: Boolean,
                            isUserGesture: Boolean,
                            resultMsg: android.os.Message?
                        ): Boolean = false // block semua popup/new window/tab iklan
                    }

                    webViewClient = object : WebViewClient() {
                        override fun shouldInterceptRequest(
                            view: WebView?,
                            request: WebResourceRequest?
                        ): WebResourceResponse? {
                            val url = request?.url?.toString() ?: return null

                            if (isLikelyVideoUrl(url)) {
                                Log.d(TAG, "Intercepted stream URL: ${url.take(120)}")
                                timeoutJob.cancel()
                                // Cookie yang lagi kepake WebView di domain ini - dikasih
                                // manual ke ResolvedStream.headers karena ExoPlayer/OkHttp
                                // (streamingHttpClient) gak otomatis share CookieJar sama
                                // WebView punya app.
                                val cookie = runCatching {
                                    CookieManager.getInstance().getCookie(url)
                                }.getOrNull()

                                val headers = buildMap {
                                    put("Referer", embedUrl)
                                    put("User-Agent", DESKTOP_UA)
                                    if (!cookie.isNullOrBlank()) put("Cookie", cookie)
                                }

                                deliver(
                                    ResolvedStream(
                                        url = url,
                                        isHls = url.contains(".m3u8"),
                                        headers = headers
                                    )
                                )
                                return null
                            }
                            return null // biarin tetap load normal (biar cookie sesi kebentuk natural)
                        }

                        override fun onPageFinished(view: WebView?, url: String?) {
                            super.onPageFinished(view, url)
                            view?.evaluateJavascript(AUTOPLAY_JS, null)
                        }

                        override fun onReceivedError(
                            view: WebView?,
                            request: WebResourceRequest?,
                            error: WebResourceError?
                        ) {
                            Log.w(TAG, "WebView error: ${error?.description} for ${request?.url}")
                        }
                    }

                    val headers = mutableMapOf<String, String>()
                    if (!referer.isNullOrBlank()) headers["Referer"] = referer
                    loadUrl(embedUrl, headers)
                }
            }
        }
}
