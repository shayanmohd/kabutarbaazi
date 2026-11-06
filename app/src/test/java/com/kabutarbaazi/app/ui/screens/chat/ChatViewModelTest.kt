package com.kabutarbaazi.app.ui.screens.chat

import android.content.ContextWrapper
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.media3.common.util.UnstableApi
import com.kabutarbaazi.app.data.AppContainer
import com.kabutarbaazi.app.data.ChatRepository
import com.kabutarbaazi.app.data.ModerationRepository
import com.kabutarbaazi.app.data.SessionRepository
import com.kabutarbaazi.app.testing.FakeSupabase
import com.kabutarbaazi.domain.model.SendStatus
import io.ktor.http.HttpMethod
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.ExecutorCoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.newSingleThreadContext
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Drives ChatViewModel against the real supabase-kt client (REST on a MockEngine, Realtime on a
 * fake Phoenix server that only streams rows to channels that joined with a matching filter).
 */
@OptIn(ExperimentalCoroutinesApi::class, kotlinx.coroutines.DelicateCoroutinesApi::class, UnstableApi::class, io.github.jan.supabase.annotations.SupabaseInternal::class)
class ChatViewModelTest {

    private val me = "11111111-1111-4111-8111-111111111111"
    private val other = "22222222-2222-4222-8222-222222222222"
    private val conv = "33333333-3333-4333-8333-333333333333"

    private lateinit var main: ExecutorCoroutineDispatcher
    private lateinit var fake: FakeSupabase
    private val stores = mutableListOf<ViewModelStore>()

    @Before
    fun setUp() {
        main = newSingleThreadContext("main")
        Dispatchers.setMain(main)
        fake = FakeSupabase(me)
        fake.rest = { call -> restAnswer(call) }
    }

    @After
    fun tearDown() {
        stores.forEach { it.clear() }
        Dispatchers.resetMain()
        main.close()
    }

    private fun row(id: String, sender: String, body: String, clientId: String = "c-$id", at: String = "2026-11-07T10:00:0${id.last()}Z") =
        buildJsonObject {
            put("id", id); put("conversation_id", conv); put("sender_id", sender)
            put("body", body); put("client_id", clientId); put("created_at", at)
        }

    private fun restAnswer(call: com.kabutarbaazi.app.testing.Call): String? = when {
        call.path.endsWith("/rest/v1/messages") && call.method == HttpMethod.Get -> "[]"
        call.path.endsWith("/rest/v1/messages") && call.method == HttpMethod.Post -> {
            // The insert echoes the stored row, as PostgREST does with return=representation.
            val sent = kotlinx.serialization.json.Json.parseToJsonElement(call.body)
            val o = if (sent is kotlinx.serialization.json.JsonArray) sent[0] else sent
            val clientId = (o as kotlinx.serialization.json.JsonObject)["client_id"]!!.toString().trim('"')
            "[${row("m9", me, "hello", clientId)}]"
        }
        call.path.endsWith("/rest/v1/conversations") -> """[{"id":"$conv","user_a":"$me","user_b":"$other",
            "last_message_at":"2026-11-07T10:00:00Z","a_last_read_at":"2026-11-07T10:00:00Z",
            "b_last_read_at":"2026-11-07T10:00:00Z"}]"""
        // RLS: the other side's profile is visible here; the blocked-list test covers the hidden case.
        call.path.endsWith("/rest/v1/profiles") -> """[{"id":"$other","username":"pigeonwala","display_name":"Pigeon Wala"}]"""
        else -> "[]"
    }

    /** What ChatScreen does: one ViewModel per chat route, through its own store. */
    private fun open(): ChatViewModel {
        val store = ViewModelStore().also { stores += it }
        val owner = object : ViewModelStoreOwner { override val viewModelStore = store }
        val chat = ChatRepository(fake.client)
        val container = AppContainer(ContextWrapper(null))
        val factory = viewModelFactory {
            initializer {
                ChatViewModel(conv, chat, SessionRepository(fake.client), ModerationRepository(fake.client), container)
            }
        }
        return runBlocking(main) { ViewModelProvider.create(owner, factory)[ChatViewModel::class] }
    }

    private suspend fun awaitJoined() = withTimeout(5_000) {
        while (fake.realtime.subscribedTopics().isEmpty()) delay(20)
    }

    @Test
    fun `a reply from the other person appears without a reload`() = runBlocking {
        val vm = open()
        awaitJoined()

        fake.realtime.pushInsert("messages", row("m1", other, "kitne ka hai?"))

        val s = withTimeout(5_000) { vm.state.first { st -> st.messages.any { it.id == "m1" } } }
        assertEquals("kitne ka hai?", s.messages.single { it.id == "m1" }.body)
    }

    @Test
    fun `the channel joins with a filter for this conversation only`() = runBlocking {
        open()
        awaitJoined()

        val join = fake.realtime.joins.single()
        val changes = join.payload["config"].toString()
        assertTrue(changes, changes.contains("\"table\":\"messages\""))
        assertTrue(changes, changes.contains("conversation_id=eq.$conv"))
    }

    @Test
    fun `a sent message stops showing Sending`() = runBlocking {
        val vm = open()
        vm.onDraft("hello")
        runBlocking(main) { vm.send() }

        val s = withTimeout(5_000) { vm.state.first { st -> st.messages.any { it.body == "hello" } } }
        assertTrue("pending should be empty: ${s.pending}", s.pending.none { it.status == SendStatus.Sending })
    }

    @Test
    fun `reopening the same chat still gets live replies`() = runBlocking {
        open()
        awaitJoined()
        // Leave the chat: its store is cleared, like the overlay route going away.
        runBlocking(main) { stores.removeAt(0).clear() }
        withTimeout(5_000) { while (fake.realtime.subscribedTopics().isNotEmpty()) delay(20) }

        val again = open()
        awaitJoined()
        fake.realtime.pushInsert("messages", row("m2", other, "abhi bhi hai"))

        withTimeout(5_000) { again.state.first { st -> st.messages.any { it.id == "m2" } } }
        Unit
    }

    @Test
    fun `block sends the other participant's id, read from the conversation`() = runBlocking {
        val vm = open()
        withTimeout(5_000) { vm.state.first { it.counterpart != null } }
        assertEquals("Pigeon Wala", vm.state.value.counterpart?.displayName)

        var done = false
        runBlocking(main) { vm.blockCounterpart { done = true }.join() }

        val insert = fake.calls.single { it.path.endsWith("/rest/v1/blocks") && it.method == HttpMethod.Post }
        assertTrue(insert.body, insert.body.contains("\"blocked_id\":\"$other\""))
        assertTrue(done)
    }
}
