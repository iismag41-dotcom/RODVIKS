package com.rodviks.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rodviks.app.data.api.User
import com.rodviks.app.data.local.UserEntity
import com.rodviks.app.di.ServiceLocator
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType

data class SearchUiState(
    val query: String = "",
    val results: List<User> = emptyList(),
    val searching: Boolean = false,
    val searched: Boolean = false,
    val error: String? = null,
    val openingChatId: String? = null,
    val opening: Boolean = false
)

class SearchViewModel : ViewModel() {

    private val _state = MutableStateFlow(SearchUiState())
    val state: StateFlow<SearchUiState> = _state

    private var searchJob: Job? = null

    fun onQueryChange(q: String) {
        _state.value = _state.value.copy(query = q, searched = false, error = null)
        searchJob?.cancel()
        if (q.trim().length >= 2) {
            searchJob = viewModelScope.launch {
                delay(400)
                search(q.trim())
            }
        } else {
            _state.value = _state.value.copy(results = emptyList())
        }
    }

    private suspend fun search(q: String) {
        _state.value = _state.value.copy(searching = true)
        val result = ServiceLocator.userRepository.search(q)
        _state.value = _state.value.copy(
            searching = false,
            searched = true,
            results = if (result.isSuccess) result.data ?: emptyList() else emptyList(),
            error = if (result.isSuccess) null else result.error
        )
    }

    fun openChat(userId: String, onChatReady: (String) -> Unit) {
        if (_state.value.opening) return
        _state.value = _state.value.copy(opening = true)
        viewModelScope.launch {
            val result = ServiceLocator.chatRepository.getOrCreateChat(userId)
            val chatId = result.data?.id
            _state.value = _state.value.copy(opening = false)
            if (chatId != null) onChatReady(chatId)
            else _state.value = _state.value.copy(error = result.error ?: "Не удалось открыть чат")
        }
    }

    fun dismissError() { _state.value = _state.value.copy(error = null) }
}

data class ProfileUiState(
    val user: UserEntity? = null,
    val displayName: String = "",
    val bio: String = "",
    val avatarUrl: String? = null,
    val loading: Boolean = false,
    val saving: Boolean = false,
    val saved: Boolean = false,
    val savingAvatar: Boolean = false,
    val error: String? = null
)

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class ProfileViewModel : ViewModel() {

    private val userIdFlow = MutableStateFlow<String?>(null)

    val state: StateFlow<ProfileUiState> = userIdFlow
        .flatMapLatest { id ->
            if (id == null) flowOf(ProfileUiState())
            else ServiceLocator.userRepository.observeUser(id)
                .flatMapLatest { entity ->
                    flowOf(
                        ProfileUiState(
                            user = entity,
                            displayName = entity?.displayName ?: entity?.username ?: "",
                            bio = entity?.bio ?: "",
                            avatarUrl = entity?.avatarUrl
                        )
                    )
                }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ProfileUiState())

    private val savingFlow = MutableStateFlow(false)

    init {
        viewModelScope.launch {
            userIdFlow.value = ServiceLocator.sessionManager.currentUserId()
        }
    }

    fun loadUser(id: String) {
        viewModelScope.launch {
            userIdFlow.value = id
            val result = ServiceLocator.userRepository.getUser(id)
            if (!result.isSuccess) {
                // error surface optional; cache may already show
            }
        }
    }

    fun setUserId(id: String) {
        userIdFlow.value = id
    }

    fun onDisplayNameChange(v: String) {
        // local edit state kept out of the observed flow; stored on save
    }

    fun onBioChange(v: String) {}

    fun save(displayName: String, bio: String) {
        if (savingFlow.value) return
        savingFlow.value = true
        viewModelScope.launch {
            val result = ServiceLocator.userRepository.updateMe(
                displayName.trim().ifBlank { null },
                bio.trim().ifBlank { null },
                null
            )
            savingFlow.value = false
            if (result.isSuccess) {
                // refresh cache already done in repo
            }
        }
    }

    fun uploadAvatar(context: android.content.Context, uri: android.net.Uri) {
        viewModelScope.launch {
            try {
                val temp = java.io.File(context.cacheDir, "avatar_${System.currentTimeMillis()}.jpg")
                context.contentResolver.openInputStream(uri)?.use { input ->
                    temp.outputStream().use { output -> input.copyTo(output) }
                }
                val part = okhttp3.MultipartBody.Part.createFormData(
                    "image", temp.name,
                    okhttp3.RequestBody.create(
                        "image/jpeg".toMediaType(),
                        temp
                    )
                )
                val resp = ServiceLocator.api.uploadImage(part)
                temp.delete()
                if (resp.isSuccessful && resp.body() != null) {
                    val url = resp.body()!!.url
                    ServiceLocator.userRepository.updateMe(null, null, url)
                }
            } catch (e: Exception) {
                // silent
            }
        }
    }
}

data class SettingsUiState(
    val darkTheme: Boolean = true,
    val serverUrl: String = "",
    val saving: Boolean = false,
    val savedMessage: String? = null,
    val loggedOut: Boolean = false,
    val loading: Boolean = false
)

class SettingsViewModel : ViewModel() {

    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _state

    init {
        viewModelScope.launch {
            val url = ServiceLocator.sessionManager.currentBaseUrl()
                ?: com.rodviks.app.data.session.SessionManager.DEFAULT_BASE_URL
            _state.value = _state.value.copy(serverUrl = url)
        }
    }

    fun setInitialDarkTheme(dark: Boolean) {
        if (!_state.value.darkTheme.equals(dark)) {
            _state.value = _state.value.copy(darkTheme = dark)
        }
    }

    fun onDarkThemeChange(dark: Boolean) {
        _state.value = _state.value.copy(darkTheme = dark)
    }

    fun onServerUrlChange(url: String) {
        _state.value = _state.value.copy(serverUrl = url)
    }

    fun saveServerUrl() {
        val url = _state.value.serverUrl.trim().ifBlank {
            com.rodviks.app.data.session.SessionManager.DEFAULT_BASE_URL
        }
        viewModelScope.launch {
            ServiceLocator.sessionManager.saveBaseUrl(url)
            com.rodviks.app.ui.components.currentBaseUrlOverride = url
            ServiceLocator.socket.reconnect(url)
            _state.value = _state.value.copy(savedMessage = "Адрес сервера сохранён")
        }
    }

    fun logout() {
        viewModelScope.launch {
            ServiceLocator.chatRepository.clearLocal()
            ServiceLocator.authRepository.logout()
            ServiceLocator.socket.disconnect()
            _state.value = _state.value.copy(loggedOut = true)
        }
    }

    fun consumeSavedMessage() {
        _state.value = _state.value.copy(savedMessage = null)
    }
}
