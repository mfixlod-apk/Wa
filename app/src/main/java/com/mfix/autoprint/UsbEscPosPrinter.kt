package com.mfix.autoprint

import android.content.Context
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import android.hardware.usb.UsbManager

class UsbEscPosPrinter(private val context: Context) {
    private val manager = context.getSystemService(Context.USB_SERVICE) as UsbManager

    private data class OutputTarget(val intf: UsbInterface, val endpoint: UsbEndpoint)

    private fun findOutputs(device: UsbDevice): List<OutputTarget> {
        val targets = mutableListOf<OutputTarget>()
        val interfaces = (0 until device.interfaceCount).map { device.getInterface(it) }
            .sortedBy { if (it.interfaceClass == UsbConstants.USB_CLASS_PRINTER) 0 else 1 }
        for (intf in interfaces) {
            for (e in 0 until intf.endpointCount) {
                val ep = intf.getEndpoint(e)
                if (ep.direction == UsbConstants.USB_DIR_OUT && ep.type == UsbConstants.USB_ENDPOINT_XFER_BULK) {
                    targets += OutputTarget(intf, ep)
                }
            }
        }
        return targets
    }

    private fun send(connection: android.hardware.usb.UsbDeviceConnection, endpoint: UsbEndpoint, data: ByteArray): Int {
        var offset = 0
        var total = 0
        while (offset < data.size) {
            val length = minOf(16 * 1024, data.size - offset)
            val chunk = data.copyOfRange(offset, offset + length)
            val sent = connection.bulkTransfer(endpoint, chunk, chunk.size, 15000)
            if (sent <= 0) return if (total > 0) total else sent
            total += sent
            offset += sent
        }
        return total
    }

    fun printTest(device: UsbDevice): Result<String> = runCatching {
        if (!manager.hasPermission(device)) error("USB permission not granted")
        val targets = findOutputs(device)
        if (targets.isEmpty()) error("No BULK OUT printer endpoint found")

        val data = EscPos.INIT + EscPos.ALIGN_CENTER + EscPos.BOLD_ON +
            EscPos.text("MFIX AUTOPRINT") + EscPos.BOLD_OFF +
            EscPos.text("USB TEST PRINT OK") +
            byteArrayOf(0x0A, 0x0A, 0x0A, 0x0A, 0x0A) + EscPos.CUT

        val errors = mutableListOf<String>()
        for ((index, target) in targets.withIndex()) {
            val connection = manager.openDevice(device)
            if (connection == null) { errors += "#" + index + " cannot open device"; continue }
            try {
                if (!connection.claimInterface(target.intf, true)) {
                    errors += "#" + index + " cannot claim interface " + target.intf.id
                    continue
                }
                val sent = send(connection, target.endpoint, data)
                if (sent == data.size) {
                    return@runCatching "SUCCESS: sent " + sent + " bytes using interface " + target.intf.id + ", endpoint " + target.endpoint.address + ". VID=" + device.vendorId + " PID=" + device.productId
                }
                errors += "#" + index + " interface " + target.intf.id + " endpoint " + target.endpoint.address + ": sent=" + sent
            } catch (t: Throwable) {
                errors += "#" + index + " interface " + target.intf.id + " endpoint " + target.endpoint.address + ": " + (t.message ?: t.javaClass.simpleName)
            } finally {
                try { connection.releaseInterface(target.intf) } catch (_: Throwable) {}
                connection.close()
            }
        }
        error("All USB print endpoints failed. " + errors.joinToString(" | "))
    }
}