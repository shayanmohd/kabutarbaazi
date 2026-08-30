package com.kabutarbaazi.app.data

import com.google.firebase.messaging.FirebaseMessaging
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class PushTokenRepository(private val client: SupabaseClient) {

    suspend fun register(userId: String, token: String, locale: String) {
        client.from("device_tokens").upsert(
            buildJsonObject {
                put("token", token)
                put("user_id", userId)
                put("platform", "android")
                put("locale", locale)
            },
        )
    }

    /**
     * Called before sign-out and on account deletion. Leaving the row behind means the next
     * person to sign in on this handset receives the previous user's chat notifications, which
     * is a privacy incident rather than a bug.
     */
    suspend fun unregister(token: String) {
        client.from("device_tokens").delete { filter { eq("token", token) } }
    }

    /**
     * Registers this handset against the signed-in user.
     *
     * FirebaseMessagingService.onNewToken only fires when FCM actually mints a token, which on a
     * fresh install happens once, before anyone has signed in. Relying on it alone meant the
     * token was stashed and never claimed, device_tokens stayed empty, and no chat push was ever
     * delivered to anybody. Reading the current token at sign-in is what makes push work at all.
     */
    suspend fun syncForUser(userId: String, locale: String) {
        val token = runCatching { currentToken() }.getOrNull() ?: return
        runCatching { register(userId, token, locale) }
    }

    /** Drops this handset's row. Used on sign-out, where the token itself is still available. */
    suspend fun unregisterCurrent() {
        val token = runCatching { currentToken() }.getOrNull() ?: return
        runCatching { unregister(token) }
    }

    // Wrapped by hand rather than pulling in kotlinx-coroutines-play-services for one call.
    private suspend fun currentToken(): String = suspendCancellableCoroutine { cont ->
        FirebaseMessaging.getInstance().token
            .addOnSuccessListener { cont.resumeWith(Result.success(it)) }
            .addOnFailureListener { cont.resumeWith(Result.failure(it)) }
    }
}
