package com.iingstd.sms

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Telephony

class SentReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val token = intent.getStringExtra("token") ?: return
        val messageUri = intent.getStringExtra("message_uri") ?: return
        val total = intent.getIntExtra("part_count",1)
        if (total < 1) return
        val prefs = context.getSharedPreferences("sms_send_results",Context.MODE_PRIVATE)
        val completed = prefs.getInt("${token}_completed",0) + 1
        val failed = prefs.getBoolean("${token}_failed",false) || resultCode != Activity.RESULT_OK
        if (completed < total) {
            prefs.edit().putInt("${token}_completed",completed).putBoolean("${token}_failed",failed).apply()
            return
        }
        prefs.edit().remove("${token}_completed").remove("${token}_failed").apply()
        try {
            val uri = Uri.parse(messageUri)
            val values = ContentValues().apply {
                put(Telephony.Sms.TYPE,if(failed) Telephony.Sms.MESSAGE_TYPE_FAILED else Telephony.Sms.MESSAGE_TYPE_SENT)
            }
            context.contentResolver.update(uri,values,null,null)
        } catch (_: Exception) {
            // A provider write failure should not crash the broadcast receiver.
        }
    }
}
