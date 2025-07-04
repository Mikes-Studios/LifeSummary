package com.mikestudios.lifesummary

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            // SecurePrefs might not yet be initialised in a cold boot context
            SecurePrefs.init(context)
            ScheduleManager.updateAlarms(context)
        }
    }
} 