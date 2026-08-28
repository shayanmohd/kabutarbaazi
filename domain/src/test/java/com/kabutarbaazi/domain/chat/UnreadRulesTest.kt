package com.kabutarbaazi.domain.chat

import com.kabutarbaazi.domain.chat.UnreadRules.ConversationReadState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UnreadRulesTest {

    private val me = "aaa"
    private val them = "bbb"

    private fun state(
        lastMessageAt: Long,
        lastSender: String?,
        myRead: Long,
    ) = ConversationReadState(
        userA = me, userB = them,
        lastMessageAtEpochMs = lastMessageAt,
        lastSenderId = lastSender,
        aLastReadAtEpochMs = myRead,
        bLastReadAtEpochMs = 0,
    )

    @Test fun `their newer message is unread`() {
        assertTrue(UnreadRules.isUnread(state(2000, them, 1000), me))
    }

    @Test fun `my own message never marks the thread unread`() {
        // Otherwise the badge lights up the instant you reply.
        assertFalse(UnreadRules.isUnread(state(2000, me, 1000), me))
    }

    @Test fun `already read is not unread`() {
        assertFalse(UnreadRules.isUnread(state(1000, them, 2000), me))
    }

    @Test fun `exactly-equal timestamps count as read`() {
        assertFalse(UnreadRules.isUnread(state(1000, them, 1000), me))
    }

    @Test fun `counts conversations not messages`() {
        val states = listOf(
            state(2000, them, 1000),
            state(2000, them, 1000),
            state(2000, me, 1000),
            state(500, them, 1000),
        )
        assertEquals(2, UnreadRules.unreadCount(states, me))
    }

    @Test(expected = IllegalStateException::class)
    fun `rejects a non participant`() {
        UnreadRules.isUnread(state(1, them, 0), "stranger")
    }
}
