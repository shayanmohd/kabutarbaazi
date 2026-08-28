package com.kabutarbaazi.app.push

import androidx.media3.common.util.UnstableApi
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.kabutarbaazi.app.KabutarBaaziApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

@UnstableApi
class KabutarMessagingService : FirebaseMessagingService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNewToken(token: String) {
        val container = (application as KabutarBaaziApp).container
        scope.launch {
            val userId = container.session.currentUserId()
            if (userId != null) {
                runCatching { container.pushTokens.register(userId, token, "hi") }
            } else {
                // Not signed in yet. Stash it and register after the next sign-in.
                container.pendingPushToken = token
            }
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val container = (application as KabutarBaaziApp).container
        val conversationId = message.data["conversationId"] ?: return

        // The thread is already on screen and Realtime has delivered the message, so a tray
        // entry would be noise.
        if (container.openConversationId == conversationId) return

        val title = message.notification?.title ?: message.data["title"] ?: "KabutarBaazi"
        val body = message.notification?.body ?: message.data["body"] ?: "New message"
        ChatNotifications.show(this, title, body, conversationId)
    }
}
