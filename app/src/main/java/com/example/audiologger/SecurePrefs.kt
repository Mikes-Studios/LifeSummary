package com.mikestudios.lifesummary

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

object SecurePrefs {
    private const val FILE_NAME = "secure_prefs"
    private const val KEY_API = "OPENAI_API_KEY"
    private const val KEY_SUMMARY_MODEL = "SUMMARY_MODEL"
    private const val KEY_TRANSCRIBE_MODEL = "TRANSCRIBE_MODEL"
    private const val KEY_SCHED_ENABLED = "SCHED_ENABLED"
    private const val KEY_SCHED_START = "SCHED_START"
    private const val KEY_SCHED_END = "SCHED_END"
    private const val KEY_DRIVE_SYNC = "DRIVE_SYNC_ENABLED"
    private const val KEY_DRIVE_FILE_ID = "DRIVE_FILE_ID"

    @Volatile
    private var ready = false
    private lateinit var prefs: SharedPreferences

    fun init(context: Context) {
        if (ready) return
        synchronized(this) {
            if (ready) return
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            try {
                prefs = EncryptedSharedPreferences.create(
                    context,
                    FILE_NAME,
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
                )
            } catch (e: Exception) {
                // If decryption fails (e.g., after re-install or key mismatch), wipe the
                // stored prefs + keyset and recreate them so the app can still start.
                // This will require the user to re-enter their settings but prevents a crash.
                context.deleteSharedPreferences(FILE_NAME)
                // Clear the Tink keyset used internally by EncryptedSharedPreferences
                context.getSharedPreferences("androidx.security.crypto.master_keyset", Context.MODE_PRIVATE)
                    .edit().clear().apply()

                prefs = EncryptedSharedPreferences.create(
                    context,
                    FILE_NAME,
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
                )
            }
            ready = true
        }
    }

    fun setApiKey(key: String) {
        if (!ready) throw IllegalStateException("SecurePrefs not initialised")
        prefs.edit().putString(KEY_API, key).apply()
    }

    fun getApiKey(): String {
        if (!ready) throw IllegalStateException("SecurePrefs not initialised")
        return prefs.getString(KEY_API, "") ?: ""
    }

    fun clearApiKey() {
        if (!ready) throw IllegalStateException("SecurePrefs not initialised")
        prefs.edit().remove(KEY_API).apply()
    }

    fun setSummaryModel(model: String) {
        if (!ready) throw IllegalStateException("SecurePrefs not initialised")
        prefs.edit().putString(KEY_SUMMARY_MODEL, model).apply()
    }

    fun getSummaryModel(): String {
        if (!ready) throw IllegalStateException("SecurePrefs not initialised")
        return prefs.getString(KEY_SUMMARY_MODEL, "gpt-4.1-mini") ?: "gpt-4.1-mini"
    }

    fun setTranscriptionModel(model: String) {
        if (!ready) throw IllegalStateException("SecurePrefs not initialised")
        prefs.edit().putString(KEY_TRANSCRIBE_MODEL, model).apply()
    }

    fun getTranscriptionModel(): String {
        if (!ready) throw IllegalStateException("SecurePrefs not initialised")
        return prefs.getString(KEY_TRANSCRIBE_MODEL, "gpt-4o-transcribe") ?: "gpt-4o-transcribe"
    }

    fun setSchedule(enabled: Boolean, start: String, end: String) {
        if (!ready) throw IllegalStateException("SecurePrefs not initialised")
        prefs.edit()
            .putBoolean(KEY_SCHED_ENABLED, enabled)
            .putString(KEY_SCHED_START, start)
            .putString(KEY_SCHED_END, end)
            .apply()
    }

    fun isScheduleEnabled(): Boolean {
        if (!ready) throw IllegalStateException("SecurePrefs not initialised")
        return prefs.getBoolean(KEY_SCHED_ENABLED, false)
    }

    fun getStartTime(): String {
        if (!ready) throw IllegalStateException("SecurePrefs not initialised")
        return prefs.getString(KEY_SCHED_START, "09:00") ?: "09:00"
    }

    fun getEndTime(): String {
        if (!ready) throw IllegalStateException("SecurePrefs not initialised")
        return prefs.getString(KEY_SCHED_END, "17:00") ?: "17:00"
    }

    fun setDriveSyncEnabled(enabled: Boolean) {
        if (!ready) throw IllegalStateException("SecurePrefs not initialised")
        prefs.edit().putBoolean(KEY_DRIVE_SYNC, enabled).apply()
    }

    fun isDriveSyncEnabled(): Boolean {
        if (!ready) throw IllegalStateException("SecurePrefs not initialised")
        return prefs.getBoolean(KEY_DRIVE_SYNC, false)
    }

    fun setDriveFileId(id: String) {
        if (!ready) throw IllegalStateException("SecurePrefs not initialised")
        prefs.edit().putString(KEY_DRIVE_FILE_ID, id).apply()
    }

    fun getDriveFileId(): String? {
        if (!ready) throw IllegalStateException("SecurePrefs not initialised")
        return prefs.getString(KEY_DRIVE_FILE_ID, null)
    }
} 