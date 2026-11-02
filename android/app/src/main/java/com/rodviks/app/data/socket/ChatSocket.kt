package com.rodviks.app.data.socket

import android.util.Log
import com.google.gson.Gson
import com.rodviks.app.data.api.Message
import com.rodviks.app.data.session.SessionManager
import io.socket.client.Ack
import io.socket.client.IO
import io.socket.client.Socket
import io.socket.emitter.Emitter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.net.URI

// Events sent to the socket flow consumers
sealed interface SocketEvent {
    data class NewMessage(val message: Message) : SocketEvent
    data class Typing(val chatId: String, val userId: String, val typing: Boolean) : SocketEvent
    data class Presence(val userId: String, val online: Boolean) : SocketEvent
    data class MessagesRead(val chatId: String, val userId: String, val messageIds: List<String>) : SocketEvent
    data object Connected : SocketEvent
    data object Disconnected : SocketEvent
    data class Error(val message: String) : SocketEvent
}

class ChatSocket(private val sessionManager: SessionManager) {

    companion object {
        private const val TAG = "ChatSocket"
        private val gson = Gson()
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var socket: Socket? = null

    private val _events = MutableSharedFlow<SocketEvent>(extraBufferCapacity = 64)
    val events: SharedFlow<SocketEvent> = _events

    private val _connectionState = MutableStateFlow(false)
    val connectionState: StateFlow<Boolean> = _connectionState

    val isConnected: Boolean get() = _connectionState.value

    @Volatile
    private var currentBaseUrl: String = SessionManager.DEFAULT_BASE_URL

    private val connectListener = Emitter.Listener { _ ->
        _connectionState.value = true
        _events.tryEmit(SocketEvent.Connected)
    }

    private val disconnectListener = Emitter.Listener { args ->
        _connectionState.value = false
        _events.tryEmit(SocketEvent.Disconnected)
        if (args.isNotEmpty() && args[0] is Exception) {
            Log.w(TAG, "socket disconnected: ${args[0]}")
        }
    }

    private val connectErrorListener = Emitter.Listener { args ->
        _connectionState.value = false
        val msg = args.firstOrNull()?.toString() ?: "connection error"
        Log.w(TAG, "socket connect error: $msg")
        _events.tryEmit(SocketEvent.Error(msg))
    }

    private val newMessageListener = Emitter.Listener { args ->
        try {
            val obj = args.firstOrNull() as? JSONObject ?: return@Listener
            val message = gson.fromJson(obj.toString(), Message::class.java)
            if (message.chatId.isNotBlank()) {
                _events.tryEmit(SocketEvent.NewMessage(message))
            }
        } catch (e: Exception) {
            Log.e(TAG, "parse message:new failed", e)
        }
    }

    private val typingListener = Emitter.Listener { args ->
        try {
            val obj = args.firstOrNull() as? JSONObject ?: return@Listener
            _events.tryEmit(
                SocketEvent.Typing(
                    chatId = obj.optString("chatId"),
                    userId = obj.optString("userId"),
                    typing = obj.optBoolean("typing")
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "parse typing failed", e)
        }
    }

    private val presenceListener = Emitter.Listener { args ->
        try {
            val obj = args.firstOrNull() as? JSONObject ?: return@Listener
            _events.tryEmit(
                SocketEvent.Presence(
                    userId = obj.optString("userId"),
                    online = obj.optBoolean("online")
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "parse presence failed", e)
        }
    }

    private val readListener = Emitter.Listener { args ->
        try {
            val obj = args.firstOrNull() as? JSONObject ?: return@Listener
            val ids = mutableListOf<String>()
            val arr = obj.optJSONArray("messageIds")
            if (arr != null) {
                for (i in 0 until arr.length()) ids.add(arr.optString(i))
            }
            _events.tryEmit(
                SocketEvent.MessagesRead(
                    chatId = obj.optString("chatId"),
                    userId = obj.optString("userId"),
                    messageIds = ids
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "parse messages:read failed", e)
        }
    }

    fun connect(baseUrlOverride: String? = null) {
        if (_connectionState.value && baseUrlOverride == null) return
        disconnect()

        // DataStore reads and socket setup go through the IO scope,
        // so callers on the main thread never block.
        scope.launch {
            val token = sessionManager.currentToken() ?: return@launch
            val base = baseUrlOverride ?: runCatching {
                sessionManager.currentBaseUrl()
            }.getOrNull() ?: SessionManager.DEFAULT_BASE_URL
            currentBaseUrl = base

            try {
                val uri = buildUri(base)
                val options = IO.Options()
                options.auth = mapOf("token" to token)
                options.reconnection = true
                options.reconnectionAttempts = Int.MAX_VALUE
                options.reconnectionDelay = 1000L
                options.reconnectionDelayMax = 5000L
                options.transports = arrayOf("websocket", "polling")

                val sock = IO.socket(uri, options)
                socket = sock
                sock.on(Socket.EVENT_CONNECT, connectListener)
                sock.on(Socket.EVENT_DISCONNECT, disconnectListener)
                sock.on(Socket.EVENT_CONNECT_ERROR, connectErrorListener)
                sock.on("message:new", newMessageListener)
                sock.on("typing", typingListener)
                sock.on("presence", presenceListener)
                sock.on("messages:read", readListener)
                sock.connect()
            } catch (e: Exception) {
                Log.e(TAG, "socket connect failed", e)
                _events.tryEmit(SocketEvent.Error(e.message ?: "connect failed"))
            }
        }
    }

    private fun buildUri(base: String): URI {
        var url = base.trim()
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            url = "http://$url"
        }
        url = url.trimEnd('/')
        return URI(url)
    }

    fun disconnect() {
        socket?.let { s ->
            runCatching {
                s.off(Socket.EVENT_CONNECT, connectListener)
                s.off(Socket.EVENT_DISCONNECT, disconnectListener)
                s.off(Socket.EVENT_CONNECT_ERROR, connectErrorListener)
                s.off("message:new", newMessageListener)
                s.off("typing", typingListener)
                s.off("presence", presenceListener)
                s.off("messages:read", readListener)
                s.disconnect()
                s.close()
            }
        }
        socket = null
        _connectionState.value = false
    }

    fun sendMessage(
        chatId: String,
        text: String?,
        imageUrl: String?,
        clientId: String?,
        ack: (success: Boolean, message: Message?, error: String?) -> Unit
    ) {
        val s = socket ?: run {
            ack(false, null, "нет соединения")
            return
        }
        val payload = JSONObject()
        payload.put("chatId", chatId)
        if (text != null) payload.put("text", text)
        if (imageUrl != null) payload.put("imageUrl", imageUrl)
        if (clientId != null) payload.put("clientId", clientId)

        try {
            s.emit("message:send", arrayOf<Any>(payload), Ack { args ->
                scope.launch {
                    try {
                        val first = args.firstOrNull()
                        if (first is JSONObject) {
                            if (first.optBoolean("ok")) {
                                val msg = gson.fromJson(
                                    first.optJSONObject("message")?.toString() ?: "{}",
                                    Message::class.java
                                )
                                ack(true, msg, null)
                            } else {
                                ack(false, null, first.optString("error").ifBlank { "ошибка отправки" })
                            }
                        } else {
                            ack(false, null, "неизвестный ответ сервера")
                        }
                    } catch (e: Exception) {
                        ack(false, null, e.message)
                    }
                }
            })
        } catch (e: Exception) {
            ack(false, null, e.message ?: "ошибка отправки")
        }
    }

    fun setTyping(chatId: String, typing: Boolean) {
        val s = socket ?: return
        val payload = JSONObject()
        payload.put("chatId", chatId)
        payload.put("typing", typing)
        runCatching { s.emit("typing", payload) }
    }

    fun markRead(chatId: String) {
        val s = socket ?: return
        runCatching { s.emit("messages:read", chatId) }
    }

    fun joinChat(chatId: String) {
        val s = socket ?: return
        runCatching { s.emit("chat:join", chatId) }
    }

    /** Force reconnect after server address change. */
    fun reconnect(newBaseUrl: String) {
        connect(baseUrlOverride = newBaseUrl)
    }
}
