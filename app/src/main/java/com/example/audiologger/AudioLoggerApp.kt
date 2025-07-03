package com.example.audiologger

import android.app.Application

class LifeSummaryApp : Application() {
    override fun onCreate() {
        super.onCreate()
        SecurePrefs.init(this)
    }
} 