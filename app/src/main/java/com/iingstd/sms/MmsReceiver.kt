package com.iingstd.sms
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
class MmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // Required default-SMS role component; MMS is not supported in Lite v1.
    }
}
