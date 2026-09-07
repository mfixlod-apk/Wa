package com.mfix.autoprint

import android.print.PrinterId
import android.print.PrinterInfo
import android.printservice.PrintJob
import android.printservice.PrintService

class MFIXPrintService : PrintService() {
    private var printerId: PrinterId? = null

    override fun onConnected() {
        super.onConnected()
        printerId = generatePrinterId("MFIX_XPRINTER")
        val info = PrinterInfo.Builder(
            printerId!!,
            "MFIX AutoPrint",
            PrinterInfo.STATUS_IDLE
        ).build()
        addPrinters(listOf(info))
    }

    override fun onPrintJobQueued(printJob: PrintJob) {
        // Phase 1: Android receives the job successfully.
        // Next commit: spool PDF/print data, render to monochrome raster,
        // and send ESC/POS bytes to the configured USB printer.
        printJob.start()
        printJob.complete()
    }

    override fun onRequestCancelPrintJob(printJob: PrintJob) {
        printJob.cancel()
    }

    override fun onDestroy() {
        printerId = null
        super.onDestroy()
    }
}