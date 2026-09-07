package com.mfix.autoprint

import android.content.Context
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import android.hardware.usb.UsbManager

class UsbEscPosPrinter(private val context: Context) {
    private val manager = context.getSystemService(Context.USB_SERVICE) as UsbManager

    private fun findOutput(device: UsbDevice): Pair<UsbInterface, UsbEndpoint> {
        for (i in 0 until device.interfaceCount) {
            val intf = device.getInterface(i)
            for (e in 0 until intf.endpointCount) {
                val ep = intf.getEndpoint(e)
                if (ep.direction == UsbConstants.USB_DIR_OUT && ep.type == UsbConstants.USB_ENDPOINT_XFER_BULK) return intf to ep
            }
        }
        error("No BULK OUT printer endpoint found")
    }

    fun printTest(device: UsbDevice): Result<String> = runCatching {
        if (!manager.hasPermission(device)) error("USB permission not granted")
        val (intf, endpoint) = findOutput(device)
        val connection = manager.openDevice(device) ?: error("Cannot open USB device")
        try {
            if (!connection.claimInterface(intf, true)) error("Cannot claim USB interface")
            val data = EscPos.INIT + EscPos.ALIGN_CENTER + EscPos.BOLD_ON +
                EscPos.text("MFIX AUTOPRINT") + EscPos.BOLD_OFF +
                EscPos.text("USB TEST PRINT OK") +
                byteArrayOf(0x0A,0x0A,0x0A,0x0A,0x0A) + EscPos.CUT
            val sent = connection.bulkTransfer(endpoint, data, data.size, 10000)
            if (sent < 0) error("USB bulk transfer failed")
            "Sent " + sent + " bytes. VID=" + device.vendorId + " PID=" + device.productId
        } finally { connection.close() }
    }
}