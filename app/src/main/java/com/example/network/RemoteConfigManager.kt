package com.example.network

import android.util.Log
import com.google.firebase.remoteconfig.ConfigUpdate
import com.google.firebase.remoteconfig.ConfigUpdateListener
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfigException
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Kill-switch fitur via Firebase Remote Config. Toggle di Firebase Console
 * (Remote Config) tanpa perlu rilis apk baru. Semua default TRUE (nyala),
 * jadi kalau parameter belum dibuat di console, fitur tetap jalan normal.
 *
 * Key parameter yang dipakai di Firebase Console:
 * - chat_room_enabled
 * - nobar_enabled
 * - chat_image_upload_enabled
 * - feed_enabled
 * - comment_enabled
 * - download_enabled
 * - maintenance_mode
 * - maintenance_message
 * - app_shutdown_enabled: kill-switch PERMANEN, beda sama maintenance_mode yang kesannya
 *   sementara. Dipakai buat nutup total aplikasi (misal: admin udah gak sanggup bayar
 *   biaya database bulanan). Prioritas paling tinggi, di atas maintenance & ban.
 * - app_shutdown_message: pesan utama yang tampil di layar shutdown
 * - app_shutdown_support_info: teks opsional (link donasi/kontak admin), boleh dikosongin
 * - default_data_source: source yang dipake user baru / kalau source aktifnya kena disable
 *   (contoh: "Dayynime-v1")
 * - disabled_sources: daftar source yang dimatiin, dipisah koma
 *   (contoh: "Dayynime-v1,Dayynime-v2")
 */
class RemoteConfigManager {

    private val remoteConfig: FirebaseRemoteConfig by lazy {
        FirebaseRemoteConfig.getInstance().apply {
            setConfigSettingsAsync(
                FirebaseRemoteConfigSettings.Builder()
                    // 1 jam cache di production. Kalau lagi testing toggle,
                    // bisa diganti sementara ke 0 biar instan tiap fetch.
                    .setMinimumFetchIntervalInSeconds(3600)
                    .build()
            )
            setDefaultsAsync(
                mapOf(
                    KEY_CHAT_ROOM to true,
                    KEY_NOBAR to true,
                    KEY_CHAT_IMAGE_UPLOAD to true,
                    KEY_FEED to true,
                    KEY_COMMENT to true,
                    KEY_DOWNLOAD to true,
                    KEY_MAINTENANCE_MODE to false,
                    KEY_MAINTENANCE_MESSAGE to "Aniku lagi maintenance sebentar, balik lagi nanti ya!",
                    KEY_SHUTDOWN_ENABLED to false,
                    KEY_SHUTDOWN_MESSAGE to "Aniku terpaksa tutup karena biaya server & database bulanan udah gak bisa ditanggung lagi oleh admin.",
                    KEY_SHUTDOWN_SUPPORT_INFO to "",
                    KEY_DEFAULT_SOURCE to "Dayynime-v1",
                    KEY_DISABLED_SOURCES to ""
                )
            )
        }
    }

    private val _chatRoomEnabled = MutableStateFlow(true)
    val chatRoomEnabled: StateFlow<Boolean> = _chatRoomEnabled.asStateFlow()

    private val _nobarEnabled = MutableStateFlow(true)
    val nobarEnabled: StateFlow<Boolean> = _nobarEnabled.asStateFlow()

    private val _chatImageUploadEnabled = MutableStateFlow(true)
    val chatImageUploadEnabled: StateFlow<Boolean> = _chatImageUploadEnabled.asStateFlow()

    private val _feedEnabled = MutableStateFlow(true)
    val feedEnabled: StateFlow<Boolean> = _feedEnabled.asStateFlow()

    private val _commentsEnabled = MutableStateFlow(true)
    val commentsEnabled: StateFlow<Boolean> = _commentsEnabled.asStateFlow()

    private val _downloadEnabled = MutableStateFlow(true)
    val downloadEnabled: StateFlow<Boolean> = _downloadEnabled.asStateFlow()

    private val _maintenanceMode = MutableStateFlow(false)
    val maintenanceMode: StateFlow<Boolean> = _maintenanceMode.asStateFlow()

    private val _maintenanceMessage = MutableStateFlow("")
    val maintenanceMessage: StateFlow<String> = _maintenanceMessage.asStateFlow()

    // Kill-switch permanen: aplikasi ditutup total (misal admin gak sanggup lagi
    // bayar database bulanan). Beda dari maintenance yang kesannya sementara.
    private val _shutdownEnabled = MutableStateFlow(false)
    val shutdownEnabled: StateFlow<Boolean> = _shutdownEnabled.asStateFlow()

