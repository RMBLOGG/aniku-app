package com.example.network

import android.util.Log
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.util.UUID
import kotlin.random.Random

/**
 * Nobar = Nonton Bareng (watch party) realtime sync via Firebase Realtime Database.
 *
 * Struktur data di Firebase:
 * rooms/{roomCode}/
 *   - hostUid: String          (uid/userId pembuat room, hanya host yang boleh kontrol)
 *   - animeSlug: String
 *   - animeTitle: String
 *   - episodeSlug: String
 *   - episodeTitle: String
 *   - isPlaying: Boolean
 *   - positionMs: Long         (posisi video terakhir, dalam milidetik)
 *   - updatedAt: Long          (server timestamp saat positionMs di-update, buat estimasi drift)
 *   - createdAt: Long
 *   - members/{userId}: { username: String, joinedAt: Long }
 */
object NobarManager {

    private const val TAG = "NobarManager"
    private const val ROOMS_PATH = "rooms"
    private const val ACTIVE_ROOMS_PATH = "active_rooms"

    // Toleransi sync: kalau selisih posisi member vs host < ini, jangan seek
    // (biar gak terlalu sering micro-seek yang bikin video nyendat-nyendat)
    const val SYNC_TOLERANCE_MS = 2_000L

    // Room otomatis "basi" kalau gak ada update > ini, buat cleanup gampang
    const val ROOM_STALE_MS = 6 * 60 * 60 * 1000L // 6 jam

    private val database: FirebaseDatabase by lazy { FirebaseDatabase.getInstance() }

    data class RoomState(
        val roomCode: String = "",
        val hostUid: String = "",
        val hostUsername: String = "",
        val animeSlug: String = "",
        val animeTitle: String = "",
        val animePoster: String = "",
        val episodeSlug: String = "",
        val episodeTitle: String = "",
        val isPlaying: Boolean = false,
        val positionMs: Long = 0L,
        val updatedAt: Long = 0L,
        val createdAt: Long = 0L,
        val memberCount: Int = 0
    )

    /**
     * Ringkasan room untuk ditampilkan di halaman "Nobar" (daftar room aktif publik).
     * Disimpan terpisah dari rooms/{code} di node active_rooms/{code} supaya halaman
     * listing bisa observe satu node ringan tanpa fetch detail tiap room satu-satu.
     */
    data class ActiveRoomSummary(
        val roomCode: String = "",
        val hostUsername: String = "",
        val animeSlug: String = "",
        val animeTitle: String = "",
        val animePoster: String = "",
        val episodeSlug: String = "",
        val episodeTitle: String = "",
        val dataSource: String = "",
        val memberCount: Int = 0,
        val updatedAt: Long = 0L
    )

    /**
     * Generate kode room unik 6 karakter alfanumerik kapital, misal "XKQP91".
     * Tidak menjamin keunikan 100% secara distributed, tapi cukup untuk skala app ini.
     * Kalau mau lebih aman, cek dulu apakah kode sudah ada sebelum dipakai (lihat createRoom).
     */
    private fun generateRoomCode(): String {
        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789" // tanpa karakter ambigu (0/O, 1/I)
        return (1..6).map { chars[Random.nextInt(chars.length)] }.joinToString("")
    }

