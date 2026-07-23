package com.example.ui.theme

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "aniku_settings")

class SettingsStore(private val context: Context) {
    companion object {
        val THEME_DARK = booleanPreferencesKey("theme_dark")
        val TEXT_SIZE = stringPreferencesKey("text_size") // "Kecil", "Sedang", "Besar"
        val ACCENT_COLOR = stringPreferencesKey("accent_color") // "Red", "Green", "Blue", "Purple", "Orange"
        val GRID_LAYOUT = stringPreferencesKey("grid_layout") // "2", "3", "List"
        val DATA_SOURCE = stringPreferencesKey("data_source") // "Dayynime-v1", "Dayynime-v2", "Dayynime-v3", "Dayynime-v4"
        val THEME_PRESET = stringPreferencesKey("theme_preset") // "Default", "Netflix", "Midnight"
        val CARD_STYLE = stringPreferencesKey("card_style") // "Rounded", "Sharp", "Poster", "Wide"
        val NAV_STYLE = stringPreferencesKey("nav_style") // "IconLabel", "IconOnly", "PillLabel", "PillIcon"

        // Auth/User details
        val AUTH_TOKEN = stringPreferencesKey("auth_token")
        val REFRESH_TOKEN = stringPreferencesKey("refresh_token")
        val USER_ID = stringPreferencesKey("user_id")
        val USER_EMAIL = stringPreferencesKey("user_email")
        val USERNAME = stringPreferencesKey("username")
        val AVATAR_URL = stringPreferencesKey("avatar_url")
        val IS_ADMIN = booleanPreferencesKey("is_admin")
        val USER_NUMBER = intPreferencesKey("user_number")
        val IS_MODERATOR = booleanPreferencesKey("is_moderator")
        val IS_BETA = booleanPreferencesKey("is_beta")
        val CUSTOM_NAME_COLOR = stringPreferencesKey("custom_name_color")
        val IS_BANNED = booleanPreferencesKey("is_banned")
        val LAST_CHAT_READ = stringPreferencesKey("last_chat_read")
        val CHAT_NOTIF_ENABLED = booleanPreferencesKey("chat_notif_enabled")
        val APP_LOCK_ENABLED = booleanPreferencesKey("app_lock_enabled")
        val APP_LOCK_TYPE = stringPreferencesKey("app_lock_type") // "pin" | "biometric"
        val APP_PIN = stringPreferencesKey("app_pin")
    }

