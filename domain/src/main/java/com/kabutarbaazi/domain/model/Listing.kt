package com.kabutarbaazi.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Listing(
    val id: String,
    @SerialName("seller_id") val sellerId: String,
    val title: String,
    val description: String? = null,
    @SerialName("breed_id") val breedId: Int? = null,
    @SerialName("breed_other") val breedOther: String? = null,
    @SerialName("price_minor") val priceMinor: Long,
    val currency: String = "INR",
    @SerialName("is_negotiable") val isNegotiable: Boolean = true,
    val quantity: Int = 1,
    @SerialName("region_code") val regionCode: String,
    val city: String? = null,
    val status: String = "active",
    @SerialName("is_hidden") val isHidden: Boolean = false,
    @SerialName("saved_count") val savedCount: Int = 0,
    @SerialName("view_count") val viewCount: Int = 0,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("bumped_at") val bumpedAt: String? = null,
    // Populated by PostgREST embedding, not columns on the row.
    @SerialName("listing_media") val media: List<ListingMedia> = emptyList(),
    val seller: Profile? = null,
) {
    val isSold: Boolean get() = status == "sold"

    /**
     * A thumbnail only means something for a video, where the poster frame is a different file.
     * For a photo the photo is its own thumbnail, and preferring thumb_url there just adds a
     * second copy of the same URL that has to be kept in step. Reading url directly for images
     * means a stale thumb_url can never outrank the picture the listing actually points at.
     */
    val coverUrl: String? get() = media.minByOrNull { it.position }?.displayUrl
}

@Serializable
data class ListingMedia(
    val id: String,
    @SerialName("listing_id") val listingId: String,
    val kind: String,
    val url: String,
    @SerialName("thumb_url") val thumbUrl: String? = null,
    val width: Int? = null,
    val height: Int? = null,
    @SerialName("duration_ms") val durationMs: Int? = null,
    val position: Int = 0,
) {
    val isVideo: Boolean get() = kind == "video"

    /** What to actually render for this item. See Listing.coverUrl for why images ignore thumbUrl. */
    val displayUrl: String get() = if (isVideo) thumbUrl ?: url else url
}

/** What the create-listing form produces. Server-defaulted columns are deliberately absent. */
@Serializable
data class NewListing(
    @SerialName("seller_id") val sellerId: String,
    val title: String,
    val description: String?,
    @SerialName("breed_id") val breedId: Int?,
    @SerialName("breed_other") val breedOther: String?,
    @SerialName("price_minor") val priceMinor: Long,
    val currency: String,
    @SerialName("is_negotiable") val isNegotiable: Boolean,
    val quantity: Int,
    @SerialName("region_code") val regionCode: String,
    val city: String?,
)
