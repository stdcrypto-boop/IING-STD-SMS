package com.iingstd.sms

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.provider.Telephony
import android.telephony.SmsManager
import android.text.util.Linkify
import android.widget.*

class ComposeActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply { orientation=1; setPadding(22,30,22,22); setBackgroundColor(Color.rgb(247,249,255)) }
        val header=TextView(this).apply { text="Pesan"; textSize=26f; setTextColor(Color.rgb(52,91,222)) }
        root.addView(header)
        val number=EditText(this).apply { hint="Nomor tujuan"; setSingleLine(true); setText(intent.getStringExtra("address") ?: intent.data?.schemeSpecificPart ?: "") }
        root.addView(number)
        val history=ScrollView(this)
        val messages=LinearLayout(this).apply { orientation=1 }
        history.addView(messages)
        root.addView(history,LinearLayout.LayoutParams(-1,0,1f))
        val body=EditText(this).apply { hint="Tulis SMS..."; minLines=2 }
        root.addView(body)
        root.addView(Button(this).apply { text="Kirim SMS"; setOnClickListener {
            val to=number.text.toString().trim(); val message=body.text.toString()
            if(to.isEmpty() || message.isBlank()) { Toast.makeText(this@ComposeActivity,"Isi nomor dan pesan",Toast.LENGTH_SHORT).show(); return@setOnClickListener }
            if(checkSelfPermission(Manifest.permission.SEND_SMS)!=PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(Manifest.permission.SEND_SMS),3); return@setOnClickListener
            }
            try {
                val manager=SmsManager.getDefault()
                val parts=manager.divideMessage(message)
                if(parts.size>1) manager.sendMultipartTextMessage(to,null,parts,null,null)
                else manager.sendTextMessage(to,null,message,null,null)
                body.setText("")
                Toast.makeText(this@ComposeActivity,"SMS diserahkan ke jaringan",Toast.LENGTH_SHORT).show()
                loadHistory(to,messages)
            } catch(e: Exception) { Toast.makeText(this@ComposeActivity,"Gagal: ${e.message}",Toast.LENGTH_LONG).show() }
        } })
        setContentView(root)
        loadHistory(number.text.toString(),messages)
    }
    private fun loadHistory(address:String,layout:LinearLayout) {
        layout.removeAllViews()
        if(address.isBlank() || checkSelfPermission(Manifest.permission.READ_SMS)!=PackageManager.PERMISSION_GRANTED) return
        contentResolver.query(Telephony.Sms.CONTENT_URI,arrayOf("body","type"),"address = ?",arrayOf(address),"date ASC")?.use { c ->
            while(c.moveToNext()) {
                val item=TextView(this).apply {
                    text=c.getString(0) ?: ""; textSize=17f; setTextColor(Color.rgb(25,35,55))
                    setPadding(18,14,18,14); autoLinkMask=Linkify.WEB_URLS or Linkify.EMAIL_ADDRESSES or Linkify.PHONE_NUMBERS
                    setBackgroundColor(if(c.getInt(1)==2) Color.rgb(207,224,255) else Color.WHITE)
                }
                layout.addView(item,LinearLayout.LayoutParams(-1,-2).apply { bottomMargin=10 })
            }
        }
    }
}
