package com.example.network

import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URI
import java.util.concurrent.TimeUnit

/**
 * Hasil resolve satu embed URL -> link video langsung yang bisa dikasih ke ExoPlayer,
 * lengkap dengan header (Referer/Origin/User-Agent) yang dibutuhkan host-nya.
 */
data class ResolvedStream(
    val url: String,
    val isHls: Boolean,
    val headers: Map<String, String> = emptyMap(),
    val debugLog: String? = null  // diisi saat extractor gagal, berisi 500 char pertama response
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

    private const val DESKTOP_UA =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    suspend fun resolve(embedUrl: String, referer: String? = null): ResolvedStream? {
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
                host.contains("blogger") || host.contains("blogspot") -> extractBlogger(embedUrl, referer)
                host.contains("filemoon") ||
                host.contains("vidhide") ||
                host.contains("wibufile") ||
                host.contains("streamhide") ||
                host.contains("moviesm4u") ||
                host.contains("ztreamhub") ||
                host.contains("guccihide") -> extractPackedJwPlayer(embedUrl, referer)
                else -> {
                    Log.d("VideoExtractor", "Host '$host' belum ada extractor-nya")
                    null
                }
            }
        } catch (e: Exception) {
            Log.e("VideoExtractor", "Gagal resolve $embedUrl: ${e.message}")
            null
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

        val url = fileUrl ?: run {
            Log.d("VideoExtractor", "Gak nemu sources/file di $embedUrl - mungkin JS-nya render dinamis (SPA/XHR), bukan static config")
            return null
        }
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

        val snippet = html.take(500)
        Log.d("VideoExtractor", "Blogger: tidak ditemukan video URL. HTML snippet: $snippet")
        return ResolvedStream(
            url = "",
            isHls = false,
            debugLog = "[Blogger Debug] Semua pattern gagal.\nResponse 500 char pertama:\n$snippet"
        )
    }

    /**
     * Implementasi unpacker generik untuk "Dean Edwards packer"
     * — format obfuscation umum yang dipakai banyak situs mirror video.
     */
    private fun unpackJs(packed: String): String? {
        val match = Regex(
            """\}\('(.*)',\s*(\d+),\s*(\d+),\s*'(.*?)'\.split\('\|'\)""",
            RegexOption.DOT_MATCHES_ALL
        ).find(packed) ?: return null

        var payload = match.groupValues[1]
        val radix = match.groupValues[2].toIntOrNull() ?: 36
        val count = match.groupValues[3].toIntOrNull() ?: return null
        val keywords = match.groupValues[4].split("|")

        for (c in count - 1 downTo 0) {
            if (c < keywords.size && keywords[c].isNotEmpty()) {
                val token = Integer.toString(c, radix)
                payload = Regex("\\b$token\\b").replace(payload, keywords[c])
            }
        }
        return payload.replace("\\'", "'").replace("\\\\", "\\")
    }
}
