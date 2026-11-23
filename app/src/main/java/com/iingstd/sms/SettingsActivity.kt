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
import android.provider.Settings
import android.provider.Telephony
import android.telephony.SubscriptionManager
import android.view.Gravity
import android.widget.*

class SettingsActivity : Activity() {
    private fun dp(v:Int)=(v*resources.displayMetrics.density+.5f).toInt()
    private fun surface(color:Int)=GradientDrawable().apply { setColor(color); cornerRadius=dp(18).toFloat() }
    private lateinit var content:LinearLayout
    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor=Color.rgb(231,239,255)
        window.navigationBarColor=Color.rgb(248,247,255)
        val root=LinearLayout(this).apply {
            orientation=LinearLayout.VERTICAL
            setPadding(dp(16),dp(18),dp(16),dp(14))
            setBackgroundColor(Color.rgb(239,244,255))
        }
        root.addView(TextView(this).apply {
            text="‹  Pengaturan & Diagnostik"
            textSize=23f
            setTypeface(null,Typeface.BOLD)
            setTextColor(Color.rgb(34,53,110))
            setOnClickListener { finish() }
        })
        val scroll=ScrollView(this)
        content=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL }
        scroll.addView(content)
        root.addView(scroll,LinearLayout.LayoutParams(-1,0,1f))
        setContentView(root)
    }
    override fun onResume() { super.onResume(); if (::content.isInitialized) refresh() }
    private fun line(title:String,value:String) {
        val item=LinearLayout(this).apply {
            orientation=LinearLayout.VERTICAL
            setPadding(dp(16),dp(13),dp(16),dp(13))
            background=surface(Color.WHITE)
        }
        item.addView(TextView(this).apply { text=title; textSize=13f; setTextColor(Color.rgb(88,102,140)) })
        item.addView(TextView(this).apply { text=value; textSize=16f; setTextColor(Color.rgb(32,45,80)); setTypeface(null,Typeface.BOLD) })
        content.addView(item,LinearLayout.LayoutParams(-1,-2).apply { topMargin=dp(9) })
    }
    private fun action(title:String,click:()->Unit) {
        content.addView(Button(this).apply {
            text=title; isAllCaps=false; setOnClickListener { click() }
        },LinearLayout.LayoutParams(-1,dp(52)).apply { topMargin=dp(10) })
    }
    private fun has(p:String)=checkSelfPermission(p)==PackageManager.PERMISSION_GRANTED
    private fun refresh() {
        content.removeAllViews()
        val defaultPackage=Telephony.Sms.getDefaultSmsPackage(this)
        val roleHeld=if(Build.VERSION.SDK_INT>=29) {
            val role=getSystemService(RoleManager::class.java)
            role.isRoleAvailable(RoleManager.ROLE_SMS)&&role.isRoleHeld(RoleManager.ROLE_SMS)
        } else defaultPackage==packageName
        line("Aplikasi SMS utama",if(roleHeld || defaultPackage==packageName) "Aktif — IING STD SMS" else "Belum aktif")
        line("Paket SMS utama Android",defaultPackage ?: "Tidak tersedia")
        line("Izin membaca SMS",if(has(Manifest.permission.READ_SMS)) "Diizinkan" else "Belum diizinkan")
        line("Izin menerima SMS",if(has(Manifest.permission.RECEIVE_SMS)) "Diizinkan" else "Belum diizinkan")
        line("Izin mengirim SMS",if(has(Manifest.permission.SEND_SMS)) "Diizinkan" else "Belum diizinkan")
        line("Izin informasi SIM",if(has(Manifest.permission.READ_PHONE_STATE)) "Diizinkan" else "Belum diizinkan")
        val defaultId=SubscriptionManager.getDefaultSmsSubscriptionId()
        line("ID SIM SMS default",if(defaultId==SubscriptionManager.INVALID_SUBSCRIPTION_ID) "Belum dipilih" else defaultId.toString())
        if(has(Manifest.permission.READ_PHONE_STATE)) {
            try {
                val manager=getSystemService(SubscriptionManager::class.java)
                val subscriptions=manager.activeSubscriptionInfoList.orEmpty()
                if(subscriptions.isEmpty()) line("SIM aktif","Tidak tersedia")
                subscriptions.forEach { sub ->
                    line("SIM ${sub.simSlotIndex+1}", "${sub.displayName} • ID ${sub.subscriptionId}" +
                        if(sub.subscriptionId==defaultId) " • Default SMS" else "")
                }
            } catch (_:SecurityException) { line("Informasi SIM","Akses dibatasi Android") }
        } else line("Informasi SIM","Aktifkan izin informasi SIM untuk melihat detail")
        line("SMSC / Pusat pesan","Dikelola operator dan sistem Android; tidak diubah aplikasi")
        line("Kode kegagalan SMS","Buka percakapan, lihat tulisan Gagal (kode ...). Kode Android -1 berarti kegagalan umum, bukan diagnosis operator.")
        action("Buka pengaturan aplikasi Android") {
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,android.net.Uri.parse("package:$packageName")))
        }
        action("Buka pengaturan SIM Android") {
            try { startActivity(Intent(Settings.ACTION_WIRELESS_SETTINGS)) }
            catch (_:Exception) { Toast.makeText(this,"Pengaturan SIM tidak tersedia",Toast.LENGTH_SHORT).show() }
        }
        if(!has(Manifest.permission.READ_PHONE_STATE)) action("Izinkan informasi SIM") {
            requestPermissions(arrayOf(Manifest.permission.READ_PHONE_STATE),11)
        }
    }
    override fun onRequestPermissionsResult(requestCode:Int,permissions:Array<out String>,grantResults:IntArray) {
        super.onRequestPermissionsResult(requestCode,permissions,grantResults)
        refresh()
    }
}
