package com.kabutarbaazi.app.data

import com.kabutarbaazi.domain.model.Profile
import com.kabutarbaazi.domain.model.ReportReason
import com.kabutarbaazi.domain.model.ReportTargetType
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Reporting, blocking and the admin queue. Google Play requires all three for an app carrying
 * user-generated content, and requires that reports are acted on rather than merely collected.
 */
class ModerationRepository(private val client: SupabaseClient) {

    suspend fun report(
        targetType: ReportTargetType,
        targetId: String,
        reason: ReportReason,
        note: String? = null,
    ) {
        client.postgrest.rpc(
            "submit_report",
            buildJsonObject {
                put("p_target_type", targetType.wire)
                put("p_target_id", targetId)
                put("p_reason", reason.wire)
                note?.takeIf { it.isNotBlank() }?.let { put("p_note", it) }
            },
        )
    }

    suspend fun block(userId: String, blockedId: String) {
        client.from("blocks")
            .insert(buildJsonObject { put("blocker_id", userId); put("blocked_id", blockedId) })
    }

    suspend fun unblock(userId: String, blockedId: String) {
        client.from("blocks").delete {
            filter { eq("blocker_id", userId); eq("blocked_id", blockedId) }
        }
    }

    suspend fun blockedProfiles(userId: String): List<Profile> {
        val ids = client.from("blocks").select(Columns.raw("blocked_id")) {
            filter { eq("blocker_id", userId) }
        }.decodeList<BlockRow>().map { it.blockedId }
        if (ids.isEmpty()) return emptyList()
        // Blocked profiles are invisible under the normal policy, so they are fetched by id
        // through the blocker's own block rows.
        return client.from("profiles").select { filter { isIn("id", ids) } }.decodeList()
    }

    // -- admin ------------------------------------------------------------------------------

    suspend fun openReports(): List<ReportRow> =
        client.from("reports").select(Columns.ALL) {
            filter { eq("status", "open") }
            order("created_at", Order.DESCENDING)
            limit(100)
        }.decodeList()

    suspend fun setHidden(
        targetType: ReportTargetType,
        targetId: String,
        hidden: Boolean,
        reason: String? = null,
    ) {
        client.postgrest.rpc(
            "admin_set_hidden",
            buildJsonObject {
                put("p_target_type", targetType.wire)
                put("p_target_id", targetId)
                put("p_hidden", hidden)
                reason?.let { put("p_reason", it) }
            },
        )
    }

    suspend fun suspendUser(userId: String, suspended: Boolean) {
        client.postgrest.rpc(
            "admin_suspend_user",
            buildJsonObject { put("p_user", userId); put("p_suspended", suspended) },
        )
    }

    suspend fun resolve(reportId: String, status: String) {
        client.postgrest.rpc(
            "resolve_report",
            buildJsonObject { put("p_report", reportId); put("p_status", status) },
        )
    }

    @Serializable
    private data class BlockRow(@SerialName("blocked_id") val blockedId: String)

    @Serializable
    data class ReportRow(
        val id: String,
        @SerialName("target_type") val targetType: String,
        @SerialName("target_id") val targetId: String,
        @SerialName("target_owner_username") val targetOwnerUsername: String? = null,
        val reason: String,
        val note: String? = null,
        val status: String,
        @SerialName("created_at") val createdAt: String,
    )
}
