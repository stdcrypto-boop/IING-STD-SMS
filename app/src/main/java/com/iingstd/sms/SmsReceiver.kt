package com.iingstd.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony

class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // Android delivers SMS to the selected default app.
        // The system SMS provider handles persistence for the default app.
        // Notification and message persistence will be added after device testing.
        Telephony.Sms.Intents.getMessagesFromIntent(intent)
    }
}
