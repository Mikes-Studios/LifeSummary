package com.example.audiologger

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

object SecurePrefs {
    private const val FILE_NAME = "secure_prefs"
    private const val KEY_API = "OPENAI_API_KEY"

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
            prefs = EncryptedSharedPreferences.create(
                context,
                FILE_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
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
} 