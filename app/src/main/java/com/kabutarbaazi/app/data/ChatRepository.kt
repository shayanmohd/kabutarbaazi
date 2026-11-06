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
import io.github.jan.supabase.postgrest.query.filter.FilterOperator
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.realtime
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.util.UUID

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

    /** Returns the stored row, so the sender's bubble settles without waiting for Realtime. */
    suspend fun send(conversationId: String, senderId: String, body: String, clientId: String): Message =
        client.from("messages").insert(
            NewMessage(
                conversationId = conversationId,
                senderId = senderId,
                body = body,
                clientId = clientId,
            ),
        ) { select() }.decodeSingle()

    suspend fun conversation(conversationId: String): Conversation? =
        client.from("conversations").select { filter { eq("id", conversationId) } }.decodeSingleOrNull()

    suspend fun markRead(conversationId: String) {
        client.postgrest.rpc(
            "mark_conversation_read",
            buildJsonObject { put("conv", conversationId) },
        )
    }

    /**
     * Live inserts for one open thread, for as long as the caller collects.
     *
     * Order matters. The filter is registered on the channel first, because the join message is
     * what tells the server which rows to stream, and supabase-kt refuses postgresChangeFlow on a
     * channel that has already joined. Only then is the channel subscribed.
     *
     * Every collection gets its own channel name and removes that channel when cancelled.
     * supabase-kt hands back the existing channel for a name it already knows, so a shared name
     * would give a reopened thread the old, already joined channel, and the old screen's leave
     * could tear down the new screen's subscription.
     */
    fun liveMessages(conversationId: String): Flow<Message> = flow {
        val realtime = client.realtime
        val channel = realtime.channel("chat:$conversationId:${UUID.randomUUID()}")
        val inserts = channel.postgresChangeFlow<PostgresAction.Insert>(schema = "public") {
            table = "messages"
            filter("conversation_id", FilterOperator.EQ, conversationId)
        }
        try {
            channel.subscribe()
            emitAll(inserts.map { SupabaseFactory.json.decodeFromJsonElement(Message.serializer(), it.record) })
        } finally {
            withContext(NonCancellable) { realtime.removeChannel(channel) }
        }
    }
}
