package com.kabutarbaazi.domain.chat

/**
 * Unread state is derived from two timestamps on the conversation row rather than a per-message
 * read flag. The tab badge counts conversations with something new, not individual messages,
 * which means it comes free with the list query instead of costing a second round trip.
 */
object UnreadRules {

    data class ConversationReadState(
        val userA: String,
        val userB: String,
        val lastMessageAtEpochMs: Long,
        val lastSenderId: String?,
        val aLastReadAtEpochMs: Long,
        val bLastReadAtEpochMs: Long,
    )

    fun lastReadFor(state: ConversationReadState, me: String): Long = when (me) {
        state.userA -> state.aLastReadAtEpochMs
        state.userB -> state.bLastReadAtEpochMs
        else -> error("User $me is not a participant")
    }

    /**
     * A conversation is unread when the newest message arrived after I last opened it and I am
     * not the one who sent it. Excluding my own messages is what stops the badge lighting up the
     * instant I reply.
     */
    fun isUnread(state: ConversationReadState, me: String): Boolean =
        state.lastMessageAtEpochMs > lastReadFor(state, me) && state.lastSenderId != me

    fun unreadCount(states: List<ConversationReadState>, me: String): Int =
        states.count { isUnread(it, me) }
}
