package com.example.audiologger

import android.os.Bundle
import android.text.InputType
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class SettingsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val etKey = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            hint = "Enter OpenAI API key"
            setText(SecurePrefs.getApiKey())
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { marginStart = 64; marginEnd = 64 }
        }

        val btnSave = Button(this).apply {
            text = "Save"
            setOnClickListener {
                val key = etKey.text.toString().trim()
                SecurePrefs.setApiKey(key)
                Toast.makeText(this@SettingsActivity, "Saved", Toast.LENGTH_SHORT).show()
                finish()
            }
        }

        val btnDelete = Button(this).apply {
            text = "Delete Key"
            setOnClickListener {
                SecurePrefs.clearApiKey()
                etKey.text.clear()
                Toast.makeText(this@SettingsActivity, "Deleted", Toast.LENGTH_SHORT).show()
            }
        }

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 64, 32, 32)
            addView(etKey)
            addView(btnSave)
            addView(btnDelete)
        }

        setContentView(layout)
    }
} 