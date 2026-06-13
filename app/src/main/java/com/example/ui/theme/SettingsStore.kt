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
        val DATA_SOURCE = stringPreferencesKey("data_source") // "AnimaSu", "Samehadaku"

        // Auth/User details
        val AUTH_TOKEN = stringPreferencesKey("auth_token")
        val REFRESH_TOKEN = stringPreferencesKey("refresh_token")
        val USER_ID = stringPreferencesKey("user_id")
        val USER_EMAIL = stringPreferencesKey("user_email")
        val USERNAME = stringPreferencesKey("username")
        val AVATAR_URL = stringPreferencesKey("avatar_url")
        val IS_ADMIN = booleanPreferencesKey("is_admin")
        val IS_BANNED = booleanPreferencesKey("is_banned")
        val LAST_CHAT_READ = stringPreferencesKey("last_chat_read")
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
        preferences[DATA_SOURCE] ?: "AnimaSu"
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
            isBanned = preferences[IS_BANNED] ?: false
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

    suspend fun saveSession(session: UserSession) {
        context.dataStore.edit { preferences ->
            preferences[AUTH_TOKEN] = session.token ?: ""
            preferences[REFRESH_TOKEN] = session.refreshToken ?: ""
            preferences[USER_ID] = session.userId ?: ""
            preferences[USER_EMAIL] = session.email ?: ""
            preferences[USERNAME] = session.username ?: ""
            preferences[AVATAR_URL] = session.avatarUrl ?: ""
            preferences[IS_ADMIN] = session.isAdmin
            preferences[IS_BANNED] = session.isBanned
        }
    }

    suspend fun clearSession() {
        context.dataStore.edit { preferences ->
            preferences.remove(AUTH_TOKEN)
            preferences.remove(USER_ID)
            preferences.remove(USER_EMAIL)
            preferences.remove(USERNAME)
            preferences.remove(AVATAR_URL)
            preferences.remove(IS_ADMIN)
            preferences.remove(IS_BANNED)
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
    val isBanned: Boolean
)
