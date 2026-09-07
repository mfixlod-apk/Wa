package com.mfix.autoprint

import android.content.Intent
import android.hardware.usb.UsbDevice
import android.os.Bundle
import android.provider.Settings
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton

class MainActivity : AppCompatActivity() {
    private lateinit var status: TextView
    private lateinit var usb: UsbPrinterManager
    private var selected: UsbDevice? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        usb = UsbPrinterManager(this)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(48,64,48,48) }
        root.addView(TextView(this).apply { text = "MFIX AutoPrint\nAutomatic Thermal Printing"; textSize = 24f })
        status = TextView(this).apply { textSize = 16f; text = "USB printer: scanning..."; setPadding(0,32,0,32) }
        root.addView(status)
        root.addView(MaterialButton(this).apply { text = "REFRESH USB PRINTER"; setOnClickListener { scanUsb() } })
        root.addView(MaterialButton(this).apply { text = "ENABLE PRINT SERVICE"; setOnClickListener { startActivity(Intent(Settings.ACTION_PRINT_SETTINGS)) } })
        root.addView(MaterialButton(this).apply { text = "TEST PRINT"; setOnClickListener { status.text = if (selected == null) "No USB printer selected" else "Printer detected. Test transport is next." } })
        setContentView(root)
        scanUsb()
    }

    private fun scanUsb() {
        val devices = usb.findDevices()
        selected = devices.firstOrNull()
        status.text = if (selected == null) "No USB printer found. Connect Xprinter and press Refresh." else {
            val d = selected!!
            val name = d.productName ?: "USB printer"
            if (!usb.hasPermission(d)) usb.requestPermission(d)
            "USB detected: " + name
        }
    }
}