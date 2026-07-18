package com.example.network

import android.util.Log
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * Status ban per-user, realtime via Firebase Realtime Database. Pola sama
 * kayak PrivateChatManager/NobarManager -- dipakai biar begitu admin nge-ban
 * seseorang, device korban langsung ke-kick SAAT ITU JUGA (gak nunggu logout
 * manual atau token expired), mirip kill-switch Maintenance Mode yang udah ada.
 *
 * Struktur data di Firebase:
 * banned_status/{userId}  -> Boolean (true = lagi di-ban)
 */
object BanStatusManager {
    private const val TAG = "BanStatusManager"
    private val database: FirebaseDatabase by lazy { FirebaseDatabase.getInstance() }

    /** Dengerin status ban user tertentu secara realtime. */
    fun listenBanStatus(userId: String): Flow<Boolean> = callbackFlow {
        val ref = database.getReference("banned_status/$userId")
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                trySend(snapshot.getValue(Boolean::class.java) ?: false)
            }
            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "listenBanStatus cancelled: ${error.message}")
            }
        }
        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }

    /** Dipanggil admin pas toggle ban/unban -- push status baru ke Firebase
     * biar device korban (kalau lagi online) langsung kena efeknya. */
    suspend fun setBanStatus(userId: String, banned: Boolean) {
        try {
            database.getReference("banned_status/$userId").setValue(banned).await()
        } catch (e: Exception) {
            // Kalau Firebase gagal ditulis (mis. offline), gak masalah -- status
            // ban asli tetap valid di Postgres, cuma efek instannya yang gak jalan
            // sampai user itu login ulang (yang bakal ke-cek juga via check_device_guard/profil).
            Log.e(TAG, "setBanStatus failed for $userId", e)
        }
    }
}
