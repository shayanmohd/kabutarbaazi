package com.kabutarbaazi.app.data

import com.kabutarbaazi.domain.model.NewReel
import com.kabutarbaazi.domain.model.Reel
import com.kabutarbaazi.domain.model.ReelComment
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class ReelRepository(private val client: SupabaseClient) {

    private val withAuthor = Columns.raw("*, author:profiles!reels_author_id_fkey(*)")

    suspend fun feed(before: String? = null, limit: Int = 10): List<Reel> =
        client.from("reels").select(withAuthor) {
            filter { before?.let { lt("created_at", it) } }
            order("created_at", Order.DESCENDING)
            limit(limit.toLong())
        }.decodeList()

    /**
     * Reels from people the caller follows. Done as an RPC rather than by sending a list of
     * followed ids from the client, which breaks once someone follows a few hundred people.
     */
    suspend fun following(before: String? = null, limit: Int = 10): List<Reel> =
        client.postgrest.rpc(
            "feed_following",
            buildJsonObject {
                before?.let { put("before", it) }
                put("lim", limit)
            },
        ).decodeList()

    suspend fun byAuthor(authorId: String): List<Reel> =
        client.from("reels").select(withAuthor) {
            filter { eq("author_id", authorId) }
            order("created_at", Order.DESCENDING)
        }.decodeList()

    suspend fun create(reel: NewReel): Reel =
        client.from("reels").insert(reel) { select(Columns.ALL) }.decodeSingle()

    suspend fun delete(id: String) {
        client.from("reels").delete { filter { eq("id", id) } }
    }

    suspend fun likedIds(userId: String): Set<String> =
        client.from("reel_likes").select(Columns.raw("reel_id")) {
            filter { eq("user_id", userId) }
        }.decodeList<LikeRow>().map { it.reelId }.toSet()

    suspend fun setLiked(userId: String, reelId: String, liked: Boolean) {
        if (liked) {
            client.from("reel_likes")
                .insert(buildJsonObject { put("user_id", userId); put("reel_id", reelId) })
        } else {
            client.from("reel_likes").delete {
                filter { eq("user_id", userId); eq("reel_id", reelId) }
            }
        }
    }

    suspend fun comments(reelId: String): List<ReelComment> =
        client.from("reel_comments")
            .select(Columns.raw("*, author:profiles!reel_comments_author_id_fkey(*)")) {
                filter { eq("reel_id", reelId) }
                order("created_at", Order.DESCENDING)
            }.decodeList()

    suspend fun addComment(reelId: String, authorId: String, body: String) {
        client.from("reel_comments").insert(
            buildJsonObject { put("reel_id", reelId); put("author_id", authorId); put("body", body) },
        )
    }

    suspend fun deleteComment(id: String) {
        client.from("reel_comments").delete { filter { eq("id", id) } }
    }

    @Serializable
    private data class LikeRow(@SerialName("reel_id") val reelId: String)
}
