package com.mfix.autoprint

import android.content.Intent
import android.hardware.usb.UsbConstants
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
        status = TextView(this).apply { textSize = 15f; setPadding(0,32,0,32) }
        root.addView(status)
        root.addView(MaterialButton(this).apply { text = "REFRESH USB PRINTER"; setOnClickListener { scanUsb() } })
        root.addView(MaterialButton(this).apply { text = "ENABLE PRINT SERVICE"; setOnClickListener { startActivity(Intent(Settings.ACTION_PRINT_SETTINGS)) } })
        root.addView(MaterialButton(this).apply { text = "TEST PRINT"; setOnClickListener { testPrint() } })
        setContentView(root)
        scanUsb()
    }

    override fun onResume() { super.onResume(); scanUsb() }

    private fun isPrinterCandidate(d: UsbDevice): Boolean {
        for (i in 0 until d.interfaceCount) {
            val intf = d.getInterface(i)
            for (e in 0 until intf.endpointCount) {
                val ep = intf.getEndpoint(e)
                if (ep.direction == UsbConstants.USB_DIR_OUT && ep.type == UsbConstants.USB_ENDPOINT_XFER_BULK) return true
            }
        }
        return false
    }

    private fun scanUsb() {
        val devices = usb.findDevices()
        selected = devices.firstOrNull { isPrinterCandidate(it) }
        status.text = if (selected == null) {
            "NO USB PRINTER FOUND\nDevices detected: " + devices.size
        } else {
            val d = selected!!
            val name = d.productName ?: "USB printer"
            "PRINTER DETECTED\n" + name + "\nVID:" + d.vendorId + " PID:" + d.productId + "\nPermission: " + usb.hasPermission(d)
        }
    }

    private fun testPrint() {
        val device = selected ?: run { status.text = "NO PRINTER DETECTED"; return }
        if (!usb.hasPermission(device)) {
            status.text = "REQUESTING USB PERMISSION..."
            usb.requestPermission(device)
            return
        }
        status.text = "SENDING TEST PRINT..."
        val result = UsbEscPosPrinter(this).printTest(device)
        status.text = result.fold(
            onSuccess = { "SUCCESS\n" + it },
            onFailure = { "PRINT ERROR\n" + (it.message ?: "Unknown") }
        )
    }
}