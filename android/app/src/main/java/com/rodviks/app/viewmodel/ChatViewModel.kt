package com.rodviks.app.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rodviks.app.data.local.MessageEntity
import com.rodviks.app.di.ServiceLocator
import com.rodviks.app.ui.components.parseIso
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.util.UUID

data class ChatUiState(
    val chatId: String? = null,
    val messages: List<MessageEntity> = emptyList(),
    val meId: String? = null,
    val peerName: String = "",
    val peerAvatarUrl: String? = null,
    val peerOnline: Boolean = false,
    val peerLastSeen: String? = null,
    val peerTyping: Boolean = false,
    val input: String = "",
    val sending: Boolean = false,
    val uploading: Boolean = false,
    val loadingHistory: Boolean = false,
    val canLoadMore: Boolean = true,
    val error: String? = null
)

@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModel : ViewModel() {

    private val chatIdFlow = MutableStateFlow<String?>(null)
    private val meIdFlow = ServiceLocator.sessionManager.sessionFlow.map { it.userId }

    private val messagesFlow = chatIdFlow.flatMapLatest { id ->
        if (id == null) flowOf(emptyList())
        else ServiceLocator.chatRepository.observeMessages(id)
    }

    private val peerFlow = chatIdFlow.flatMapLatest { id ->
        if (id == null) flowOf(null)
        else ServiceLocator.chatRepository.observeChats().map { chats ->
            chats.firstOrNull { it.id == id }?.peerId
        }.flatMapLatest { peerId ->
            if (peerId == null) flowOf(null)
            else ServiceLocator.chatRepository.observeUser(peerId)
        }
    }

    private val peerTypingFlow = MutableStateFlow(false)
    private val inputFlow = MutableStateFlow("")
    private val sendingFlow = MutableStateFlow(false)
    private val uploadingFlow = MutableStateFlow(false)
    private val loadingHistoryFlow = MutableStateFlow(false)
    private val canLoadMoreFlow = MutableStateFlow(true)
    private val errorFlow = MutableStateFlow<String?>(null)

    private var typingJob: Job? = null
    private var lastTypingSent = false

    val state: StateFlow<ChatUiState> = combine(
        combine(chatIdFlow, messagesFlow, meIdFlow) { id, msgs, me -> Triple(id, msgs, me) },
        peerFlow,
        combine(peerTypingFlow, inputFlow, sendingFlow, uploadingFlow) { t, i, s, u ->
            TypingBundle(t, i, s, u)
        },
        combine(loadingHistoryFlow, canLoadMoreFlow, errorFlow) { l, m, e -> HistoryBundle(l, m, e) }
    ) { (id, msgs, me), peer, bundle, history ->
        ChatUiState(
            chatId = id,
            messages = msgs,
            meId = me,
            peerName = peer?.displayName ?: peer?.username ?: "",
            peerAvatarUrl = peer?.avatarUrl,
            peerOnline = peer?.online ?: false,
            peerLastSeen = peer?.lastSeenAt,
            peerTyping = bundle.peerTyping,
            input = bundle.input,
            sending = bundle.sending,
            uploading = bundle.uploading,
            loadingHistory = history.loadingHistory,
            canLoadMore = history.canLoadMore,
            error = history.error
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ChatUiState())

    private data class TypingBundle(
        val peerTyping: Boolean, val input: String,
        val sending: Boolean, val uploading: Boolean
    )

    private data class HistoryBundle(
        val loadingHistory: Boolean, val canLoadMore: Boolean, val error: String?
    )

    fun openChat(chatId: String) {
        if (chatIdFlow.value == chatId) return
        chatIdFlow.value = chatId
        canLoadMoreFlow.value = true
        ServiceLocator.socket.joinChat(chatId)
        viewModelScope.launch {
            val meId = ServiceLocator.sessionManager.currentUserId() ?: return@launch
            // load history from network, mark as read
            ServiceLocator.chatRepository.loadMessages(chatId, null)
            ServiceLocator.chatRepository.markLocalRead(chatId, meId)
            ServiceLocator.socket.markRead(chatId)
        }
    }

    fun onInputChange(v: String) {
        inputFlow.value = v
        val chatId = chatIdFlow.value ?: return
        if (v.isNotBlank() && !lastTypingSent) {
            lastTypingSent = true
            ServiceLocator.socket.setTyping(chatId, true)
        }
        typingJob?.cancel()
        typingJob = viewModelScope.launch {
            delay(2500)
            if (lastTypingSent) {
                lastTypingSent = false
                ServiceLocator.socket.setTyping(chatId, false)
            }
        }
    }

    fun sendCurrent() {
        val chatId = chatIdFlow.value ?: return
        val text = inputFlow.value.trim()
        if (text.isEmpty() || sendingFlow.value) return
        inputFlow.value = ""
        typingJob?.cancel()
        if (lastTypingSent) {
            lastTypingSent = false
            ServiceLocator.socket.setTyping(chatId, false)
        }
        send(chatId, text, null)
    }

    fun send(chatId: String, text: String?, imageUrl: String?) {
        if (sendingFlow.value) return
        sendingFlow.value = true
        errorFlow.value = null
        val clientId = UUID.randomUUID().toString()
        val createdAt = java.time.Instant.now().toString()

        viewModelScope.launch {
            val meId = meIdFlow.first() ?: ""
            // optimistic local insert
            ServiceLocator.chatRepository.insertLocalPending(
                MessageEntity(
                    id = "local_$clientId",
                    chatId = chatId,
                    senderId = meId,
                    text = text,
                    imageUrl = imageUrl,
                    createdAt = createdAt,
                    readByJson = "[]",
                    pending = true,
                    failed = false,
                    clientId = clientId
                )
            )
            ServiceLocator.socket.sendMessage(chatId, text, imageUrl, clientId) { ok, message, err ->
                viewModelScope.launch {
                    sendingFlow.value = false
                    if (ok && message != null) {
                        ServiceLocator.chatRepository.confirmSent(clientId, message)
                    } else {
                        ServiceLocator.chatRepository.markFailed(clientId)
                        errorFlow.value = err ?: "Ошибка отправки"
                    }
                }
            }
        }
    }

    fun retryFailed() {
        val chatId = chatIdFlow.value ?: return
        viewModelScope.launch {
            val failed = ServiceLocator.chatRepository.retryFailed(chatId)
            for (m in failed) {
                val clientId = m.clientId ?: continue
                ServiceLocator.chatRepository.insertLocalPending(m.copy(pending = true, failed = false))
                ServiceLocator.socket.sendMessage(chatId, m.text, m.imageUrl, clientId) { ok, message, err ->
                    viewModelScope.launch {
                        if (ok && message != null) {
                            ServiceLocator.chatRepository.confirmSent(clientId, message)
                        } else {
                            ServiceLocator.chatRepository.markFailed(clientId)
                        }
                    }
                }
            }
        }
    }

    fun uploadAndSend(context: android.content.Context, uri: Uri) {
        val chatId = chatIdFlow.value ?: return
        if (uploadingFlow.value) return
        uploadingFlow.value = true
        errorFlow.value = null
        viewModelScope.launch {
            try {
                val file = copyToFile(context, uri)
                val mimeType = context.contentResolver.getType(uri) ?: "image/jpeg"
                val part = MultipartBody.Part.createFormData(
                    "image",
                    file.name,
                    file.asRequestBody(mimeType.toMediaType())
                )
                val resp = ServiceLocator.api.uploadImage(part)
                file.delete()
                if (resp.isSuccessful && resp.body() != null) {
                    send(chatId, null, resp.body()!!.url)
                } else {
                    errorFlow.value = "Ошибка загрузки файла (${resp.code()})"
                }
            } catch (e: Exception) {
                errorFlow.value = "Ошибка загрузки файла"
            } finally {
                uploadingFlow.value = false
            }
        }
    }

    private fun copyToFile(context: android.content.Context, uri: Uri): File {
        val temp = File(context.cacheDir, "upload_${System.currentTimeMillis()}.jpg")
        context.contentResolver.openInputStream(uri)?.use { input ->
            temp.outputStream().use { output -> input.copyTo(output) }
        }
        return temp
    }

    fun loadEarlier() {
        val chatId = chatIdFlow.value ?: return
        val messages = state.value.messages.filter { !it.pending && !it.failed }
        if (loadingHistoryFlow.value || messages.isEmpty()) return
        val beforeIso = messages.minByOrNull { parseIso(it.createdAt) ?: 0L }?.createdAt
        if (beforeIso.isNullOrBlank()) return
        loadingHistoryFlow.value = true
        viewModelScope.launch {
            val result = ServiceLocator.chatRepository.loadMessages(chatId, beforeIso)
            loadingHistoryFlow.value = false
            if (result.isSuccess && (result.data?.size ?: 0) < 50) {
                canLoadMoreFlow.value = false
            } else if (!result.isSuccess) {
                errorFlow.value = result.error
            }
        }
    }

    fun dismissError() { errorFlow.value = null }

    fun setPeerTyping(chatId: String, userId: String, typing: Boolean) {
        viewModelScope.launch {
            val meId = ServiceLocator.sessionManager.currentUserId() ?: return@launch
            if (userId != meId && chatId == chatIdFlow.value) {
                peerTypingFlow.value = typing
                if (typing) {
                    typingJob?.cancel()
                    typingJob = viewModelScope.launch {
                        delay(4000)
                        peerTypingFlow.value = false
                    }
                }
            }
        }
    }

    override fun onCleared() {
        typingJob?.cancel()
        super.onCleared()
    }
}
