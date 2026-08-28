package com.kabutarbaazi.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.kabutarbaazi.app.KabutarBaaziApp
import com.kabutarbaazi.app.data.SessionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class RootUiState(
    val loading: Boolean = true,
    val signedIn: Boolean = false,
    val termsAccepted: Boolean = false,
    val isAdmin: Boolean = false,
    val userId: String? = null,
    val accepting: Boolean = false,
)

class RootViewModel(private val session: SessionRepository) : ViewModel() {

    private val _state = MutableStateFlow(RootUiState())
    val state: StateFlow<RootUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            session.sessionStatus.collect { authenticated ->
                if (authenticated) loadProfile() else _state.value = RootUiState(loading = false)
            }
        }
    }

    fun refresh() = viewModelScope.launch { loadProfile() }

    private suspend fun loadProfile() {
        val profile = runCatching { session.currentProfile() }.getOrNull()
        _state.value = RootUiState(
            loading = false,
            signedIn = session.currentUserId() != null,
            termsAccepted = profile?.hasAcceptedTerms == true,
            isAdmin = profile?.isAdmin == true,
            userId = profile?.id ?: session.currentUserId(),
        )
    }

    fun acceptTerms() {
        if (_state.value.accepting) return
        _state.update { it.copy(accepting = true) }
        viewModelScope.launch {
            runCatching { session.acceptTerms() }
            loadProfile()
        }
    }

    fun signOut() = viewModelScope.launch {
        // The push token is deleted before signing out. Leaving it behind means the next person
        // to sign in on this handset receives the previous user's chat notifications.
        session.signOut()
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as KabutarBaaziApp
                RootViewModel(app.container.session)
            }
        }
    }
}
