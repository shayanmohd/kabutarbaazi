package com.kabutarbaazi.domain.chat

/**
 * A conversation is one row per pair of users, never one per listing. Users do not think in
 * threads-per-ad, and per-listing threads triple the unread bookkeeping for no benefit.
 *
 * The pair is stored in canonical order (lower id first) with a UNIQUE constraint, so the
 * database itself makes a duplicate pair impossible. This computes the same ordering on the
 * client, which means the client can predict the key rather than round-tripping to find out.
 */
object ConversationKey {

    data class Pair(val userA: String, val userB: String)

    fun of(one: String, other: String): Pair {
        require(one != other) { "A user cannot open a conversation with themselves" }
        return if (one < other) Pair(one, other) else Pair(other, one)
    }

    /** The participant who is not me. */
    fun counterpart(pair: Pair, me: String): String = when (me) {
        pair.userA -> pair.userB
        pair.userB -> pair.userA
        else -> error("User $me is not a participant in this conversation")
    }
}
