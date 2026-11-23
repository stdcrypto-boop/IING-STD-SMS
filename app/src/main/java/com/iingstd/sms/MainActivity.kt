package com.iingstd.sms

import android.Manifest
import android.app.Activity
import android.app.role.RoleManager
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.provider.Telephony
import android.view.View
import android.widget.*
import android.text.util.Linkify

class MainActivity : Activity() {
    private lateinit var list: LinearLayout
    private val blue = Color.rgb(52, 91, 222)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply { orientation = 1; setPadding(20, 30, 20, 12); setBackgroundColor(Color.rgb(247,249,255)) }
        val title = TextView(this).apply { text = "IING STD SMS"; textSize = 27f; setTextColor(blue); setTypeface(null,Typeface.BOLD) }
        root.addView(title)
        val button = Button(this).apply { text = "Jadikan aplikasi SMS utama"; setOnClickListener { requestDefault() } }
        root.addView(button)
        val newMessage = Button(this).apply { text = "＋ Pesan baru"; setOnClickListener { startActivity(Intent(this@MainActivity, ComposeActivity::class.java)) } }
        root.addView(newMessage)
        val scroll = ScrollView(this)
        list = LinearLayout(this).apply { orientation = 1 }
        scroll.addView(list)
        root.addView(scroll, LinearLayout.LayoutParams(-1,0,1f))
        setContentView(root)
        requestNextPermission()
    }
    override fun onResume() {
        super.onResume()
        if (::list.isInitialized && checkSelfPermission(Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED) refresh()
    }
    private fun requestNextPermission() {
        val required = listOf(Manifest.permission.READ_SMS, Manifest.permission.RECEIVE_SMS, Manifest.permission.SEND_SMS)
        val missingSms = required.filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
        if (missingSms.isNotEmpty()) {
            requestPermissions(missingSms.toTypedArray(), 1)
            return
        }
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 4)
            return
        }
        refresh()
    }
    override fun onRequestPermissionsResult(requestCode:Int, permissions:Array<out String>, grantResults:IntArray) {
        super.onRequestPermissionsResult(requestCode,permissions,grantResults)
        if ((requestCode == 1 || requestCode == 4) && grantResults.any { it == PackageManager.PERMISSION_GRANTED }) requestNextPermission()
        else if (::list.isInitialized) refresh()
    }
    private fun requestDefault() {
        if (Build.VERSION.SDK_INT >= 29) {
            val role = getSystemService(RoleManager::class.java)
            if (role.isRoleAvailable(RoleManager.ROLE_SMS) && !role.isRoleHeld(RoleManager.ROLE_SMS))
                startActivityForResult(role.createRequestRoleIntent(RoleManager.ROLE_SMS),2)
        } else startActivityForResult(Intent(Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT).putExtra(Telephony.Sms.Intents.EXTRA_PACKAGE_NAME,packageName),2)
    }
    private fun refresh() {
        list.removeAllViews()
        if (checkSelfPermission(Manifest.permission.READ_SMS)!=PackageManager.PERMISSION_GRANTED) return
        val seen = mutableSetOf<String>()
        try {
            contentResolver.query(Telephony.Sms.CONTENT_URI,arrayOf("address","body","date"),null,null,"date DESC")?.use { c ->
                while(c.moveToNext()) {
                    val address = c.getString(0) ?: "Tidak dikenal"
                    if(!seen.add(address)) continue
                    val body = c.getString(1) ?: ""
                    val item = TextView(this).apply {
                        text = "$address\n$body"; textSize=17f; setTextColor(Color.rgb(30,40,65))
                        setPadding(18,20,18,20); maxLines=3
                        setOnClickListener { startActivity(Intent(this@MainActivity,ComposeActivity::class.java).putExtra("address",address)) }
                    }
                    list.addView(item)
                    list.addView(View(this).apply { setBackgroundColor(Color.LTGRAY) },LinearLayout.LayoutParams(-1,1))
                }
            }
        } catch (e: SecurityException) { Toast.makeText(this,"Izin SMS diperlukan",Toast.LENGTH_SHORT).show() }
    }
}
