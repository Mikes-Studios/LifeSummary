package com.example.audiologger

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import java.io.File
import com.example.audiologger.ui.theme.LifeSummaryTheme
import androidx.compose.foundation.layout.padding

@OptIn(ExperimentalMaterial3Api::class)
class SummariesActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val entries = loadSummaries()
        setContent {
            LifeSummaryTheme {
                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = { Text("Summaries") },
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

    private fun loadSummaries(): List<LogEntry> {
        // --- Load summaries ---
        val sumDir = getExternalFilesDir("summaries") ?: return emptyList()
        val sumFile = File(sumDir, "summaries.txt")
        if (!sumFile.exists()) return emptyList()

        // --- Load transcripts into a map keyed by the shared timestamp ---
        val transcriptMap: Map<Long, String> = run {
            val tDir = getExternalFilesDir("transcripts") ?: return@run emptyMap()
            val tFile = File(tDir, "transcripts.txt")
            if (!tFile.exists()) emptyMap() else {
                tFile.readLines().mapNotNull { l ->
                    val i = l.indexOf(',')
                    if (i <= 0) null else {
                        val key = l.substring(0, i).toLongOrNull() ?: return@mapNotNull null
                        key to l.substring(i + 1)
                    }
                }.toMap()
            }
        }

        return sumFile.readLines()
            .mapNotNull { line ->
                val idx = line.indexOf(',')
                if (idx <= 0) return@mapNotNull null
                val ts = line.substring(0, idx).toLongOrNull() ?: return@mapNotNull null
                val content = line.substring(idx + 1)

                val parts = content.split('|', limit = 2)
                val baseText = if (parts.size == 2) {
                    "${parts[0]}\n\n${parts[1]}"
                } else content

                val transcript = transcriptMap[ts]
                val fullText = if (!transcript.isNullOrBlank()) {
                    "$baseText\n\n---\n$transcript"
                } else baseText

                LogEntry(ts, fullText)
            }
            .asReversed()

    }
} 