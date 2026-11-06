package com.kabutarbaazi.app.testing

import com.kabutarbaazi.app.data.SupabaseFactory
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.annotations.SupabaseInternal
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.MemoryCodeVerifierCache
import io.github.jan.supabase.auth.MemorySessionManager
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.user.UserInfo
import io.github.jan.supabase.auth.user.UserSession
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.realtime.RealtimeMessage
import io.github.jan.supabase.realtime.websocket.RealtimeWebsocket
import io.github.jan.supabase.realtime.websocket.RealtimeWebsocketFactory
import io.github.jan.supabase.serializer.KotlinXSerializer
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.headersOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import java.util.Base64
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicLong

/** One HTTP call the app made, with its body decoded to text. */
data class Call(val method: HttpMethod, val path: String, val query: String, val body: String)

/**
 * The real supabase-kt client, signed in as [userId], talking to a Ktor MockEngine for REST and
 * to [FakeRealtimeServer] for the websocket. Tests answer REST calls through [rest].
 */
@OptIn(SupabaseInternal::class)
class FakeSupabase(val userId: String) {

    val calls = CopyOnWriteArrayList<Call>()
    val realtime = FakeRealtimeServer()

    /** Return the JSON body for a call, or null for 404. */
    @Volatile
    var rest: (Call) -> String? = { "[]" }

    val client: SupabaseClient = createSupabaseClient("https://test.supabase.co", "anon-key") {
        httpEngine = MockEngine { request -> answer(request) }
        defaultSerializer = KotlinXSerializer(SupabaseFactory.json)
        install(Auth) {
            sessionManager = MemorySessionManager()
            codeVerifierCache = MemoryCodeVerifierCache()
            enableLifecycleCallbacks = false
            autoLoadFromStorage = false
            alwaysAutoRefresh = false
        }
        install(Postgrest)
        install(Realtime) { websocketFactory = realtime.factory }
    }

    init {
        runBlocking {
            client.auth.importSession(
                UserSession(
                    accessToken = fakeJwt(userId),
                    refreshToken = "refresh",
                    expiresIn = 3600,
                    tokenType = "bearer",
                    user = UserInfo(aud = "authenticated", id = userId),
                ),
                autoRefresh = false,
            )
        }
    }

    private suspend fun MockRequestHandleScope.answer(request: HttpRequestData): HttpResponseData {
        val body = when (val c = request.body) {
            is OutgoingContent.ByteArrayContent -> c.bytes().decodeToString()
            else -> ""
        }
        val call = Call(request.method, request.url.encodedPath, request.url.encodedQuery, body)
        calls += call
        val json = rest(call)
        return if (json == null) {
            respond("{}", HttpStatusCode.NotFound, headersOf(HttpHeaders.ContentType, "application/json"))
        } else {
            respond(json, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
        }
    }

    private fun fakeJwt(sub: String): String {
        val enc = Base64.getUrlEncoder().withoutPadding()
        val header = enc.encodeToString("""{"alg":"HS256","typ":"JWT"}""".toByteArray())
        val payload = Base64.getEncoder()
            .encodeToString("""{"sub":"$sub","exp":4102444800,"role":"authenticated"}""".toByteArray())
        return "$header.$payload.sig"
    }
}

/**
 * Speaks enough of the Supabase Realtime (Phoenix) protocol to stand in for the server: it
 * remembers the postgres_changes filters each channel sent when it joined, answers the join with
 * server ids for them, and pushes a row only to channels whose joined filters match it. A
 * channel that joined without a filter receives nothing, exactly as on the real server.
 */
@OptIn(SupabaseInternal::class)
class FakeRealtimeServer {

    private data class Sub(val id: Long, val table: String?, val filter: String?)

    private val nextId = AtomicLong(1)
    private val sockets = CopyOnWriteArrayList<Socket>()
    private val subs = java.util.concurrent.ConcurrentHashMap<String, List<Sub>>()
    val joins = CopyOnWriteArrayList<RealtimeMessage>()
    val leaves = CopyOnWriteArrayList<String>()

    val factory = object : RealtimeWebsocketFactory {
        override suspend fun create(url: String): RealtimeWebsocket = Socket().also { sockets += it }
    }

    /** Topics that currently hold a joined subscription with at least one filter. */
    fun subscribedTopics(): Set<String> = subs.filterValues { it.isNotEmpty() }.keys

    /** Delivers an INSERT on [table] to every channel whose joined filter matches [record]. */
    fun pushInsert(table: String, record: JsonObject) {
        subs.forEach { (topic, list) ->
            val ids = list.filter { it.table == table && matches(it.filter, record) }.map { it.id }
            if (ids.isEmpty()) return@forEach
            val msg = RealtimeMessage(
                topic, "postgres_changes",
                buildJsonObject {
                    putJsonArray("ids") { ids.forEach { add(kotlinx.serialization.json.JsonPrimitive(it)) } }
                    putJsonObject("data") {
                        put("type", "INSERT")
                        put("schema", "public")
                        put("table", table)
                        put("record", record)
                        put("columns", JsonArray(emptyList()))
                        put("commit_timestamp", "2026-11-07T10:00:00Z")
                    }
                },
                null,
            )
            sockets.forEach { it.incoming.trySend(msg) }
        }
    }

    private fun matches(filter: String?, record: JsonObject): Boolean {
        if (filter == null) return true
        val (col, rest) = filter.split("=", limit = 2)
        val value = rest.removePrefix("eq.")
        return record[col]?.jsonPrimitive?.content == value
    }

    private fun reply(socket: Socket, topic: String, ref: String?, response: JsonObject) {
        socket.incoming.trySend(
            RealtimeMessage(
                topic, "phx_reply",
                buildJsonObject { put("status", "ok"); put("response", response) },
                ref,
            ),
        )
    }

    private fun handle(socket: Socket, m: RealtimeMessage) {
        when (m.event) {
            "phx_join" -> {
                joins += m
                val requested = m.payload["config"]?.jsonObject?.get("postgres_changes")?.jsonArray.orEmpty()
                val assigned = requested.map {
                    val o = it.jsonObject
                    Sub(
                        nextId.getAndIncrement(),
                        o["table"]?.jsonPrimitive?.content,
                        o["filter"]?.jsonPrimitive?.content,
                    ) to o
                }
                subs[m.topic] = assigned.map { it.first }
                reply(
                    socket, m.topic, m.ref,
                    buildJsonObject {
                        put("postgres_changes", buildJsonArray {
                            assigned.forEach { (sub, o) ->
                                add(JsonObject(o + ("id" to kotlinx.serialization.json.JsonPrimitive(sub.id))))
                            }
                        })
                    },
                )
            }
            "phx_leave" -> {
                leaves += m.topic
                subs.remove(m.topic)
                reply(socket, m.topic, m.ref, JsonObject(emptyMap()))
            }
            "heartbeat" -> reply(socket, "phoenix", m.ref, JsonObject(emptyMap()))
            else -> Unit
        }
    }

    inner class Socket : RealtimeWebsocket {
        val incoming = Channel<RealtimeMessage>(Channel.UNLIMITED)
        private val closed = CompletableDeferred<Unit>()
        @Volatile private var open = true

        override val hasIncomingMessages: Boolean get() = open
        override suspend fun receive(): RealtimeMessage = incoming.receive()
        override suspend fun send(message: RealtimeMessage) = handle(this, message)
        override suspend fun blockUntilDisconnect() = closed.await()
        override fun disconnect() {
            open = false
            incoming.close()
            closed.complete(Unit)
            sockets.remove(this)
        }
    }
}
