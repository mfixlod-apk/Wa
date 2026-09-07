package com.mfix.autoprint

import android.content.Context
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import android.hardware.usb.UsbManager

class UsbEscPosPrinter(private val context: Context) {
    private val manager = context.getSystemService(Context.USB_SERVICE) as UsbManager

    private data class OutputTarget(
        val intf: UsbInterface,
        val endpoint: UsbEndpoint,
        val priority: Int
    )

    private fun findOutputs(device: UsbDevice): List<OutputTarget> {
        val targets = mutableListOf<OutputTarget>()
        for (i in 0 until device.interfaceCount) {
            val intf = device.getInterface(i)
            for (e in 0 until intf.endpointCount) {
                val ep = intf.getEndpoint(e)
                if (ep.direction != UsbConstants.USB_DIR_OUT) continue
                val transportPriority = when (ep.type) {
                    UsbConstants.USB_ENDPOINT_XFER_BULK -> 0
                    UsbConstants.USB_ENDPOINT_XFER_INT -> 1
                    else -> 2
                }
                val interfacePriority = when (intf.interfaceClass) {
                    UsbConstants.USB_CLASS_PRINTER -> 0
                    UsbConstants.USB_CLASS_VENDOR_SPEC -> 1
                    else -> 2
                }
                targets += OutputTarget(intf, ep, interfacePriority * 10 + transportPriority)
            }
        }
        return targets.sortedBy { it.priority }
    }

    private fun sendWithChunkSize(
        connection: UsbDeviceConnection,
        endpoint: UsbEndpoint,
        data: ByteArray,
        chunkSize: Int,
        timeoutMs: Int
    ): Int {
        var offset = 0
        while (offset < data.size) {
            val length = minOf(chunkSize, data.size - offset)
            val sent = connection.bulkTransfer(endpoint, data, offset, length, timeoutMs)
            if (sent <= 0) return if (offset == 0) sent else offset
            offset += sent
        }
        return offset
    }

    private fun sendFallback(
        connection: UsbDeviceConnection,
        endpoint: UsbEndpoint,
        data: ByteArray
    ): Pair<Int, String> {
        val strategies = listOf(
            16 * 1024 to 15_000,
            8 * 1024 to 15_000,
            4 * 1024 to 20_000,
            1024 to 20_000,
            512 to 30_000,
            64 to 30_000
        )
        var last = -1
        for ((chunk, timeout) in strategies) {
            val sent = sendWithChunkSize(connection, endpoint, data, chunk, timeout)
            last = sent
            if (sent == data.size) return sent to "chunk=$chunk timeout=$timeout"
        }
        return last to "all chunk strategies failed"
    }

    fun printRaw(device: UsbDevice, data: ByteArray): Result<String> = runCatching {
        if (!manager.hasPermission(device)) error("USB permission not granted")
        val targets = findOutputs(device)
        if (targets.isEmpty()) error("No USB OUT endpoint found. Interfaces=" + device.interfaceCount)

        val errors = mutableListOf<String>()

        for ((index, target) in targets.withIndex()) {
            for (forceClaim in listOf(true, false)) {
                val connection = manager.openDevice(device)
                if (connection == null) {
                    errors += "#$index cannot open device"
                    continue
                }

                try {
                    if (!connection.claimInterface(target.intf, forceClaim)) {
                        errors += "#$index iface=\${target.intf.id} claim(force=$forceClaim)=false"
                        continue
                    }

                    val (sent, strategy) = sendFallback(connection, target.endpoint, data)
                    if (sent == data.size) {
                        return@runCatching "SUCCESS: sent=$sent iface=\${target.intf.id} ep=\${target.endpoint.address} type=\${target.endpoint.type} $strategy VID=\${device.vendorId} PID=\${device.productId}"
                    }

                    errors += "#$index iface=\${target.intf.id} ep=\${target.endpoint.address} force=$forceClaim sent=$sent $strategy"
                } catch (t: Throwable) {
                    errors += "#$index iface=\${target.intf.id} ep=\${target.endpoint.address} force=$forceClaim \${t.javaClass.simpleName}:\${t.message}"
                } finally {
                    try { connection.releaseInterface(target.intf) } catch (_: Throwable) {}
                    try { connection.close() } catch (_: Throwable) {}
                }
            }
        }

        error("All USB transport strategies failed. " + errors.joinToString(" | "))
    }

    fun printTest(device: UsbDevice): Result<String> {
        val data = EscPos.INIT +
            EscPos.ALIGN_CENTER +
            EscPos.BOLD_ON +
            EscPos.text("MFIX AUTOPRINT") +
            EscPos.BOLD_OFF +
            EscPos.text("USB TEST PRINT OK") +
            byteArrayOf(0x0A, 0x0A, 0x0A, 0x0A, 0x0A) +
            EscPos.CUT
        return printRaw(device, data)
    }
}
