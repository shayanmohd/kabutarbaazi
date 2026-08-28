package com.kabutarbaazi.domain.feed

/**
 * Decides which reels are playing, prepared, or evicted around the settled page.
 *
 * This is pure arithmetic pulled out of the Compose layer on purpose: it is the piece most
 * likely to be subtly wrong (off-by-one at the feed edges, slot collisions after pagination)
 * and it costs nothing to test on the JVM instead of chasing it on a device.
 */
object ReelWindow {

    /**
     * Three players. One for the visible page, one either side so a swipe starts instantly.
     * More than three buys nothing and costs decoder handles, which cheap phones are short of.
     */
    const val POOL_SIZE = 3

    /** Fetch the next page once the user is this close to the end. */
    const val LOAD_MORE_THRESHOLD = 3

    data class Window(
        /** The one and only page allowed to have playWhenReady = true. */
        val play: Int,
        /** Prepared but paused, so swiping either way is instant. */
        val prepare: List<Int>,
        /** Everything the pool should still hold. Anything else is released. */
        val retain: Set<Int>,
    )

    /**
     * Slot assignment. Uses mod so neighbouring pages never collide, but the pool must compare
     * the reel id rather than the index before deciding a slot is already correct: indices shift
     * when the feed appends, and an index-keyed pool silently plays the wrong video after
     * pagination.
     */
    fun slotFor(index: Int): Int = index.mod(POOL_SIZE)

    fun forSettled(settled: Int, itemCount: Int): Window {
        require(itemCount >= 0) { "itemCount cannot be negative" }
        if (itemCount == 0) return Window(play = -1, prepare = emptyList(), retain = emptySet())

        val current = settled.coerceIn(0, itemCount - 1)
        val neighbours = listOf(current + 1, current - 1).filter { it in 0 until itemCount }

        return Window(
            play = current,
            prepare = neighbours,
            retain = (neighbours + current).toSet(),
        )
    }

    fun shouldLoadMore(settled: Int, itemCount: Int, hasMore: Boolean): Boolean =
        hasMore && itemCount > 0 && settled >= itemCount - LOAD_MORE_THRESHOLD
}
