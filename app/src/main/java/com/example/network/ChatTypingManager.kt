package com.example.network

import android.util.Log
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.TimeUnit

/** Info user lain yang lagi ngetik di Chat Room global. */
data class TypingUser(
    val userId: String,
    val username: String
)

/**
 * Presence "sedang mengetik..." di Chat Room global, via Supabase Realtime
 * (Phoenix channel protocol, format pesan JSON object / vsn=1.0.0).
 *
 * Alur singkat:
 * 1. Buka WebSocket ke /realtime/v1/websocket, phx_join ke topic channel presence.
 * 2. Server balas presence_state (snapshot user yang lagi "online" di channel ini),
 *    lalu tiap ada perubahan kirim presence_diff (joins/leaves).
 * 3. Pas user ngetik -> track presence sendiri dengan payload {typing:true, username}.
 *    Pas berhenti ngetik / kirim pesan / idle 3 detik -> track ulang {typing:false}.
 * 4. typingUsers cuma isi user LAIN yang typing:true (diri sendiri di-exclude).
 *
 * Catatan penting:
 * - Ini implementasi manual protokol Phoenix pakai OkHttp WebSocket biasa (project ini
 *   belum pakai SDK resmi supabase-kt), jadi WAJIB dites di device/emulator asli sebelum
 *   rilis — belum pernah dites terhadap server Supabase beneran dari sandbox pembuatan kode ini.
 * - Heartbeat wajib dikirim tiap ~25 detik ke topic "phoenix" biar koneksi gak di-drop server.
 */
class ChatTypingManager {

    companion object {
        private const val TAG = "ChatTypingManager"
        private const val TOPIC = "realtime:chat_room_typing"
        private const val HEARTBEAT_INTERVAL_MS = 25_000L
        private const val TYPING_IDLE_TIMEOUT_MS = 3_000L
        private const val RECONNECT_DELAY_MS = 4_000L
    }

    private val moshi = Moshi.Builder().build()
    private val mapType = Types.newParameterizedType(Map::class.java, String::class.java, Any::class.java)
    private val mapAdapter = moshi.adapter<Map<String, Any?>>(mapType)

