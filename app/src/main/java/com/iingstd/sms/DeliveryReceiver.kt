package com.iingstd.sms

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Telephony

/** Records an actual carrier delivery report; never claims the recipient read an SMS. */
class DeliveryReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (resultCode != Activity.RESULT_OK) return
        val messageUri = intent.getStringExtra("message_uri") ?: return
        try {
            val values = ContentValues().apply {
                put(Telephony.Sms.STATUS, Telephony.Sms.STATUS_COMPLETE)
            }
            context.contentResolver.update(Uri.parse(messageUri), values, null, null)
        } catch (_: Exception) {
            // Delivery report support varies by carrier and Android SMS provider.
        }
    }
}
