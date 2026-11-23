package com.iingstd.sms

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Telephony
import android.app.Notification
import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri

class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_DELIVER_ACTION) return
        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        if (messages.isEmpty()) return
        val sender = messages.first().displayOriginatingAddress ?: return
        val body = messages.joinToString("") { it.displayMessageBody ?: "" }
        val receivedAt = messages.first().timestampMillis.takeIf { it > 0L } ?: System.currentTimeMillis()
        val fingerprint = "$sender|$receivedAt|$body"
        val prefs = context.getSharedPreferences("sms_incoming", Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        val lastTime = prefs.getLong("last_received_at", 0L)
        if (prefs.getString("last_fingerprint", null) == fingerprint &&
            now >= lastTime && now - lastTime < 120_000L) return
        val values = ContentValues().apply {
            put(Telephony.Sms.ADDRESS, sender)
            put(Telephony.Sms.BODY, body)
            put(Telephony.Sms.DATE, receivedAt)
            put(Telephony.Sms.READ, 0)
            put(Telephony.Sms.SEEN, 0)
        }
        try {
            val saved = context.contentResolver.insert(Telephony.Sms.Inbox.CONTENT_URI, values)
            if (saved == null) return
            prefs.edit().putString("last_fingerprint", fingerprint).putLong("last_received_at", now).apply()
        } catch (_: Exception) {
            return
        }
        val manager = context.getSystemService(NotificationManager::class.java)
        val channelId = "sms_messages"
        if (Build.VERSION.SDK_INT >= 26) {
            manager.createNotificationChannel(NotificationChannel(channelId, "Pesan SMS", NotificationManager.IMPORTANCE_HIGH))
        }
        if (Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val open = Intent(context, ComposeActivity::class.java).apply {
            data = Uri.parse("iingsms://conversation/" + Uri.encode(sender))
            putExtra("address", sender)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pending = PendingIntent.getActivity(context, sender.hashCode(), open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = Notification.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_email)
            .setContentTitle(sender)
            .setContentText(body)
            .setStyle(Notification.BigTextStyle().bigText(body))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()
        manager.notify(sender.hashCode(), notification)
    }
}
