package com.kabutarbaazi.app.ui.screens.chat

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.kabutarbaazi.app.KabutarBaaziApp
import com.kabutarbaazi.app.data.AppContainer
import com.kabutarbaazi.app.data.ChatRepository
import com.kabutarbaazi.app.data.ModerationRepository
import com.kabutarbaazi.app.data.SessionRepository
import com.kabutarbaazi.domain.chat.ConversationKey
import com.kabutarbaazi.domain.chat.UnreadRules
import com.kabutarbaazi.domain.model.Conversation
import com.kabutarbaazi.domain.model.Message
import com.kabutarbaazi.domain.model.PendingMessage
import com.kabutarbaazi.domain.model.Profile
import com.kabutarbaazi.domain.model.SendStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.util.UUID

data class ConversationsUiState(
    val conversations: List<Conversation> = emptyList(),
    val counterparts: Map<String, Profile> = emptyMap(),
    val unreadIds: Set<String> = emptySet(),
    val loading: Boolean = true,
    val error: String? = null,
)

class ConversationsViewModel(
    private val chat: ChatRepository,
    private val session: SessionRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ConversationsUiState())
    val state: StateFlow<ConversationsUiState> = _state.asStateFlow()

    init { refresh() }

    /**
     * A full fetch every time the screen resumes, never trusting that the socket caught
     * everything. Realtime drops events across reconnects and does not replay them, so the
     * fetch is the source of truth and Realtime is only a latency optimisation.
     */
    fun refresh() {
        viewModelScope.launch {
            runCatching {
                val me = session.currentUserId() ?: return@runCatching null
                val convos = chat.conversations()
                val otherIds = convos.map { ConversationKey.counterpart(
                    ConversationKey.Pair(it.userA, it.userB), me,
                ) }
                val profiles = chat.counterparts(otherIds)
                Triple(me, convos, profiles)
            }.onSuccess { result ->
                if (result == null) return@onSuccess
                val (me, convos, profiles) = result
                val unread = convos.filter { c ->
                    UnreadRules.isUnread(
                        UnreadRules.ConversationReadState(
                            userA = c.userA, userB = c.userB,
                            lastMessageAtEpochMs = c.lastMessageAt.toEpochMsOrZero(),
                            lastSenderId = c.lastSenderId,
                            aLastReadAtEpochMs = c.aLastReadAt.toEpochMsOrZero(),
                            bLastReadAtEpochMs = c.bLastReadAt.toEpochMsOrZero(),
                        ),
                        me,
                    )
                }.map { it.id }.toSet()
                _state.update {
                    it.copy(
                        conversations = convos, counterparts = profiles,
                        unreadIds = unread, loading = false, error = null,
                    )
                }
            }.onFailure {
                _state.update { it.copy(loading = false, error = "Could not load chats.") }
            }
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as KabutarBaaziApp
                ConversationsViewModel(app.container.chat, app.container.session)
            }
        }
    }
}

internal fun String?.toEpochMsOrZero(): Long =
    this?.let { runCatching { Instant.parse(it.normalizeIso()).toEpochMilli() }.getOrNull() } ?: 0L

/** Postgres emits microsecond precision and sometimes no zone; Instant.parse needs both fixed. */
internal fun String.normalizeIso(): String {
    var s = replace(" ", "T")
    if (!s.endsWith("Z") && !s.contains('+') && !Regex("-\\d{2}:\\d{2}$").containsMatchIn(s)) s += "Z"
    return s
}

// ---------------------------------------------------------------------------------------------

/**
 * Adds messages that arrived from any source (initial load, Realtime, the insert's own reply,
 * a backfill), skipping ids already shown, and settles the optimistic bubble whose client_id
 * each one carries.
 */
internal fun ChatUiState.withArrived(arrived: List<Message>): ChatUiState {
    val known = messages.map { it.id }.toMutableSet()
    val fresh = arrived.filter { known.add(it.id) }
    val settled = arrived.map { it.clientId }.toSet()
    return copy(
        messages = (messages + fresh).sortedBy { it.createdAt.toEpochMsOrZero() },
        pending = pending.filterNot { it.clientId in settled },
    )
}

data class ChatUiState(
    val messages: List<Message> = emptyList(),
    val pending: List<PendingMessage> = emptyList(),
    /** The other participant, read from the conversation row itself. */
    val counterpartId: String? = null,
    /** Null when the profile is not visible, for example after a block. */
    val counterpart: Profile? = null,
    val loading: Boolean = true,
    val draft: String = "",
    val error: String? = null,
)

