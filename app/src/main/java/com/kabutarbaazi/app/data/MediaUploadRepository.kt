package com.kabutarbaazi.app.data

import com.kabutarbaazi.domain.media.MediaConstraints
import com.kabutarbaazi.domain.media.MediaKind
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.functions.functions
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.onUpload
import io.ktor.client.request.header
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.io.File

/**
 * Uploads go straight to Cloudflare R2 with a short-lived presigned URL minted by an Edge
 * Function. The R2 credentials never reach the app.
 *
 * The flow is deliberately: compress locally, ask for a URL, PUT the bytes, then insert the row.
 * Media must exist before the database row that points at it, or a feed can render a broken image.
 */
class MediaUploadRepository(private val client: SupabaseClient) {

    private val http = HttpClient(OkHttp)

    @Serializable
    private data class PresignRequest(
        val kind: String,
        val uploadId: String,
        val index: Int,
        val contentType: String,
        val bytes: Long,
    )

    @Serializable
    private data class PresignResponse(
        val putUrl: String,
        val publicUrl: String,
        val key: String,
        @SerialName("expiresAt") val expiresAt: String? = null,
    )

    private fun wire(kind: MediaKind) = when (kind) {
        MediaKind.ReelVideo -> "reel_video"
        MediaKind.ListingVideo -> "listing_video"
        MediaKind.ListingImage -> "listing_image"
        MediaKind.PostImage -> "post_image"
        MediaKind.Avatar -> "avatar"
        MediaKind.Thumbnail -> "thumbnail"
    }

    /**
     * @param onProgress fraction 0..1, so the upload sheet can show real progress rather than an
     *   indeterminate spinner on a connection where an 8 MB video takes a while.
     * @return the public URL the row should store.
     */
    suspend fun upload(
        file: File,
        kind: MediaKind,
        uploadId: String,
        index: Int = 0,
        onProgress: (Float) -> Unit = {},
    ): String {
        val bytes = file.length()
        MediaConstraints.check(kind, bytes)?.let { error("media_rejected:$it") }

        val contentType = MediaConstraints.allowedContentType(kind)

        val presign: PresignResponse = client.functions.invoke(
            function = "media-upload-url",
            body = PresignRequest(wire(kind), uploadId, index, contentType, bytes),
        ).body()

        val response = http.put(presign.putUrl) {
            contentType(ContentType.parse(contentType))
            // Content-Length and Content-Type are part of the signature the server produced, so
            // they must match exactly or R2 rejects the PUT with an opaque 403.
            header("Cache-Control", "public, max-age=31536000, immutable")
            setBody(file.readBytes())
            onUpload { sent, total ->
                if (total != null && total > 0) onProgress(sent.toFloat() / total.toFloat())
            }
        }

        if (!response.status.isSuccess()) {
            error("upload_failed:${response.status.value}:${response.bodyAsText().take(200)}")
        }
        onProgress(1f)
        return presign.publicUrl
    }
}
