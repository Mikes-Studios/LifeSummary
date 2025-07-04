package com.mikestudios.lifesummary

import android.app.Application
import com.mikestudios.lifesummary.ScheduleManager
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.mikestudios.lifesummary.DriveSync

class LifeSummaryApp : Application() {
    override fun onCreate() {
        super.onCreate()
        SecurePrefs.init(this)
        // Ensure any saved schedule alarms are (re)registered
        ScheduleManager.updateAlarms(this)
        kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            DriveSync.syncAndMerge(this@LifeSummaryApp)
            DriveSync.syncAndMergeFile(this@LifeSummaryApp, "transcripts", "transcripts.txt")
            listOf(30,60,120,240).forEach { mins ->
                DriveSync.syncAndMergeFile(
                    this@LifeSummaryApp,
                    SummaryUtils.dirName(mins),
                    SummaryUtils.fileName(mins)
                )
            }
        }
    }
} 