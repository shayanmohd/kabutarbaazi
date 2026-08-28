package com.kabutarbaazi.app.data

import com.kabutarbaazi.domain.auth.UsernameRules
import com.kabutarbaazi.domain.model.Profile
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.functions.functions
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Username and password on top of Supabase Auth, which only speaks email.
 *
 * A synthetic address is derived from the normalised username. The user never sees it, never
 * types it, and is never told it exists.
 */
class SessionRepository(private val client: SupabaseClient) {

    val sessionStatus: Flow<Boolean> =
        client.auth.sessionStatus.map { it is SessionStatus.Authenticated }

    fun currentUserId(): String? = client.auth.currentUserOrNull()?.id

    suspend fun signUp(
        username: String,
        password: String,
        phoneE164: String,
        displayName: String,
        regionCode: String?,
        locale: String,
    ) {
        val normalized = UsernameRules.normalize(username)
        require(UsernameRules.isValid(normalized)) { "invalid_username" }

        client.auth.signUpWith(Email) {
            email = UsernameRules.syntheticEmail(normalized)
            this.password = password
            // The handle_new_user trigger reads these and creates the profile and private
            // contact row inside the same transaction as the auth user, so a half-created
            // account cannot exist.
            data = buildJsonObject {
                put("username", normalized)
                put("display_name", displayName.ifBlank { normalized })
                put("phone_e164", phoneE164)
                regionCode?.let { put("region_code", it) }
                put("locale", locale)
            }
        }
    }

    suspend fun signIn(username: String, password: String) {
        val normalized = UsernameRules.normalize(username)
        require(UsernameRules.isValid(normalized)) { "invalid_username" }
        client.auth.signInWith(Email) {
            email = UsernameRules.syntheticEmail(normalized)
            this.password = password
        }
    }

    suspend fun isUsernameAvailable(username: String): Boolean {
        val normalized = UsernameRules.normalize(username)
        if (!UsernameRules.isValid(normalized)) return false
        return client.postgrest.rpc(
            "username_available",
            buildJsonObject { put("u", normalized) },
        ).decodeAs()
    }

    suspend fun currentProfile(): Profile? {
        val id = currentUserId() ?: return null
        return client.from("profiles").select {
            filter { eq("id", id) }
        }.decodeSingleOrNull()
    }

    suspend fun acceptTerms() {
        client.postgrest.rpc("accept_terms")
    }

    suspend fun changePassword(newPassword: String) {
        client.auth.updateUser { password = newPassword }
    }

    /**
     * Deletes the account and everything it owns, including every media object under the user's
     * R2 prefix. Reports filed AGAINST the account are deliberately preserved with only a
     * username snapshot, so deleting an account cannot erase the evidence of abuse.
     *
     * Google Play requires both this in-app path and a publicly reachable web URL.
     */
    suspend fun deleteAccount() {
        client.functions.invoke("delete-account")
        client.auth.signOut()
    }

    /**
     * Clears the push token before signing out. Skipping this means the next person to sign in
     * on the same handset receives the previous user's chat notifications, which is a privacy
     * incident rather than a bug.
     */
    suspend fun signOut(onBeforeSignOut: suspend () -> Unit = {}) {
        runCatching { onBeforeSignOut() }
        client.auth.signOut()
    }
}
