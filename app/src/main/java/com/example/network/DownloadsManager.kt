package com.example.network

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.util.Log
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import java.io.File

enum class DownloadStatus { PENDING, DOWNLOADING, COMPLETE, FAILED }

data class DownloadRecord(
    val downloadId: Long,
    val userId: String,
    val animeSlug: String,
    val animeTitle: String,
    val animePoster: String,
    val episodeSlug: String,
    val episodeTitle: String,
    val fileName: String,
    val localPath: String? = null,
    val status: String = DownloadStatus.PENDING.name,
    val downloadedAt: Long = System.currentTimeMillis()
)

/**
 * Nyimpen daftar episode yang lagi/udah didownload, PER USER (di-filter pakai userId),
 * lokal di device via SharedPreferences — pola yang sama kayak WatchHistoryManager/BookmarkManager.
 *
 * PENTING soal uninstall app:
 * - Metadata (judul, poster, status) ada di SharedPreferences punya app, jadi IKUT
 *   kehapus kalau app di-uninstall/clear-data.
 * - FILE VIDEO-nya sendiri SENGAJA disimpen di folder publik (Movies/Aniku), lewat
 *   DownloadManager.setDestinationInExternalPublicDir() di VideoDownloadManager — folder
 *   ini punya sistem/shared storage, BUKAN punya package aplikasi, jadi secara desain
 *   Android TIDAK ikut kehapus pas app di-uninstall.
 * - Biar list di-app gak "amnesia" abis install ulang, refreshForUser() ikut nge-scan
 *   folder Movies/Aniku dan bikin ulang entry buat file yang udah ada tapi belum
 *   tercatat lagi di metadata (kepemilikan otomatis di-assign ke user yang lagi login,
 *   karena userId asli gak bisa direkonstruksi cuma dari nama file).
 */
class DownloadsManager(private val context: Context) {
    private val prefs = context.getSharedPreferences("aniku_downloads", Context.MODE_PRIVATE)
    private val moshi = Moshi.Builder()
        .add(com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory())
        .build()
    private val type = Types.newParameterizedType(List::class.java, DownloadRecord::class.java)
    private val adapter = moshi.adapter<List<DownloadRecord>>(type)

    companion object {
        const val SUBFOLDER = "Aniku"
    }

    private fun getAll(): List<DownloadRecord> {
        val json = prefs.getString("download_list", null) ?: return emptyList()
        return try {
            adapter.fromJson(json) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun saveAll(list: List<DownloadRecord>) {
        prefs.edit().putString("download_list", adapter.toJson(list)).apply()
    }

    /** List download milik user tertentu doang — user lain di device yang sama gak keliatan. */
    fun getForUser(userId: String): List<DownloadRecord> {
        scanForOrphanFiles(userId)
        return getAll().filter { it.userId == userId }.sortedByDescending { it.downloadedAt }
    }

    fun addRecord(record: DownloadRecord) {
        val current = getAll().toMutableList()
        current.removeAll { it.downloadId == record.downloadId }
        current.add(0, record)
        saveAll(current)
    }

    fun removeRecord(record: DownloadRecord) {
        try {
            record.localPath?.let { path ->
                val f = File(path)
                if (f.exists()) f.delete()
            }
        } catch (e: Exception) {
            Log.e("DownloadsManager", "Gagal hapus file lokal", e)
        }
        try {
            val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            downloadManager.remove(record.downloadId)
        } catch (_: Exception) {
        }
        val current = getAll().toMutableList()
        current.removeAll { it.downloadId == record.downloadId }
        saveAll(current)
    }

    /** Cek status DownloadManager buat semua record yang masih PENDING/DOWNLOADING, update kalau udah kelar. */
    fun refreshPendingStatuses() {
        val current = getAll()
        val pending = current.filter {
            it.status == DownloadStatus.PENDING.name || it.status == DownloadStatus.DOWNLOADING.name
        }
        if (pending.isEmpty()) return
        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        pending.forEach { record ->
            try {
                val cursor = downloadManager.query(DownloadManager.Query().setFilterById(record.downloadId))
                cursor.use {
                    if (it.moveToFirst()) {
                        val statusIdx = it.getColumnIndex(DownloadManager.COLUMN_STATUS)
                        val uriIdx = it.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI)
                        val statusCode = if (statusIdx >= 0) it.getInt(statusIdx) else -1
                        when (statusCode) {
                            DownloadManager.STATUS_SUCCESSFUL -> {
                                val localUriStr = if (uriIdx >= 0) it.getString(uriIdx) else null
                                val localPath = localUriStr?.let { u -> Uri.parse(u).path }
                                updateStatus(record.downloadId, DownloadStatus.COMPLETE, localPath)
                            }
                            DownloadManager.STATUS_FAILED -> updateStatus(record.downloadId, DownloadStatus.FAILED)
                            DownloadManager.STATUS_RUNNING,
                            DownloadManager.STATUS_PENDING,
                            DownloadManager.STATUS_PAUSED -> updateStatus(record.downloadId, DownloadStatus.DOWNLOADING)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("DownloadsManager", "Gagal cek status download ${record.downloadId}", e)
            }
        }
    }

    private fun updateStatus(downloadId: Long, status: DownloadStatus, localPath: String? = null) {
        val current = getAll().toMutableList()
        val idx = current.indexOfFirst { it.downloadId == downloadId }
        if (idx == -1) return
        current[idx] = current[idx].copy(status = status.name, localPath = localPath ?: current[idx].localPath)
        saveAll(current)
    }

    /** Rekonstruksi entry buat file yang udah ada di folder publik tapi belum/gak lagi
     *  tercatat di metadata (kasus abis reinstall app / clear data). */
    private fun scanForOrphanFiles(currentUserId: String) {
        try {
            val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES), SUBFOLDER)
            if (!dir.exists() || !dir.isDirectory) return
            val current = getAll().toMutableList()
            val knownPaths = current.mapNotNull { it.localPath }.toSet()
            val orphanFiles = dir.listFiles { f -> f.isFile && f.absolutePath !in knownPaths } ?: emptyArray()
            if (orphanFiles.isEmpty()) return
            orphanFiles.forEach { file ->
                val nameNoExt = file.nameWithoutExtension
                val parts = nameNoExt.split(" - ", limit = 2)
                current.add(
                    0,
                    DownloadRecord(
                        downloadId = -(file.absolutePath.hashCode().toLong()),
                        userId = currentUserId,
                        animeSlug = "",
                        animeTitle = parts.getOrNull(0) ?: nameNoExt,
                        animePoster = "",
                        episodeSlug = "",
                        episodeTitle = parts.getOrNull(1) ?: "",
                        fileName = file.name,
                        localPath = file.absolutePath,
                        status = DownloadStatus.COMPLETE.name,
                        downloadedAt = file.lastModified()
                    )
                )
            }
            saveAll(current)
        } catch (e: Exception) {
            Log.e("DownloadsManager", "Gagal scan folder download", e)
        }
    }
}
