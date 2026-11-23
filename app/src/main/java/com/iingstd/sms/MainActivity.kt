package com.iingstd.sms

import android.Manifest
import android.app.Activity
import android.app.role.RoleManager
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.os.Build
import android.os.Bundle
import android.provider.Telephony
import android.text.TextUtils
import android.text.format.DateUtils
import android.view.Gravity
import android.view.View
import android.widget.*

class MainActivity : Activity() {
    private lateinit var list: LinearLayout
    private lateinit var defaultButton: Button
    private val blue = Color.rgb(37, 211, 102)
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
        val scroll = ScrollView(this).apply { isFillViewport = true }
        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(list)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
        updateDefaultButton()
        requestNextPermission()
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
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 4)
        }
    }
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if ((requestCode == 1 || requestCode == 4) && grantResults.any { it == PackageManager.PERMISSION_GRANTED }) requestNextPermission()
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
        list.removeAllViews()
        if (checkSelfPermission(Manifest.permission.READ_SMS) != PackageManager.PERMISSION_GRANTED) {
            list.addView(TextView(this).apply {
                text = "Izin membaca SMS belum tersedia."
                textSize = 15f; setTextColor(Color.WHITE); setPadding(dp(8), dp(24), 0, 0)
            })
            return
        }
        val seen = mutableSetOf<String>()
        try {
            contentResolver.query(Telephony.Sms.CONTENT_URI,
                arrayOf("address", "body", "date", "read", "type"), null, null, "date DESC")?.use { cursor ->
                while (cursor.moveToNext()) {
                    val address = cursor.getString(0) ?: "Tidak dikenal"
                    if (!seen.add(keyFor(address))) continue
                    val body = cursor.getString(1).orEmpty()
                    val date = cursor.getLong(2)
                    val unread = cursor.getInt(4) == Telephony.Sms.MESSAGE_TYPE_INBOX && cursor.getInt(3) == 0
                    addConversation(address, body, date, unread)
                }
            }
            if (seen.isEmpty()) list.addView(TextView(this).apply {
                text = "Belum ada percakapan. Ketuk + Baru untuk menulis pesan."
                textSize = 15f; setTextColor(Color.WHITE); setPadding(dp(8), dp(30), dp(8), 0)
            })
        } catch (_: SecurityException) {
            Toast.makeText(this, "Izin SMS diperlukan", Toast.LENGTH_SHORT).show()
        }
    }
    private fun addConversation(address: String, body: String, date: Long, unread: Boolean) {
        val row = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(13), dp(14), dp(13))
            background = conversationSurface(unread)
            elevation = 0f
            setOnClickListener {
                startActivity(Intent(this@MainActivity, ComposeActivity::class.java).putExtra("address", address))
            }
        }
        row.addView(TextView(this).apply {
            text = address.firstOrNull()?.uppercaseChar()?.toString() ?: "?"
            textSize = 20f; setTypeface(null, Typeface.BOLD)
            setTextColor(blue); gravity = Gravity.CENTER
            background = rounded(Color.rgb(36, 65, 62), 50)
        }, LinearLayout.LayoutParams(dp(48), dp(48)))
        val info = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), 0, dp(8), 0)
        }
        info.addView(TextView(this).apply {
            text = address; textSize = 16f; setTypeface(null, if (unread) Typeface.BOLD else Typeface.NORMAL)
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
