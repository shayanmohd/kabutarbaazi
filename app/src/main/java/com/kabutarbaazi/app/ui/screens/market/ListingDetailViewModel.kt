package com.kabutarbaazi.app.ui.screens.market

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.kabutarbaazi.app.KabutarBaaziApp
import com.kabutarbaazi.app.data.ChatRepository
import com.kabutarbaazi.app.data.ListingRepository
import com.kabutarbaazi.app.data.ModerationRepository
import com.kabutarbaazi.app.data.SessionRepository
import com.kabutarbaazi.domain.auth.PhoneNumber
import com.kabutarbaazi.domain.model.Listing
import com.kabutarbaazi.domain.model.ReportReason
import com.kabutarbaazi.domain.model.ReportTargetType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ListingDetailUiState(
    val listing: Listing? = null,
    val loading: Boolean = true,
    val error: String? = null,
    val saved: Boolean = false,
    val isOwn: Boolean = false,
    val revealing: Boolean = false,
    val whatsAppUrl: String? = null,
    val contactError: String? = null,
    val openingChat: Boolean = false,
    val actionDone: String? = null,
)

class ListingDetailViewModel(
    private val listingId: String,
    private val listings: ListingRepository,
    private val session: SessionRepository,
    private val chat: ChatRepository,
    private val moderation: ModerationRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ListingDetailUiState())
    val state: StateFlow<ListingDetailUiState> = _state.asStateFlow()

    init { load() }

    fun load() {
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            runCatching {
                val l = listings.byId(listingId)
                val me = session.currentUserId()
                val saved = me?.let { listings.savedIds(it) }.orEmpty()
                Triple(l, me, saved)
            }.onSuccess { (l, me, saved) ->
                _state.update {
                    it.copy(
                        listing = l,
                        loading = false,
                        isOwn = l != null && l.sellerId == me,
                        saved = listingId in saved,
                        error = if (l == null) "This ad is no longer available." else null,
                    )
                }
            }.onFailure { e -> _state.update { it.copy(loading = false, error = e.friendly()) } }
        }
    }

    /**
     * The only path to a seller's number. The RPC checks the listing is visible, that neither
     * party has blocked the other, rate-limits to 30 reveals a day, and logs the access, so the
     * privacy policy's "hidden until a buyer taps WhatsApp" is a fact rather than a UI convention.
     */
    fun revealWhatsApp(prefill: String) {
        if (_state.value.revealing) return
        _state.update { it.copy(revealing = true, contactError = null) }
        viewModelScope.launch {
            runCatching { listings.revealWhatsApp(listingId) }
                .onSuccess { phone ->
                    _state.update {
                        it.copy(revealing = false, whatsAppUrl = PhoneNumber.waMeUrl(phone, prefill))
                    }
                }
                .onFailure { e ->
                    val msg = when {
                        "rate_limited" in e.message.orEmpty() ->
                            "You have opened a lot of numbers today. Please try again tomorrow."
                        "blocked" in e.message.orEmpty() -> "You cannot contact this seller."
                        else -> "Could not open WhatsApp. Please try again."
                    }
                    _state.update { it.copy(revealing = false, contactError = msg) }
                }
        }
    }

    fun consumeWhatsAppUrl() = _state.update { it.copy(whatsAppUrl = null) }
    fun clearAction() = _state.update { it.copy(actionDone = null, contactError = null) }

    fun openChat(onOpened: (String) -> Unit) {
        val l = _state.value.listing ?: return
        if (_state.value.openingChat) return
        _state.update { it.copy(openingChat = true) }
        viewModelScope.launch {
            runCatching { chat.startConversation(l.sellerId, l.id) }
                .onSuccess { id -> _state.update { it.copy(openingChat = false) }; onOpened(id) }
                .onFailure { _state.update { it.copy(openingChat = false, contactError = "Could not open chat.") } }
        }
    }

    fun toggleSave() {
        val me = session.currentUserId() ?: return
        val was = _state.value.saved
        _state.update { it.copy(saved = !was) }
        viewModelScope.launch {
            runCatching { listings.setSaved(me, listingId, !was) }
                .onFailure { _state.update { it.copy(saved = was) } }
        }
    }

    fun report(reason: ReportReason, note: String?) = viewModelScope.launch {
        runCatching { moderation.report(ReportTargetType.Listing, listingId, reason, note) }
            .onSuccess { _state.update { it.copy(actionDone = "Report sent. Thank you.") } }
            .onFailure { _state.update { it.copy(actionDone = "Could not send the report.") } }
    }

    fun blockSeller() = viewModelScope.launch {
        val me = session.currentUserId() ?: return@launch
        val l = _state.value.listing ?: return@launch
        runCatching { moderation.block(me, l.sellerId) }
            .onSuccess { _state.update { it.copy(actionDone = "Blocked. You will not see their ads.") } }
    }

    fun markSold() = viewModelScope.launch {
        runCatching { listings.markSold(listingId) }.onSuccess { load() }
    }

    fun delete(onDeleted: () -> Unit) = viewModelScope.launch {
        runCatching { listings.delete(listingId) }.onSuccess { onDeleted() }
    }

    companion object {
        fun factory(listingId: String): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as KabutarBaaziApp
                ListingDetailViewModel(
                    listingId,
                    app.container.listings,
                    app.container.session,
                    app.container.chat,
                    app.container.moderation,
                )
            }
        }
    }
}
