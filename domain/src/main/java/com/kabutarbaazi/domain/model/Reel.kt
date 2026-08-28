package com.kabutarbaazi.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Reel(
    val id: String,
    @SerialName("author_id") val authorId: String,
    val caption: String? = null,
    @SerialName("video_url") val videoUrl: String,
    @SerialName("thumb_url") val thumbUrl: String,
    val width: Int,
    val height: Int,
    @SerialName("duration_ms") val durationMs: Int,
    @SerialName("like_count") val likeCount: Int = 0,
    @SerialName("comment_count") val commentCount: Int = 0,
    @SerialName("view_count") val viewCount: Int = 0,
    @SerialName("region_code") val regionCode: String? = null,
    @SerialName("is_hidden") val isHidden: Boolean = false,
    @SerialName("created_at") val createdAt: String? = null,
    val author: Profile? = null,
)

@Serializable
data class ReelComment(
    val id: String,
    @SerialName("reel_id") val reelId: String,
    @SerialName("author_id") val authorId: String,
    val body: String,
    @SerialName("created_at") val createdAt: String? = null,
    val author: Profile? = null,
)

@Serializable
data class NewReel(
    @SerialName("author_id") val authorId: String,
    val caption: String?,
    @SerialName("video_url") val videoUrl: String,
    @SerialName("thumb_url") val thumbUrl: String,
    val width: Int,
    val height: Int,
    @SerialName("duration_ms") val durationMs: Int,
    @SerialName("region_code") val regionCode: String?,
)
