package com.mikestudios.lifesummary

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import java.text.SimpleDateFormat
import java.util.*

/**
 * Handles registering/cancelling daily alarms that automatically start and
 * stop [MicCaptureService] at the user-chosen times.
 */
object ScheduleManager {
    const val ACTION_START = "com.mikestudios.lifesummary.START_REC"
    const val ACTION_STOP = "com.mikestudios.lifesummary.STOP_REC"

    fun updateAlarms(ctx: Context) {
        cancelAlarms(ctx)
        if (!SecurePrefs.isScheduleEnabled()) return

        val (startCal, stopCal) = nextDailyTimes()
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        try {
            if (am.canScheduleExactAlarms()) {
                am.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    startCal.timeInMillis,
                    pending(ctx, ACTION_START, 0)
                )
                am.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    stopCal.timeInMillis,
                    pending(ctx, ACTION_STOP, 1)
                )
            } else {
                // Fallback: inexact alarms
                am.setInexactRepeating(
                    AlarmManager.RTC_WAKEUP,
                    startCal.timeInMillis,
                    AlarmManager.INTERVAL_DAY,
                    pending(ctx, ACTION_START, 0)
                )
                am.setInexactRepeating(
                    AlarmManager.RTC_WAKEUP,
                    stopCal.timeInMillis,
                    AlarmManager.INTERVAL_DAY,
                    pending(ctx, ACTION_STOP, 1)
                )
            }
        } catch (se: SecurityException) {
            se.printStackTrace()
            // If exact not allowed, fallback similarly
            am.setInexactRepeating(
                AlarmManager.RTC_WAKEUP,
                startCal.timeInMillis,
                AlarmManager.INTERVAL_DAY,
                pending(ctx, ACTION_START, 0)
            )
            am.setInexactRepeating(
                AlarmManager.RTC_WAKEUP,
                stopCal.timeInMillis,
                AlarmManager.INTERVAL_DAY,
                pending(ctx, ACTION_STOP, 1)
            )
        }
    }

    fun cancelAlarms(ctx: Context) {
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.cancel(pending(ctx, ACTION_START, 0))
        am.cancel(pending(ctx, ACTION_STOP, 1))
    }

    private fun pending(ctx: Context, action: String, req: Int): PendingIntent =
        PendingIntent.getBroadcast(
            ctx,
            req,
            Intent(ctx, ScheduleReceiver::class.java).setAction(action),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    // Build the next start/end Calendar instances based on stored times
    private fun nextDailyTimes(): Pair<Calendar, Calendar> {
        val fmt = SimpleDateFormat("HH:mm", Locale.US)
        val now = Calendar.getInstance()

        fun buildCal(timeStr: String): Calendar {
            val base = Calendar.getInstance().apply { time = fmt.parse(timeStr) ?: Date() }
            // align date to today
            base.set(Calendar.YEAR, now.get(Calendar.YEAR))
            base.set(Calendar.DAY_OF_YEAR, now.get(Calendar.DAY_OF_YEAR))
            return base
        }

        var start = buildCal(SecurePrefs.getStartTime())
        var end = buildCal(SecurePrefs.getEndTime())

        // If start already passed today, schedule for tomorrow
        if (start.before(now)) start.add(Calendar.DAY_OF_YEAR, 1)
        // If end is before start, roll end to the next day.
        if (!end.after(start)) end.add(Calendar.DAY_OF_YEAR, 1)

        return Pair(start, end)
    }
} 