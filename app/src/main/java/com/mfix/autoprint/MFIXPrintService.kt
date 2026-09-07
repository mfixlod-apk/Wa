package com.mfix.autoprint

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.print.PrinterId
import android.print.PrinterInfo
import android.printservice.PrintJob
import android.printservice.PrintService
import android.printservice.PrinterDiscoverySession
import java.io.File
import java.io.FileOutputStream
import java.io.FileInputStream
import kotlin.math.max

class MFIXPrintService : PrintService() {

    override fun onCreatePrinterDiscoverySession(): PrinterDiscoverySession {
        return object : PrinterDiscoverySession() {
            override fun onStartPrinterDiscovery(priorityList: MutableList<PrinterId>) {
                val printerId = generatePrinterId("MFIX_XPRINTER")
                val info = PrinterInfo.Builder(
                    printerId,
                    "MFIX AutoPrint",
                    PrinterInfo.STATUS_IDLE
                ).build()
                addPrinters(listOf(info))
            }

            override fun onStopPrinterDiscovery() {}
            override fun onValidatePrinters(printerIds: MutableList<PrinterId>) {}
            override fun onStartPrinterStateTracking(printerId: PrinterId) {}
            override fun onStopPrinterStateTracking(printerId: PrinterId) {}
            override fun onDestroy() {}
        }
    }

    override fun onPrintJobQueued(printJob: PrintJob) {
        Thread {
            try {
                printJob.start()

                val manager = UsbPrinterManager(this)
                val device = manager.findDevices().firstOrNull { isPrinterCandidate(it) }
                    ?: error("No USB printer detected")
                if (!manager.hasPermission(device)) {
                    error("USB permission required. Open MFIX AutoPrint and grant printer permission once.")
                }

                val pdf = File(cacheDir, "printjob-${System.currentTimeMillis()}.pdf")
                val source = printJob.document.data
                    ?: error("Print job has no document data")
                source.use { pfd ->
                    FileInputStream(pfd.fileDescriptor).use { input ->
                        FileOutputStream(pdf).use { output ->
                            input.copyTo(output)
                            output.flush()
                        }
                    }
                }

                val bytes = renderPdfToEscPos(pdf)
                val result = UsbEscPosPrinter(this).printRaw(device, bytes)
                pdf.delete()

                result.getOrElse { throw it }
                printJob.complete()
            } catch (t: Throwable) {
                printJob.fail(t.message ?: "Printing failed")
            }
        }.start()
    }

    private fun isPrinterCandidate(device: android.hardware.usb.UsbDevice): Boolean {
        for (i in 0 until device.interfaceCount) {
            val intf = device.getInterface(i)
            for (e in 0 until intf.endpointCount) {
                val ep = intf.getEndpoint(e)
                if (ep.direction == android.hardware.usb.UsbConstants.USB_DIR_OUT) return true
            }
        }
        return false
    }

    private fun renderPdfToEscPos(pdf: File): ByteArray {
        val out = ArrayList<Byte>()
        out.addAll(EscPos.INIT.toList())
        ParcelFileDescriptor.open(pdf, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
            PdfRenderer(fd).use { renderer ->
                for (pageIndex in 0 until renderer.pageCount) {
                    renderer.openPage(pageIndex).use { page ->
                        val targetWidth = 576
                        val scale = targetWidth.toFloat() / max(1, page.width)
                        val targetHeight = max(1, (page.height * scale).toInt())
                        val bitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
                        bitmap.eraseColor(Color.WHITE)
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
                        out.addAll(bitmapToEscPos(bitmap).toList())
                        bitmap.recycle()
                    }
                }
            }
        }
        out.addAll(byteArrayOf(0x0A, 0x0A, 0x0A, 0x0A).toList())
        out.addAll(EscPos.CUT.toList())
        return out.toByteArray()
    }

    private fun bitmapToEscPos(bitmap: Bitmap): ByteArray {
        val width = bitmap.width
        val height = bitmap.height
        val bytesPerLine = (width + 7) / 8
        val image = ByteArray(8 + bytesPerLine * height)
        image[0] = 0x1D
        image[1] = 0x76
        image[2] = 0x30
        image[3] = 0x00
        image[4] = (bytesPerLine and 0xFF).toByte()
        image[5] = ((bytesPerLine shr 8) and 0xFF).toByte()
        image[6] = (height and 0xFF).toByte()
        image[7] = ((height shr 8) and 0xFF).toByte()

        var pos = 8
        for (y in 0 until height) {
            for (xByte in 0 until bytesPerLine) {
                var value = 0
                for (bit in 0 until 8) {
                    val x = xByte * 8 + bit
                    if (x < width) {
                        val pixel = bitmap.getPixel(x, y)
                        val r = Color.red(pixel)
                        val g = Color.green(pixel)
                        val b = Color.blue(pixel)
                        val luminance = (r * 299 + g * 587 + b * 114) / 1000
                        if (luminance < 180) value = value or (1 shl (7 - bit))
                    }
                }
                image[pos++] = value.toByte()
            }
        }
        return image
    }

    override fun onRequestCancelPrintJob(printJob: PrintJob) {
        printJob.cancel()
    }
}