    /**
     * Buat room baru. Return roomCode kalau berhasil, null kalau gagal setelah beberapa retry.
     */
    suspend fun createRoom(
        hostUid: String,
        hostUsername: String,
        animeSlug: String,
        animeTitle: String,
        animePoster: String,
        episodeSlug: String,
        episodeTitle: String,
        dataSource: String
    ): String? {
        repeat(5) { attempt ->
            val code = generateRoomCode()
            val roomRef = database.getReference("$ROOMS_PATH/$code")
            try {
                val existing = roomRef.get().await()
                if (existing.exists()) {
                    Log.w(TAG, "Room code collision: $code, retry attempt $attempt")
                    return@repeat // kode sudah dipakai, coba lagi
                }
                val now = System.currentTimeMillis()
                val data = mapOf(
                    "hostUid" to hostUid,
                    "hostUsername" to hostUsername,
                    "animeSlug" to animeSlug,
                    "animeTitle" to animeTitle,
                    "animePoster" to animePoster,
                    "episodeSlug" to episodeSlug,
                    "episodeTitle" to episodeTitle,
                    "dataSource" to dataSource,
                    "isPlaying" to false,
                    "positionMs" to 0L,
                    "updatedAt" to now,
                    "createdAt" to now,
                    "members" to mapOf(
                        hostUid to mapOf("username" to hostUsername, "joinedAt" to now)
                    )
                )
                roomRef.setValue(data).await()
                // Index ringan untuk halaman "Nobar" (daftar room aktif publik)
                val summary = mapOf(
                    "hostUsername" to hostUsername,
                    "animeSlug" to animeSlug,
                    "animeTitle" to animeTitle,
                    "animePoster" to animePoster,
                    "episodeSlug" to episodeSlug,
                    "episodeTitle" to episodeTitle,
                    "dataSource" to dataSource,
                    "memberCount" to 1,
                    "updatedAt" to now
                )
                database.getReference("$ACTIVE_ROOMS_PATH/$code").setValue(summary).await()
                Log.d(TAG, "Room created: $code")
                return code
            } catch (e: Exception) {
                Log.e(TAG, "Failed to create room (attempt $attempt): ${e.message}", e)
            }
        }
        return null
    }

    /**
     * Join room dengan kode. Return RoomState kalau room ditemukan, null kalau tidak ada.
     */
    suspend fun joinRoom(roomCode: String, userId: String, username: String): RoomState? {
        val normalizedCode = roomCode.trim().uppercase()
        val roomRef = database.getReference("$ROOMS_PATH/$normalizedCode")
        return try {
            val snapshot = roomRef.get().await()
            if (!snapshot.exists()) {
                Log.w(TAG, "Room not found: $normalizedCode")
                return null
            }
            // Daftarkan diri sebagai member
            roomRef.child("members").child(userId).setValue(
                mapOf("username" to username, "joinedAt" to System.currentTimeMillis())
            ).await()
            val state = parseRoomSnapshot(normalizedCode, roomRef.get().await())
            syncActiveRoomMemberCount(normalizedCode, state.memberCount)
            state
        } catch (e: Exception) {
            Log.e(TAG, "Failed to join room $normalizedCode: ${e.message}", e)
            null
        }
    }

