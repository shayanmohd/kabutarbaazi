package com.kabutarbaazi.app.ui.screens.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.kabutarbaazi.app.KabutarBaaziApp
import com.kabutarbaazi.app.data.SessionRepository
import com.kabutarbaazi.domain.auth.PhoneNumber
import com.kabutarbaazi.domain.auth.UsernameError
import com.kabutarbaazi.domain.auth.UsernameRules
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AuthUiState(
    val username: String = "",
    val password: String = "",
    val displayName: String = "",
    val dialCode: String = "+91",
    val phoneLocal: String = "",
    val regionCode: String? = null,
    val usernameError: UsernameError? = null,
    val phoneError: Boolean = false,
    val submitting: Boolean = false,
    val error: String? = null,
) {
    val canSignIn: Boolean
        get() = !submitting && username.isNotBlank() && password.length >= 6

    val canSignUp: Boolean
        get() = !submitting &&
            UsernameRules.isValid(username) &&
            password.length >= 6 &&
            PhoneNumber.parse(dialCode, phoneLocal) != null &&
            regionCode != null
}

class AuthViewModel(private val session: SessionRepository) : ViewModel() {

    private val _state = MutableStateFlow(AuthUiState())
    val state: StateFlow<AuthUiState> = _state.asStateFlow()

    fun onUsername(v: String) = _state.update {
        // Validate as they type, but only show an error once there is something to complain
        // about, so the field is not red before it has been filled in.
        it.copy(username = v, usernameError = if (v.isBlank()) null else UsernameRules.validate(v), error = null)
    }

    fun onPassword(v: String) = _state.update { it.copy(password = v, error = null) }
    fun onDisplayName(v: String) = _state.update { it.copy(displayName = v) }
    fun onDialCode(v: String) = _state.update { it.copy(dialCode = v, phoneError = false) }
    fun onPhone(v: String) = _state.update {
        it.copy(phoneLocal = v.filter(Char::isDigit), phoneError = false, error = null)
    }
    fun onRegion(v: String) = _state.update { it.copy(regionCode = v) }
    fun clearError() = _state.update { it.copy(error = null) }

    fun signIn(onDone: () -> Unit) = submit(onDone) { s ->
        session.signIn(s.username, s.password)
    }

    fun signUp(onDone: () -> Unit) = submit(onDone) { s ->
        val phone = PhoneNumber.parse(s.dialCode, s.phoneLocal) ?: error("invalid_phone")
        session.signUp(
            username = s.username,
            password = s.password,
            phoneE164 = phone,
            displayName = s.displayName.ifBlank { s.username },
            regionCode = s.regionCode,
            locale = "hi",
        )
    }

    private fun submit(onDone: () -> Unit, block: suspend (AuthUiState) -> Unit) {
        val snapshot = _state.value
        if (snapshot.submitting) return
        _state.update { it.copy(submitting = true, error = null) }
        viewModelScope.launch {
            runCatching { block(snapshot) }
                .onSuccess {
                    _state.update { it.copy(submitting = false) }
                    onDone()
                }
                .onFailure { e ->
                    _state.update { it.copy(submitting = false, error = friendlyMessage(e)) }
                }
        }
    }

    /**
     * Supabase surfaces auth failures as opaque strings. These are the only three a user can
     * actually act on; anything else is reported plainly rather than dressed up.
     */
    private fun friendlyMessage(e: Throwable): String {
        val raw = e.message.orEmpty().lowercase()
        return when {
            "already" in raw || "duplicate" in raw || "23505" in raw ->
                "That username is already taken. Please choose another."
            "invalid login" in raw || "credentials" in raw ->
                "Wrong username or password."
            "network" in raw || "unresolved" in raw || "timeout" in raw ->
                "No internet connection. Please try again."
            "invalid_phone" in raw -> "That phone number does not look right."
            else -> "Something went wrong. Please try again."
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as KabutarBaaziApp
                AuthViewModel(app.container.session)
            }
        }
    }
}
