package com.example.ui

import android.annotation.SuppressLint
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.os.HandlerCompat

/**
 * Widget Cloudflare Turnstile (invisible captcha) buat proteksi form daftar dari bot.
 *
 * Cara kerja: render halaman HTML kecil yang isinya cuma widget Turnstile.
 * Kalau berhasil diverifikasi (otomatis, biasanya gak perlu user interaksi apa-apa),
 * token dikirim balik ke Kotlin lewat JavascriptInterface, dipanggil lewat [onToken].
 *
 * @param siteKey Site Key dari Cloudflare Turnstile dashboard (bukan Secret Key).
 * @param onToken dipanggil sekali dengan token captcha begitu verifikasi sukses.
 * @param onError dipanggil kalau widget gagal/expired, biar bisa kasih tau user buat coba lagi.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun TurnstileWidget(
    siteKey: String,
    modifier: Modifier = Modifier,
    onToken: (String) -> Unit,
    onError: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val mainHandler = remember { HandlerCompat.createAsync(android.os.Looper.getMainLooper()) }
    val currentOnToken = rememberUpdatedState(onToken)
    val currentOnError = rememberUpdatedState(onError)

    val html = remember(siteKey) {
        """
        <!DOCTYPE html>
        <html>
        <head>
          <meta name="viewport" content="width=device-width, initial-scale=1.0">
          <script src="https://challenges.cloudflare.com/turnstile/v0/api.js" async defer></script>
          <style>
            html, body { margin:0; padding:0; background:transparent; display:flex; justify-content:center; }
          </style>
        </head>
        <body>
          <div class="cf-turnstile"
               data-sitekey="$siteKey"
               data-theme="dark"
               data-callback="onTurnstileToken"
               data-error-callback="onTurnstileError"
               data-expired-callback="onTurnstileError">
          </div>
          <script>
            function onTurnstileToken(token) {
              AndroidBridge.onToken(token);
            }
            function onTurnstileError() {
              AndroidBridge.onError();
            }
          </script>
        </body>
        </html>
        """.trimIndent()
    }

    AndroidView(
        modifier = modifier.height(72.dp),
        factory = { ctx ->
            WebView(ctx).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                webViewClient = WebViewClient()
                addJavascriptInterface(object {
                    @JavascriptInterface
                    fun onToken(token: String) {
                        mainHandler.post { currentOnToken.value(token) }
                    }

                    @JavascriptInterface
                    fun onError() {
                        mainHandler.post { currentOnError.value?.invoke() }
                    }
                }, "AndroidBridge")
                // baseUrl HARUS sama dengan domain yang didaftarin di Cloudflare Turnstile
                // dashboard buat site key ini (Settings > Domains).
                loadDataWithBaseURL(
                    "https://aniku-app.local",
                    html,
                    "text/html",
                    "utf-8",
                    null
                )
            }
        }
    )
}
