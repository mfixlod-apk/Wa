package com.mfix.autoprint

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class UsbPermissionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // Permission result is read by the UI on refresh.
    }
}