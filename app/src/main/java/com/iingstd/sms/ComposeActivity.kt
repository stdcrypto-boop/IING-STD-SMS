package com.iingstd.sms

import android.Manifest
import android.app.PendingIntent
import android.content.Intent
import android.content.ContentValues
import android.net.Uri
import java.util.UUID
import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.app.role.RoleManager
import android.graphics.Typeface
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
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.view.WindowManager
import android.text.format.DateFormat
import android.widget.*

class ComposeActivity : Activity() {
    private fun dp(n:Int) = (n * resources.displayMetrics.density + .5f).toInt()
    private fun shape(a:Int,b:Int,r:Int=22) = GradientDrawable(GradientDrawable.Orientation.TL_BR,intArrayOf(a,b)).apply { cornerRadius=dp(r).toFloat() }
    private fun isSmsDefault(): Boolean {
        if (Build.VERSION.SDK_INT >= 29) {
            val role = getSystemService(RoleManager::class.java)
            if (role.isRoleAvailable(RoleManager.ROLE_SMS) && role.isRoleHeld(RoleManager.ROLE_SMS)) return true
        }
        return Telephony.Sms.getDefaultSmsPackage(this) == packageName
    }
    private fun requestSmsRole() {
        if (Build.VERSION.SDK_INT >= 29) {
            val role = getSystemService(RoleManager::class.java)
            if (role.isRoleAvailable(RoleManager.ROLE_SMS)) {
                startActivityForResult(role.createRequestRoleIntent(RoleManager.ROLE_SMS), 22)
                return
            }
        }
        startActivityForResult(Intent(Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT).putExtra(Telephony.Sms.Intents.EXTRA_PACKAGE_NAME, packageName),22)
    }
    private var currentAddress = ""
    private var messageList: LinearLayout? = null
    private var historyScroll: ScrollView? = null
    private fun scrollToLatest() {
        historyScroll?.post { historyScroll?.fullScroll(View.FOCUS_DOWN) }
    }
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
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 5 && grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) recreate()
    }
    override fun onPause() {
        contentResolver.unregisterContentObserver(smsObserver)
        super.onPause()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        window.statusBarColor = Color.rgb(31,44,52)
        window.navigationBarColor = Color.rgb(11,20,26)
        window.decorView.systemUiVisibility = 0
        val root = LinearLayout(this).apply { orientation=1; setPadding(dp(16),dp(12),dp(16),dp(12)); background=shape(Color.rgb(11,20,26),Color.rgb(11,20,26),0) }
        if (Build.VERSION.SDK_INT >= 30) {
            root.setOnApplyWindowInsetsListener { view, insets ->
                val ime = insets.getInsets(WindowInsets.Type.ime()).bottom
                val nav = insets.getInsets(WindowInsets.Type.navigationBars()).bottom
                val bottom = if (insets.isVisible(WindowInsets.Type.ime())) ime else nav
                view.setPadding(dp(16),dp(12),dp(16),bottom + dp(12))
                scrollToLatest()
                insets
            }
        }
        val header=LinearLayout(this).apply { orientation=1; setPadding(dp(18),dp(15),dp(18),dp(15)); background=shape(Color.rgb(31,44,52),Color.rgb(31,44,52)) }
        header.addView(TextView(this).apply { text="‹  Percakapan"; textSize=23f; setTypeface(null,Typeface.BOLD); setTextColor(Color.WHITE); setOnClickListener { finish() } })
        header.addView(TextView(this).apply { text="IING STD SMS  •  Pesan pribadi"; textSize=12f; setTextColor(Color.rgb(135,156,165)) })
        header.addView(TextView(this).apply {
            text="⋮  Opsi percakapan"; textSize=14f; setTextColor(Color.WHITE)
            setPadding(0,dp(12),0,0)
            setOnClickListener { showConversationActions() }
        })
        root.addView(header,LinearLayout.LayoutParams(-1,-2).apply { bottomMargin=dp(12) })
        val number=EditText(this).apply { hint="Nomor tujuan"; setSingleLine(true); setText(intent.getStringExtra("address") ?: intent.data?.schemeSpecificPart ?: "") }
        number.background=shape(Color.rgb(31,44,52),Color.rgb(31,44,52),16)
        number.setTextColor(Color.WHITE); number.setHintTextColor(Color.rgb(135,156,165))
        number.setPadding(dp(14),dp(10),dp(14),dp(10))
        number.textSize=16f
        root.addView(number,LinearLayout.LayoutParams(-1,dp(52)).apply { bottomMargin=dp(8) })
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
        simSpinner.adapter = object : ArrayAdapter<String>(this, android.R.layout.simple_spinner_dropdown_item, simChoices.map { it.first }) {
            override fun getView(position: Int, convertView: View?, parent: android.view.ViewGroup): View {
                return (super.getView(position, convertView, parent) as TextView).apply {
                    setTextColor(Color.rgb(9, 44, 35))
                    setTypeface(null, Typeface.BOLD)
                    textSize = 16f
                    setPadding(dp(16), dp(9), dp(12), dp(9))
                }
            }
            override fun getDropDownView(position: Int, convertView: View?, parent: android.view.ViewGroup): View {
                return (super.getDropDownView(position, convertView, parent) as TextView).apply {
                    setTextColor(Color.rgb(9, 44, 35))
                    setBackgroundColor(Color.rgb(188, 244, 207))
                    textSize = 16f
                    setPadding(dp(16), dp(12), dp(12), dp(12))
                }
            }
        }
        val defaultSimIndex = simChoices.indexOfFirst { it.second == SubscriptionManager.getDefaultSmsSubscriptionId() }
        if (defaultSimIndex >= 0) simSpinner.setSelection(defaultSimIndex)
        simSpinner.background=shape(Color.rgb(122,230,164),Color.rgb(80,203,145),14)
        root.addView(simSpinner,LinearLayout.LayoutParams(-1,dp(48)).apply { bottomMargin=dp(8) })
        if (checkSelfPermission(Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
            root.addView(Button(this).apply {
                text = "Aktifkan pilihan SIM 1 / SIM 2"
                setOnClickListener {
                    requestPermissions(arrayOf(Manifest.permission.READ_PHONE_STATE), 5)
                }
            })
        }
        val history=ScrollView(this).apply { isFillViewport = true; descendantFocusability = android.view.ViewGroup.FOCUS_BEFORE_DESCENDANTS }
        historyScroll=history
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
        history.clipToPadding=false
        history.addOnLayoutChangeListener { _, _, _, _, bottom, _, _, _, oldBottom ->
            if (bottom != oldBottom) scrollToLatest()
        }
        root.addView(history,LinearLayout.LayoutParams(-1,0,1f))
        val body=EditText(this).apply { hint="Tulis pesan..."; minLines=1; maxLines=4; textSize=16f; background=shape(Color.rgb(42,57,66),Color.rgb(42,57,66),24); setTextColor(Color.WHITE); setHintTextColor(Color.rgb(135,156,165)); setPadding(dp(15),dp(12),dp(15),dp(12)) }
        intent.getStringExtra("forward_body")?.let { body.setText(it); body.setSelection(body.length()) }
        body.setOnFocusChangeListener { _, focused -> if (focused) scrollToLatest() }
        val composer = LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL; gravity=Gravity.CENTER_VERTICAL; setPadding(dp(6),dp(8),dp(6),dp(8)); background=shape(Color.rgb(31,44,52),Color.rgb(31,44,52),24) }
        composer.addView(body,LinearLayout.LayoutParams(0,-2,1f).apply { rightMargin=dp(8) })
        composer.addView(Button(this).apply { text="➤"; contentDescription="Kirim pesan"; isAllCaps=false; textSize=22f; setTextColor(Color.WHITE); background=shape(Color.rgb(0,168,132),Color.rgb(0,168,132),26); minHeight=dp(52); minWidth=dp(52); setOnClickListener {
            val to=number.text.toString().trim(); val message=body.text.toString()
            if(to.isEmpty() || message.isBlank()) { Toast.makeText(this@ComposeActivity,"Isi nomor dan pesan",Toast.LENGTH_SHORT).show(); return@setOnClickListener }
            if(checkSelfPermission(Manifest.permission.SEND_SMS)!=PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(Manifest.permission.SEND_SMS),3); return@setOnClickListener
            }
            var outgoingUri: Uri? = null
            try {
                if (!isSmsDefault()) {
                    Toast.makeText(this@ComposeActivity,"Android belum mengizinkan IING STD SMS sebagai SMS utama. Pilih aplikasi ini pada dialog berikut.",Toast.LENGTH_LONG).show()
                    requestSmsRole()
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
                    callbacks.add(PendingIntent.getBroadcast(this@ComposeActivity,0,callback,PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE))
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
        } },LinearLayout.LayoutParams(dp(56),dp(52)))
        root.addView(composer,LinearLayout.LayoutParams(-1,-2))
        setContentView(root)
        loadHistory(number.text.toString(),messages)
    }
    private fun showConversationActions() {
        AlertDialog.Builder(this).setTitle("Opsi percakapan")
            .setItems(arrayOf("Blokir nomor (pengaturan Android)", "Hapus seluruh percakapan")) { _, which ->
                if (which == 0) openBlockedNumbers() else confirmDeleteConversation()
            }.show()
    }
    private fun openBlockedNumbers() {
        try { startActivity(Intent("android.settings.BLOCKED_NUMBERS_SETTINGS")) }
        catch (_: Exception) {
            Toast.makeText(this,"Pengaturan blokir nomor tidak tersedia di perangkat ini",Toast.LENGTH_LONG).show()
        }
    }
    private fun confirmDeleteConversation() {
        val target=currentAddress
        if (target.isBlank()) return
        AlertDialog.Builder(this).setTitle("Hapus seluruh percakapan?")
            .setMessage("Semua SMS dari dan ke $target akan dipindahkan ke Tempat Sampah selama 30 hari.")
            .setNegativeButton("Batal",null)
            .setPositiveButton("Pindahkan ke Sampah") { _,_ ->
                if (!isSmsDefault()) { Toast.makeText(this,"Jadikan aplikasi SMS utama terlebih dahulu",Toast.LENGTH_LONG).show(); return@setPositiveButton }
                try {
                    val ids=mutableListOf<Long>()
                    contentResolver.query(Telephony.Sms.CONTENT_URI,arrayOf(Telephony.Sms._ID,Telephony.Sms.ADDRESS),null,null,null)?.use { c ->
                        while(c.moveToNext()) if(normalized(c.getString(1).orEmpty())==normalized(target)) ids.add(c.getLong(0))
                    }
                    var removed=0
                    val trash=TrashStore(this)
                    trash.purgeExpired()
                    ids.forEach { id -> if(trash.moveSmsToTrash(id)) removed++ }
                    Toast.makeText(this,"$removed SMS dipindahkan ke Tempat Sampah",Toast.LENGTH_SHORT).show()
                    messageList?.let { loadHistory(target,it) }
                } catch(e:Exception) { Toast.makeText(this,"Gagal menghapus: ${e.message}",Toast.LENGTH_LONG).show() }
            }.show()
    }
    private fun showMessageActions(id:Long,body:String) {
        AlertDialog.Builder(this).setTitle("Tindakan pesan")
            .setItems(arrayOf("Salin teks", "Teruskan pesan", "Hapus pesan")) { _,which ->
                when(which) {
                    0 -> {
                        val clipboard=getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("SMS",body))
                        Toast.makeText(this,"Teks disalin",Toast.LENGTH_SHORT).show()
                    }
                    1 -> startActivity(Intent(this,ComposeActivity::class.java).putExtra("forward_body",body))
                    2 -> AlertDialog.Builder(this).setTitle("Hapus pesan?")
                        .setMessage("Pesan ini akan dipindahkan ke Tempat Sampah dan dapat dipulihkan selama 30 hari.")
                        .setNegativeButton("Batal",null).setPositiveButton("Pindahkan") { _,_ ->
                            if (!isSmsDefault()) { Toast.makeText(this,"Jadikan aplikasi SMS utama terlebih dahulu",Toast.LENGTH_LONG).show(); return@setPositiveButton }
                            try {
                                val trash=TrashStore(this)
                                trash.purgeExpired()
                                val deleted=if(trash.moveSmsToTrash(id)) 1 else 0
                                Toast.makeText(this,if(deleted>0) "Pesan dipindahkan ke Sampah" else "Gagal memindahkan pesan",Toast.LENGTH_SHORT).show()
                                messageList?.let { loadHistory(currentAddress,it) }
                            } catch(e:Exception) { Toast.makeText(this,"Gagal menghapus: ${e.message}",Toast.LENGTH_LONG).show() }
                        }.show()
                }
            }.show()
    }
    private fun normalized(address:String):String {
        val digits=address.filter { it.isDigit() }
        return when {
            digits.startsWith("62") && digits.length>=10 -> "0"+digits.drop(2)
            digits.startsWith("0") && digits.length>=9 -> digits
            else -> address.trim().uppercase()
        }
    }
    private fun loadHistory(address:String,layout:LinearLayout) {
        layout.removeAllViews()
        if(address.isBlank() || checkSelfPermission(Manifest.permission.READ_SMS)!=PackageManager.PERMISSION_GRANTED) return
        contentResolver.query(Telephony.Sms.CONTENT_URI,arrayOf("_id","address","body","type","date","error_code"),null,null,"date ASC")?.use { c ->
            while(c.moveToNext()) {
                if (normalized(c.getString(1).orEmpty()) != normalized(address)) continue
                val outgoing = c.getInt(3) != Telephony.Sms.MESSAGE_TYPE_INBOX
                val messageText = c.getString(2) ?: ""
                val date = DateFormat.format("HH:mm", c.getLong(4)).toString()
                val status = when (c.getInt(3)) {
                    Telephony.Sms.MESSAGE_TYPE_SENT -> " • Terkirim"
                    Telephony.Sms.MESSAGE_TYPE_FAILED -> " • Gagal (kode ${c.getInt(5)})"
                    Telephony.Sms.MESSAGE_TYPE_OUTBOX -> " • Mengirim"
                    else -> ""
                }
                val bubble = GradientDrawable().apply {
                    setColor(if(outgoing) Color.rgb(0,92,75) else Color.rgb(31,44,52))
                    cornerRadius = dp(20).toFloat()
                }
                val item=TextView(this).apply {
                    val messageColor = if (outgoing) Color.rgb(255, 224, 120) else Color.rgb(255, 245, 158)
                    val metadataColor = if (outgoing) Color.rgb(183, 235, 207) else Color.rgb(157, 180, 187)
                    val fullText = "$messageText\n$date$status"
                    text = SpannableString(fullText).apply {
                        setSpan(ForegroundColorSpan(messageColor), 0, messageText.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                        setSpan(ForegroundColorSpan(metadataColor), messageText.length, fullText.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    }
                    textSize=16f
                    setTextColor(messageColor)
                    setPadding(dp(14),dp(11),dp(14),dp(11))
                    autoLinkMask=Linkify.WEB_URLS or Linkify.EMAIL_ADDRESSES or Linkify.PHONE_NUMBERS
                    linksClickable=true
                    movementMethod=android.text.method.LinkMovementMethod.getInstance()
                    background=bubble
                    val smsId=c.getLong(0)
                    setOnLongClickListener { showMessageActions(smsId,messageText); true }
                }
                val row=LinearLayout(this).apply {
                    gravity=if(outgoing) Gravity.END else Gravity.START
                    setPadding(0,dp(5),0,dp(5))
                }
                row.addView(item,LinearLayout.LayoutParams(-2,-2).apply { width=LinearLayout.LayoutParams.WRAP_CONTENT })
                layout.addView(row,LinearLayout.LayoutParams(-1,-2))

            }
        }
        scrollToLatest()
    }
}
