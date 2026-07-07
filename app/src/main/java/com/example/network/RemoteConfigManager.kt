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
                    KEY_COMMENT to true
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
    }

    companion object {
        private const val KEY_CHAT_ROOM = "chat_room_enabled"
        private const val KEY_NOBAR = "nobar_enabled"
        private const val KEY_CHAT_IMAGE_UPLOAD = "chat_image_upload_enabled"
        private const val KEY_FEED = "feed_enabled"
        private const val KEY_COMMENT = "comment_enabled"
    }
}
