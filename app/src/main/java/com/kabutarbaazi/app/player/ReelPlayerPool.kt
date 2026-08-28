package com.kabutarbaazi.app.player

import android.content.Context
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.kabutarbaazi.domain.feed.ReelWindow

/**
 * Exactly three ExoPlayer instances, reused across the whole feed.
 *
 * The pool deliberately lives in the composable, never in a ViewModel: a player held by a
 * ViewModel survives configuration changes and leaks its Surface, which is the single most
 * common reels bug.
 *
 * The slot is index-mod-3, but binding compares the REEL ID, not the index. Indices shift when
 * the feed appends a page, and an index-keyed pool silently plays the wrong video after
 * pagination.
 */
@UnstableApi
class ReelPlayerPool(
    context: Context,
    cache: SimpleCache,
) {
    private val appContext = context.applicationContext

    private val sourceFactory = DefaultMediaSourceFactory(
        CacheDataSource.Factory()
            .setCache(cache)
            .setUpstreamDataSourceFactory(
                DefaultHttpDataSource.Factory().setUserAgent("KabutarBaazi"),
            )
            // Read-only on cache miss would defeat the point; write through so a re-watch is free.
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR),
    )

    private val players: List<ExoPlayer> = List(ReelWindow.POOL_SIZE) {
        ExoPlayer.Builder(appContext)
            .setMediaSourceFactory(sourceFactory)
            .setLoadControl(
                DefaultLoadControl.Builder()
                    // Short buffers: in a feed, time-to-first-frame is the only thing that matters.
                    .setBufferDurationsMs(2_000, 15_000, 500, 1_000)
                    .setPrioritizeTimeOverSizeThresholds(true)
                    .build(),
            )
            .build()
            .apply {
                repeatMode = Player.REPEAT_MODE_ONE
                // Safe because exactly one player ever has playWhenReady = true, so only one
                // ever requests audio focus.
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(C.USAGE_MEDIA)
                        .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                        .build(),
                    true,
                )
                playWhenReady = false
            }
    }

    /** Which reel each slot currently holds, so a rebind can be skipped when nothing changed. */
    private val boundReelIds = arrayOfNulls<String>(ReelWindow.POOL_SIZE)

    fun playerFor(index: Int): ExoPlayer = players[ReelWindow.slotFor(index)]

    /** Binds without playing. Returns the player so the page can attach it to a PlayerView. */
    fun bind(index: Int, reelId: String, url: String): ExoPlayer {
        val slot = ReelWindow.slotFor(index)
        val player = players[slot]
        if (boundReelIds[slot] != reelId) {
            player.setMediaItem(MediaItem.fromUri(url))
            player.prepare()
            boundReelIds[slot] = reelId
        }
        return player
    }

    /**
     * The single-playing invariant, in exactly one place. Every other slot is muted and paused
     * BEFORE the target starts, so a fast fling can never leave two audio streams running.
     */
    fun playOnly(index: Int) {
        val slot = ReelWindow.slotFor(index)
        players.forEachIndexed { i, p ->
            if (i != slot) {
                p.playWhenReady = false
                p.volume = 0f
            }
        }
        players[slot].volume = 1f
        players[slot].playWhenReady = true
    }

    fun setMuted(muted: Boolean, index: Int) {
        players[ReelWindow.slotFor(index)].volume = if (muted) 0f else 1f
    }

    fun pauseAll() = players.forEach { it.playWhenReady = false }

    fun release() {
        players.forEach {
            it.stop()
            it.release()
        }
        boundReelIds.fill(null)
    }
}
