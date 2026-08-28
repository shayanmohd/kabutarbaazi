package com.kabutarbaazi.app.data

import com.kabutarbaazi.domain.model.Conversation
import com.kabutarbaazi.domain.model.Message
import com.kabutarbaazi.domain.model.NewMessage
import com.kabutarbaazi.domain.model.Profile
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.realtime
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class ChatRepository(private val client: SupabaseClient) {

    suspend fun conversations(): List<Conversation> =
        client.from("conversations").select {
            order("last_message_at", Order.DESCENDING)
        }.decodeList()

    /** Profiles for the other side of each conversation, fetched in one request. */
    suspend fun counterparts(ids: Collection<String>): Map<String, Profile> {
        if (ids.isEmpty()) return emptyMap()
        return client.from("profiles").select { filter { isIn("id", ids.toList()) } }
            .decodeList<Profile>().associateBy { it.id }
    }

    /** Idempotent: two clients racing to open the same thread both get the same row. */
    suspend fun startConversation(otherUserId: String, listingId: String? = null): String =
        client.postgrest.rpc(
            "start_conversation",
            buildJsonObject {
                put("other", otherUserId)
                listingId?.let { put("listing", it) }
            },
        ).decodeAs()

    suspend fun messages(conversationId: String, limit: Int = 50): List<Message> =
        client.from("messages").select {
            filter { eq("conversation_id", conversationId) }
            order("created_at", Order.DESCENDING)
            limit(limit.toLong())
        }.decodeList<Message>().reversed()

    /**
     * Backfill after a socket reconnect. Realtime does not replay events missed while the
     * connection was down, so this is load-bearing rather than belt-and-braces: without it a
     * message that arrives during a tunnel is simply never seen.
     */
    suspend fun messagesSince(conversationId: String, afterIso: String): List<Message> =
        client.from("messages").select {
            filter { eq("conversation_id", conversationId); gt("created_at", afterIso) }
            order("created_at", Order.ASCENDING)
        }.decodeList()

    suspend fun send(conversationId: String, senderId: String, body: String, clientId: String) {
        client.from("messages").insert(
            NewMessage(
                conversationId = conversationId,
                senderId = senderId,
                body = body,
                clientId = clientId,
            ),
        )
    }

    suspend fun markRead(conversationId: String) {
        client.postgrest.rpc(
            "mark_conversation_read",
            buildJsonObject { put("conv", conversationId) },
        )
    }

    /**
     * Live inserts for one open thread. The caller must collect this inside the screen's scope;
     * unsubscribing happens when that scope is cancelled.
     */
    fun liveMessages(conversationId: String): Flow<Message> {
        val channel = client.realtime.channel("chat:$conversationId")
        return channel.postgresChangeFlow<PostgresAction.Insert>(schema = "public") {
            table = "messages"
            filter("conversation_id", io.github.jan.supabase.postgrest.query.filter.FilterOperator.EQ, conversationId)
        }.map { SupabaseFactory.json.decodeFromJsonElement(Message.serializer(), it.record) }
    }

    suspend fun subscribe(conversationId: String) {
        client.realtime.channel("chat:$conversationId").subscribe()
    }

    suspend fun unsubscribe(conversationId: String) {
        client.realtime.channel("chat:$conversationId").unsubscribe()
    }
}
