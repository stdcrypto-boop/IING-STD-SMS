package com.iingstd.sms

import android.app.Activity
import android.app.AlertDialog
import android.app.role.RoleManager
import android.graphics.Color
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.provider.Telephony
import android.view.View
import android.widget.*
import java.text.DateFormat
import java.util.Date

class TrashActivity : Activity() {
    private val store by lazy { TrashStore(this) }
    private lateinit var list: LinearLayout
    private fun dp(n:Int)=(n*resources.displayMetrics.density+.5f).toInt()
    private fun isDefault():Boolean {
        if(Build.VERSION.SDK_INT>=29) {
            val role=getSystemService(RoleManager::class.java)
            if(role.isRoleAvailable(RoleManager.ROLE_SMS) && role.isRoleHeld(RoleManager.ROLE_SMS)) return true
        }
        return Telephony.Sms.getDefaultSmsPackage(this)==packageName
    }
    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState)
        val root=LinearLayout(this).apply { orientation=1; setPadding(dp(18),dp(18),dp(18),dp(12)); setBackgroundColor(Color.rgb(247,249,253)) }
        root.addView(TextView(this).apply { text="‹  Tempat Sampah"; textSize=25f; setTypeface(null,Typeface.BOLD); setTextColor(Color.rgb(27,48,95)); setOnClickListener { finish() } })
        root.addView(TextView(this).apply { text="SMS yang dihapus melalui IING STD SMS disimpan hingga 30 hari. Data lama dibersihkan saat halaman ini atau fitur sampah diakses."; textSize=13f; setPadding(0,dp(10),0,dp(12)) })
        root.addView(Button(this).apply { text="Kosongkan Tempat Sampah"; isAllCaps=false; setOnClickListener {
            AlertDialog.Builder(this@TrashActivity).setTitle("Hapus semua secara permanen?")
                .setMessage("Semua pesan di Tempat Sampah akan hilang dan tidak bisa dipulihkan.")
                .setNegativeButton("Batal",null).setPositiveButton("Hapus permanen") { _,_ -> store.empty(); refresh() }.show()
        } })
        val scroll=ScrollView(this)
        list=LinearLayout(this).apply { orientation=1 }
        scroll.addView(list)
        root.addView(scroll,LinearLayout.LayoutParams(-1,0,1f))
        setContentView(root)
    }
    override fun onResume() { super.onResume(); if(::list.isInitialized) refresh() }
    private fun refresh() {
        list.removeAllViews()
        val entries=store.entries()
        if(entries.isEmpty()) list.addView(TextView(this).apply { text="Tempat Sampah kosong"; textSize=16f; setPadding(0,dp(24),0,0) })
        entries.forEach { e ->
            val row=LinearLayout(this).apply { orientation=1; setPadding(dp(12),dp(12),dp(12),dp(12)) }
            row.addView(TextView(this).apply { text=e.address; textSize=16f; setTypeface(null,Typeface.BOLD) })
            row.addView(TextView(this).apply { text=e.body; textSize=14f; maxLines=3 })
            row.addView(TextView(this).apply { text="Dihapus: "+DateFormat.getDateTimeInstance().format(Date(e.deletedAt)); textSize=12f })
            val actions=LinearLayout(this)
            actions.addView(Button(this).apply { text="Pulihkan"; isAllCaps=false; setOnClickListener {
                if(!isDefault()) { Toast.makeText(this@TrashActivity,"Jadikan IING STD SMS aplikasi utama sebelum memulihkan",Toast.LENGTH_LONG).show(); return@setOnClickListener }
                try {
                    if(store.restore(e.id)) { Toast.makeText(this@TrashActivity,"Pesan dipulihkan",Toast.LENGTH_SHORT).show(); refresh() }
                    else Toast.makeText(this@TrashActivity,"Pemulihan gagal",Toast.LENGTH_LONG).show()
                } catch(ex:Exception) { Toast.makeText(this@TrashActivity,"Gagal: ${ex.message}",Toast.LENGTH_LONG).show() }
            } },LinearLayout.LayoutParams(0,dp(48),1f))
            actions.addView(Button(this).apply { text="Hapus permanen"; isAllCaps=false; setOnClickListener {
                AlertDialog.Builder(this@TrashActivity).setTitle("Hapus permanen?")
                    .setMessage("Pesan ini tidak dapat dipulihkan lagi.")
                    .setNegativeButton("Batal",null).setPositiveButton("Hapus") { _,_ -> store.permanentlyDelete(e.id); refresh() }.show()
            } },LinearLayout.LayoutParams(0,dp(48),1f))
            row.addView(actions)
            list.addView(row)
            list.addView(View(this).apply { setBackgroundColor(Color.rgb(225,231,242)) },LinearLayout.LayoutParams(-1,dp(1)))
        }
    }
}
