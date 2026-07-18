package com.example.network

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.util.Log

/**
 * Download episode ke penyimpanan device, TAPI cuma buat stream yang udah berhasil
 * di-resolve VideoExtractor jadi direct link (isDirectStream == true di ViewModel),
 * karena cuma di kondisi itu kita punya URL video mentah + header yang dibutuhin.
 *
 * Episode yang masih WebView fallback (embed Mega/Filedon dkk yang gak berhasil
 * di-extract) TIDAK bisa didownload lewat sini — gak ada direct URL yang bisa dikasih
 * ke DownloadManager, cuma HTML embed page.
 *
 * HLS (.m3u8) juga sengaja dikecualikan: itu cuma manifest teks berisi daftar link
 * segment .ts, DownloadManager bakal ngedownload teks manifest-nya doang bukan
 * video utuh — butuh HLS segment-downloader (media3 DownloadManager) buat itu,
 * di luar scope simple direct-download ini.
 *
 * File disimpen ke folder PUBLIK (Movies/Aniku) lewat setDestinationInExternalPublicDir(),
 * bukan folder internal/khusus app — ini yang bikin file-nya SELAMAT walau app di-uninstall,
 * karena folder publik itu bukan punya package aplikasi, jadi gak ikut kehapus sama sistem.
 */
object VideoDownloadManager {

    private const val TAG = "VideoDownloadManager"
    const val SUBFOLDER = DownloadsManager.SUBFOLDER

    /** Auto-deteksi: URL ini bisa didownload langsung (progressive mp4/mkv), bukan HLS. */
    fun isDownloadableUrl(url: String?): Boolean {
        if (url.isNullOrBlank()) return false
        return !url.substringBefore("?").contains(".m3u8", ignoreCase = true)
    }

    private fun sanitize(name: String): String =
        name.replace(Regex("[^A-Za-z0-9 _.-]"), "").trim().ifBlank { "Aniku" }

    fun buildFileName(animeTitle: String, episodeTitle: String, url: String): String {
        val extension = if (url.substringBefore("?").endsWith(".mkv", ignoreCase = true)) "mkv" else "mp4"
        return "${sanitize(animeTitle)} - ${sanitize(episodeTitle)}.$extension"
    }

    /**
     * Enqueue download ke DownloadManager sistem Android.
     * Return downloadId kalau berhasil di-enqueue, null kalau gagal (misal service gak ada).
     */
    fun enqueueDownload(
        context: Context,
        url: String,
        headers: Map<String, String>,
        fileName: String
    ): Long? {
        return try {
            val request = DownloadManager.Request(Uri.parse(url)).apply {
                setTitle(fileName)
                setDescription("Mengunduh dari Aniku")
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                setDestinationInExternalPublicDir(Environment.DIRECTORY_MOVIES, "$SUBFOLDER/$fileName")
                setAllowedOverMetered(true)
                setAllowedOverRoaming(true)

                // Header dari hasil resolve (Referer/Origin/User-Agent) wajib disertain,
                // banyak host nge-block request tanpa Referer yang bener (hotlink protection).
                headers.forEach { (key, value) -> addRequestHeader(key, value) }
                if (headers.keys.none { it.equals("User-Agent", ignoreCase = true) }) {
                    addRequestHeader(
                        "User-Agent",
                        "Mozilla/5.0 (Linux; Android 13; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
                    )
                }
            }

            val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            downloadManager.enqueue(request)
        } catch (e: Exception) {
            Log.e(TAG, "Gagal enqueue download", e)
            null
        }
    }
}