    val isDarkFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[THEME_DARK] ?: true // Premium Dark is default
    }

    val textSizeFlow: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[TEXT_SIZE] ?: "Sedang"
    }

    val accentColorFlow: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[ACCENT_COLOR] ?: "Red"
    }

    val gridLayoutFlow: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[GRID_LAYOUT] ?: "2"
    }

    val dataSourceFlow: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[DATA_SOURCE] ?: "Dayynime-v1"
    }

    // Nilai mentah (null kalau user belum pernah milih source sendiri). Dipakai
    // buat bedain "belum pernah pilih" (pakai default_data_source dari remote config)
    // vs "user emang milih Dayynime-v1" secara eksplisit.
    val dataSourceRawFlow: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[DATA_SOURCE]
    }

    val themePresetFlow: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[THEME_PRESET] ?: "Default"
    }

    val cardStyleFlow: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[CARD_STYLE] ?: "Rounded"
    }

    val navStyleFlow: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[NAV_STYLE] ?: "IconLabel"
    }

    val sessionFlow: Flow<UserSession> = context.dataStore.data.map { preferences ->
        val token = preferences[AUTH_TOKEN]
        UserSession(
            token = if (token.isNullOrEmpty()) null else token,
            refreshToken = preferences[REFRESH_TOKEN],
            userId = preferences[USER_ID],
            email = preferences[USER_EMAIL],
            username = preferences[USERNAME],
            avatarUrl = preferences[AVATAR_URL],
            isAdmin = preferences[IS_ADMIN] ?: false,
            isModerator = preferences[IS_MODERATOR] ?: false,
            isBeta = preferences[IS_BETA] ?: false,
            customNameColor = preferences[CUSTOM_NAME_COLOR],
            isBanned = preferences[IS_BANNED] ?: false,
            userNumber = preferences[USER_NUMBER]
        )
    }

    suspend fun setTheme(isDark: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[THEME_DARK] = isDark
        }
    }

    suspend fun setTextSize(size: String) {
        context.dataStore.edit { preferences ->
            preferences[TEXT_SIZE] = size
        }
    }

    suspend fun setAccentColor(color: String) {
        context.dataStore.edit { preferences ->
            preferences[ACCENT_COLOR] = color
        }
    }

    suspend fun setGridLayout(layout: String) {
        context.dataStore.edit { preferences ->
            preferences[GRID_LAYOUT] = layout
        }
    }

    suspend fun setDataSource(source: String) {
        context.dataStore.edit { preferences ->
            preferences[DATA_SOURCE] = source
        }
    }

    suspend fun setThemePreset(preset: String) {
        context.dataStore.edit { preferences ->
            preferences[THEME_PRESET] = preset
        }
    }

    suspend fun setCardStyle(style: String) {
        context.dataStore.edit { preferences ->
            preferences[CARD_STYLE] = style
        }
    }

    suspend fun setNavStyle(style: String) {
        context.dataStore.edit { preferences ->
            preferences[NAV_STYLE] = style
        }
    }

    suspend fun saveSession(session: UserSession) {
        context.dataStore.edit { preferences ->
            preferences[AUTH_TOKEN] = session.token ?: ""
            preferences[REFRESH_TOKEN] = session.refreshToken ?: ""
            preferences[USER_ID] = session.userId ?: ""
            preferences[USER_EMAIL] = session.email ?: ""
            preferences[USERNAME] = session.username ?: ""
            preferences[AVATAR_URL] = session.avatarUrl ?: ""
            preferences[IS_ADMIN] = session.isAdmin
            preferences[IS_MODERATOR] = session.isModerator
            preferences[IS_BETA] = session.isBeta
            if (session.customNameColor != null) {
                preferences[CUSTOM_NAME_COLOR] = session.customNameColor
            } else {
                preferences.remove(CUSTOM_NAME_COLOR)
            }
            preferences[IS_BANNED] = session.isBanned
            session.userNumber?.let { preferences[USER_NUMBER] = it }
        }
    }

    suspend fun clearSession() {
        context.dataStore.edit { preferences ->
            preferences.remove(AUTH_TOKEN)
            preferences.remove(REFRESH_TOKEN) // Fix: juga hapus refresh token agar tidak dipakai ulang setelah expired
            preferences.remove(USER_ID)
            preferences.remove(USER_EMAIL)
            preferences.remove(USERNAME)
            preferences.remove(AVATAR_URL)
            preferences.remove(IS_ADMIN)
            preferences.remove(IS_MODERATOR)
            preferences.remove(IS_BETA)
            preferences.remove(CUSTOM_NAME_COLOR)
            preferences.remove(IS_BANNED)
            preferences.remove(USER_NUMBER)
        }
    }

    val lastChatReadFlow: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[LAST_CHAT_READ] ?: ""
    }

    suspend fun saveLastChatRead(timestamp: String) {
        context.dataStore.edit { preferences ->
            preferences[LAST_CHAT_READ] = timestamp
        }
    }

    // Toggle notifikasi chat (on by default)
    val chatNotifEnabledFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[CHAT_NOTIF_ENABLED] ?: true
    }

    suspend fun setChatNotifEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[CHAT_NOTIF_ENABLED] = enabled
        }
        // Mirror ke SharedPreferences biasa, soalnya FCM service baca ini
        // secara sinkron (DataStore butuh coroutine/Flow buat dibaca).
        context.getSharedPreferences("aniku_settings_cache", Context.MODE_PRIVATE)
            .edit().putBoolean("chat_notif_enabled", enabled).apply()
    }

    val appLockEnabledFlow: Flow<Boolean> = context.dataStore.data.map { it[APP_LOCK_ENABLED] ?: false }
    val appLockTypeFlow: Flow<String> = context.dataStore.data.map { it[APP_LOCK_TYPE] ?: "pin" }
    val appPinFlow: Flow<String> = context.dataStore.data.map { it[APP_PIN] ?: "" }

    suspend fun saveAppLock(enabled: Boolean, type: String, pin: String) {
        context.dataStore.edit { preferences ->
            preferences[APP_LOCK_ENABLED] = enabled
            preferences[APP_LOCK_TYPE] = type
            preferences[APP_PIN] = pin
        }
    }
}

data class UserSession(
    val token: String?,
    val refreshToken: String?,
    val userId: String?,
    val email: String?,
    val username: String?,
    val avatarUrl: String?,
    val isAdmin: Boolean,
    val isModerator: Boolean = false,
    // Beta - badge kosmetik doang, sengaja TIDAK dipakai di canModerate() atau
    // pengecekan permission manapun.
    val isBeta: Boolean = false,
    // Warna nama chat custom (hex "#RRGGBB") - null berarti pakai warna default sesuai role
    val customNameColor: String? = null,
    val isBanned: Boolean,
    val userNumber: Int? = null,
    // Status Premium (dari fitur Gift Premium) & total dukungan yang pernah
    // diterima (dipakai buat badge "Top Support" di profil).
    val premiumUntil: String? = null,
    val supportPoints: Int? = 0
) {
    fun canModerate() = isAdmin || isModerator

    fun isPremiumActive(): Boolean {
        val until = premiumUntil ?: return false
        return try {
            val parser = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.getDefault())
            parser.timeZone = java.util.TimeZone.getTimeZone("UTC")
            parser.parse(until.take(19))?.after(java.util.Date()) ?: false
        } catch (e: Exception) { false }
    }
}
