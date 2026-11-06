package com.kabutarbaazi.app.data

import com.kabutarbaazi.app.testing.FakeSupabase
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Settings > Blocked people. RLS answers the blocker's own block rows but hides a blocked
 * profile from the blocker (is_blocked_with is symmetric), so the profiles endpoint returns
 * nothing for exactly the people on this list.
 */
class BlockedPeopleTest {

    private val me = "11111111-1111-4111-8111-111111111111"
    private val blocked = "22222222-2222-4222-8222-222222222222"

    @Test
    fun `a blocked person is listed even though RLS hides their profile`() = runBlocking {
        val fake = FakeSupabase(me)
        fake.rest = { call ->
            when {
                call.path.endsWith("/rest/v1/blocks") ->
                    """[{"blocked_id":"$blocked","created_at":"2026-11-07T09:30:00+00:00"}]"""
                call.path.endsWith("/rest/v1/profiles") -> "[]"
                else -> "[]"
            }
        }

        val list = ModerationRepository(fake.client).blockedPeople(me)

        assertEquals(listOf(blocked), list.map { it.id })
        assertEquals("2026-11-07", list.single().blockedAt?.take(10))
    }

    @Test
    fun `unblock deletes the caller's own row for that person`() = runBlocking {
        val fake = FakeSupabase(me)
        ModerationRepository(fake.client).unblock(me, blocked)
        val call = fake.calls.single { it.path.endsWith("/rest/v1/blocks") }
        assertEquals("DELETE", call.method.value)
        assertEquals(true, call.query.contains("blocker_id=eq.$me") && call.query.contains("blocked_id=eq.$blocked"))
    }
}
