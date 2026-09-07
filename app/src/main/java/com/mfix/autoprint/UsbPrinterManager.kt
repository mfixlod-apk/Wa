package com.mfix.autoprint

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager

class UsbPrinterManager(private val context: Context) {
    companion object { const val ACTION_USB_PERMISSION = "com.mfix.autoprint.USB_PERMISSION" }
    private val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager

    fun findDevices(): List<UsbDevice> = usbManager.deviceList.values.toList()

    fun requestPermission(device: UsbDevice) {
        val intent = Intent(ACTION_USB_PERMISSION).setPackage(context.packageName)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        usbManager.requestPermission(device, PendingIntent.getBroadcast(context, 0, intent, flags))
    }

    fun hasPermission(device: UsbDevice): Boolean = usbManager.hasPermission(device)
}