package com.iingstd.sms

import android.Manifest
import android.app.PendingIntent
import android.content.Intent
import android.content.ContentValues
import android.net.Uri
import java.util.UUID
import android.app.Activity
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.provider.Telephony
import android.telephony.SmsManager
import android.telephony.SubscriptionManager
import android.telephony.SubscriptionInfo
import android.os.Build
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.text.util.Linkify
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.text.format.DateFormat
import android.widget.*

class ComposeActivity : Activity() {
    private var currentAddress = ""
    private var messageList: LinearLayout? = null
    private val smsObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) {
            super.onChange(selfChange)
            messageList?.let { if (currentAddress.isNotBlank()) loadHistory(currentAddress, it) }
        }
    }
    override fun onResume() {
        super.onResume()
        contentResolver.registerContentObserver(Telephony.Sms.CONTENT_URI, true, smsObserver)
        messageList?.let { if (currentAddress.isNotBlank()) loadHistory(currentAddress, it) }
    }
    override fun onPause() {
        contentResolver.unregisterContentObserver(smsObserver)
        super.onPause()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply { orientation=1; setPadding(22,30,22,22); setBackgroundColor(Color.rgb(247,249,255)) }
        val header=TextView(this).apply { text="Percakapan"; textSize=26f; setTextColor(Color.rgb(52,91,222)); setPadding(0,0,0,18) }
        root.addView(header)
        val number=EditText(this).apply { hint="Nomor tujuan"; setSingleLine(true); setText(intent.getStringExtra("address") ?: intent.data?.schemeSpecificPart ?: "") }
        root.addView(number)
        val simChoices = mutableListOf<Pair<String,Int>>()
        val simSpinner = Spinner(this)
        val subscriptionManager = getSystemService(SubscriptionManager::class.java)
        if (checkSelfPermission(Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED) {
            try {
                subscriptionManager.activeSubscriptionInfoList?.forEach { info ->
                    simChoices.add("SIM ${info.simSlotIndex + 1} — ${info.displayName}" to info.subscriptionId)
                }
            } catch (_: SecurityException) {}
        }
        if (simChoices.isEmpty()) {
            simChoices.add("SIM default Android" to SubscriptionManager.getDefaultSmsSubscriptionId())
        }
        simSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, simChoices.map { it.first })
        val defaultSimIndex = simChoices.indexOfFirst { it.second == SubscriptionManager.getDefaultSmsSubscriptionId() }
        if (defaultSimIndex >= 0) simSpinner.setSelection(defaultSimIndex)
        root.addView(simSpinner)
        val history=ScrollView(this)
        val messages=LinearLayout(this).apply { orientation=1 }
        history.addView(messages)
        messageList = messages
        currentAddress = number.text.toString().trim()
        number.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                currentAddress = s?.toString()?.trim().orEmpty()
                loadHistory(currentAddress, messages)
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })
        root.addView(history,LinearLayout.LayoutParams(-1,0,1f))
        val body=EditText(this).apply { hint="Tulis SMS..."; minLines=2; maxLines=5 }
        root.addView(body)
        root.addView(Button(this).apply { text="Kirim SMS"; setOnClickListener {
            val to=number.text.toString().trim(); val message=body.text.toString()
            if(to.isEmpty() || message.isBlank()) { Toast.makeText(this@ComposeActivity,"Isi nomor dan pesan",Toast.LENGTH_SHORT).show(); return@setOnClickListener }
            if(checkSelfPermission(Manifest.permission.SEND_SMS)!=PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(Manifest.permission.SEND_SMS),3); return@setOnClickListener
            }
            var outgoingUri: Uri? = null
            try {
                if (Telephony.Sms.getDefaultSmsPackage(this@ComposeActivity) != packageName) {
                    Toast.makeText(this@ComposeActivity,"Jadikan IING STD SMS aplikasi SMS utama terlebih dahulu",Toast.LENGTH_LONG).show()
                    return@setOnClickListener
                }
                val selectedId = simChoices[simSpinner.selectedItemPosition].second
                if (selectedId == SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
                    Toast.makeText(this@ComposeActivity,"Pilih SIM SMS default di pengaturan HP",Toast.LENGTH_LONG).show()
                    return@setOnClickListener
                }
                val values = ContentValues().apply {
                    put(Telephony.Sms.ADDRESS,to)
                    put(Telephony.Sms.BODY,message)
                    put(Telephony.Sms.DATE,System.currentTimeMillis())
                    put(Telephony.Sms.READ,1)
                    put(Telephony.Sms.TYPE,Telephony.Sms.MESSAGE_TYPE_OUTBOX)
                }
                val uri = contentResolver.insert(Telephony.Sms.Outbox.CONTENT_URI,values)
                    ?: throw IllegalStateException("Tidak bisa menyimpan SMS")
                outgoingUri = uri
                val manager = if (Build.VERSION.SDK_INT >= 31) {
                    getSystemService(SmsManager::class.java).createForSubscriptionId(selectedId)
                } else {
                    @Suppress("DEPRECATION")
                    SmsManager.getSmsManagerForSubscriptionId(selectedId)
                }
                val parts=manager.divideMessage(message)
                val token=UUID.randomUUID().toString()
                val callbacks=ArrayList<PendingIntent>()
                parts.forEachIndexed { index, _ ->
                    val callback=Intent(this@ComposeActivity,SentReceiver::class.java).apply {
                        action="com.iingstd.sms.SENT"
                        data=Uri.parse("iingsms://sent/$token/$index")
                        putExtra("message_uri",uri.toString())
                        putExtra("token",token)
                        putExtra("part_count",parts.size)
                    }
                    callbacks.add(PendingIntent.getBroadcast(this@ComposeActivity,0,callback,PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
                }
                if(parts.size>1) manager.sendMultipartTextMessage(to,null,parts,callbacks,null)
                else manager.sendTextMessage(to,null,message,callbacks.first(),null)
                body.setText("")
                Toast.makeText(this@ComposeActivity,"Mengirim SMS...",Toast.LENGTH_SHORT).show()
                currentAddress = to
                loadHistory(to,messages)
            } catch(e: Exception) {
                outgoingUri?.let { saved ->
                    try {
                        contentResolver.update(saved, ContentValues().apply {
                            put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_FAILED)
                        }, null, null)
                    } catch (_: Exception) {}
                }
                Toast.makeText(this@ComposeActivity,"Gagal: ${e.message}",Toast.LENGTH_LONG).show()
                loadHistory(to,messages)
            }
        } })
        setContentView(root)
        loadHistory(number.text.toString(),messages)
    }
    private fun loadHistory(address:String,layout:LinearLayout) {
        layout.removeAllViews()
        if(address.isBlank() || checkSelfPermission(Manifest.permission.READ_SMS)!=PackageManager.PERMISSION_GRANTED) return
        contentResolver.query(Telephony.Sms.CONTENT_URI,arrayOf("body","type","date"),"address = ?",arrayOf(address),"date ASC")?.use { c ->
            while(c.moveToNext()) {
                val outgoing = c.getInt(1) != Telephony.Sms.MESSAGE_TYPE_INBOX
                val messageText = c.getString(0) ?: ""
                val date = DateFormat.format("HH:mm", c.getLong(2)).toString()
                val status = when (c.getInt(1)) {
                    Telephony.Sms.MESSAGE_TYPE_SENT -> " • Terkirim"
                    Telephony.Sms.MESSAGE_TYPE_FAILED -> " • Gagal"
                    Telephony.Sms.MESSAGE_TYPE_OUTBOX -> " • Mengirim"
                    else -> ""
                }
                val bubble = GradientDrawable().apply {
                    setColor(if(outgoing) Color.rgb(211,226,255) else Color.WHITE)
                    cornerRadius = 32f
                }
                val item=TextView(this).apply {
                    text="$messageText\n$date$status"
                    textSize=16f
                    setTextColor(Color.rgb(25,35,55))
                    setPadding(22,16,22,16)
                    autoLinkMask=Linkify.WEB_URLS or Linkify.EMAIL_ADDRESSES or Linkify.PHONE_NUMBERS
                    linksClickable=true
                    movementMethod=android.text.method.LinkMovementMethod.getInstance()
                    background=bubble
                }
                val row=LinearLayout(this).apply {
                    gravity=if(outgoing) Gravity.END else Gravity.START
                    setPadding(0,5,0,5)
                }
                row.addView(item,LinearLayout.LayoutParams(-2,-2).apply { width=LinearLayout.LayoutParams.WRAP_CONTENT })
                layout.addView(row,LinearLayout.LayoutParams(-1,-2))

            }
        }
    }
}
