package com.mfix.autoprint

import android.print.PrinterId
import android.printservice.PrintJob
import android.printservice.PrintService
import android.printservice.PrinterDiscoverySession

class MFIXPrintService : PrintService() {

    override fun onCreatePrinterDiscoverySession(): PrinterDiscoverySession {
        return object : PrinterDiscoverySession() {
            override fun onStartPrinterDiscovery(priorityList: MutableList<PrinterId>) {
                val printerId = generatePrinterId("MFIX_XPRINTER")
                val info = android.print.PrinterInfo.Builder(
                    printerId,
                    "MFIX AutoPrint",
                    android.print.PrinterInfo.STATUS_IDLE
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
        // Temporary plumbing: accept Android print jobs.
        // USB transport and ESC/POS rendering are implemented next.
        printJob.start()
        printJob.complete()
    }

    override fun onRequestCancelPrintJob(printJob: PrintJob) {
        printJob.cancel()
    }
}
