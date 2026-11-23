package com.iingstd.sms.preview

import android.app.Activity
import android.os.Bundle
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView

class PreviewActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 60, 32, 24)
            setBackgroundColor(Color.rgb(247, 249, 255))
        }
        root.addView(TextView(this).apply {
            text = "IING STD SMS"
            textSize = 28f
            setTextColor(Color.rgb(52, 91, 222))
            setTypeface(null, Typeface.BOLD)
        })
        root.addView(TextView(this).apply {
            text = "Pratinjau aman — tanpa akses SMS"
            textSize = 17f
            setPadding(0, 20, 0, 30)
            setTextColor(Color.DKGRAY)
        })
        root.addView(TextView(this).apply {
            text = "✓ Aplikasi berhasil dibuka.\n\nVersi ini hanya menguji instalasi dan tampilan dasar. Tidak dapat membaca, menerima, atau mengirim SMS. Jangan jadikan sebagai aplikasi SMS utama."
            textSize = 17f
            setTextColor(Color.rgb(30, 40, 65))
        })
        setContentView(root)
    }
}
