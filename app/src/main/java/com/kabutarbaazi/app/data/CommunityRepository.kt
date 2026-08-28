package com.kabutarbaazi.app.data

import com.kabutarbaazi.domain.model.Community
import com.kabutarbaazi.domain.model.CommunityPost
import com.kabutarbaazi.domain.model.PostComment
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class CommunityRepository(private val client: SupabaseClient) {

    private val postColumns = Columns.raw(
        "*, community_post_media(*), author:profiles!community_posts_author_id_fkey(*)"
    )

    suspend fun all(): List<Community> =
        client.from("communities").select {
            filter { eq("is_active", true) }
        }.decodeList<Community>().sortedByDescending { it.memberCount }

    suspend fun joinedIds(userId: String): Set<String> =
        client.from("community_members").select(Columns.raw("community_id")) {
            filter { eq("user_id", userId) }
        }.decodeList<MemberRow>().map { it.communityId }.toSet()

    suspend fun setJoined(userId: String, communityId: String, joined: Boolean) {
        if (joined) {
            client.from("community_members")
                .insert(buildJsonObject { put("user_id", userId); put("community_id", communityId) })
        } else {
            client.from("community_members").delete {
                filter { eq("user_id", userId); eq("community_id", communityId) }
            }
        }
    }

    suspend fun feed(communityId: String, before: String? = null, limit: Int = 20): List<CommunityPost> =
        client.from("community_posts").select(postColumns) {
            filter {
                eq("community_id", communityId)
                before?.let { lt("created_at", it) }
            }
            order("created_at", Order.DESCENDING)
            limit(limit.toLong())
        }.decodeList()

    suspend fun post(id: String): CommunityPost? =
        client.from("community_posts").select(postColumns) { filter { eq("id", id) } }.decodeSingleOrNull()

    suspend fun createPost(communityId: String, authorId: String, body: String): CommunityPost =
        client.from("community_posts").insert(
            buildJsonObject {
                put("community_id", communityId); put("author_id", authorId); put("body", body)
            },
        ) { select(Columns.ALL) }.decodeSingle()

    suspend fun deletePost(id: String) {
        client.from("community_posts").delete { filter { eq("id", id) } }
    }

    suspend fun comments(postId: String): List<PostComment> =
        client.from("community_post_comments")
            .select(Columns.raw("*, author:profiles!community_post_comments_author_id_fkey(*)")) {
                filter { eq("post_id", postId) }
                order("created_at", Order.ASCENDING)
            }.decodeList()

    suspend fun addComment(postId: String, authorId: String, body: String) {
        client.from("community_post_comments").insert(
            buildJsonObject { put("post_id", postId); put("author_id", authorId); put("body", body) },
        )
    }

    suspend fun likedPostIds(userId: String): Set<String> =
        client.from("community_post_likes").select(Columns.raw("post_id")) {
            filter { eq("user_id", userId) }
        }.decodeList<PostLikeRow>().map { it.postId }.toSet()

    suspend fun setPostLiked(userId: String, postId: String, liked: Boolean) {
        if (liked) {
            client.from("community_post_likes")
                .insert(buildJsonObject { put("user_id", userId); put("post_id", postId) })
        } else {
            client.from("community_post_likes").delete {
                filter { eq("user_id", userId); eq("post_id", postId) }
            }
        }
    }

    @Serializable
    private data class MemberRow(@SerialName("community_id") val communityId: String)

    @Serializable
    private data class PostLikeRow(@SerialName("post_id") val postId: String)
}
