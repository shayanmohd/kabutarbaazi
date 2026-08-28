package com.kabutarbaazi.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Conversation(
    val id: String,
    @SerialName("user_a") val userA: String,
    @SerialName("user_b") val userB: String,
    @SerialName("listing_id") val listingId: String? = null,
    @SerialName("last_message_at") val lastMessageAt: String,
    @SerialName("last_message_preview") val lastMessagePreview: String? = null,
    @SerialName("last_sender_id") val lastSenderId: String? = null,
    @SerialName("a_last_read_at") val aLastReadAt: String,
    @SerialName("b_last_read_at") val bLastReadAt: String,
)

@Serializable
data class Message(
    val id: String,
    @SerialName("conversation_id") val conversationId: String,
    @SerialName("sender_id") val senderId: String,
    val body: String,
    @SerialName("client_id") val clientId: String,
    @SerialName("created_at") val createdAt: String,
)

/**
 * The optimistic send envelope. [clientId] is minted before the insert so a retry is idempotent
 * at the database level and the Realtime echo of our own message can be deduped.
 */
data class PendingMessage(
    val clientId: String,
    val body: String,
    val status: SendStatus,
)

enum class SendStatus { Sending, Sent, Failed }

@Serializable
data class NewMessage(
    @SerialName("conversation_id") val conversationId: String,
    @SerialName("sender_id") val senderId: String,
    val body: String,
    @SerialName("client_id") val clientId: String,
)
