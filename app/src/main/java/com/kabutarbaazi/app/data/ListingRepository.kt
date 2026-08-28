package com.kabutarbaazi.app.data

import com.kabutarbaazi.domain.model.Listing
import com.kabutarbaazi.domain.model.ListingMedia
import com.kabutarbaazi.domain.model.NewListing
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.postgrest.query.filter.TextSearchType
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class ListingRepository(private val client: SupabaseClient) {

    // Embeds media and seller in one round trip. Fetching them separately would mean three
    // requests per feed page on a connection that is the scarcest resource these users have.
    private val withRelations = Columns.raw(
        "*, listing_media(*), seller:profiles!listings_seller_id_fkey(*)"
    )

    /**
     * Keyset pagination on bumped_at. OFFSET would duplicate rows as new listings arrive
     * between pages, which is exactly what happens on an active marketplace.
     */
    suspend fun feed(
        before: String? = null,
        regionCode: String? = null,
        breedId: Int? = null,
        query: String? = null,
        limit: Int = 20,
    ): List<Listing> = client.from("listings").select(withRelations) {
        filter {
            eq("status", "active")
            regionCode?.let { eq("region_code", it) }
            breedId?.let { eq("breed_id", it) }
            before?.let { lt("bumped_at", it) }
            query?.takeIf { it.isNotBlank() }?.let {
                textSearch("search_tsv", it.trim().split(" ").joinToString(" & "), TextSearchType.NONE)
            }
        }
        order("bumped_at", Order.DESCENDING)
        limit(limit.toLong())
    }.decodeList()

    suspend fun byId(id: String): Listing? =
        client.from("listings").select(withRelations) { filter { eq("id", id) } }.decodeSingleOrNull()

    suspend fun bySeller(sellerId: String): List<Listing> =
        client.from("listings").select(withRelations) {
            filter { eq("seller_id", sellerId) }
            order("created_at", Order.DESCENDING)
        }.decodeList()

    suspend fun create(listing: NewListing): Listing =
        client.from("listings").insert(listing) { select(Columns.ALL) }.decodeSingle()

    suspend fun attachMedia(media: List<ListingMedia>) {
        if (media.isNotEmpty()) client.from("listing_media").insert(media)
    }

    suspend fun markSold(id: String) {
        client.from("listings").update({ set("status", "sold") }) { filter { eq("id", id) } }
    }

    suspend fun delete(id: String) {
        client.from("listings").delete { filter { eq("id", id) } }
    }

    // -- saved ------------------------------------------------------------------------------

    suspend fun saved(userId: String): List<Listing> {
        val ids = client.from("saved_listings").select(Columns.raw("listing_id")) {
            filter { eq("user_id", userId) }
            order("created_at", Order.DESCENDING)
        }.decodeList<SavedRow>().map { it.listingId }
        if (ids.isEmpty()) return emptyList()
        return client.from("listings").select(withRelations) {
            filter { isIn("id", ids) }
        }.decodeList()
    }

    suspend fun savedIds(userId: String): Set<String> =
        client.from("saved_listings").select(Columns.raw("listing_id")) {
            filter { eq("user_id", userId) }
        }.decodeList<SavedRow>().map { it.listingId }.toSet()

    suspend fun setSaved(userId: String, listingId: String, saved: Boolean) {
        if (saved) {
            client.from("saved_listings")
                .insert(buildJsonObject { put("user_id", userId); put("listing_id", listingId) })
        } else {
            client.from("saved_listings").delete {
                filter { eq("user_id", userId); eq("listing_id", listingId) }
            }
        }
    }

    /**
     * The only route to a seller's phone number. The RPC checks the listing is visible, that
     * neither party has blocked the other, rate-limits to 30 reveals a day, and logs the access.
     */
    suspend fun revealWhatsApp(listingId: String): String =
        client.postgrest.rpc(
            "get_seller_whatsapp",
            buildJsonObject { put("listing", listingId) },
        ).decodeAs()

    @kotlinx.serialization.Serializable
    private data class SavedRow(
        @kotlinx.serialization.SerialName("listing_id") val listingId: String,
    )
}
