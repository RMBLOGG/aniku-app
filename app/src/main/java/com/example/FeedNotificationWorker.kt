package com.example

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.*
import com.example.network.NetworkClient
import com.example.network.SUPABASE_ANON_KEY
import java.util.concurrent.TimeUnit

class FeedNotificationWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        const val CHANNEL_ID = "aniku_feed_channel"
        const val CHANNEL_NAME = "Feed Aniku"
        const val PREFS_NAME = "aniku_feed_prefs"
        const val KEY_LAST_POST_ID = "last_post_id"
        const val WORK_NAME = "feed_notification_work"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<FeedNotificationWorker>(
                15, TimeUnit.MINUTES
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
        return try {
            val posts = NetworkClient.supabaseDbApi.getPosts(
                authHeader = "Bearer $SUPABASE_ANON_KEY",
                apiKey = SUPABASE_ANON_KEY
            )

            if (posts.isEmpty()) return Result.success()

            val latestPost = posts.first()
            val prefs = applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val lastSeenId = prefs.getString(KEY_LAST_POST_ID, null)

            if (lastSeenId == null) {
                // Pertama kali, simpan ID tanpa notif
                prefs.edit().putString(KEY_LAST_POST_ID, latestPost.id).apply()
                return Result.success()
            }

            if (latestPost.id != lastSeenId) {
                // Ada post baru — hitung berapa
                val newPostsCount = posts.indexOfFirst { it.id == lastSeenId }.let {
                    if (it == -1) posts.size else it
                }

                sendNotification(latestPost.username ?: "Seseorang", newPostsCount)
                prefs.edit().putString(KEY_LAST_POST_ID, latestPost.id).apply()
            }

            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    private fun sendNotification(username: String, count: Int) {
        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE)
            as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notifikasi post baru di Feed Aniku"
            }
            manager.createNotificationChannel(channel)
        }

        // Deep link intent ke Feed
        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            data = android.net.Uri.parse("aniku://feed")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = if (count == 1) "Post baru di Feed" else "$count post baru di Feed"
        val body = if (count == 1) "$username baru saja posting sesuatu." else "Ada $count postingan baru, termasuk dari $username."

        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        manager.notify(1001, notification)
    }
}
