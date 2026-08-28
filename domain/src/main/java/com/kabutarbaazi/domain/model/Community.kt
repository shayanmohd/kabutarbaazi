package com.kabutarbaazi.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Community(
    val id: String,
    @SerialName("region_code") val regionCode: String,
    val slug: String,
    @SerialName("name_en") val nameEn: String,
    @SerialName("name_hi") val nameHi: String,
    @SerialName("name_ur") val nameUr: String,
    @SerialName("member_count") val memberCount: Int = 0,
    @SerialName("post_count") val postCount: Int = 0,
    @SerialName("cover_url") val coverUrl: String? = null,
) {
    fun name(language: String): String = when (language) {
        "hi" -> nameHi
        "ur" -> nameUr
        else -> nameEn
    }
}

@Serializable
data class CommunityPost(
    val id: String,
    @SerialName("community_id") val communityId: String,
    @SerialName("author_id") val authorId: String,
    val body: String,
    @SerialName("like_count") val likeCount: Int = 0,
    @SerialName("comment_count") val commentCount: Int = 0,
    @SerialName("is_hidden") val isHidden: Boolean = false,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("community_post_media") val media: List<PostMedia> = emptyList(),
    val author: Profile? = null,
)

@Serializable
data class PostMedia(
    val id: String,
    @SerialName("post_id") val postId: String,
    val kind: String,
    val url: String,
    @SerialName("thumb_url") val thumbUrl: String? = null,
    val position: Int = 0,
)

@Serializable
data class PostComment(
    val id: String,
    @SerialName("post_id") val postId: String,
    @SerialName("author_id") val authorId: String,
    val body: String,
    @SerialName("created_at") val createdAt: String? = null,
    val author: Profile? = null,
)
