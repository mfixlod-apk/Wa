package com.mfix.autoprint

import android.content.Context
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager

class UsbEscPosPrinter(private val context: Context) {
    private val manager = context.getSystemService(Context.USB_SERVICE) as UsbManager

    fun printTest(device: UsbDevice): Result<Unit> = runCatching {
        if (!manager.hasPermission(device)) error("USB permission not granted")

        val connection = manager.openDevice(device) ?: error("Cannot open USB device")
        try {
            val intf = (0 until device.interfaceCount)
                .map { device.getInterface(it) }
                .firstOrNull { it.interfaceClass == UsbConstants.USB_CLASS_PRINTER }
                ?: device.getInterface(0)

            val endpoint = (0 until intf.endpointCount)
                .map { intf.getEndpoint(it) }
                .firstOrNull { it.direction == UsbConstants.USB_DIR_OUT }
                ?: error("No USB output endpoint")

            if (!connection.claimInterface(intf, true)) error("Cannot claim USB printer interface")

            val data = buildList<Byte> {
                addAll(EscPos.INIT.toList())
                addAll(EscPos.ALIGN_CENTER.toList())
                addAll(EscPos.BOLD_ON.toList())
                addAll(EscPos.text("MFIX AUTOPRINT").toList())
                addAll(EscPos.BOLD_OFF.toList())
                addAll(EscPos.ALIGN_LEFT.toList())
                addAll(EscPos.text("USB TEST PRINT OK").toList())
                addAll(EscPos.text("").toList())
                addAll(EscPos.text("").toList())
                addAll(EscPos.text("").toList())
                addAll(EscPos.CUT.toList())
            }.toByteArray()

            val sent = connection.bulkTransfer(endpoint, data, data.size, 5000)
            if (sent < 0) error("USB transfer failed")
            connection.releaseInterface(intf)
        } finally {
            connection.close()
        }
    }
}