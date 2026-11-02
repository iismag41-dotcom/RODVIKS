package com.rodviks.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rodviks.app.data.repo.ApiResult
import com.rodviks.app.di.ServiceLocator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class AuthUiState(
    val username: String = "",
    val email: String = "",
    val displayName: String = "",
    val password: String = "",
    val passwordRepeat: String = "",
    val serverUrl: String = "",
    val loading: Boolean = false,
    val error: String? = null,
    val fieldError: String? = null,
    val success: Boolean = false
)

class LoginViewModel : ViewModel() {

    private val _state = MutableStateFlow(AuthUiState())
    val state: StateFlow<AuthUiState> = _state

    init {
        viewModelScope.launch {
            val saved = ServiceLocator.sessionManager.currentBaseUrl()
            _state.value = _state.value.copy(serverUrl = saved ?: "")
        }
    }

    fun onUsernameChange(v: String) { _state.value = _state.value.copy(username = v, fieldError = null) }
    fun onPasswordChange(v: String) { _state.value = _state.value.copy(password = v, fieldError = null) }
    fun onServerUrlChange(v: String) { _state.value = _state.value.copy(serverUrl = v) }

    fun login() {
        val s = _state.value
        if (s.loading) return
        when {
            s.username.isBlank() -> { _state.value = s.copy(fieldError = "Заполните поле"); return }
            s.password.isBlank() -> { _state.value = s.copy(fieldError = "Заполните поле"); return }
        }
        _state.value = s.copy(loading = true, error = null)
        viewModelScope.launch {
            // persist a non-default server address before the request so the
            // AuthInterceptor routes this and all future calls to the right host
            val trimmedUrl = s.serverUrl.trim()
            if (trimmedUrl.isNotEmpty()) {
                ServiceLocator.sessionManager.saveBaseUrl(trimmedUrl)
                com.rodviks.app.ui.components.currentBaseUrlOverride = trimmedUrl
            }
            val result = ServiceLocator.authRepository.login(s.username, s.password)
            _state.value = _state.value.copy(
                loading = false,
                success = result.isSuccess,
                error = if (result.isSuccess) null else result.error
            )
        }
    }
}

class RegisterViewModel : ViewModel() {

    private val _state = MutableStateFlow(AuthUiState())
    val state: StateFlow<AuthUiState> = _state

    init {
        viewModelScope.launch {
            val saved = ServiceLocator.sessionManager.currentBaseUrl()
            _state.value = _state.value.copy(serverUrl = saved ?: "")
        }
    }

    fun onUsernameChange(v: String) { _state.value = _state.value.copy(username = v, fieldError = null) }
    fun onEmailChange(v: String) { _state.value = _state.value.copy(email = v, fieldError = null) }
    fun onDisplayNameChange(v: String) { _state.value = _state.value.copy(displayName = v, fieldError = null) }
    fun onPasswordChange(v: String) { _state.value = _state.value.copy(password = v, fieldError = null) }
    fun onPasswordRepeatChange(v: String) { _state.value = _state.value.copy(passwordRepeat = v, fieldError = null) }
    fun onServerUrlChange(v: String) { _state.value = _state.value.copy(serverUrl = v) }

    private fun validate(s: AuthUiState): String? = when {
        s.username.isBlank() || s.email.isBlank() -> "Заполните поле"
        s.username.length < 3 -> "Имя пользователя минимум 3 символа"
        !s.username.all { it.isLetterOrDigit() || it == '_' } -> "Только латиница, цифры и подчёркивание"
        !s.email.contains('@') || s.email.length < 5 -> "Некорректный email"
        s.password.length < 6 -> "Пароль минимум 6 символов"
        s.password != s.passwordRepeat -> "Пароли не совпадают"
        else -> null
    }

    fun register() {
        val s = _state.value
        if (s.loading) return
        val fieldError = validate(s)
        if (fieldError != null) {
            _state.value = s.copy(fieldError = fieldError)
            return
        }
        _state.value = s.copy(loading = true, error = null)
        viewModelScope.launch {
            // persist a non-default server address before the request so the
            // AuthInterceptor routes this and all future calls to the right host
            val trimmedUrl = s.serverUrl.trim()
            if (trimmedUrl.isNotEmpty()) {
                ServiceLocator.sessionManager.saveBaseUrl(trimmedUrl)
                com.rodviks.app.ui.components.currentBaseUrlOverride = trimmedUrl
            }
            val result = ServiceLocator.authRepository.register(
                s.username, s.email, s.displayName.ifBlank { null }, s.password
            )
            _state.value = _state.value.copy(
                loading = false,
                success = result.isSuccess,
                error = if (result.isSuccess) null else result.error
            )
        }
    }
}

class SplashViewModel : ViewModel() {
    val sessionFlow = ServiceLocator.sessionManager.sessionFlow
}
