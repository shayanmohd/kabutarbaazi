package com.kabutarbaazi.app.data

import com.kabutarbaazi.domain.model.Profile
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class ProfileRepository(private val client: SupabaseClient) {

    suspend fun byId(id: String): Profile? =
        client.from("profiles").select { filter { eq("id", id) } }.decodeSingleOrNull()

    suspend fun byUsername(username: String): Profile? =
        client.from("profiles").select { filter { eq("username", username) } }.decodeSingleOrNull()

    suspend fun update(
        id: String,
        displayName: String,
        bio: String?,
        city: String?,
        regionCode: String?,
        avatarUrl: String?,
    ) {
        // Only these columns are grantable to `authenticated`; role and is_suspended are
        // revoked at the column level so this cannot be turned into a privilege escalation.
        client.from("profiles").update({
            set("display_name", displayName)
            set("bio", bio)
            set("city", city)
            set("region_code", regionCode)
            avatarUrl?.let { set("avatar_url", it) }
        }) { filter { eq("id", id) } }
    }

    suspend fun setLocale(id: String, locale: String) {
        // Mirrored onto the profile purely so push notifications can be localised server-side:
        // below API 33 the per-app locale does not reach a background service.
        client.from("profiles").update({ set("locale", locale) }) { filter { eq("id", id) } }
    }

    suspend fun followingIds(userId: String): Set<String> =
        client.from("follows").select(Columns.raw("followee_id")) {
            filter { eq("follower_id", userId) }
        }.decodeList<FollowRow>().map { it.followeeId }.toSet()

    suspend fun setFollowing(userId: String, targetId: String, following: Boolean) {
        if (following) {
            client.from("follows")
                .insert(buildJsonObject { put("follower_id", userId); put("followee_id", targetId) })
        } else {
            client.from("follows").delete {
                filter { eq("follower_id", userId); eq("followee_id", targetId) }
            }
        }
    }

    suspend fun followers(userId: String): List<Profile> = related(userId, "followee_id", "follower_id")
    suspend fun following(userId: String): List<Profile> = related(userId, "follower_id", "followee_id")

    private suspend fun related(userId: String, matchCol: String, pickCol: String): List<Profile> {
        val ids = client.from("follows").select(Columns.raw(pickCol)) {
            filter { eq(matchCol, userId) }
        }.decodeList<Map<String, String>>().mapNotNull { it[pickCol] }
        if (ids.isEmpty()) return emptyList()
        return client.from("profiles").select { filter { isIn("id", ids) } }.decodeList()
    }

    @Serializable
    private data class FollowRow(@SerialName("followee_id") val followeeId: String)
}
