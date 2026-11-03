package com.rodviks.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rodviks.app.data.repo.PhoneAuthResult
import com.rodviks.app.di.ServiceLocator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Phone + password auth: login / register / first-login username setup.
 */
data class PhoneAuthUiState(
    val phone: String = "",
    val password: String = "",
    val passwordRepeat: String = "",
    val username: String = "",
    val displayName: String = "",
    val mode: PhoneAuthMode = PhoneAuthMode.LOGIN,
    val stage: PhoneAuthStage = PhoneAuthStage.FORM,
    val loading: Boolean = false,
    val error: String? = null,
    val authorized: Boolean = false
)

enum class PhoneAuthMode { LOGIN, REGISTER }
enum class PhoneAuthStage { FORM, USERNAME, DONE }

class PhoneAuthViewModel : ViewModel() {

    private val _state = MutableStateFlow(PhoneAuthUiState())
    val state: StateFlow<PhoneAuthUiState> = _state

    fun onPhoneChange(v: String) {
        val digits = v.filter { it.isDigit() }.take(11)
        _state.value = _state.value.copy(phone = digits, error = null)
    }

    fun onPasswordChange(v: String) {
        _state.value = _state.value.copy(password = v, error = null)
    }

    fun onPasswordRepeatChange(v: String) {
        _state.value = _state.value.copy(passwordRepeat = v, error = null)
    }

    fun onUsernameChange(v: String) {
        _state.value = _state.value.copy(username = v.filter { it.isLetterOrDigit() || it == '_' }.take(24), error = null)
    }

    fun onDisplayNameChange(v: String) {
        _state.value = _state.value.copy(displayName = v.take(50), error = null)
    }

    fun switchMode() {
        val s = _state.value
        _state.value = s.copy(
            mode = if (s.mode == PhoneAuthMode.LOGIN) PhoneAuthMode.REGISTER else PhoneAuthMode.LOGIN,
            error = null,
            password = "",
            passwordRepeat = ""
        )
    }

    private fun validateForm(s: PhoneAuthUiState): String? = when {
        s.phone.length != 11 -> "Введите номер полностью"
        s.password.isEmpty() -> "Введите пароль"
        s.mode == PhoneAuthMode.REGISTER && s.password.length < 8 -> "Пароль минимум 8 символов"
        s.mode == PhoneAuthMode.REGISTER && s.password != s.passwordRepeat -> "Пароли не совпадают"
        else -> null
    }

    fun submit() {
        val s = _state.value
        if (s.loading) return
        val fieldError = validateForm(s)
        if (fieldError != null) {
            _state.value = s.copy(error = fieldError)
            return
        }
        _state.value = s.copy(loading = true, error = null)
        viewModelScope.launch {
            val result: PhoneAuthResult = if (s.mode == PhoneAuthMode.LOGIN) {
                ServiceLocator.authRepository.phoneLogin("+" + s.phone, s.password)
            } else {
                ServiceLocator.authRepository.phoneRegister("+" + s.phone, s.password)
            }
            when {
                result.error != null || result.user == null ->
                    _state.value = _state.value.copy(loading = false, error = result.error ?: "Ошибка")
                result.needsUsername ->
                    _state.value = _state.value.copy(loading = false, stage = PhoneAuthStage.USERNAME, error = null)
                else ->
                    _state.value = _state.value.copy(loading = false, stage = PhoneAuthStage.DONE, authorized = true)
            }
        }
    }

    fun saveUsername() {
        val s = _state.value
        if (s.loading) return
        if (s.username.length < 3) {
            _state.value = s.copy(error = "Имя минимум 3 символа")
            return
        }
        _state.value = s.copy(loading = true, error = null)
        viewModelScope.launch {
            val result = ServiceLocator.authRepository.setPhoneUsername(s.username, s.displayName)
            if (result.isSuccess) {
                _state.value = _state.value.copy(loading = false, stage = PhoneAuthStage.DONE, authorized = true)
            } else {
                _state.value = _state.value.copy(loading = false, error = result.error)
            }
        }
    }
}
