package com.kabutarbaazi.app.data

import android.content.Context
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import io.github.jan.supabase.SupabaseClient
import java.io.File

/**
 * Manual dependency container, held by [com.kabutarbaazi.app.KabutarBaaziApp]. No DI framework,
 * matching the rest of the house. Everything is process-scoped and lazily built.
 */
@UnstableApi
class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val supabase: SupabaseClient by lazy { SupabaseFactory.create() }

    val session by lazy { SessionRepository(supabase) }
    val profiles by lazy { ProfileRepository(supabase) }
    val listings by lazy { ListingRepository(supabase) }
    val reels by lazy { ReelRepository(supabase) }
    val communities by lazy { CommunityRepository(supabase) }
    val chat by lazy { ChatRepository(supabase) }
    val moderation by lazy { ModerationRepository(supabase) }
    val media by lazy { MediaUploadRepository(supabase) }
    val pushTokens by lazy { PushTokenRepository(supabase) }

    /**
     * One SimpleCache per process, forever. Two instances pointed at the same directory throw
     * immediately, and because the reels screen can be created and destroyed many times per
     * session this is the single thing that most justifies living in the container.
     */
    val reelCache: SimpleCache by lazy {
        SimpleCache(
            File(appContext.cacheDir, "reels"),
            LeastRecentlyUsedCacheEvictor(96L * 1024 * 1024),
            StandaloneDatabaseProvider(appContext),
        )
    }

    /**
     * Set by the chat screen while a thread is on screen. A push for the conversation the user
     * is already reading is suppressed, because Realtime has already delivered the message.
     * One volatile field beats an event bus.
     */
    @Volatile
    var openConversationId: String? = null

    /**
     * FCM can hand us a token before anyone has signed in. Stash it here and register it
     * against the user right after the next successful sign-in.
     */
    @Volatile
    var pendingPushToken: String? = null
}
