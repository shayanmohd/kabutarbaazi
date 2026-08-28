package com.kabutarbaazi.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * A public profile. The phone number is deliberately absent: it lives in a separate table that
 * row-level security keeps private, and is reachable only through the get_seller_whatsapp RPC.
 */
@Serializable
data class Profile(
    val id: String,
    val username: String,
    @SerialName("display_name") val displayName: String,
    @SerialName("avatar_url") val avatarUrl: String? = null,
    val bio: String? = null,
    @SerialName("region_code") val regionCode: String? = null,
    val city: String? = null,
    val locale: String = "hi",
    val role: String = "user",
    @SerialName("terms_accepted_at") val termsAcceptedAt: String? = null,
    @SerialName("follower_count") val followerCount: Int = 0,
    @SerialName("following_count") val followingCount: Int = 0,
    @SerialName("is_suspended") val isSuspended: Boolean = false,
    @SerialName("created_at") val createdAt: String? = null,
) {
    val isAdmin: Boolean get() = role == "admin"
    val hasAcceptedTerms: Boolean get() = termsAcceptedAt != null
}