class ChatViewModel(
    private val conversationId: String,
    private val chat: ChatRepository,
    private val session: SessionRepository,
    private val moderation: ModerationRepository,
    private val container: AppContainer,
) : ViewModel() {

    private val _state = MutableStateFlow(ChatUiState())
    val state: StateFlow<ChatUiState> = _state.asStateFlow()

    init {
        // Suppresses a push for the thread the user is already reading.
        container.openConversationId = conversationId
        load()
        observeLive()
    }

    override fun onCleared() {
        // The live channel is torn down by the cancellation of viewModelScope (see liveMessages).
        container.openConversationId = null
        super.onCleared()
    }

    fun onDraft(v: String) = _state.update { it.copy(draft = v) }

    fun load() {
        viewModelScope.launch {
            runCatching { chat.messages(conversationId) }
                .onSuccess { msgs -> _state.update { it.withArrived(msgs).copy(loading = false, error = null) } }
                .onFailure { _state.update { it.copy(loading = false, error = "Could not load messages.") } }
            runCatching { chat.markRead(conversationId) }
        }
        if (_state.value.counterpartId == null) loadCounterpart()
    }

    private fun loadCounterpart() {
        viewModelScope.launch {
            runCatching {
                val me = session.currentUserId() ?: return@runCatching
                val c = chat.conversation(conversationId) ?: return@runCatching
                val otherId = ConversationKey.counterpart(ConversationKey.Pair(c.userA, c.userB), me)
                _state.update { it.copy(counterpartId = otherId) }
                // Empty when RLS hides the profile, which is fine: the id is what Block needs.
                chat.counterparts(listOf(otherId))[otherId]?.let { p ->
                    _state.update { it.copy(counterpart = p) }
                }
            }.onFailure { Log.w(TAG, "Could not load the other participant", it) }
        }
    }

    /** Backfills anything that arrived while the socket was down. */
    fun backfill() {
        viewModelScope.launch {
            val newest = _state.value.messages.lastOrNull()?.createdAt ?: return@launch
            runCatching { chat.messagesSince(conversationId, newest) }
                .onSuccess { extra -> if (extra.isNotEmpty()) _state.update { it.withArrived(extra) } }
            runCatching { chat.markRead(conversationId) }
        }
    }

    private fun observeLive() {
        viewModelScope.launch {
            try {
                chat.liveMessages(conversationId).collect { msg ->
                    _state.update { it.withArrived(listOf(msg)) }
                    runCatching { chat.markRead(conversationId) }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Not fatal: sends still land and the resume backfill still fetches replies.
                Log.w(TAG, "Live chat updates stopped", e)
            }
        }
    }

    fun send() {
        val me = session.currentUserId() ?: return
        val body = _state.value.draft.trim()
        if (body.isBlank()) return

        // client_id is minted here so a retry is idempotent at the database level and the
        // Realtime echo of this very message can be matched back to its optimistic row.
        val clientId = UUID.randomUUID().toString()
        _state.update {
            it.copy(draft = "", pending = it.pending + PendingMessage(clientId, body, SendStatus.Sending))
        }
        viewModelScope.launch {
            runCatching { chat.send(conversationId, me, body, clientId) }
                .onSuccess { stored -> _state.update { it.withArrived(listOf(stored)) } }
                .onFailure {
                    _state.update { s ->
                        s.copy(pending = s.pending.map { p ->
                            if (p.clientId == clientId) p.copy(status = SendStatus.Failed) else p
                        })
                    }
                }
        }
    }

    fun retry(clientId: String) {
        val me = session.currentUserId() ?: return
        val p = _state.value.pending.firstOrNull { it.clientId == clientId } ?: return
        _state.update { s ->
            s.copy(pending = s.pending.map { if (it.clientId == clientId) it.copy(status = SendStatus.Sending) else it })
        }
        viewModelScope.launch {
            // Same client_id: the unique constraint makes the retry a no-op if the first
            // attempt actually landed.
            runCatching { chat.send(conversationId, me, p.body, clientId) }
                .onSuccess { stored -> _state.update { it.withArrived(listOf(stored)) } }
                .onFailure {
                    _state.update { s ->
                        s.copy(pending = s.pending.map {
                            if (it.clientId == clientId) it.copy(status = SendStatus.Failed) else it
                        })
                    }
                }
        }
    }

    fun blockCounterpart(onDone: () -> Unit) = viewModelScope.launch {
        val me = session.currentUserId() ?: return@launch
        val otherId = _state.value.counterpartId
        if (otherId == null) {
            _state.update { it.copy(error = "Could not block right now. Please try again.") }
            return@launch
        }
        runCatching { moderation.block(me, otherId) }
            .onSuccess { onDone() }
            .onFailure { e ->
                Log.w(TAG, "Block failed", e)
                _state.update { it.copy(error = "Could not block right now. Please try again.") }
            }
    }

    companion object {
        private const val TAG = "ChatViewModel"

        fun factory(conversationId: String): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as KabutarBaaziApp
                ChatViewModel(
                    conversationId, app.container.chat, app.container.session,
                    app.container.moderation, app.container,
                )
            }
        }
    }
}
