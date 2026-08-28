package com.kabutarbaazi.app.push

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.kabutarbaazi.app.MainActivity
import com.kabutarbaazi.app.R

object ChatNotifications {
    const val CHANNEL_ID = "chat"
    const val EXTRA_CONVERSATION_ID = "conversationId"

    fun ensureChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Messages",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply { description = "New messages from buyers and sellers" }
        NotificationManagerCompat.from(context).createNotificationChannel(channel)
    }

    fun show(context: Context, title: String, body: String, conversationId: String) {
        ensureChannel(context)

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_CONVERSATION_ID, conversationId)
        }
        val pending = PendingIntent.getActivity(
            context,
            conversationId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pending)
            .build()

        runCatching {
            // One live notification per conversation, replaced rather than stacked.
            NotificationManagerCompat.from(context)
                .notify(conversationId.hashCode(), notification)
        }
    }
}
