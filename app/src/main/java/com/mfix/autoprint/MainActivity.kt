package com.mfix.autoprint

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 64, 48, 48)
        }
        root.addView(TextView(this).apply {
            text = "MFIX AutoPrint\nAutomatic Thermal Printing"
            textSize = 24f
        })
        root.addView(MaterialButton(this).apply {
            text = "ENABLE PRINT SERVICE"
            setOnClickListener { startActivity(Intent(Settings.ACTION_PRINT_SETTINGS)) }
        })
        root.addView(MaterialButton(this).apply {
            text = "TEST PRINT (coming next)"
            isEnabled = false
        })
        setContentView(root)
    }
}