    private val _shutdownMessage = MutableStateFlow("")
    val shutdownMessage: StateFlow<String> = _shutdownMessage.asStateFlow()

    private val _shutdownSupportInfo = MutableStateFlow("")
    val shutdownSupportInfo: StateFlow<String> = _shutdownSupportInfo.asStateFlow()

    // Sumber Data: default buat user baru + daftar source yang dimatiin dari console
    private val _defaultDataSource = MutableStateFlow("Dayynime-v1")
    val defaultDataSource: StateFlow<String> = _defaultDataSource.asStateFlow()

    private val _disabledSources = MutableStateFlow<Set<String>>(emptySet())
    val disabledSources: StateFlow<Set<String>> = _disabledSources.asStateFlow()

    /** Fetch nilai terbaru dari server lalu update semua flag. Panggil pas app start. */
    fun fetchAndApply() {
        // Ambil nilai awal pas app pertama kali dibuka
        remoteConfig.fetchAndActivate()
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    Log.d("RemoteConfig", "Fetch awal sukses, updated: ${task.result}")
                } else {
                    Log.w("RemoteConfig", "Fetch awal gagal, pakai nilai default/cache lama")
                }
                applyValues()
            }

        // Real-time listener: begitu ada perubahan yang di-publish di console,
        // langsung ke-apply ke app tanpa nunggu fetch interval atau buka ulang app.
        remoteConfig.addOnConfigUpdateListener(object : ConfigUpdateListener {
            override fun onUpdate(configUpdate: ConfigUpdate) {
                Log.d("RemoteConfig", "Ada update real-time: ${configUpdate.updatedKeys}")
                remoteConfig.activate().addOnCompleteListener {
                    applyValues()
                }
            }

            override fun onError(error: FirebaseRemoteConfigException) {
                Log.w("RemoteConfig", "Real-time listener error", error)
            }
        })
    }

    private fun applyValues() {
        _chatRoomEnabled.value = remoteConfig.getBoolean(KEY_CHAT_ROOM)
        _nobarEnabled.value = remoteConfig.getBoolean(KEY_NOBAR)
        _chatImageUploadEnabled.value = remoteConfig.getBoolean(KEY_CHAT_IMAGE_UPLOAD)
        _feedEnabled.value = remoteConfig.getBoolean(KEY_FEED)
        _commentsEnabled.value = remoteConfig.getBoolean(KEY_COMMENT)
        _downloadEnabled.value = remoteConfig.getBoolean(KEY_DOWNLOAD)
        _maintenanceMode.value = remoteConfig.getBoolean(KEY_MAINTENANCE_MODE)
        _maintenanceMessage.value = remoteConfig.getString(KEY_MAINTENANCE_MESSAGE)
        _shutdownEnabled.value = remoteConfig.getBoolean(KEY_SHUTDOWN_ENABLED)
        _shutdownMessage.value = remoteConfig.getString(KEY_SHUTDOWN_MESSAGE)
        _shutdownSupportInfo.value = remoteConfig.getString(KEY_SHUTDOWN_SUPPORT_INFO)
        _defaultDataSource.value = remoteConfig.getString(KEY_DEFAULT_SOURCE).ifBlank { "Dayynime-v1" }
        _disabledSources.value = remoteConfig.getString(KEY_DISABLED_SOURCES)
            .split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toSet()
    }

    companion object {
        private const val KEY_CHAT_ROOM = "chat_room_enabled"
        private const val KEY_NOBAR = "nobar_enabled"
        private const val KEY_CHAT_IMAGE_UPLOAD = "chat_image_upload_enabled"
        private const val KEY_FEED = "feed_enabled"
        private const val KEY_COMMENT = "comment_enabled"
        private const val KEY_DOWNLOAD = "download_enabled"
        private const val KEY_MAINTENANCE_MODE = "maintenance_mode"
        private const val KEY_MAINTENANCE_MESSAGE = "maintenance_message"
        private const val KEY_SHUTDOWN_ENABLED = "app_shutdown_enabled"
        private const val KEY_SHUTDOWN_MESSAGE = "app_shutdown_message"
        private const val KEY_SHUTDOWN_SUPPORT_INFO = "app_shutdown_support_info"
        private const val KEY_DEFAULT_SOURCE = "default_data_source"
        private const val KEY_DISABLED_SOURCES = "disabled_sources"
    }
}
