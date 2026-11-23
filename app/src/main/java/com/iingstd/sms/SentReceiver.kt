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
        val modemError = intent.getIntExtra("errorCode", 0)
        val failureCode = if (resultCode != Activity.RESULT_OK) resultCode else prefs.getInt("${token}_error", 0)
        val diagnostic = if (resultCode != Activity.RESULT_OK) "Hasil SMS: $resultCode, modem: $modemError" else ""
        if (completed < total) {
            prefs.edit().putInt("${token}_completed",completed).putBoolean("${token}_failed",failed).putInt("${token}_error",failureCode).putString("${token}_detail",diagnostic).apply()
            return
        }
        prefs.edit().remove("${token}_completed").remove("${token}_failed").remove("${token}_error").remove("${token}_detail").apply()
        try {
            val uri = Uri.parse(messageUri)
            val values = ContentValues().apply {
                put(Telephony.Sms.TYPE,if(failed) Telephony.Sms.MESSAGE_TYPE_FAILED else Telephony.Sms.MESSAGE_TYPE_SENT)
                if (failed) put(Telephony.Sms.ERROR_CODE, if (modemError != 0) modemError else failureCode)
            }
            context.contentResolver.update(uri,values,null,null)
        } catch (_: Exception) {
            // A provider write failure should not crash the broadcast receiver.
        }
    }
}