    private val client = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS) // websocket perlu no timeout di sisi read
        .build()

    private var webSocket: WebSocket? = null
    private var scope: CoroutineScope? = null
    private var heartbeatJob: Job? = null
    private var typingIdleJob: Job? = null
    private var refCounter = 0
    private var myUserId: String? = null
    private var myUsername: String? = null
    private var isJoined = false
    private var isTyping = false

    private val _typingUsers = MutableStateFlow<List<TypingUser>>(emptyList())
    val typingUsers: StateFlow<List<TypingUser>> = _typingUsers

    private fun nextRef(): String {
        refCounter += 1
        return refCounter.toString()
    }

    /** Buka koneksi & join channel presence. No-op kalau udah connect (aman dipanggil berkali-kali). */
    fun connect(userId: String, username: String) {
        if (webSocket != null) return
        myUserId = userId
        myUsername = username
        val newScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        scope = newScope

        val url = "wss://uczxaiyibnwgycodtcvm.supabase.co/realtime/v1/websocket" +
            "?apikey=$SUPABASE_ANON_KEY&vsn=1.0.0"
        val request = Request.Builder().url(url).build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(ws: WebSocket, response: Response) {
                sendJoin(ws, userId)
                startHeartbeat(ws, newScope)
            }

            override fun onMessage(ws: WebSocket, text: String) {
                handleMessage(text)
            }

            override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "Typing presence socket error, coba reconnect", t)
                isJoined = false
                _typingUsers.value = emptyList()
                if (webSocket != null) {
                    newScope.launch {
                        delay(RECONNECT_DELAY_MS)
                        webSocket = null
                        connect(userId, username)
                    }
                }
            }

            override fun onClosed(ws: WebSocket, code: Int, reason: String) {
                isJoined = false
            }
        })
    }

    private fun sendJoin(ws: WebSocket, userId: String) {
        val payload = mapOf(
            "config" to mapOf(
                "presence" to mapOf("key" to userId),
                "broadcast" to mapOf("ack" to false, "self" to false),
                "private" to false
            )
        )
        val msg = mapOf(
            "topic" to TOPIC,
            "event" to "phx_join",
            "payload" to payload,
            "ref" to nextRef(),
            "join_ref" to "1"
        )
        ws.send(mapAdapter.toJson(msg))
    }

    private fun startHeartbeat(ws: WebSocket, sc: CoroutineScope) {
        heartbeatJob?.cancel()
        heartbeatJob = sc.launch {
            while (isActive) {
                delay(HEARTBEAT_INTERVAL_MS)
                val msg = mapOf(
                    "topic" to "phoenix",
                    "event" to "heartbeat",
                    "payload" to emptyMap<String, Any?>(),
                    "ref" to nextRef()
                )
                ws.send(mapAdapter.toJson(msg))
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun handleMessage(text: String) {
        val json = try {
            mapAdapter.fromJson(text)
        } catch (e: Exception) {
            Log.e(TAG, "Gagal parse pesan realtime", e)
            return
        } ?: return

        when (json["event"] as? String) {
            "phx_reply" -> {
                val payload = json["payload"] as? Map<String, Any?>
                if (payload?.get("status") == "ok") {
                    isJoined = true
                    // Kalau lagi ngetik pas koneksi baru connect/reconnect, kirim ulang state-nya
                    if (isTyping) trackPresence()
                }
            }
            "presence_state" -> {
                val payload = json["payload"] as? Map<String, Any?> ?: return
                _typingUsers.value = extractTypingUsers(payload)
            }
            "presence_diff" -> {
                val payload = json["payload"] as? Map<String, Any?> ?: return
                val joins = payload["joins"] as? Map<String, Any?> ?: emptyMap()
                val leaves = payload["leaves"] as? Map<String, Any?> ?: emptyMap()
                val current = _typingUsers.value.associateBy { it.userId }.toMutableMap()
                extractTypingUsers(joins).forEach { current[it.userId] = it }
                leaves.keys.forEach { current.remove(it) }
                _typingUsers.value = current.values.toList()
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun extractTypingUsers(stateMap: Map<String, Any?>): List<TypingUser> {
        val result = mutableListOf<TypingUser>()
        for ((key, value) in stateMap) {
            if (key == myUserId) continue // jangan tampilin diri sendiri
            val entry = value as? Map<String, Any?> ?: continue
            val metas = entry["metas"] as? List<Map<String, Any?>> ?: continue
            val latestMeta = metas.lastOrNull() ?: continue
            val typing = latestMeta["typing"] as? Boolean ?: false
            if (typing) {
                val username = latestMeta["username"] as? String ?: "Seseorang"
                result.add(TypingUser(userId = key, username = username))
            }
        }
        return result
    }

    private fun trackPresence() {
        val ws = webSocket ?: return
        if (!isJoined) return
        val username = myUsername ?: return
        val trackPayload = mapOf(
            "type" to "presence",
            "event" to "track",
            "payload" to mapOf("username" to username, "typing" to isTyping)
        )
        val msg = mapOf(
            "topic" to TOPIC,
            "event" to "presence",
            "payload" to trackPayload,
            "ref" to nextRef(),
            "join_ref" to "1"
        )
        ws.send(mapAdapter.toJson(msg))
    }

    /** Dipanggil tiap kali teks input berubah (non-kosong). Auto reset ke false abis idle 3 detik. */
    fun notifyTyping() {
        if (webSocket == null) return
        if (!isTyping) {
            isTyping = true
            trackPresence()
        }
        typingIdleJob?.cancel()
        typingIdleJob = scope?.launch {
            delay(TYPING_IDLE_TIMEOUT_MS)
            stopTyping()
        }
    }

    /** Dipanggil pas kirim pesan / input dikosongin, biar status "mengetik" langsung ilang. */
    fun stopTyping() {
        typingIdleJob?.cancel()
        if (isTyping) {
            isTyping = false
            trackPresence()
        }
    }

    /** Tutup koneksi total, dipanggil pas keluar dari layar Chat Room. */
    fun disconnect() {
        typingIdleJob?.cancel()
        heartbeatJob?.cancel()
        webSocket?.close(1000, "leaving")
        webSocket = null
        scope?.cancel()
        scope = null
        isJoined = false
        isTyping = false
        _typingUsers.value = emptyList()
    }
}
