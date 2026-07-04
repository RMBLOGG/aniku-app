package com.example.network

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.InetAddress
import java.net.URI
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit

/**
 * Hasil resolve satu embed URL -> link video langsung yang bisa dikasih ke ExoPlayer,
 * lengkap dengan header (Referer/Origin/User-Agent) yang dibutuhkan host-nya.
 */
data class ResolvedStream(
    val url: String,
    val isHls: Boolean,
    val headers: Map<String, String> = emptyMap()
)

/**
 * Mengubah embed URL (Filemoon, Mp4upload, Streamtape, Vidhide, Wibufile, Pixeldrain,
 * Mediafire, dll) jadi direct link video, dengan cara nge-scrape HTML/JS halaman embed-nya
 * persis seperti yang dilakukan WebView sebelumnya - bedanya di sini link mentahnya
 * diambil duluan supaya bisa dikasih ke ExoPlayer.
 *
 * Kalau host belum/tidak bisa di-extract, `resolve()` return null - caller WAJIB fallback
 * ke WebView lama supaya video tetap bisa diputar.
 */
object VideoExtractor {

    /**
     * Debug info dari percobaan resolve terakhir yang GAGAL (fileUrl == null).
     * Diisi dari extractPackedJwPlayer (dan bisa dipakai extractor lain juga).
     * Tujuannya biar bisa ditampilin di UI (Toast/dialog) buat yang gak punya
     * akses Logcat/adb — jadi tinggal screenshot popup-nya aja.
     */
    @Volatile
    var lastDebugSnippet: String? = null
        private set

    private const val DESKTOP_UA =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

    // Client polos khusus buat query DoH sendiri (jangan pakai `client` di bawah,
    // supaya gak infinite-loop kalau dns.google ikut ke-resolve pakai FallbackDns).
    private val dohClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    /**
     * DNS sistem dulu (cepat & biasanya cukup). Kalau gagal resolve (UnknownHostException) —
     * kasus umum buat shortlink (short.ink, dll) yang di-block ISP di level DNS —
     * fallback ke DNS-over-HTTPS (Google) biar tetep bisa connect walau DNS lokal ngeblokir.
     */
    private object FallbackDns : Dns {
        override fun lookup(hostname: String): List<InetAddress> {
            try {
                return Dns.SYSTEM.lookup(hostname)
            } catch (e: UnknownHostException) {
                Log.w("VideoExtractor", "DNS sistem gagal utk $hostname, coba DoH...")
                return lookupViaDoH(hostname) ?: throw e
            }
        }

