package com.kabutarbaazi.domain.feed

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReelWindowTest {

    @Test fun `middle of the feed plays one and prepares both neighbours`() {
        val w = ReelWindow.forSettled(settled = 5, itemCount = 20)
        assertEquals(5, w.play)
        assertEquals(setOf(4, 6), w.prepare.toSet())
        assertEquals(setOf(4, 5, 6), w.retain)
    }

    @Test fun `first page has no previous neighbour`() {
        val w = ReelWindow.forSettled(settled = 0, itemCount = 20)
        assertEquals(0, w.play)
        assertEquals(listOf(1), w.prepare)
        assertEquals(setOf(0, 1), w.retain)
    }

    @Test fun `last page has no next neighbour`() {
        val w = ReelWindow.forSettled(settled = 19, itemCount = 20)
        assertEquals(19, w.play)
        assertEquals(listOf(18), w.prepare)
    }

    @Test fun `a single item feed prepares nothing`() {
        val w = ReelWindow.forSettled(settled = 0, itemCount = 1)
        assertEquals(0, w.play)
        assertTrue(w.prepare.isEmpty())
        assertEquals(setOf(0), w.retain)
    }

    @Test fun `empty feed plays nothing`() {
        val w = ReelWindow.forSettled(settled = 0, itemCount = 0)
        assertEquals(-1, w.play)
        assertTrue(w.retain.isEmpty())
    }

    @Test fun `an out of range settled index is clamped rather than crashing`() {
        // The pager can briefly report a stale index after the feed shrinks.
        assertEquals(9, ReelWindow.forSettled(settled = 99, itemCount = 10).play)
        assertEquals(0, ReelWindow.forSettled(settled = -5, itemCount = 10).play)
    }

    @Test fun `retained pages never collide in the three slot pool`() {
        // This is the property the whole pool design rests on: the playing page and both
        // neighbours must map to three different players, at every position in the feed.
        for (settled in 0 until 50) {
            val w = ReelWindow.forSettled(settled, itemCount = 50)
            val slots = w.retain.map { ReelWindow.slotFor(it) }
            assertEquals("slot collision at settled=$settled", slots.size, slots.toSet().size)
        }
    }

    @Test fun `slot assignment handles negative indices safely`() {
        // mod, not rem: rem would return a negative slot and index out of the pool array.
        assertTrue(ReelWindow.slotFor(-1) in 0 until ReelWindow.POOL_SIZE)
    }

    @Test fun `loads more when approaching the end`() {
        assertTrue(ReelWindow.shouldLoadMore(settled = 17, itemCount = 20, hasMore = true))
        assertFalse(ReelWindow.shouldLoadMore(settled = 10, itemCount = 20, hasMore = true))
    }

    @Test fun `never loads more when the feed is exhausted`() {
        assertFalse(ReelWindow.shouldLoadMore(settled = 19, itemCount = 20, hasMore = false))
    }
}
