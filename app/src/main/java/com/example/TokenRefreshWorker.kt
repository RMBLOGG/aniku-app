package com.example

import android.content.Context
import androidx.work.*
import com.example.network.NetworkClient
import com.example.network.RefreshTokenRequest
import com.example.network.SUPABASE_ANON_KEY
import com.example.ui.theme.SettingsStore
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

class TokenRefreshWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        const val WORK_NAME = "token_refresh_work"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<TokenRefreshWorker>(
                50, TimeUnit.MINUTES
            )
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }

    override suspend fun doWork(): Result {
        val store = SettingsStore(applicationContext)
        val session = store.sessionFlow.first()
        val refreshToken = session.refreshToken

        if (refreshToken.isNullOrEmpty()) return Result.success()

        return try {
            val res = NetworkClient.supabaseAuthApi.refreshToken(
                request = RefreshTokenRequest(refresh_token = refreshToken),
                apiKey = SUPABASE_ANON_KEY
            )
            val newToken = res.access_token ?: return Result.failure()
            store.saveSession(
                session.copy(
                    token = newToken,
                    refreshToken = res.refresh_token ?: refreshToken
                )
            )
            Result.success()
        } catch (e: Exception) {
            // Refresh token invalid/expired → paksa logout, user perlu login ulang
            store.clearSession()
            Result.failure()
        }
    }
}