    /**
     * Keluar dari room — hapus diri dari daftar members.
     * Tidak menghapus room itu sendiri (biar member lain yang masih nonton gak terganggu).
     */
    suspend fun leaveRoom(roomCode: String, userId: String) {
        try {
            database.getReference("$ROOMS_PATH/$roomCode/members/$userId").removeValue().await()
            val freshSnapshot = database.getReference("$ROOMS_PATH/$roomCode").get().await()
            if (freshSnapshot.exists()) {
                val count = freshSnapshot.child("members").childrenCount.toInt()
                syncActiveRoomMemberCount(roomCode, count)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to leave room $roomCode: ${e.message}", e)
        }
    }

    /**
     * Host menghapus room secara permanen (misal saat host keluar / selesai nobar).
     */
    suspend fun closeRoom(roomCode: String) {
        try {
            database.getReference("$ROOMS_PATH/$roomCode").removeValue().await()
            database.getReference("$ACTIVE_ROOMS_PATH/$roomCode").removeValue().await()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to close room $roomCode: ${e.message}", e)
        }
    }

    private suspend fun syncActiveRoomMemberCount(roomCode: String, count: Int) {
        try {
            database.getReference("$ACTIVE_ROOMS_PATH/$roomCode/memberCount").setValue(count).await()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to sync active room member count for $roomCode: ${e.message}", e)
        }
    }

    /**
     * Listen daftar semua room aktif publik secara realtime, untuk halaman "Nobar".
     * Diurutkan dari yang paling baru di-update (paling ramai/baru aktif duluan).
     */
    fun observeActiveRooms(): Flow<List<ActiveRoomSummary>> = callbackFlow {
        val ref = database.getReference(ACTIVE_ROOMS_PATH)
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val rooms = snapshot.children.mapNotNull { child ->
                    val code = child.key ?: return@mapNotNull null
                    ActiveRoomSummary(
                        roomCode = code,
                        hostUsername = child.child("hostUsername").getValue(String::class.java) ?: "",
                        animeSlug = child.child("animeSlug").getValue(String::class.java) ?: "",
                        animeTitle = child.child("animeTitle").getValue(String::class.java) ?: "",
                        animePoster = child.child("animePoster").getValue(String::class.java) ?: "",
                        episodeSlug = child.child("episodeSlug").getValue(String::class.java) ?: "",
                        episodeTitle = child.child("episodeTitle").getValue(String::class.java) ?: "",
                        dataSource = child.child("dataSource").getValue(String::class.java) ?: "",
                        memberCount = child.child("memberCount").getValue(Int::class.java) ?: 0,
                        updatedAt = child.child("updatedAt").getValue(Long::class.java) ?: 0L
                    )
                }.sortedByDescending { it.updatedAt }
                trySend(rooms)
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "observeActiveRooms cancelled: ${error.message}")
                trySend(emptyList())
            }
        }
        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }

    /**
     * Listen perubahan state room secara realtime. Emit RoomState setiap ada update,
     * atau null kalau room dihapus/tidak ada.
     */
    fun observeRoom(roomCode: String): Flow<RoomState?> = callbackFlow {
        val roomRef = database.getReference("$ROOMS_PATH/$roomCode")
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (!snapshot.exists()) {
                    trySend(null)
                    return
                }
                trySend(parseRoomSnapshot(roomCode, snapshot))
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "observeRoom cancelled: ${error.message}")
                trySend(null)
            }
        }
        roomRef.addValueEventListener(listener)
        awaitClose { roomRef.removeEventListener(listener) }
    }

    private fun parseRoomSnapshot(roomCode: String, snapshot: DataSnapshot): RoomState {
        val membersSnapshot = snapshot.child("members")
        return RoomState(
            roomCode = roomCode,
            hostUid = snapshot.child("hostUid").getValue(String::class.java) ?: "",
            hostUsername = snapshot.child("hostUsername").getValue(String::class.java) ?: "",
            animeSlug = snapshot.child("animeSlug").getValue(String::class.java) ?: "",
            animeTitle = snapshot.child("animeTitle").getValue(String::class.java) ?: "",
            animePoster = snapshot.child("animePoster").getValue(String::class.java) ?: "",
            episodeSlug = snapshot.child("episodeSlug").getValue(String::class.java) ?: "",
            episodeTitle = snapshot.child("episodeTitle").getValue(String::class.java) ?: "",
            isPlaying = snapshot.child("isPlaying").getValue(Boolean::class.java) ?: false,
            positionMs = snapshot.child("positionMs").getValue(Long::class.java) ?: 0L,
            updatedAt = snapshot.child("updatedAt").getValue(Long::class.java) ?: 0L,
            createdAt = snapshot.child("createdAt").getValue(Long::class.java) ?: 0L,
            memberCount = membersSnapshot.childrenCount.toInt()
        )
    }

    /**
     * Host mengirim event play/pause/seek. HARUS divalidasi di sisi caller bahwa
     * userId yang memanggil ini adalah hostUid room tersebut — manager ini sendiri
     * tidak melakukan pengecekan ulang supaya tetap ringan (validasi sudah dilakukan
     * oleh ViewModel sebelum memanggil fungsi ini, lihat AnikuViewModel.nobarUpdatePlayback).
     */
    suspend fun updatePlaybackState(roomCode: String, isPlaying: Boolean, positionMs: Long) {
        try {
            val roomRef = database.getReference("$ROOMS_PATH/$roomCode")
            roomRef.updateChildren(
                mapOf(
                    "isPlaying" to isPlaying,
                    "positionMs" to positionMs,
                    "updatedAt" to System.currentTimeMillis()
                )
            ).await()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update playback state for $roomCode: ${e.message}", e)
        }
    }

    /**
     * Estimasi posisi video host saat ini, dengan asumsi video terus berjalan
     * sejak updatedAt kalau isPlaying = true. Dipakai member untuk seek ke posisi
     * yang lebih akurat (mengkompensasi latency network sejak event terakhir dikirim).
     */
    fun estimateCurrentPositionMs(state: RoomState): Long {
        if (!state.isPlaying) return state.positionMs
        val elapsed = System.currentTimeMillis() - state.updatedAt
        return state.positionMs + elapsed.coerceAtLeast(0L)
    }
}
