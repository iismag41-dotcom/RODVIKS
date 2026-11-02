package com.rodviks.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rodviks.app.data.local.ChatEntity
import com.rodviks.app.data.local.UserEntity
import com.rodviks.app.di.ServiceLocator
import com.rodviks.app.ui.components.parseIso
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ChatListItem(
    val chat: ChatEntity,
    val peer: UserEntity?,
    val unreadCount: Int
)

data class ChatsUiState(
    val items: List<ChatListItem> = emptyList(),
    val meId: String? = null,
    val loading: Boolean = false,
    val error: String? = null
)

@OptIn(ExperimentalCoroutinesApi::class)
class ChatsViewModel : ViewModel() {

    private val loadingFlow = MutableStateFlow(false)
    private val errorFlow = MutableStateFlow<String?>(null)

    private val meIdFlow = ServiceLocator.sessionManager.sessionFlow
        .map { it.userId }

    private val itemsFlow: kotlinx.coroutines.flow.Flow<List<ChatListItem>> =
        combine(
            ServiceLocator.chatRepository.observeChats(),
            meIdFlow
        ) { chats, meId -> chats.sortedByDescending { parseIso(it.updatedAt) ?: 0L } to meId }
            .flatMapLatest { (chats, meId) ->
                if (meId == null || chats.isEmpty()) flowOf(emptyList())
                else combine(
                    chats.map { chat ->
                        combine(
                            chat.peerId?.let { ServiceLocator.chatRepository.observeUser(it) } ?: flowOf(null),
                            ServiceLocator.chatRepository.observeUnread(chat.id, meId)
                        ) { peer, unread -> ChatListItem(chat, peer, unread) }
                    }
                ) { it.toList() }
            }

    val state: StateFlow<ChatsUiState> = combine(
        itemsFlow, loadingFlow, errorFlow, meIdFlow
    ) { items, loading, error, meId ->
        ChatsUiState(items = items, meId = meId, loading = loading, error = error)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ChatsUiState())

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            loadingFlow.value = true
            errorFlow.value = null
            val result = ServiceLocator.chatRepository.syncChats()
            loadingFlow.value = false
            if (!result.isSuccess) {
                errorFlow.value = result.error
            }
        }
    }

    fun dismissError() { errorFlow.value = null }
}
