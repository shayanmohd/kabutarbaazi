package com.kabutarbaazi.app.ui.screens.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.kabutarbaazi.app.KabutarBaaziApp
import com.kabutarbaazi.app.data.AppContainer
import com.kabutarbaazi.domain.model.Listing
import com.kabutarbaazi.domain.model.Profile
import com.kabutarbaazi.domain.model.Reel
import com.kabutarbaazi.domain.model.ReportReason
import com.kabutarbaazi.domain.model.ReportTargetType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ProfileUiState(
    val profile: Profile? = null,
    val listings: List<Listing> = emptyList(),
    val reels: List<Reel> = emptyList(),
    val following: Boolean = false,
    val isMe: Boolean = false,
    val loading: Boolean = true,
    val toast: String? = null,
)

class ProfileViewModel(
    private val userId: String,
    private val container: AppContainer,
) : ViewModel() {

    private val _state = MutableStateFlow(ProfileUiState())
    val state: StateFlow<ProfileUiState> = _state.asStateFlow()

    init { load() }

    fun load() = viewModelScope.launch {
        runCatching {
            val me = container.session.currentUserId()
            val p = container.profiles.byId(userId)
            val l = container.listings.bySeller(userId)
            val r = container.reels.byAuthor(userId)
            val f = me?.let { container.profiles.followingIds(it) }.orEmpty()
            listOf(me, p, l, r, f)
        }.onSuccess { parts ->
            @Suppress("UNCHECKED_CAST")
            _state.update {
                it.copy(
                    profile = parts[1] as Profile?,
                    listings = parts[2] as List<Listing>,
                    reels = parts[3] as List<Reel>,
                    following = userId in (parts[4] as Set<String>),
                    isMe = parts[0] == userId,
                    loading = false,
                )
            }
        }.onFailure { _state.update { it.copy(loading = false) } }
    }

    fun toggleFollow() {
        val me = container.session.currentUserId() ?: return
        if (me == userId) return
        val was = _state.value.following
        _state.update { it.copy(following = !was) }
        viewModelScope.launch {
            runCatching { container.profiles.setFollowing(me, userId, !was) }
                .onFailure { _state.update { it.copy(following = was) } }
        }
    }

    fun block(onDone: () -> Unit) = viewModelScope.launch {
        val me = container.session.currentUserId() ?: return@launch
        runCatching { container.moderation.block(me, userId) }.onSuccess { onDone() }
    }

    fun report(reason: ReportReason, note: String?) = viewModelScope.launch {
        runCatching { container.moderation.report(ReportTargetType.User, userId, reason, note) }
            .onSuccess { _state.update { it.copy(toast = "Report sent. Thank you.") } }
    }

    fun clearToast() = _state.update { it.copy(toast = null) }

    companion object {
        fun factory(userId: String): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as KabutarBaaziApp
                ProfileViewModel(userId, app.container)
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------

data class SettingsUiState(
    val profile: Profile? = null,
    val blocked: List<Profile> = emptyList(),
    val language: String = "en",
    val deleting: Boolean = false,
    val deleted: Boolean = false,
    val error: String? = null,
)

class SettingsViewModel(private val container: AppContainer) : ViewModel() {

    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    init { load() }

    fun load() = viewModelScope.launch {
        runCatching {
            val me = container.session.currentUserId()
            val p = container.session.currentProfile()
            val b = me?.let { container.moderation.blockedProfiles(it) }.orEmpty()
            p to b
        }.onSuccess { (p, b) ->
            _state.update { it.copy(profile = p, blocked = b) }
        }
    }

    fun setLanguage(tag: String) {
        _state.update { it.copy(language = tag) }
        val me = container.session.currentUserId() ?: return
        // Mirrored to the profile so push notifications can be localised server-side.
        viewModelScope.launch { runCatching { container.profiles.setLocale(me, tag) } }
    }

    fun unblock(userId: String) = viewModelScope.launch {
        val me = container.session.currentUserId() ?: return@launch
        runCatching { container.moderation.unblock(me, userId) }
            .onSuccess { _state.update { it.copy(blocked = it.blocked.filterNot { p -> p.id == userId }) } }
    }

    fun signOut() = viewModelScope.launch {
        container.session.signOut {
            // Clear the push token first, or the next person to sign in on this handset gets
            // the previous user's chat notifications.
            container.pendingPushToken?.let { runCatching { container.pushTokens.unregister(it) } }
        }
    }

    /**
     * Deletes the account and everything under it, including every media object in R2.
     * Play requires both this and a publicly reachable web URL that does the same.
     */
    fun deleteAccount() {
        if (_state.value.deleting) return
        _state.update { it.copy(deleting = true, error = null) }
        viewModelScope.launch {
            runCatching { container.session.deleteAccount() }
                .onSuccess { _state.update { it.copy(deleting = false, deleted = true) } }
                .onFailure {
                    _state.update {
                        it.copy(deleting = false, error = "Could not delete the account. Please try again.")
                    }
                }
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as KabutarBaaziApp
                SettingsViewModel(app.container)
            }
        }
    }
}