        private fun lookupViaDoH(hostname: String): List<InetAddress>? {
            return try {
                val req = Request.Builder()
                    .url("https://dns.google/resolve?name=$hostname&type=A")
                    .header("Accept", "application/dns-json")
                    .build()
                dohClient.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) return null
                    val body = resp.body?.string() ?: return null
                    val json = org.json.JSONObject(body)
                    val answers = json.optJSONArray("Answer") ?: return null
                    val ips = mutableListOf<InetAddress>()
                    for (i in 0 until answers.length()) {
                        val a = answers.getJSONObject(i)
                        if (a.optInt("type") == 1) { // A record
                            runCatching { InetAddress.getByName(a.getString("data")) }
                                .getOrNull()?.let { ips.add(it) }
                        }
                    }
                    ips.ifEmpty { null }.also {
                        if (it != null) Log.d("VideoExtractor", "DoH resolve $hostname -> ${it.map { ip -> ip.hostAddress }}")
                    }
                }
            } catch (e: Exception) {
                Log.e("VideoExtractor", "DoH lookup gagal utk $hostname: ${e.message}")
                null
            }
        }
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .callTimeout(20, TimeUnit.SECONDS)
        .followRedirects(true)
        .retryOnConnectionFailure(true)
        // Pool lebih gede & keep-alive lebih lama - dalam satu sesi nonton,
        // host yang sama (mis. server video/CDN) sering di-hit berkali-kali
        // (getHome page, resolve, ganti kualitas...), jadi koneksi TCP/TLS-nya
        // enak dipakai ulang instead of handshake dari nol tiap kali.
        .connectionPool(okhttp3.ConnectionPool(10, 5, TimeUnit.MINUTES))
        .dns(FallbackDns)
        .build()

    // ---------------------------------------------------------------------
    // Cache hasil resolve (in-memory, per proses app - bukan disk) supaya
    // buka episode yang sama / gonta-ganti kualitas / balik ke episode
    // sebelumnya gak perlu scrape ulang dari nol tiap kali (itu penyebab
    // utama loading lama, karena tiap resolve = 1+ HTTP request + parsing
    // HTML/JS, kadang sampe WebView buat Blogger).
    //
    // TTL dibikin pendek (10 menit) karena banyak host (Filedon dkk) ngasih
    // presigned URL yang expired setelah beberapa waktu - jangan di-cache
    // kelamaan atau nanti player dapet link basi.
    // ---------------------------------------------------------------------
    private data class CacheEntry(val stream: ResolvedStream, val expiresAt: Long)

    private val resolveCache = java.util.concurrent.ConcurrentHashMap<String, CacheEntry>()
    private const val CACHE_TTL_MS = 10 * 60 * 1000L
    private const val CACHE_MAX_SIZE = 200

    private fun cacheKey(embedUrl: String, referer: String?) = "$embedUrl|${referer ?: ""}"

    private fun cacheGet(key: String): ResolvedStream? {
        val entry = resolveCache[key] ?: return null
        if (System.currentTimeMillis() > entry.expiresAt) {
            resolveCache.remove(key)
            return null
        }
        return entry.stream
    }

    private fun cachePut(key: String, stream: ResolvedStream) {
        if (resolveCache.size > CACHE_MAX_SIZE) {
            // Beres-beres entry basi biar map gak numpuk terus selama app hidup
            val now = System.currentTimeMillis()
            resolveCache.entries.removeAll { it.value.expiresAt < now }
        }
        resolveCache[key] = CacheEntry(stream, System.currentTimeMillis() + CACHE_TTL_MS)
    }

    /** Buang seluruh cache resolve — dipanggil manual kalau ada link yang udah kadaluarsa/rusak. */
    fun clearCache() {
        resolveCache.clear()
    }

    /**
     * Beberapa sumber (mis. server GDRIVE/GDRIVE HD - gdriveplayer.to) ngasih URL
     * "protocol-relative" (diawali "//host/path", tanpa "https:"). Di web/browser ini
     * otomatis diwarisi scheme dari halaman yang lagi dibuka, jadi kelihatan "jalan".
     * Tapi di sini (OkHttp & WebView Android) gak ada halaman buat diwarisi schemenya:
     * OkHttp bakal gagal parse (exception ke-catch, jadi seolah "gagal resolve" diam-diam),
     * dan kalau kebawa mentah-mentah ke WebView.loadUrl(), WebView nge-resolve-nya relatif
     * jadi "file:///host/path" -> net::ERR_ACCESS_DENIED. Makanya di-normalize ke https: dulu
     * di SETIAP titik masuk (resolve & fallback WebView) sebelum diproses lebih lanjut.
     */
    private fun normalizeUrl(url: String): String =
        if (url.startsWith("//")) "https:$url" else url

    suspend fun resolve(embedUrl: String, referer: String? = null, context: Context? = null): ResolvedStream? {
        val embedUrl = normalizeUrl(embedUrl)
        lastDebugSnippet = null
        val key = cacheKey(embedUrl, referer)
        cacheGet(key)?.let {
            Log.d("VideoExtractor", "Cache hit: $embedUrl")
            return it
        }
        val result = resolveUncached(embedUrl, referer, context)
        if (result != null) cachePut(key, result)
        return result
    }

    private suspend fun resolveUncached(embedUrl: String, referer: String? = null, context: Context? = null): ResolvedStream? {
        Log.d("VideoExtractor", "Resolving embed: $embedUrl (referer=$referer)")

        // Fast-path: beberapa server (terutama varian Wibufile seperti s0.wibufile.com)
        // sebenarnya udah ngasih link file langsung, bukan embed page. Kalau gitu,
        // langsung dipakai aja tanpa di-scrape - hemat 1 request & gak ada yang gagal parse.
        if (Regex("""\.(mp4|m3u8|ts)(\?|$)""").containsMatchIn(embedUrl)) {
            return ResolvedStream(
                url = embedUrl,
                isHls = embedUrl.contains(".m3u8"),
                headers = mapOf(
                    "Referer" to (referer ?: embedUrl),
                    "User-Agent" to DESKTOP_UA
                )
            )
        }

        val host = runCatching { URI(embedUrl).host?.lowercase() }.getOrNull() ?: return null
        return try {
            when {
                host.contains("mp4upload") -> extractMp4Upload(embedUrl)
                host.contains("streamtape") -> extractStreamTape(embedUrl)
                host.contains("pixeldrain") -> extractPixeldrain(embedUrl)
                host.contains("mediafire") -> extractMediafire(embedUrl)
                host.contains("filedon") -> extractFiledon(embedUrl, referer)
                host.contains("blogger") || host.contains("blogspot") -> {
                    // Blogger video butuh WebView karena URL video di-render via JS
                    // Coba BloggerWebViewExtractor dulu (butuh context & Google login di device)
                    if (context != null) {
                        val googlevideoUrl = BloggerWebViewExtractor.resolve(context, embedUrl)
                        if (googlevideoUrl != null) {
                            Log.d("VideoExtractor", "Blogger resolved via WebView: ${googlevideoUrl.take(80)}")
                            return ResolvedStream(
                                url = googlevideoUrl,
                                isHls = googlevideoUrl.contains(".m3u8"),
                                headers = mapOf(
                                    "Referer" to "https://www.blogger.com/",
                                    "User-Agent" to DESKTOP_UA
                                )
                            )
                        }
                        Log.w("VideoExtractor", "BloggerWebViewExtractor gagal, fallback ke HTML parse")
                    }
                    extractBlogger(embedUrl, referer)
                }
                host.contains("filemoon") ||
                host.contains("vidhide") ||
                host.contains("wibufile") ||
                host.contains("streamhide") ||
                host.contains("moviesm4u") ||
                host.contains("ztreamhub") ||
                host.contains("guccihide") ||
                // GDRIVE/GDRIVE HD (gdriveplayer.to) — dari network trace-nya kepakai
                // JWPlayer juga (ssl.p.jwpcdn.com, provider.hlsjs.js), source video-nya
                // di endpoint sendiri (hlsplaylist.php?s=...), jadi extractor JWPlayer
                // generik yang udah ada di bawah ini bisa dipakai ulang.
                host.contains("gdriveplayer") -> extractPackedJwPlayer(embedUrl, referer)
                else -> {
                    // Host belum dikenal — kemungkinan besar shortlink (short.ink, dll)
                    // yang ngebungkus URL server video asli. Ikutin redirect-nya sendiri
                    // lewat OkHttp (pakai FallbackDns di atas, jadi tetep jalan walau
                    // domain shortlink-nya di-block DNS ISP), terus resolve ulang hasil
                    // akhirnya. Kalau ternyata gak ada redirect / hostnya emang gak
                    // dikenal, tetap balikin null seperti biasa -> caller fallback WebView.
                    val finalUrl = followRedirect(embedUrl, referer)
                    if (!finalUrl.isNullOrBlank() && finalUrl != embedUrl) {
                        Log.d("VideoExtractor", "Shortlink '$host' -> $finalUrl")
                        resolve(finalUrl, referer, context)
                    } else {
                        Log.d("VideoExtractor", "Host '$host' belum ada extractor-nya")
                        null
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("VideoExtractor", "Gagal resolve $embedUrl: ${e.message}")
            null
        }
    }

    /**
     * Dipakai SETIAP KALI resolve() gagal (return null) dan caller mau fallback ke WebView.
     * Jangan langsung pakai embedUrl mentah buat WebView.loadUrl() — kalau embedUrl itu
     * shortlink (short.ink, short.icu, atau apapun besok) yang domainnya di-block ISP di
     * level DNS, WebView bakal langsung dapet net::ERR_NAME_NOT_RESOLVED karena WebView
     * pakai DNS sistem biasa, BUKAN FallbackDns (DoH) yang dipasang di client OkHttp atas.
     *
     * Fungsi ini follow redirect-nya dulu lewat OkHttp (yang udah pasang FallbackDns),
     * jadi walau domain shortlink-nya di-block ISP, kita tetap bisa nyampe ke URL akhirnya
     * (host video/iframe asli) — baru itu yang dikasih ke WebView. Aman dipanggil untuk
     * URL apapun; kalau bukan shortlink / gak ada redirect / gagal, balikin url aslinya.
     */
    suspend fun resolveForWebViewFallback(url: String, referer: String? = null): String {
        val url = normalizeUrl(url)
        return withContext(Dispatchers.IO) {
            runCatching { followRedirect(url, referer) }
                .getOrNull()
                ?.takeIf { it.isNotBlank() }
                ?: url
        }
    }

    private fun fetchHtml(url: String, referer: String? = null): String {
        val builder = Request.Builder()
            .url(url)
            .header("User-Agent", DESKTOP_UA)
        if (referer != null) builder.header("Referer", referer)
        client.newCall(builder.build()).execute().use { resp ->
            if (!resp.isSuccessful) throw Exception("HTTP ${resp.code}")
            return resp.body?.string() ?: throw Exception("Body kosong")
        }
    }

    private fun originOf(url: String): String =
        runCatching { URI(url).let { "${it.scheme}://${it.host}" } }.getOrDefault(url)

    // ---------------------------------------------------------------------
    // Follow redirect chain (buat shortlink kayak short.ink) pakai OkHttp
    // (yang udah pasang FallbackDns), terus balikin URL final-nya.
    // Coba HEAD duluan (gak download body sama sekali, jauh lebih cepat &
    // hemat data buat halaman yang cuma nge-redirect doang) - kalau server-nya
    // nolak HEAD (405/403/dll atau gak ke-redirect sama sekali), baru fallback GET.
    // ---------------------------------------------------------------------
    private fun followRedirect(url: String, referer: String?): String? {
        fun request(method: String) = Request.Builder().url(url).method(method, null)
            .header("User-Agent", DESKTOP_UA)
            .apply { if (referer != null) header("Referer", referer) }
            .build()

        try {
            client.newCall(request("HEAD")).execute().use { resp ->
                val finalUrl = resp.request.url.toString()
                if (resp.isSuccessful && finalUrl != url) return finalUrl
            }
        } catch (_: Exception) {
            // sebagian server nolak/error di HEAD - lanjut coba GET di bawah
        }

        return try {
            client.newCall(request("GET")).execute().use { resp ->
                resp.request.url.toString()
            }
        } catch (e: Exception) {
            Log.e("VideoExtractor", "Gagal follow redirect $url: ${e.message}")
            null
        }
    }

    // ---------------------------------------------------------------------
    // Mp4upload — link mp4 langsung ditaruh di player.src({...}) pada <script>.
    // ---------------------------------------------------------------------
    private fun extractMp4Upload(embedUrl: String): ResolvedStream? {
        val html = fetchHtml(embedUrl, embedUrl)
        val url = Regex("""src\s*:\s*"([^"]+)"""").find(html)?.groupValues?.get(1) ?: return null
        return ResolvedStream(
            url = url,
            isHls = false,
            headers = mapOf("Referer" to embedUrl, "User-Agent" to DESKTOP_UA)
        )
    }

    // ---------------------------------------------------------------------
    // Streamtape — link digabung dari dua potong string lewat JS di halaman embed.
    // CATATAN: Streamtape lumayan sering ganti pola obfuscation-nya, jadi extractor
    // ini best-effort. Kalau polanya berubah, fungsi ini akan return null secara
    // aman dan ExoPlayer screen otomatis fallback ke WebView (lihat onPlayerError).
    // ---------------------------------------------------------------------
    private fun extractStreamTape(embedUrl: String): ResolvedStream? {
        val html = fetchHtml(embedUrl, embedUrl)
        val match = Regex("""robotlink'\)\.innerHTML\s*=\s*"([^"]*)"\s*\+\s*\('([^']*)'\)""")
            .find(html) ?: return null
        val part1 = match.groupValues[1]
        val part2 = match.groupValues[2]
        val tail = if (part2.length > 4) part2.substring(4) else part2
        val url = "https:$part1$tail"
        return ResolvedStream(
            url = url,
            isHls = false,
            headers = mapOf("Referer" to embedUrl, "User-Agent" to DESKTOP_UA)
        )
    }

    // ---------------------------------------------------------------------
    // Pixeldrain — id file ada di URL-nya sendiri, tinggal dibentuk ke endpoint API.
    // ---------------------------------------------------------------------
    private fun extractPixeldrain(embedUrl: String): ResolvedStream? {
        val id = Regex("""pixeldrain\.com/(?:u|e|l)/([a-zA-Z0-9]+)""")
            .find(embedUrl)?.groupValues?.get(1) ?: return null
        return ResolvedStream(
            url = "https://pixeldrain.com/api/file/$id",
            isHls = false,
            headers = mapOf("User-Agent" to DESKTOP_UA)
        )
    }

    // ---------------------------------------------------------------------
    // Filedon — halaman embed-nya pakai Inertia.js (Laravel). Data video-nya
    // (link presigned S3 langsung) ada statis di atribut data-page="{...}"
    // dalam bentuk JSON yang di-HTML-encode. Gak perlu unpack JS sama sekali.
    // ---------------------------------------------------------------------
    private fun extractFiledon(embedUrl: String, referer: String?): ResolvedStream? {
        val html = fetchHtml(embedUrl, referer ?: embedUrl)
        val raw = Regex("""data-page="([^"]*)"""").find(html)?.groupValues?.get(1) ?: return null
        val decoded = raw
            .replace("&quot;", "\"")
            .replace("&#039;", "'")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")

        val props = org.json.JSONObject(decoded).getJSONObject("props")
        val hlsUrl = props.optJSONObject("media")?.optString("hls_url")
            ?.takeIf { it.isNotBlank() && it != "null" }
        val directUrl = props.optString("url").takeIf { it.isNotBlank() && it != "null" }
        val url = hlsUrl ?: directUrl ?: return null

        return ResolvedStream(
            url = url,
            isHls = url.contains(".m3u8"),
            headers = mapOf("User-Agent" to DESKTOP_UA)
        )
    }

    // ---------------------------------------------------------------------
    // Mediafire — direct link ada di href tombol download halaman file.
    // ---------------------------------------------------------------------
    private fun extractMediafire(embedUrl: String): ResolvedStream? {
        val html = fetchHtml(embedUrl, embedUrl)
        val url = Regex("""id="downloadButton"[^>]*href="([^"]+)"""")
            .find(html)?.groupValues?.get(1)?.replace("&amp;", "&") ?: return null
        return ResolvedStream(url = url, isHls = false, headers = mapOf("User-Agent" to DESKTOP_UA))
    }

    // ---------------------------------------------------------------------
    // Filemoon / Vidhide / Wibufile / Filedon / Streamhide-style host:
    // halaman embed-nya pakai JWPlayer yang konfigurasinya dibungkus JS
    // "packed" (eval(function(p,a,c,k,e,d){...}(...))). Kita unpack JS-nya
    // dulu, baru ambil sources[0].file dari config JWPlayer-nya.
    // ---------------------------------------------------------------------
    private fun extractPackedJwPlayer(embedUrl: String, referer: String?): ResolvedStream? {
        val html = fetchHtml(embedUrl, referer ?: embedUrl)

        var fileUrl = extractSourceFile(html)
        var working = html
        var attempts = 0
        while (fileUrl == null && attempts < 3) {
            val packedMatch = Regex(
                """eval\(function\(p,a,c,k,e,[rd]\).*?\)\)""",
                RegexOption.DOT_MATCHES_ALL
            ).find(working) ?: break
            val unpacked = unpackJs(packedMatch.value) ?: break
            fileUrl = extractSourceFile(unpacked)
            working = unpacked
            attempts++
        }

        // Kalau gak nemu di HTML utama, coba juga file.js eksternal yang direferensikan
        // (mis. <script src="file.js?v=1">) — mungkin logic isi sources/file-nya ada
        // di situ, bukan di HTML utama.
        var jsSrc: String? = null
        var externalJs: String? = null
        if (fileUrl == null) {
            jsSrc = Regex("""<script[^>]+src=["']([^"']*file\.js[^"']*)["']""").find(html)?.groupValues?.get(1)
            if (jsSrc != null) {
                val jsUrl = runCatching { URI(embedUrl).resolve(normalizeUrl(jsSrc)).toString() }.getOrDefault(jsSrc)
                externalJs = runCatching { fetchHtml(jsUrl, embedUrl) }.getOrNull()
                if (!externalJs.isNullOrBlank()) {
                    fileUrl = extractSourceFile(externalJs)
                    var jsWorking = externalJs!!
                    var jsAttempts = 0
                    while (fileUrl == null && jsAttempts < 3) {
                        val packedMatch = Regex(
                            """eval\(function\(p,a,c,k,e,[rd]\).*?\)\)""",
                            RegexOption.DOT_MATCHES_ALL
                        ).find(jsWorking) ?: break
                        val unpacked = unpackJs(packedMatch.value) ?: break
                        fileUrl = extractSourceFile(unpacked)
                        jsWorking = unpacked
                        jsAttempts++
                    }
                }
            }
        }

        val rawUrl = fileUrl ?: run {
            Log.d("VideoExtractor", "Gak nemu sources/file di $embedUrl - mungkin JS-nya render dinamis (SPA/XHR), bukan static config")
            val hasPacked = Regex("""eval\(function\(p,a,c,k,e,[rd]\)""").containsMatchIn(html)
            fun findContext(pattern: String, label: String): String {
                val idx = html.indexOf(pattern, ignoreCase = true)
                if (idx == -1) return "[$label]: tidak ketemu"
                val start = (idx - 80).coerceAtLeast(0)
                val end = (idx + pattern.length + 200).coerceAtMost(html.length)
                return "[$label] @$idx: ...${html.substring(start, end).replace("\n", " ")}..."
            }
            lastDebugSnippet = buildString {
                appendLine("embedUrl: $embedUrl")
                appendLine("html length: ${html.length}")
                appendLine("ada packed-JS (eval p,a,c,k,e)?: $hasPacked")
                appendLine("file.js src ditemuin di HTML?: ${jsSrc ?: "tidak ada"}")
                appendLine("file.js berhasil di-fetch?: ${!externalJs.isNullOrBlank()} (length=${externalJs?.length ?: 0})")
                appendLine()
                appendLine(findContext("hlsplaylist", "hlsplaylist"))
                appendLine()
                appendLine(findContext("eardropcurls", "eardropcurls (domain fetch json)"))
                appendLine()
                appendLine(findContext("fetch(", "fetch("))
                appendLine()
                appendLine(findContext(".setup(", "jwplayer .setup("))
                appendLine()
                appendLine(findContext("jwplayer(", "jwplayer("))
                appendLine()
                appendLine(findContext("no_adult", "no_adult (param embed)"))
                appendLine()
                if (!externalJs.isNullOrBlank()) {
                    appendLine("--- 800 char pertama file.js ---")
                    appendLine(externalJs.take(800))
                    appendLine()
                }
                appendLine("--- 600 char pertama HTML ---")
                appendLine(html.take(600))
            }
            return null
        }
        // Beberapa host (mis. gdriveplayer.to) nulis `file:` sebagai path relatif
        // ke domain sendiri (mis. "hlsplaylist.php?s=xxx", bukan URL absolut).
        // Resolve relatif ke embedUrl dulu, biar ExoPlayer/WebView gak nerima
        // path mentah tanpa scheme+host (penyebab bug file:/// yang sama kayak
        // di normalizeUrl()).
        val url = runCatching { URI(embedUrl).resolve(normalizeUrl(rawUrl)).toString() }
            .getOrDefault(normalizeUrl(rawUrl))
        return ResolvedStream(
            url = url,
            isHls = url.contains(".m3u8"),
            headers = mapOf(
                "Referer" to embedUrl,
                "Origin" to originOf(embedUrl),
                "User-Agent" to DESKTOP_UA
            )
        )
    }

    private fun extractSourceFile(js: String): String? {
        return Regex("""sources\s*:\s*\[\s*\{\s*file\s*:\s*["']([^"']+)["']""").find(js)?.groupValues?.get(1)
            ?: Regex("""file\s*:\s*["'](https?://[^"']+\.(?:m3u8|mp4)[^"']*)["']""").find(js)?.groupValues?.get(1)
    }

    // ---------------------------------------------------------------------
    // Blogger video embed — URL format: blogger.com/video.g?token=XXX
    // Response bisa berupa JSON dengan "streams" array atau JS dengan VIDEO_CONFIG
    private fun extractBlogger(embedUrl: String, referer: String?): ResolvedStream? {
        val html = fetchHtml(embedUrl, referer ?: "https://www.blogger.com/")

        if (html.isBlank()) {
            Log.d("VideoExtractor", "Blogger: empty response for $embedUrl")
            return null
        }

        // Pattern 1: VIDEO_CONFIG = {...} JavaScript object
        val videoConfigJson = Regex("""VIDEO_CONFIG\s*=\s*(\{.+?\})\s*;""", RegexOption.DOT_MATCHES_ALL)
            .find(html)?.groupValues?.get(1)
        if (!videoConfigJson.isNullOrBlank()) {
            // Cari play_url di dalam VIDEO_CONFIG
            val playUrl = Regex(""""play_url"\s*:\s*"([^"]+)"""")
                .find(videoConfigJson)?.groupValues?.get(1)?.replace("\\/", "/")
            if (!playUrl.isNullOrBlank()) {
                return ResolvedStream(url = playUrl, isHls = playUrl.contains(".m3u8"),
                    headers = mapOf("Referer" to "https://www.blogger.com/", "User-Agent" to DESKTOP_UA))
            }
        }

        // Pattern 2: "streams":[{"play_url":"..."}]
        val streamsBlock = Regex(""""streams"\s*:\s*\[(.+?)\]""", RegexOption.DOT_MATCHES_ALL)
            .find(html)?.groupValues?.get(1)
        if (!streamsBlock.isNullOrBlank()) {
            // Ambil play_url tertinggi (biasanya format_id terbesar = kualitas terbaik)
            val allUrls = Regex(""""play_url"\s*:\s*"([^"]+)"""")
                .findAll(streamsBlock).map { it.groupValues[1].replace("\\/", "/") }.toList()
            val best = allUrls.lastOrNull() // last = highest quality
            if (!best.isNullOrBlank()) {
                return ResolvedStream(url = best, isHls = best.contains(".m3u8"),
                    headers = mapOf("Referer" to "https://www.blogger.com/", "User-Agent" to DESKTOP_UA))
            }
        }

        // Pattern 3: "play_url":"..." anywhere
        val playUrl = Regex(""""play_url"\s*:\s*"([^"]+)"""")
            .find(html)?.groupValues?.get(1)?.replace("\\/", "/")
        if (!playUrl.isNullOrBlank()) {
            return ResolvedStream(url = playUrl, isHls = playUrl.contains(".m3u8"),
                headers = mapOf("Referer" to "https://www.blogger.com/", "User-Agent" to DESKTOP_UA))
        }

        // Pattern 4: <video src="..."> atau <source src="...">
        val videoSrc = Regex("""<(?:video|source)[^>]+src=["']([^"']+\.(?:mp4|m3u8)[^"']*)["']""", RegexOption.IGNORE_CASE)
            .find(html)?.groupValues?.get(1)
        if (!videoSrc.isNullOrBlank()) {
            return ResolvedStream(url = videoSrc, isHls = videoSrc.contains(".m3u8"),
                headers = mapOf("Referer" to "https://www.blogger.com/", "User-Agent" to DESKTOP_UA))
        }

        // Pattern 5: URL .mp4 atau .m3u8 langsung dalam response
        val directUrl = Regex("""https?://[^\s"'<>]+\.(?:mp4|m3u8)[^\s"'<>]*""")
            .find(html)?.value
        if (!directUrl.isNullOrBlank()) {
            return ResolvedStream(url = directUrl, isHls = directUrl.contains(".m3u8"),
                headers = mapOf("Referer" to "https://www.blogger.com/", "User-Agent" to DESKTOP_UA))
        }

        Log.d("VideoExtractor", "Blogger: tidak ditemukan video URL. HTML snippet: ${html.take(500)}")
        return null
    }

    /**
     * Implementasi unpacker generik untuk "Dean Edwards packer"
     * — format obfuscation umum yang dipakai banyak situs mirror video.
     *
     * CATATAN PERFORMA: versi lama nge-loop tiap keyword (bisa ratusan) dan
     * bikin + jalanin Regex baru + scan ULANG SELURUH string per keyword —
     * jadi O(jumlah_keyword x panjang_payload). Ini penyebab utama lag di
     * host yang JS-nya di-pack (Filemoon/Vidhide/Wibufile/Streamhide/dll).
     * Versi ini cuma sekali scan (O(n)): jalan karakter per karakter, kumpulin
     * token alfanumerik, terus lookup ke dictionary — hasil akhirnya identik,
     * tapi jauh lebih cepat (dari ratusan pass jadi 1 pass).
     */
    private fun unpackJs(packed: String): String? {
        val match = Regex(
            """\}\('(.*)',\s*(\d+),\s*(\d+),\s*'(.*?)'\.split\('\|'\)""",
            RegexOption.DOT_MATCHES_ALL
        ).find(packed) ?: return null

        val payload = match.groupValues[1]
        val radix = match.groupValues[2].toIntOrNull() ?: 36
        val count = match.groupValues[3].toIntOrNull() ?: return null
        val keywords = match.groupValues[4].split("|")

        // token base-radix (mis. "a3", "12") -> keyword aslinya, sekali bikin aja
        val dict = HashMap<String, String>(count * 2)
        for (c in 0 until count) {
            if (c < keywords.size && keywords[c].isNotEmpty()) {
                dict[Integer.toString(c, radix)] = keywords[c]
            }
        }

        val sb = StringBuilder(payload.length + payload.length / 2)
        val n = payload.length
        var i = 0
        while (i < n) {
            val ch = payload[i]
            if (ch.isLetterOrDigit()) {
                val start = i
                while (i < n && payload[i].isLetterOrDigit()) i++
                val token = payload.substring(start, i)
                sb.append(dict[token] ?: token)
            } else {
                sb.append(ch)
                i++
            }
        }
        return sb.toString().replace("\\'", "'").replace("\\\\", "\\")
    }
}
