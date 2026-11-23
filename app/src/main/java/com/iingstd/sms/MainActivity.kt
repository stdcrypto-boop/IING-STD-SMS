package com.iingstd.sms

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.app.role.RoleManager
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.os.Build
import android.os.Bundle
import android.provider.ContactsContract
import android.provider.Telephony
import android.text.TextUtils
import android.text.format.DateUtils
import android.view.MotionEvent
import android.view.Gravity
import android.view.View
import android.widget.*

class MainActivity : Activity() {
    private lateinit var list: LinearLayout
    private var refreshHint: TextView? = null
    private var inboxScroll: ScrollView? = null
    private var pullStartY = 0f
    private var pullStartX = 0f
    private var pullEligible = false
    private lateinit var defaultButton: Button
    private lateinit var selectionBar: LinearLayout
    private lateinit var selectionLabel: TextView
    private val selectedConversations = mutableSetOf<String>()
    private val visibleConversations = mutableSetOf<String>()
    private var selectionMode = false
    private var deleting = false
    private var refreshGeneration = 0
    private data class Conversation(val address: String, val body: String, val date: Long, val unread: Boolean)
    private val blue = Color.rgb(37, 211, 102)
    private val contactNames = mutableMapOf<String, String>()
    private fun dp(value: Int) = (value * resources.displayMetrics.density + .5f).toInt()
    private fun rounded(color: Int, radius: Int) = GradientDrawable().apply {
        setColor(color); cornerRadius = dp(radius).toFloat()
    }
    private fun gradient(top: Int, bottom: Int, radius: Int = 0) = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(top, bottom)).apply { cornerRadius = dp(radius).toFloat() }
    private fun conversationSurface(unread: Boolean): LayerDrawable {
        val shadow = rounded(Color.TRANSPARENT, 0)
        val fill = gradient(if (unread) Color.rgb(28, 45, 48) else Color.rgb(11, 20, 26),
            if (unread) Color.rgb(28, 45, 48) else Color.rgb(11, 20, 26), 10)
        fill.setStroke(0, Color.TRANSPARENT)
        return LayerDrawable(arrayOf(shadow, fill)).apply {
            setLayerInset(1, 0, 0, dp(2), dp(3))
        }
    }
    private fun keyFor(address: String): String {
        val digits = address.filter { it.isDigit() }
        return when {
            digits.startsWith("62") && digits.length >= 10 -> "0" + digits.drop(2)
            digits.startsWith("0") && digits.length >= 9 -> digits
            else -> address.trim().uppercase()
        }
    }
    private fun isDefaultSms(): Boolean {
        if (Build.VERSION.SDK_INT >= 29) {
            val role = getSystemService(RoleManager::class.java)
            if (role.isRoleAvailable(RoleManager.ROLE_SMS) && role.isRoleHeld(RoleManager.ROLE_SMS)) return true
        }
        return Telephony.Sms.getDefaultSmsPackage(this) == packageName
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(11, 20, 26)
        window.navigationBarColor = Color.rgb(248, 250, 255)
        window.decorView.systemUiVisibility = 0
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(16), dp(18), dp(12))
            setBackgroundColor(Color.rgb(11, 20, 26))
        }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(20), dp(20), dp(16))
            background = gradient(Color.rgb(31, 44, 52), Color.rgb(31, 44, 52), 16)
        }
        header.addView(TextView(this).apply {
            text = "Pesan"; textSize = 30f; setTextColor(Color.WHITE)
            setTypeface(null, Typeface.BOLD)
        })
        header.addView(TextView(this).apply {
            text = "IING STD  •  MESSENGER"; textSize = 12f; setTextColor(Color.rgb(135, 156, 165))
            letterSpacing = .12f; setPadding(0, 0, 0, dp(12))
        })
        header.addView(TextView(this).apply {
            text="⚙  Pengaturan & Diagnostik"; textSize=13f; setTextColor(Color.WHITE)
            setPadding(0,dp(5),0,dp(8))
            setOnClickListener { startActivity(Intent(this@MainActivity, SettingsActivity::class.java)) }
        })
        header.addView(TextView(this).apply {
            text="▤  Tempat Sampah"; textSize=14f; setTextColor(Color.WHITE)
            setPadding(0,dp(7),0,dp(5))
            setOnClickListener { startActivity(Intent(this@MainActivity,TrashActivity::class.java)) }
        })
        root.addView(header, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(10) })
        defaultButton = Button(this).apply {
            text = "Aktifkan sebagai aplikasi SMS utama"
            isAllCaps = false
            setOnClickListener { requestDefault() }
        }
        root.addView(defaultButton, LinearLayout.LayoutParams(-1, dp(52)))
        val section = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(12), 0, dp(10))
        }
        section.addView(TextView(this).apply {
            text = "Percakapan"; textSize = 17f
            setTextColor(Color.WHITE)
            setTypeface(null, Typeface.BOLD)
        }, LinearLayout.LayoutParams(0, -2, 1f))
        section.addView(TextView(this).apply {
            text = "＋  Baru"; textSize = 15f
            setTextColor(Color.WHITE); gravity = Gravity.CENTER
            setTypeface(null, Typeface.BOLD)
            background = gradient(Color.rgb(0, 168, 132), Color.rgb(0, 168, 132), 18)
            setOnClickListener { startActivity(Intent(this@MainActivity, ComposeActivity::class.java)) }
        }, LinearLayout.LayoutParams(dp(100), dp(44)))
        root.addView(section)
        selectionBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), dp(6), dp(8), dp(8))
            background = rounded(Color.rgb(31, 44, 52), 14)
            visibility = View.GONE
        }
        selectionLabel = TextView(this).apply {
            setTextColor(Color.WHITE); textSize = 14f
        }
        selectionBar.addView(selectionLabel, LinearLayout.LayoutParams(0, -2, 1f))
        selectionBar.addView(TextView(this).apply {
            text = "Semua"; textSize = 14f; setTextColor(Color.rgb(122, 230, 164))
            setPadding(dp(8), dp(12), dp(8), dp(12))
            setOnClickListener {
                selectedConversations.clear()
                selectedConversations.addAll(visibleConversations)
                refresh()
            }
        })
        selectionBar.addView(TextView(this).apply {
            text = "Hapus"; textSize = 14f; setTypeface(null, Typeface.BOLD)
            setTextColor(Color.rgb(255, 210, 120))
            setPadding(dp(8), dp(12), dp(8), dp(12))
            setOnClickListener { confirmDeleteSelected() }
        })
        selectionBar.addView(TextView(this).apply {
            text = "Batal"; textSize = 14f; setTextColor(Color.WHITE)
            setPadding(dp(8), dp(12), dp(8), dp(12))
            setOnClickListener {
                selectionMode = false; selectedConversations.clear(); refresh()
            }
        })
        root.addView(selectionBar)
        val hint = TextView(this).apply {
            text = "↓ Tarik ke bawah untuk refresh"
            textSize = 12f
            setTextColor(Color.rgb(145, 175, 179))
            gravity = Gravity.CENTER
            visibility = View.GONE
            setPadding(0, dp(6), 0, dp(6))
        }
        refreshHint = hint
        root.addView(hint)
        val scroll = ScrollView(this).apply { isFillViewport = true }
        inboxScroll = scroll
        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(list)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
        updateDefaultButton()
        requestNextPermission()
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        val scroll = inboxScroll
        if (scroll != null) {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    val location = IntArray(2)
                    scroll.getLocationOnScreen(location)
                    val rawY = event.rawY
                    pullEligible = rawY >= location[1] && rawY <= location[1] + scroll.height &&
                        scroll.scrollY == 0 && !deleting
                    pullStartY = rawY
                    pullStartX = event.rawX
                }
                MotionEvent.ACTION_MOVE -> {
                    if (pullEligible && scroll.scrollY == 0) {
                        val dy = event.rawY - pullStartY
                        val dx = kotlin.math.abs(event.rawX - pullStartX)
                        if (dx > dp(65) && dx > kotlin.math.abs(dy)) pullEligible = false
                        else {
                            refreshHint?.visibility = if (dy > dp(12)) View.VISIBLE else View.GONE
                            refreshHint?.text = if (dy >= dp(48)) "↑ Lepaskan untuk refresh" else "↓ Tarik untuk refresh"
                        }
                    }
                }
                MotionEvent.ACTION_UP -> {
                    val dy = event.rawY - pullStartY
                    val dx = kotlin.math.abs(event.rawX - pullStartX)
                    if (pullEligible && scroll.scrollY == 0 && dy >= dp(48) &&
                        dx < kotlin.math.abs(dy)) {
                        refreshHint?.text = "⟳ Memuat percakapan…"
                        refreshHint?.visibility = View.VISIBLE
                        refresh()
                    } else refreshHint?.visibility = View.GONE
                    pullEligible = false
                }
                MotionEvent.ACTION_CANCEL -> {
                    pullEligible = false
                    refreshHint?.visibility = View.GONE
                }
            }
        }
        return super.dispatchTouchEvent(event)
    }

    override fun onDestroy() {
        refreshGeneration++
        super.onDestroy()
    }
    override fun onResume() {
        super.onResume()
        if (::defaultButton.isInitialized) updateDefaultButton()
        if (::list.isInitialized) refresh()
    }
    private fun updateDefaultButton() {
        defaultButton.visibility = if (isDefaultSms()) View.GONE else View.VISIBLE
    }
    private fun requestNextPermission() {
        // Ask for SMS access only after Android grants this app the default SMS role.
        if (!isDefaultSms()) return
        val required = listOf(Manifest.permission.READ_SMS, Manifest.permission.RECEIVE_SMS, Manifest.permission.SEND_SMS)
        val missing = required.filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isNotEmpty()) { requestPermissions(missing.toTypedArray(), 1); return }
        if (checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.READ_CONTACTS), 6)
            return
        }
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 4)
        }
    }
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if ((requestCode == 1 || requestCode == 4 || requestCode == 6) && grantResults.any { it == PackageManager.PERMISSION_GRANTED }) requestNextPermission()
        refresh()
    }
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 2) { updateDefaultButton(); if (isDefaultSms()) requestNextPermission(); refresh() }
    }
    private fun requestDefault() {
        if (isDefaultSms()) { updateDefaultButton(); return }
        if (Build.VERSION.SDK_INT >= 29) {
            val role = getSystemService(RoleManager::class.java)
            if (role.isRoleAvailable(RoleManager.ROLE_SMS))
                startActivityForResult(role.createRequestRoleIntent(RoleManager.ROLE_SMS), 2)
            else Toast.makeText(this, "Peran SMS tidak tersedia", Toast.LENGTH_SHORT).show()
        } else {
            startActivityForResult(Intent(Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT)
                .putExtra(Telephony.Sms.Intents.EXTRA_PACKAGE_NAME, packageName), 2)
        }
    }
    private fun refresh() {
        val generation = ++refreshGeneration
        if (checkSelfPermission(Manifest.permission.READ_SMS) != PackageManager.PERMISSION_GRANTED) {
            list.removeAllViews()
            list.addView(TextView(this).apply {
                text = "Izin membaca SMS belum tersedia."
                textSize = 15f; setTextColor(Color.WHITE); setPadding(dp(8), dp(24), 0, 0)
            })
            return
        }
        // Read the SMS provider off the UI thread. Rebuild views only for the latest result.
        Thread {
            val conversations = mutableListOf<Conversation>()
            val names = mutableMapOf<String, String>()
            if (checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) {
                try {
                    contentResolver.query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                        arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER, ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME),
                        null, null, null)?.use { c ->
                        while (c.moveToNext()) {
                            val number = c.getString(0).orEmpty()
                            val name = c.getString(1).orEmpty()
                            if (name.isNotBlank()) names[keyFor(number)] = name
                        }
                    }
                } catch (_: Exception) {}
            }
            var error = false
            try {
                val seen = HashSet<String>()
                val unreadKeys = HashSet<String>()
                contentResolver.query(Telephony.Sms.CONTENT_URI,
                    arrayOf("address", "body", "date", "read", "type"), null, null, "date DESC")?.use { cursor ->
                    while (cursor.moveToNext()) {
                        val address = cursor.getString(0) ?: "Tidak dikenal"
                        val key = keyFor(address)
                        if (cursor.getInt(4) == Telephony.Sms.MESSAGE_TYPE_INBOX && cursor.getInt(3) == 0) unreadKeys.add(key)
                        if (!seen.add(key)) continue
                        conversations.add(Conversation(address, cursor.getString(1).orEmpty(),
                            cursor.getLong(2), cursor.getInt(4) == Telephony.Sms.MESSAGE_TYPE_INBOX && cursor.getInt(3) == 0))
                    }
                }
                for (i in conversations.indices) {
                    val item = conversations[i]
                    if (unreadKeys.contains(keyFor(item.address)) && !item.unread)
                        conversations[i] = item.copy(unread = true)
                }
            } catch (_: Exception) { error = true }
            runOnUiThread {
                if (isFinishing || isDestroyed || generation != refreshGeneration) return@runOnUiThread
                if (error) {
                    refreshHint?.visibility = View.GONE
                    Toast.makeText(this@MainActivity, "Gagal memuat SMS", Toast.LENGTH_SHORT).show()
                    return@runOnUiThread
                }
                contactNames.clear()
                contactNames.putAll(names)
                list.removeAllViews()
                visibleConversations.clear()
                conversations.forEach { conversation ->
                    visibleConversations.add(keyFor(conversation.address))
                    addConversation(conversation.address, conversation.body, conversation.date, conversation.unread)
                }
                if (conversations.isEmpty()) list.addView(TextView(this).apply {
                    text = "Belum ada percakapan. Ketuk + Baru untuk menulis pesan."
                    textSize = 15f; setTextColor(Color.WHITE); setPadding(dp(8), dp(30), dp(8), 0)
                })
                selectedConversations.retainAll(visibleConversations)
                updateSelectionBar()
                refreshHint?.visibility = View.GONE
            }
        }.start()
    }
    private fun updateSelectionBar() {
        if (!::selectionBar.isInitialized) return
        selectionBar.visibility = if (selectionMode) View.VISIBLE else View.GONE
        selectionLabel.text = "${selectedConversations.size} dipilih"
    }
    private fun toggleConversation(address: String) {
        val key = keyFor(address)
        selectionMode = true
        if (!selectedConversations.add(key)) selectedConversations.remove(key)
        refresh()
    }
    private fun confirmDeleteSelected() {
        if (deleting || selectedConversations.isEmpty()) {
            Toast.makeText(this, "Pilih percakapan terlebih dahulu", Toast.LENGTH_SHORT).show()
            return
        }
        if (!isDefaultSms()) {
            Toast.makeText(this, "Jadikan IING STD SMS sebagai aplikasi SMS utama dahulu", Toast.LENGTH_LONG).show()
            requestDefault()
            return
        }
        val keys = selectedConversations.toSet()
        AlertDialog.Builder(this)
            .setTitle("Hapus ${keys.size} percakapan?")
            .setMessage("Semua SMS di percakapan terpilih akan dipindahkan ke Tempat Sampah. Pesan dapat dipulihkan selama belum dihapus permanen atau melewati masa simpan 30 hari.")
            .setNegativeButton("Batal", null)
            .setPositiveButton("Pindahkan ke Sampah") { _, _ ->
                deleting = true
                Toast.makeText(this, "Memindahkan SMS ke Tempat Sampah...", Toast.LENGTH_SHORT).show()
                Thread {
                    var success = 0
                    var failed = 0
                    try {
                        val ids = mutableListOf<Long>()
                        contentResolver.query(Telephony.Sms.CONTENT_URI, arrayOf("_id", "address"), null, null, null)?.use { c ->
                            while (c.moveToNext()) {
                                if (keys.contains(keyFor(c.getString(1).orEmpty()))) ids.add(c.getLong(0))
                            }
                        }
                        val trash = TrashStore(this@MainActivity)
                        try {
                            val result = trash.moveSmsToTrashBatch(ids) { done, total ->
                                runOnUiThread {
                                    if (!isFinishing && !isDestroyed)
                                        selectionLabel.text = "Menghapus $done / $total SMS…"
                                }
                            }
                            success = result.first
                            failed = result.second
                        } finally { trash.close() }
                    } catch (_: Exception) { failed++ }
                    runOnUiThread {
                        deleting = false
                        selectedConversations.clear()
                        selectionMode = false
                        refresh()
                        Toast.makeText(this@MainActivity,
                            "$success SMS dipindahkan ke Sampah" + if (failed > 0) ", $failed gagal" else "",
                            Toast.LENGTH_LONG).show()
                    }
                }.start()
            }.show()
    }
    private fun addConversation(address: String, body: String, date: Long, unread: Boolean) {
        val row = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(13), dp(14), dp(13))
            background = if (selectedConversations.contains(keyFor(address)))
                rounded(Color.rgb(39, 104, 88), 12) else conversationSurface(unread)
            elevation = 0f
            setOnLongClickListener { toggleConversation(address); true }
            setOnClickListener {
                if (selectionMode) toggleConversation(address)
                else startActivity(Intent(this@MainActivity, ComposeActivity::class.java).putExtra("address", address))
            }
        }
        row.addView(TextView(this).apply {
            text = (contactNames[keyFor(address)] ?: address).firstOrNull()?.uppercaseChar()?.toString() ?: "?"
            textSize = 20f; setTypeface(null, Typeface.BOLD)
            setTextColor(blue); gravity = Gravity.CENTER
            background = rounded(Color.rgb(36, 65, 62), 50)
        }, LinearLayout.LayoutParams(dp(48), dp(48)))
        val info = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), 0, dp(8), 0)
        }
        info.addView(TextView(this).apply {
            text = contactNames[keyFor(address)] ?: address; textSize = 16f; setTypeface(null, if (unread) Typeface.BOLD else Typeface.NORMAL)
            setTextColor(Color.WHITE)
            maxLines = 1; ellipsize = TextUtils.TruncateAt.END
        })
        info.addView(TextView(this).apply {
            text = body; textSize = 14f; setTextColor(if (unread) Color.rgb(221, 232, 232) else Color.rgb(134, 150, 158))
            maxLines = 2; ellipsize = TextUtils.TruncateAt.END
        })
        row.addView(info, LinearLayout.LayoutParams(0, -2, 1f))
        if (unread) row.addView(View(this).apply {
            background = rounded(blue, 20)
        }, LinearLayout.LayoutParams(dp(9), dp(9)).apply { rightMargin = dp(6) })
        row.addView(TextView(this).apply {
            text = DateUtils.getRelativeTimeSpanString(date, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString()
            textSize = 11f; setTextColor(Color.rgb(134,150,158)); maxLines = 1
        })
        list.addView(row, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(7) })
    }
}
