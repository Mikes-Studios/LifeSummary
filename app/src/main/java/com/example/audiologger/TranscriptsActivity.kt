package com.mikestudios.lifesummary

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import java.io.File
import com.mikestudios.lifesummary.ui.theme.LifeSummaryTheme
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.background
import com.mikestudios.lifesummary.ui.theme.primaryGradient

@OptIn(ExperimentalMaterial3Api::class)
class TranscriptsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val entries = loadTranscripts()
        setContent {
            LifeSummaryTheme {
                Scaffold(
                    modifier = Modifier.background(primaryGradient()),
                    topBar = {
                        TopAppBar(
                            title = { Text("Transcripts") },
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer
                            )
                        )
                    },
                    floatingActionButton = {
                        FloatingActionButton(
                            onClick = { finish() }
                        ) {
                            Icon(Icons.Default.Home, contentDescription = "Back to home")
                        }
                    }
                ) { padding ->
                    Surface(modifier = Modifier.padding(padding)) {
                        LogListWithSearch(entries)
                    }
                }
            }
        }
    }

    private fun loadTranscripts(): List<LogEntry> {
        val dir = getExternalFilesDir("transcripts") ?: return emptyList()
        val f = File(dir, "transcripts.txt")
        if (!f.exists()) return emptyList()
        return f.readLines()
            .mapNotNull { line ->
                val idx = line.indexOf(',')
                if (idx <= 0) return@mapNotNull null
                val ts = line.substring(0, idx).toLongOrNull() ?: return@mapNotNull null
                val content = line.substring(idx + 1)
                LogEntry(ts, content)
            }
            .asReversed()
    }
} 