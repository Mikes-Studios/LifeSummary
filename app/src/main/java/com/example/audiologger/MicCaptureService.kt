package com.mikestudios.lifesummary

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.media.MediaRecorder
import android.os.*
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.work.*
import kotlinx.coroutines.*
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.coroutines.coroutineContext

class MicCaptureService : Service() {

    companion object { internal var isRunning = false }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var recorder: MediaRecorder? = null
    private lateinit var curFile: File
    private var nextCut: Long = 0L
    private var recStart: Long = 0L

    @RequiresApi(Build.VERSION_CODES.S)
    override fun onCreate() {
        super.onCreate()
        startForeground(1, buildNotif())
        isRunning = true
        scope.launch { loop() }
    }

    override fun onStartCommand(i: Intent?, f: Int, s: Int): Int = START_STICKY

    @RequiresApi(Build.VERSION_CODES.S)
    private suspend fun loop() {
        while (coroutineContext.isActive) {
            startRec()
            delay(nextCut - android.os.SystemClock.elapsedRealtime())
            stopRecAndQueue()
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun startRec() {
        val dir = File(getExternalFilesDir("audio"), "")
        dir.mkdirs()
        curFile = File(dir, "rec_${System.currentTimeMillis()}.m4a")

        recorder = MediaRecorder(this@MicCaptureService).apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setAudioEncodingBitRate(96_000)
            setAudioSamplingRate(44_100)
            setOutputFile(curFile)
            prepare(); start()
        }
        recStart = android.os.SystemClock.elapsedRealtime()
        nextCut = android.os.SystemClock.elapsedRealtime() + TimeUnit.MINUTES.toMillis(10)
    }

    private fun stopRecAndQueue() {
        recorder?.runCatching { stop(); release() }
        enqueueUpload(curFile)
    }

    private fun enqueueUpload(f: File) {
        val req = OneTimeWorkRequestBuilder<TranscribeWorker>()
            .setInputData(workDataOf("path" to f.absolutePath))
            .build()
        WorkManager.getInstance(this).enqueue(req)
    }

    override fun onDestroy() {
        scope.cancel()
        recorder?.runCatching { stop(); release() }

        val dur = android.os.SystemClock.elapsedRealtime() - recStart
        if (dur >= 5000 && ::curFile.isInitialized && curFile.exists()) {
            enqueueUpload(curFile)
        } else {
            curFile.delete()
        }

        isRunning = false
        super.onDestroy()
    }

    private fun buildNotif(): Notification {
        val chanId = "rec"
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(chanId, "Recording", NotificationManager.IMPORTANCE_LOW)
        )
        return NotificationCompat.Builder(this, chanId)
            .setContentTitle(getString(R.string.notif_title))
            .setContentText(getString(R.string.notif_text))
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setOngoing(true)
            .build()
    }

    override fun onBind(intent: Intent?) = null
}
