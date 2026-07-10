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

    /**
     * OkHttpClient khusus buat ExoPlayer (streaming manifest + segment video),
     * pakai FallbackDns yang sama kaya `client` di atas.
     *
     * KENAPA INI PENTING: sebelum ini, ExoPlayer pakai DefaultHttpDataSource
     * bawaan Media3, yang resolve DNS langsung lewat sistem HP - TIDAK ikut
     * fallback DoH kaya proses resolve/extract di atas. Akibatnya: proses
     * extract link video bisa aja sukses (karena udah fallback DoH), tapi
     * begitu ExoPlayer sendiri nyoba connect buat streaming manifest/segment
     * ke domain yang sama, macet/timeout kalau DNS domain itu di-block ISP
     * user (kejadian ini KHUSUS ke jaringan user tertentu - ok.ru misalnya
     * sering di-block ISP Indonesia di level DNS). Gejalanya: loading muter
     * terus tanpa error, padahal di jaringan lain (mis. punya developer)
     * lancar-lancar aja.
     *
     * Timeout dibikin lebih longgar dari `client` di atas karena ini dipakai
     * buat load segment video yang bisa lumayan gede, bukan cuma HTML/JSON kecil.
     */
    val streamingHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .callTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .retryOnConnectionFailure(true)
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
        var result = resolveUncached(embedUrl, referer, context)
        // Beberapa server (kejadian nyata: anichin.stream) ngasih HLS MASTER
        // playlist yang keputus di tengah - baris terakhir #EXT-X-STREAM-INF
        // gak punya baris URL sesudahnya (mis. varian 1080p ke-cut, padahal
        // 360p/480p/720p di atasnya lengkap). Browser/hls.js cuek aja soal ini,
        // tapi ExoPlayer strict dan gagal total parsing SELURUH manifest gara-gara
        // 1 baris rusak di ujung. Di-patch di sini: bersihin baris yang orphan,
        // simpen ke file lokal, baru itu yang dikasih ke ExoPlayer - biar 3 varian
        // yang valid tetap bisa diputar walau 1 varian di ujungnya cacat.
        if (result != null && result.isHls && context != null) {
            val sanitizedUri = withContext(Dispatchers.IO) {
                sanitizeHlsMasterPlaylist(context, result!!.url, result!!.headers)
            }
            if (sanitizedUri != null) {
                Log.d("VideoExtractor", "HLS master playlist di-sanitize, disimpen lokal: $sanitizedUri")
                result = result!!.copy(url = sanitizedUri)
            }
        }
        if (result != null) cachePut(key, result!!)
        return result
    }

    /**
     * Fetch manifest master HLS, buang baris #EXT-X-STREAM-INF yang gak punya
     * baris URL sesudahnya (orphan/keputus), simpen hasil bersihnya ke file lokal
     * di cache dir. Return null kalau: gagal fetch, atau manifest-nya udah bener
     * (gak ada yang perlu dibersihin) - jadi caller tau harus tetap pakai URL
     * remote asli, gak usah ganti ke file lokal kalau gak perlu.
     */
    private fun sanitizeHlsMasterPlaylist(context: Context, url: String, headers: Map<String, String>): String? {
        val body = try {
            val builder = Request.Builder().url(url)
            headers.forEach { (k, v) -> builder.header(k, v) }
            client.newCall(builder.build()).execute().use { resp ->
                if (!resp.isSuccessful) return null
                resp.body?.string() ?: return null
            }
        } catch (e: Exception) {
            Log.w("VideoExtractor", "sanitizeHlsMasterPlaylist: gagal fetch manifest - ${e.message}")
            return null
        }

        // Bukan master playlist (tidak ada tag ini) - gak relevan buat di-sanitize.
        if (!body.contains("#EXT-X-STREAM-INF")) return null

        val lines = body.lines()
        val cleaned = mutableListOf<String>()
        var droppedAny = false
        var i = 0
        while (i < lines.size) {
            val line = lines[i]
            if (line.startsWith("#EXT-X-STREAM-INF")) {
                // Cari baris URI berikutnya yang bukan blank/comment.
                var j = i + 1
                while (j < lines.size && lines[j].isBlank()) j++
                val hasUri = j < lines.size && !lines[j].startsWith("#")
                if (hasUri) {
                    cleaned.add(line)
                } else {
                    // Orphan - baris STREAM-INF ini gak punya URL, buang.
                    droppedAny = true
                }
                i++
            } else {
                cleaned.add(line)
                i++
            }
        }

        if (!droppedAny) return null // manifest udah OK apa adanya, gak usah ganti apa-apa

        val resolvedLines = cleaned.map { l ->
            if (l.isNotBlank() && !l.startsWith("#")) {
                runCatching { URI(url).resolve(normalizeUrl(l.trim())).toString() }.getOrDefault(l)
            } else l
        }

        return try {
            val cacheDir = java.io.File(context.cacheDir, "hls_sanitized").apply { mkdirs() }
            val fileName = "manifest_${url.hashCode()}.m3u8"
            val outFile = java.io.File(cacheDir, fileName)
            outFile.writeText(resolvedLines.joinToString("\n"))
            "file://${outFile.absolutePath}"
        } catch (e: Exception) {
            Log.w("VideoExtractor", "sanitizeHlsMasterPlaylist: gagal simpen file lokal - ${e.message}")
            null
        }
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
                // rubyvidhub.com / streamruby.com — sama-sama JWPlayer packed-JS
                // standar (eval(function(p,a,c,k,e,d){...}), confirmed dari HTML
                // embed asli via curl manual), cuma beda brand mirror doang.
                host.contains("rubyvidhub") ||
                host.contains("streamruby") ||
                // anichin.stream — domain embed "Premium" milik Anichin sendiri (dipakai
                // Donghua/Dayynime-v4). Konfigurasi JWPlayer-nya packed-JS standar
                // (eval(function(p,a,c,k,e,d){...})), sama kayak Filemoon/Vidhide dkk
                // di atas -> extractPackedJwPlayer.
                host.contains("anichin.stream") -> extractPackedJwPlayer(embedUrl, referer)
                // GDRIVE/GDRIVE HD (gdriveplayer.to) — dikonfirmasi dari network trace
                // langsung: config JWPlayer-nya disimpen ter-obfuscate (base64+XOR,
                // lihat extractGdrivePlayer). BUKAN packed-JS biasa, jadi harus
                // branch TERPISAH dari grup di atas (dulu pernah ke-gabung jadi 1
                // kondisi `||` yang sama-sama jatuh ke extractGdrivePlayer -> semua
                // host packed-JS di atas, termasuk anichin.stream, jadi salah rute
                // dan gak pernah lewat extractPackedJwPlayer sama sekali).
                host.contains("gdriveplayer") -> extractGdrivePlayer(embedUrl, referer)
                host.contains("rumble") -> extractRumble(embedUrl, referer)
                host.contains("ok.ru") -> extractOkRu(embedUrl, referer)
                host.contains("dailymotion") || host.contains("dai.ly") -> extractDailymotion(embedUrl, referer)
                // Abyssplayer nyimpen source video dalam bentuk terenkripsi (didekripsi
                // oleh JS mereka sendiri di browser) - gak bisa di-regex dari HTML mentah.
                // Sama kayak Blogger, harus lewat WebView beneran & nyadap request video
                // asli begitu JWPlayer mulai narik manifest/segment-nya.
                host.contains("abyssplayer") || host.contains("abyss.to") -> {
                    if (context != null) {
                        val stream = AbyssWebViewExtractor.resolve(context, embedUrl, referer)
                        if (stream != null) {
                            Log.d("VideoExtractor", "Abyss resolved via WebView: ${stream.url.take(80)}")
                            stream
                        } else {
                            Log.w("VideoExtractor", "AbyssWebViewExtractor gagal, fallback WebView biasa")
                            null
                        }
                    } else {
                        Log.d("VideoExtractor", "Abyss butuh context (WebView) - belum ada, fallback WebView biasa")
                        null
                    }
                }
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

    /**
     * Ambil isi teks mentah manifest (.m3u8) atau response lain buat keperluan
     * DEBUG doang - dipanggil dari AnikuViewModel abis resolve() sukses & isHls
     * true, biar isinya bisa ditampilin di dialog "Info stream" tanpa user
     * harus buka browser terpisah & paste URL manual.
     *
     * Pakai header yang SAMA PERSIS kayak yang bakal dipakai ExoPlayer (Referer/
     * Origin/User-Agent dari ResolvedStream.headers), supaya kalau ada
     * perbedaan hasil antara "app fetch" vs "ExoPlayer fetch" bisa ketauan
     * juga (mis. dua-duanya sama-sama dapet manifest rusak -> confirmed
     * server-side, bukan bug spesifik ExoPlayer/OkHttpDataSource).
     *
     * Dibatasin ke ~4000 karakter pertama - manifest HLS biasanya kecil (list
     * teks), jadi ini harusnya udah cukup buat lihat struktur lengkapnya tanpa
     * bikin dialog debug kebanjiran teks kalau ternyata malah dapet HTML gede.
     */
    fun peekManifestText(url: String, headers: Map<String, String>, maxChars: Int = 4000): String? {
        return try {
            val builder = Request.Builder().url(url)
            headers.forEach { (k, v) -> builder.header(k, v) }
            client.newCall(builder.build()).execute().use { resp ->
                val body = resp.body?.string() ?: return "(HTTP ${resp.code}, body kosong)"
                val status = "HTTP ${resp.code}, content-type: ${resp.header("content-type") ?: "?"}, panjang: ${body.length} char"
                val preview = if (body.length > maxChars) body.take(maxChars) + "\n... (dipotong, total ${body.length} char)" else body
                "$status\n\n$preview"
            }
        } catch (e: Exception) {
            "(Gagal ambil manifest: ${e.message})"
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
    // Rumble — video ID sama persis dengan kode di path embed URL
    // (rumble.com/embed/{videoId}/...). Endpoint embedJS/u3/ balikin JSON
    // resmi yang dipanggil player Rumble sendiri; kita ambil ua.hls.auto.url
    // (master playlist HLS standar). Field lain di response (`tar`,
    // `timeline`) itu skema byte-range custom Rumble (video di-pack jadi
    // 1 file .tar terus di-"potong" lewat query param r_file/r_range) -
    // gak perlu disentuh sama sekali karena hls.auto sudah nyediain
    // manifest m3u8 biasa yang CDN-nya translate otomatis di server-side
    // (dikonfirmasi manual: curl ke variant .tar?r_file=chunklist.m3u8...
    // balikin teks m3u8 valid, bukan file tar mentah/binary).
    //
    // CATATAN JARINGAN: Rumble dikonfirmasi diblokir sebagian ISP Indonesia
    // (minimal Telkomsel) di level SNI/TLS interception (server ngasih
    // sertifikat "internetbaik.telkomsel.com", bukan sertifikat Rumble asli).
    // FallbackDns (DoH) di client OkHttp TIDAK menolong kasus ini - itu cuma
    // efektif buat block di level DNS. User dengan jaringan yang diblokir
    // begini bakal tetap gagal resolve; caller otomatis fallback ke WebView
    // seperti host lain yang gagal resolve.
    // ---------------------------------------------------------------------
    private fun extractRumble(embedUrl: String, referer: String?): ResolvedStream? {
        val videoId = Regex("""rumble\.com/embed/([a-zA-Z0-9]+)""")
            .find(embedUrl)?.groupValues?.get(1) ?: return null

        val apiUrl = "https://rumble.com/embedJS/u3/?request=video&ver=2&v=$videoId"
        val json = fetchHtml(apiUrl, embedUrl)
        val ua = org.json.JSONObject(json).optJSONObject("ua") ?: return null

        val hlsUrl = ua.optJSONObject("hls")?.optJSONObject("auto")?.optString("url")
            ?.takeIf { it.isNotBlank() } ?: return null

        return ResolvedStream(
            url = hlsUrl,
            isHls = true,
            headers = mapOf("Referer" to "https://rumble.com/", "User-Agent" to DESKTOP_UA)
        )
    }

    // ---------------------------------------------------------------------
    // OK.ru (Odnoklassniki) — halaman embed-nya nyimpen metadata video di
    // attribute data-options pada div player (JSON ter-HTML-encode). Di
    // dalamnya ada flashvars.metadata yang itu sendiri STRING JSON lagi
    // (nested, di-escape sekali lagi) berisi hlsManifestUrl + daftar
    // kualitas video satuan (name: "full"/"720"/"480"/dst + url).
    //
    // CATATAN: kalau WebView aja udah gagal konek ke ok.ru
    // (net::ERR_CONNECTION_TIMED_OUT), scrape HTML di sini kemungkinan
    // besar JUGA bakal gagal connect - client-nya udah pasang FallbackDns
    // (DoH) jadi kalau masalahnya di-block di level DNS ISP ini bisa
    // nolong, tapi kalau block-nya di level koneksi/IP, tetap gagal.
    // ---------------------------------------------------------------------
    private fun extractOkRu(embedUrl: String, referer: String?): ResolvedStream? {
        val html = fetchHtml(embedUrl, referer ?: "https://ok.ru/")
        val raw = Regex("""data-options="([^"]*)"""").find(html)?.groupValues?.get(1) ?: return null
        val decoded = raw
            .replace("&quot;", "\"")
            .replace("&#039;", "'")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")

        val options = org.json.JSONObject(decoded)
        val flashvars = options.optJSONObject("flashvars") ?: return null
        val metadataRaw = flashvars.optString("metadata").takeIf { it.isNotBlank() } ?: return null
        val metadata = org.json.JSONObject(metadataRaw)

        // Prioritas: HLS manifest (adaptif, paling stabil buat ExoPlayer).
        val hlsUrl = metadata.optString("hlsManifestUrl").takeIf { it.isNotBlank() }
        if (hlsUrl != null) {
            return ResolvedStream(
                url = hlsUrl,
                isHls = true,
                headers = mapOf("Referer" to "https://ok.ru/", "User-Agent" to DESKTOP_UA)
            )
        }

        // Fallback: pilih video kualitas tertinggi yang tersedia dari daftar "videos".
        val videos = metadata.optJSONArray("videos") ?: return null
        val qualityOrder = listOf("full", "1080", "hd", "720", "sd", "480", "360", "low", "240", "mobile", "144")
        var bestUrl: String? = null
        for (wantedName in qualityOrder) {
            for (i in 0 until videos.length()) {
                val v = videos.getJSONObject(i)
                if (v.optString("name").equals(wantedName, ignoreCase = true)) {
                    bestUrl = v.optString("url").takeIf { it.isNotBlank() }
                    break
                }
            }
            if (bestUrl != null) break
        }
        if (bestUrl == null && videos.length() > 0) {
            bestUrl = videos.getJSONObject(0).optString("url").takeIf { it.isNotBlank() }
        }
        val url = bestUrl ?: return null

        return ResolvedStream(
            url = url,
            isHls = url.contains(".m3u8"),
            headers = mapOf("Referer" to "https://ok.ru/", "User-Agent" to DESKTOP_UA)
        )
    }

    // ---------------------------------------------------------------------
    // Dailymotion — video id diambil dari URL embed (/video/{id} atau
    // ?video={id} atau dai.ly/{id}), terus dipanggil ke endpoint metadata
    // resmi player-nya buat dapetin daftar kualitas (mp4 per-resolusi +
    // manifest HLS "auto"). Gak perlu HTML scraping, ini JSON API publik
    // yang emang dipanggil sama web player Dailymotion sendiri.
    //
    // PENTING: video Dailymotion sering dibatasi cuma bisa di-embed dari
    // domain tertentu (whitelist yang di-set uploader-nya). Jadi header
    // Referer/Origin buat MUTER stream-nya (bukan buat fetch metadata JSON)
    // harus pakai domain halaman ASLI tempat embed ini nempel (`referer`
    // yang dioper dari WebView/detail episode), bukan dailymotion.com —
    // kalau di-hardcode ke dailymotion.com, CDN-nya bisa nolak request
    // segment/manifest walau URL-nya sendiri valid (hasilnya: layar hitam,
    // durasi 00:00, ExoPlayer gak crash tapi videonya gak pernah kemuat).
    // ---------------------------------------------------------------------
    private fun extractDailymotion(embedUrl: String, referer: String?): ResolvedStream? {
        val videoId = Regex("""video[/=]([a-zA-Z0-9]+)""").find(embedUrl)?.groupValues?.get(1)
            ?: Regex("""dai\.ly/([a-zA-Z0-9]+)""").find(embedUrl)?.groupValues?.get(1)
            ?: return null

        val metaUrl = "https://www.dailymotion.com/player/metadata/video/$videoId"
        // Endpoint metadata JSON-nya sendiri API publik dailymotion.com, jadi aman
        // tetap pakai referer dailymotion.com khusus buat request INI SAJA.
        val json = fetchHtml(metaUrl, "https://www.dailymotion.com/")
        val meta = org.json.JSONObject(json)
        val qualities = meta.optJSONObject("qualities") ?: return null

        // Origin/Referer buat MUTER stream-nya (headers yang bakal dipasang ke
        // ExoPlayer): pakai domain halaman asli kalau ada, fallback dailymotion.com.
        val playbackReferer = referer?.takeIf { it.isNotBlank() } ?: "https://www.dailymotion.com/"
        val playbackHeaders = mapOf(
            "Referer" to playbackReferer,
            "Origin" to originOf(playbackReferer),
            "User-Agent" to DESKTOP_UA
        )

        // Prioritas: "auto" (HLS adaptif) baru fallback ke mp4 kualitas tertinggi.
        val autoArr = qualities.optJSONArray("auto")
        if (autoArr != null && autoArr.length() > 0) {
            val hlsUrl = autoArr.getJSONObject(0).optString("url").takeIf { it.isNotBlank() }
            if (hlsUrl != null) {
                return ResolvedStream(url = hlsUrl, isHls = true, headers = playbackHeaders)
            }
        }

        val qualityOrder = listOf("1080", "720", "480", "380", "240", "144")
        for (q in qualityOrder) {
            val arr = qualities.optJSONArray(q) ?: continue
            for (i in 0 until arr.length()) {
                val url = arr.getJSONObject(i).optString("url").takeIf { it.isNotBlank() }
                if (url != null) {
                    return ResolvedStream(url = url, isHls = url.contains(".m3u8"), headers = playbackHeaders)
                }
            }
        }
        return null
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
    // gdriveplayer.to (server GDRIVE/GDRIVE HD) — halaman embed-nya nyimpen
    // config JWPlayer dalam bentuk ter-obfuscate: base64 (atob) yang di-XOR
    // pakai key sendiri, bukan packer eval(p,a,c,k,e,..) standar. Polanya
    // (dikonfirmasi dari network trace langsung):
    //
    //   (function(){
    //     var k="xxxx", b=atob("BASE64...");
    //     var o=""; for(i=0;i<b.length;i++) o+=String.fromCharCode(b.charCodeAt(i)^k.charCodeAt(i%k.length));
    //     (0,eval)(o);
    //   })();
    //
    // `o` hasil decode-nya berisi JS biasa (kemungkinan besar `jwplayer(...).setup({sources:[{file:"..."}]})`),
    // jadi kita gak perlu eval beneran — cukup decode manual (base64+XOR) lalu
    // jalanin regex extractSourceFile() yang sama kayak buat packer JS lain.
    // ---------------------------------------------------------------------
    private fun extractGdrivePlayer(embedUrl: String, referer: String?): ResolvedStream? {
        val html = fetchHtml(embedUrl, referer ?: embedUrl)

        val kMatch = Regex("var k\\s*=\\s*\"([^\"]+)\"").find(html)
        val bMatch = Regex("""atob\(\s*"([^"]+)"\s*\)""").find(html, kMatch?.range?.last ?: 0)
        val k = kMatch?.groupValues?.get(1)
        val b64 = bMatch?.groupValues?.get(1)
        if (k.isNullOrEmpty() || b64.isNullOrEmpty()) {
            Log.d("VideoExtractor", "gdriveplayer: pola var k=/atob(...) gak ketemu di $embedUrl")
            lastDebugSnippet = buildString {
                appendLine("embedUrl: $embedUrl")
                appendLine("html length: ${html.length}")
                appendLine("kMatch ketemu?: ${k != null} (k=${k ?: "-"})")
                appendLine("bMatch (atob) ketemu?: ${b64 != null} (length=${b64?.length ?: 0})")
                val varKIdx = html.indexOf("var k")
                appendLine("index 'var k' di html: $varKIdx")
                if (varKIdx != -1) {
                    val s = (varKIdx - 20).coerceAtLeast(0)
                    val e = (varKIdx + 200).coerceAtMost(html.length)
                    appendLine("konteks: ...${html.substring(s, e)}...")
                }
            }
            return null
        }

        val decoded = runCatching {
            val bBytes = android.util.Base64.decode(b64, android.util.Base64.DEFAULT)
            // atob() JS menghasilkan "binary string" (1 char = 1 byte, Latin1/ISO-8859-1),
            // jadi kita mapping byte->char dengan cara yang sama biar charCodeAt cocok.
            val bStr = String(bBytes, Charsets.ISO_8859_1)
            val sb = StringBuilder(bStr.length)
            for (i in bStr.indices) {
                val xorCode = (bStr[i].code xor k[i % k.length].code)
                sb.append(xorCode.toChar())
            }
            sb.toString()
        }.getOrNull()

        if (decoded.isNullOrBlank()) {
            Log.d("VideoExtractor", "gdriveplayer: gagal decode base64+XOR di $embedUrl")
            lastDebugSnippet = buildString {
                appendLine("embedUrl: $embedUrl")
                appendLine("k: $k")
                appendLine("base64 length: ${b64.length}")
                appendLine("Proses decode base64+XOR GAGAL (exception atau hasil blank).")
                appendLine("base64 500 char pertama: ${b64.take(500)}")
            }
            return null
        }

        // Format aslinya BUKAN "sources: [{file: ...}]" ala JWPlayer biasa, tapi
        // variabel JS sendiri: HLS="hlsplaylist.php?s=...&idhls=....m3u8"
        // (dikonfirmasi dari hasil decode asli). Cek ini duluan, baru fallback
        // ke extractSourceFile generik buat jaga-jaga kalau formatnya berubah.
        val hlsVar = Regex("HLS\\s*=\\s*\"([^\"]+)\"").find(decoded)?.groupValues?.get(1)
        val fileUrl = hlsVar ?: extractSourceFile(decoded) ?: run {
            Log.d("VideoExtractor", "gdriveplayer: decode sukses tapi gak nemu HLS=/sources/file di hasil decode-nya")
            lastDebugSnippet = buildString {
                appendLine("embedUrl: $embedUrl")
                appendLine("k: $k")
                appendLine("base64 length: ${b64.length}")
                appendLine("decoded length: ${decoded.length}")
                appendLine("--- hasil decode (o) ---")
                appendLine(decoded.take(2000))
            }
            null
        } ?: return null

        val url = runCatching { URI(embedUrl).resolve(normalizeUrl(fileUrl)).toString() }
            .getOrDefault(normalizeUrl(fileUrl))
        Log.d("VideoExtractor", "gdriveplayer: berhasil decode -> $url")
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
            val packedMatch = PACKED_EVAL_REGEX.find(working) ?: break
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
                        val packedMatch = PACKED_EVAL_REGEX.find(jsWorking) ?: break
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
            fun findFullIife(): String {
                val idx = html.indexOf("var k=")
                if (idx == -1) return "[var k= IIFE]: tidak ketemu"
                // Cari penutup atob("....") -> lompat ke SETELAH blob base64-nya,
                // karena bagian penting (fungsi decode k+b) ada di situ, bukan di
                // tengah-tengah blob base64 yang panjang banget.
                val atobIdx = html.indexOf("atob(", idx)
                val afterBlobIdx = if (atobIdx != -1) {
                    // cari `")` pertama setelah atob( sebagai penutup string base64
                    val closeIdx = html.indexOf("\")", atobIdx)
                    if (closeIdx != -1) closeIdx else idx
                } else idx
                val start = (afterBlobIdx - 40).coerceAtLeast(0)
                val end = (afterBlobIdx + 3000).coerceAtMost(html.length)
                return "[lanjutan setelah atob(...) ] @$afterBlobIdx: ${html.substring(start, end)}"
            }
            lastDebugSnippet = buildString {
                appendLine("embedUrl: $embedUrl")
                appendLine("html length: ${html.length}")
                appendLine("ada packed-JS (eval p,a,c,k,e)?: $hasPacked")
                appendLine("file.js src ditemuin di HTML?: ${jsSrc ?: "tidak ada"}")
                appendLine("file.js berhasil di-fetch?: ${!externalJs.isNullOrBlank()} (length=${externalJs?.length ?: 0})")
                appendLine()
                appendLine(findFullIife())
                appendLine()
                appendLine(findContext("hlsplaylist", "hlsplaylist"))
                appendLine()
                appendLine(findContext("fetch(", "fetch("))
                appendLine()
                appendLine(findContext("no_adult", "no_adult (param embed)"))
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
    // Regex buat nangkep FULL isi `eval(function(p,a,c,k,e,d){...}(payload,radix,count,'dict'.split('|'),0,{}))`.
    //
    // PENTING kenapa gak boleh cuma `.*?\)\)` (non-greedy) doang: fungsi unpacker-nya
    // SENDIRI (`e=function(c){return(c<a?'':e(parseInt(c/a)))+...`) udah punya `))`
    // di tengah body-nya sendiri (dari `parseInt(c/a))`), jauh sebelum payload asli
    // kelar. Non-greedy match bakal berhenti DI SITU (ketemu di anichin.stream:
    // hasilnya cuma ke-capture 71 karakter doang dari ~1743 karakter yang harusnya),
    // jadi `unpackJs()` selalu dikasih string setengah jadi -> selalu gagal parse
    // -> extractor ini selalu jatuh ke WebView fallback padahal sebenarnya JS-nya
    // decode-able. Fix: paksa match harus lewatin `.split('|')` dulu baru boleh
    // berhenti di `))` — itu satu-satunya `))` yang valid jadi penutup IIFE.
    private val PACKED_EVAL_REGEX = Regex(
        """eval\(function\(p,a,c,k,e,[rd]\).*?\.split\('\|'\).*?\)\)""",
        RegexOption.DOT_MATCHES_ALL
    )

    /**
     * Replikasi persis fungsi encode `e(c)` bawaan packer (BUKAN base konversi
     * standar!). Dipakai buat bangun dictionary token->keyword di unpackJs().
     *
     * Kenapa gak bisa pakai `Integer.toString(c, radix)` bawaan Kotlin/Java:
     * radix di packed script BISA lebih dari 36 (mis. anichin.stream pakai a=62,
     * dictionary isinya 117 kata) - tapi `Integer.toString()` di JVM cuma
     * support radix 2-36 dan bakal LEMPAR IllegalArgumentException kalau dikasih
     * radix di atas itu (ke-catch diam-diam di try/catch luar -> extraction
     * selalu gagal). Packer aslinya nge-handle ini dengan encoding custom:
     * digit 0-35 pakai karakter biasa (0-9,a-z, sama kayak toString(36)), tapi
     * digit 36 ke atas (buat radix > 36) pakai `String.fromCharCode(digit+29)`
     * yang hasilnya karakter 'A'-'Z' (36+29=65='A', 61+29=90='Z'). Ini fungsi
     * Kotlin-nya, sama persis logikanya cuma bentuk iteratif (bukan rekursif).
     */
    private fun packerEncodeToken(value: Int, radix: Int): String {
        fun digitChar(d: Int): Char =
            if (d > 35) (d + 29).toChar() else if (d < 10) ('0' + d) else ('a' + (d - 10))
        if (value == 0) return digitChar(0).toString()
        var n = value
        val digits = ArrayDeque<Char>()
        while (n > 0) {
            digits.addFirst(digitChar(n % radix))
            n /= radix
        }
        return digits.joinToString("")
    }

    private fun unpackJs(packed: String): String? {
        val match = Regex(
            """\}\('(.*)',\s*(\d+),\s*(\d+),\s*'(.*?)'\.split\('\|'\)""",
            RegexOption.DOT_MATCHES_ALL
        ).find(packed) ?: return null

        val payload = match.groupValues[1]
        val radix = match.groupValues[2].toIntOrNull() ?: 36
        val count = match.groupValues[3].toIntOrNull() ?: return null
        val keywords = match.groupValues[4].split("|")

        // token base-radix (mis. "a3", "12", atau "A3" kalau radix>36) -> keyword
        // aslinya, sekali bikin aja. Pakai packerEncodeToken (bukan
        // Integer.toString) supaya benar juga untuk radix>36 (mis. anichin.stream
        // yang pakai a=62 - Integer.toString cuma sanggup radix 2-36 dan bakal
        // throw IllegalArgumentException kalau dipaksa radix 62).
        val dict = HashMap<String, String>(count * 2)
        for (c in 0 until count) {
            if (c < keywords.size && keywords[c].isNotEmpty()) {
                dict[packerEncodeToken(c, radix)] = keywords[c]
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
