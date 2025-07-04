package com.mikestudios.lifesummary

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.Fragment
import com.mikestudios.lifesummary.ui.theme.LifeSummaryTheme
import java.io.File
import androidx.compose.runtime.Composable
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import com.mikestudios.lifesummary.ui.theme.primaryGradient
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.platform.LocalContext
import android.content.IntentFilter
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class SummariesFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setContent {
                val initial = loadSummaries()
                val entries = remember { mutableStateListOf<LogEntry>().apply { addAll(initial) } }
                LifeSummaryTheme {
                    SummaryScreen(entries)
                }
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun SummaryScreen(entries: SnapshotStateList<LogEntry>) {
        val ctx = LocalContext.current
        Scaffold(
            modifier = Modifier.background(primaryGradient()),
            floatingActionButton = {
                FloatingActionButton(onClick = {
                    // return to home
                    (requireActivity() as MainActivity).loadFragment(HomeFragment())
                }) {
                    Icon(Icons.Default.Home, contentDescription = "Back to home")
                }
            }
        ) { padding ->
            Surface(modifier = Modifier.padding(padding)) {
                LogListWithSearch(entries, onDelete = { entry ->
                    entries.remove(entry)
                    SummaryUtils.deleteEntry(ctx, entry.timestamp)
                })
            }
        }

        // Listen for background merges that update the summaries file
        androidx.compose.runtime.DisposableEffect(Unit) {
            val filter = IntentFilter(DriveSync.ACTION_SUMMARIES_UPDATED)
            val recv = object : BroadcastReceiver() {
                override fun onReceive(c: Context?, i: Intent?) {
                    val updated = loadSummaries()
                    entries.clear(); entries.addAll(updated)
                }
            }
            ctx.registerReceiver(recv, filter, android.content.Context.RECEIVER_NOT_EXPORTED)
            onDispose { ctx.unregisterReceiver(recv) }
        }
    }

    private fun loadSummaries(): List<LogEntry> {
        // replicated from SummariesActivity
        val sumDir = requireContext().getExternalFilesDir("summaries") ?: return emptyList()
        val sumFile = File(sumDir, "summaries.txt")
        if (!sumFile.exists()) return emptyList()

        // Load transcripts into map
        val transcriptMap: Map<Long, String> = run {
            val tDir = requireContext().getExternalFilesDir("transcripts") ?: return@run emptyMap()
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

    override fun onResume() {
        super.onResume()
        (requireActivity() as? AppCompatActivity)?.supportActionBar?.title = "Summaries"
    }
} 