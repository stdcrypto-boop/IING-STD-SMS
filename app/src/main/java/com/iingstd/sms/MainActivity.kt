package com.iingstd.sms

import android.Manifest
import android.app.Activity
import android.app.role.RoleManager
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
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
    private val blue = Color.rgb(54, 91, 220)
    private fun dp(value: Int) = (value * resources.displayMetrics.density + .5f).toInt()
    private fun rounded(color: Int, radius: Int) = GradientDrawable().apply {
        setColor(color); cornerRadius = dp(radius).toFloat()
    }
    private fun gradient(top: Int, bottom: Int, radius: Int = 0) = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(top, bottom)).apply { cornerRadius = dp(radius).toFloat() }
    private fun isDefaultSms(): Boolean =
        Telephony.Sms.getDefaultSmsPackage(this) == packageName

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(248, 250, 255)
        window.navigationBarColor = Color.rgb(248, 250, 255)
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(16), dp(18), dp(12))
            background = gradient(Color.rgb(235, 242, 255), Color.rgb(251, 246, 255))
        }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(12))
            background = gradient(Color.rgb(222, 234, 255), Color.rgb(242, 231, 255), 24)
        }
        header.addView(TextView(this).apply {
            text = "Pesan"; textSize = 30f; setTextColor(Color.rgb(25, 35, 60))
            setTypeface(null, Typeface.BOLD)
        })
        header.addView(TextView(this).apply {
            text = "IING STD SMS"; textSize = 12f; setTextColor(blue)
            letterSpacing = .12f; setPadding(0, 0, 0, dp(12))
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
            setTextColor(Color.rgb(36, 45, 67))
            setTypeface(null, Typeface.BOLD)
        }, LinearLayout.LayoutParams(0, -2, 1f))
        section.addView(TextView(this).apply {
            text = "＋  Baru"; textSize = 15f
            setTextColor(Color.WHITE); gravity = Gravity.CENTER
            setTypeface(null, Typeface.BOLD)
            background = gradient(Color.rgb(53, 107, 228), Color.rgb(120, 80, 212), 22)
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
                textSize = 15f; setTextColor(Color.DKGRAY); setPadding(dp(8), dp(24), 0, 0)
            })
            return
        }
        val seen = mutableSetOf<String>()
        try {
            contentResolver.query(Telephony.Sms.CONTENT_URI,
                arrayOf("address", "body", "date", "read", "type"), null, null, "date DESC")?.use { cursor ->
                while (cursor.moveToNext()) {
                    val address = cursor.getString(0) ?: "Tidak dikenal"
                    if (!seen.add(address)) continue
                    val body = cursor.getString(1).orEmpty()
                    val date = cursor.getLong(2)
                    val unread = cursor.getInt(4) == Telephony.Sms.MESSAGE_TYPE_INBOX && cursor.getInt(3) == 0
                    addConversation(address, body, date, unread)
                }
            }
            if (seen.isEmpty()) list.addView(TextView(this).apply {
                text = "Belum ada percakapan. Ketuk + Baru untuk menulis pesan."
                textSize = 15f; setPadding(dp(8), dp(30), dp(8), 0)
            })
        } catch (_: SecurityException) {
            Toast.makeText(this, "Izin SMS diperlukan", Toast.LENGTH_SHORT).show()
        }
    }
    private fun addConversation(address: String, body: String, date: Long, unread: Boolean) {
        val row = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(14), dp(12), dp(14))
            background = if (unread) gradient(Color.rgb(205, 225, 255), Color.rgb(233, 224, 255), 18) else gradient(Color.WHITE, Color.rgb(249, 248, 255), 18)
            setOnClickListener {
                startActivity(Intent(this@MainActivity, ComposeActivity::class.java).putExtra("address", address))
            }
        }
        row.addView(TextView(this).apply {
            text = address.firstOrNull()?.uppercaseChar()?.toString() ?: "?"
            textSize = 20f; setTypeface(null, Typeface.BOLD)
            setTextColor(blue); gravity = Gravity.CENTER
            background = rounded(Color.rgb(231, 237, 255), 50)
        }, LinearLayout.LayoutParams(dp(48), dp(48)))
        val info = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), 0, dp(8), 0)
        }
        info.addView(TextView(this).apply {
            text = address; textSize = 16f; setTypeface(null, if (unread) Typeface.BOLD else Typeface.NORMAL)
            setTextColor(Color.rgb(29, 39, 61))
            maxLines = 1; ellipsize = TextUtils.TruncateAt.END
        })
        info.addView(TextView(this).apply {
            text = body; textSize = 14f; setTextColor(if (unread) Color.rgb(39, 63, 111) else Color.rgb(111, 121, 141))
            maxLines = 2; ellipsize = TextUtils.TruncateAt.END
        })
        row.addView(info, LinearLayout.LayoutParams(0, -2, 1f))
        if (unread) row.addView(View(this).apply {
            background = rounded(blue, 20)
        }, LinearLayout.LayoutParams(dp(9), dp(9)).apply { rightMargin = dp(6) })
        row.addView(TextView(this).apply {
            text = DateUtils.getRelativeTimeSpanString(date, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString()
            textSize = 11f; setTextColor(Color.GRAY); maxLines = 1
        })
        list.addView(row, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) })
    }
}
