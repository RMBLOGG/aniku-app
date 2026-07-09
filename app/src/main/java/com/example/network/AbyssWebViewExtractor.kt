package com.example.network

import android.content.Context
import android.util.Log
import android.webkit.*
import kotlinx.coroutines.*
import kotlin.coroutines.resume

/**
 * Extractor abyssplayer.com via hidden WebView.
 *
 * KENAPA WEBVIEW (bukan regex/OkHttp kayak host lain): halaman embed-nya gak
 * nyimpen source video mentah di HTML - yang ada cuma payload terenkripsi
 * (field "media" di dalam blob base64 `datas`) yang didekripsi oleh
 * lite.bundle.js/core.bundle.js mereka sendiri di browser. Kita gak coba
 * bongkar skema enkripsinya - biarin JS asli mereka yang jalan & decode,
 * kita cuma "nguping" request network asli begitu JWPlayer mulai narik
 * manifest/segmentnya (sama prinsipnya kayak BloggerWebViewExtractor).
 *
 * CATATAN GUARD: script di halaman abyss ngecek
 *   `if (top.location == self.location && !hostname.endsWith(".abyss.to")) location = "https://abyss.to"`
 * Makanya URL abyss WAJIB di-load di dalam iframe (bukan top-level langsung),
 * persis kayak cara aslinya di-embed oleh situs sumber (mis. animasu).
 */
object AbyssWebViewExtractor {

    private const val TAG = "AbyssWVExtractor"
    private const val TIMEOUT_MS = 20_000L

    // Domain infrastruktur/iklan abyss yang HARUS diabaikan supaya gak
    // ketuker sama request video asli (semua ini kepanggil normal di
    // setiap load halaman, gak ada hubungannya sama source videonya).
    private val IGNORED_HOST_FRAGMENTS = listOf(
        "iamcdn.net",
        "googletagmanager.com",
        "google-analytics.com",
        "googlesyndication.com",
        "pixel.morphify.net",
        "decafeligiblyhad.com",
        "cloudflare.com",
        "cdnjs.cloudflare.com",
        "fuckadblock"
    )

    private fun isLikelyVideoUrl(url: String): Boolean {
        val lower = url.lowercase()
        if (IGNORED_HOST_FRAGMENTS.any { lower.contains(it) }) return false
        return lower.contains(".m3u8") ||
            (lower.contains(".mp4") && !lower.contains(".min.js") && !lower.contains(".bundle."))
    }

    /**
     * Resolve embed abyssplayer -> direct stream URL (m3u8/mp4) buat ExoPlayer.
     * WAJIB dipanggil dari Main thread (WebView requirement).
     * Return null kalau timeout / gak ketemu (caller fallback ke WebView biasa).
     */
    suspend fun resolve(context: Context, abyssUrl: String, referer: String? = null): ResolvedStream? =
        withContext(Dispatchers.Main) {
            suspendCancellableCoroutine { continuation ->
                var webView: WebView? = null
                var resolved = false

                // Origin dari Referer asli (mis. situs animasu) - dipakai sebagai
                // base URL wrapper iframe, biar Referer header ke abyss keisi benar
                // dan biar `top.location == self.location` di script guard-nya FALSE
                // (karena abyss di-load sebagai child iframe, bukan top frame).
                val baseOrigin = referer
                    ?.let { runCatching { java.net.URI(it) }.getOrNull() }
                    ?.let { "${it.scheme ?: "https"}://${it.host}" }
                    ?: "https://v1.animasu.work"

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
                        Log.w(TAG, "Timeout waiting for stream URL from $abyssUrl")
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
                        userAgentString = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                            "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
                    }

                    CookieManager.getInstance().setAcceptCookie(true)
                    CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)

                    // Jangan biarin ad script buka window/tab baru (window.open) -
                    // ini juga jadi jaring pengaman kalau overlay click ke-trigger
                    // gak sengaja lewat JS sintetis.
                    settings.javaScriptCanOpenWindowsAutomatically = false
                    webChromeClient = object : WebChromeClient() {
                        override fun onCreateWindow(
                            view: WebView?,
                            isDialog: Boolean,
                            isUserGesture: Boolean,
                            resultMsg: android.os.Message?
                        ): Boolean = false // block semua popup/new window
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
                                deliver(
                                    ResolvedStream(
                                        url = url,
                                        isHls = url.contains(".m3u8"),
                                        headers = mapOf(
                                            "Referer" to abyssUrl,
                                            "Origin" to baseOrigin
                                        )
                                    )
                                )
                            }
                            return null // biarin tetap load normal (biar player jalan sampai narik video)
                        }

                        override fun onReceivedError(
                            view: WebView?,
                            request: WebResourceRequest?,
                            error: WebResourceError?
                        ) {
                            Log.w(TAG, "WebView error: ${error?.description} for ${request?.url}")
                        }
                    }

                    // Wrapper HTML: abyss di-load sebagai IFRAME (bukan top-level),
                    // biar lolos guard `top.location == self.location` di halamannya
                    // dan biar Referer header keisi origin situs sumber.
                    val wrapperHtml = """
                        <!DOCTYPE html>
                        <html><head><meta charset="UTF-8"></head>
                        <body style="margin:0;padding:0;background:#000">
                        <iframe src="${abyssUrl.replace("\"", "%22")}"
                                width="100%" height="100%" frameborder="0"
                                allowfullscreen scrolling="no"
                                style="border:0;position:fixed;top:0;left:0;width:100%;height:100%">
                        </iframe>
                        </body></html>
                    """.trimIndent()

                    loadDataWithBaseURL(baseOrigin, wrapperHtml, "text/html", "UTF-8", null)
                }
            }
        }
}
