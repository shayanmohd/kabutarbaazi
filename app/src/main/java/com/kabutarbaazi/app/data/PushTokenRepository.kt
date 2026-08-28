package com.kabutarbaazi.app.data

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
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
}
