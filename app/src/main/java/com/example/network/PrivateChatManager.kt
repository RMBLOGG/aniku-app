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
 * Private chat (DM) antar teman — realtime via Firebase Realtime Database.
 * Pola sama kayak NobarManager, cuma buat 1-on-1 chat.
 *
 * Struktur data di Firebase:
 * private_chats/{chatId}/messages/{messageId}
 *   - senderId: String
 *   - text: String
 *   - timestamp: Long
 *
 * user_chats/{userId}/{chatId}   (buat nampilin daftar chat per-user, urut terbaru)
 *   - chatId: String
 *   - otherUserId: String
 *   - lastMessage: String
 *   - lastMessageAt: Long
 *   - lastSenderId: String
 */
data class PrivateMessage(
    val id: String = "",
    val senderId: String = "",
    val text: String = "",
    val timestamp: Long = 0L
)

data class ChatPreview(
    val chatId: String = "",
    val otherUserId: String = "",
    val lastMessage: String = "",
    val lastMessageAt: Long = 0L,
    val lastSenderId: String = ""
)

object PrivateChatManager {
    private const val TAG = "PrivateChatManager"
    private val database: FirebaseDatabase by lazy { FirebaseDatabase.getInstance() }

    /** ID chat deterministik dari 2 user id, urutan gak ngaruh. */
    fun chatIdFor(userA: String, userB: String): String {
        return if (userA < userB) "${userA}_$userB" else "${userB}_$userA"
    }

    fun listenMessages(chatId: String): Flow<List<PrivateMessage>> = callbackFlow {
        val ref = database.getReference("private_chats/$chatId/messages")
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val messages = snapshot.children.mapNotNull { child ->
                    try {
                        PrivateMessage(
                            id = child.key ?: "",
                            senderId = child.child("senderId").getValue(String::class.java) ?: "",
                            text = child.child("text").getValue(String::class.java) ?: "",
                            timestamp = child.child("timestamp").getValue(Long::class.java) ?: 0L
                        )
                    } catch (e: Exception) {
                        null
                    }
                }.sortedBy { it.timestamp }
                trySend(messages)
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "listenMessages cancelled: ${error.message}")
            }
        }
        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }

    fun listenUserChats(userId: String): Flow<List<ChatPreview>> = callbackFlow {
        val ref = database.getReference("user_chats/$userId")
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val chats = snapshot.children.mapNotNull { child ->
                    try {
                        ChatPreview(
                            chatId = child.child("chatId").getValue(String::class.java) ?: child.key ?: "",
                            otherUserId = child.child("otherUserId").getValue(String::class.java) ?: "",
                            lastMessage = child.child("lastMessage").getValue(String::class.java) ?: "",
                            lastMessageAt = child.child("lastMessageAt").getValue(Long::class.java) ?: 0L,
                            lastSenderId = child.child("lastSenderId").getValue(String::class.java) ?: ""
                        )
                    } catch (e: Exception) {
                        null
                    }
                }.sortedByDescending { it.lastMessageAt }
                trySend(chats)
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "listenUserChats cancelled: ${error.message}")
            }
        }
        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }

    suspend fun sendMessage(chatId: String, senderId: String, receiverId: String, text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        val timestamp = System.currentTimeMillis()

        val msgRef = database.getReference("private_chats/$chatId/messages").push()
        msgRef.setValue(
            mapOf(
                "senderId" to senderId,
                "text" to trimmed,
                "timestamp" to timestamp
            )
        ).await()

        database.getReference("user_chats/$senderId/$chatId").setValue(
            mapOf(
                "chatId" to chatId,
                "otherUserId" to receiverId,
                "lastMessage" to trimmed,
                "lastMessageAt" to timestamp,
                "lastSenderId" to senderId
            )
        ).await()

        database.getReference("user_chats/$receiverId/$chatId").setValue(
            mapOf(
                "chatId" to chatId,
                "otherUserId" to senderId,
                "lastMessage" to trimmed,
                "lastMessageAt" to timestamp,
                "lastSenderId" to senderId
            )
        ).await()
    }
}
