package com.mikestudios.lifesummary

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

class ScheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ScheduleManager.ACTION_START -> {
                if (!MicCaptureService.isRunning) {
                    ContextCompat.startForegroundService(
                        context,
                        Intent(context, MicCaptureService::class.java)
                    )
                }
            }
            ScheduleManager.ACTION_STOP -> {
                if (MicCaptureService.isRunning) {
                    context.stopService(Intent(context, MicCaptureService::class.java))
                }
            }
        }
        // Re-schedule for the next day so we always have valid alarms
        ScheduleManager.updateAlarms(context)
    }
} 