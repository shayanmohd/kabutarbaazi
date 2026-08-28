package com.kabutarbaazi.domain.chat

import org.junit.Assert.assertEquals
import org.junit.Test

class ConversationKeyTest {

    private val alice = "0a11c3de-0000-4000-8000-000000000001"
    private val bob = "f0b00000-0000-4000-8000-000000000002"

    @Test fun `ordering is canonical regardless of argument order`() {
        assertEquals(ConversationKey.of(alice, bob), ConversationKey.of(bob, alice))
    }

    @Test fun `lower id always lands in userA`() {
        val pair = ConversationKey.of(bob, alice)
        assertEquals(alice, pair.userA)
        assertEquals(bob, pair.userB)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `a user cannot message themselves`() {
        ConversationKey.of(alice, alice)
    }

    @Test fun `counterpart returns the other participant`() {
        val pair = ConversationKey.of(alice, bob)
        assertEquals(bob, ConversationKey.counterpart(pair, alice))
        assertEquals(alice, ConversationKey.counterpart(pair, bob))
    }

    @Test(expected = IllegalStateException::class)
    fun `counterpart rejects a stranger`() {
        ConversationKey.counterpart(ConversationKey.of(alice, bob), "someone-else")
    }
}
