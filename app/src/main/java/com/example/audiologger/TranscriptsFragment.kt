package com.mikestudios.lifesummary

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.Fragment
import com.mikestudios.lifesummary.ui.theme.LifeSummaryTheme
import java.io.File
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import com.mikestudios.lifesummary.ui.theme.primaryGradient
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import android.content.IntentFilter
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext

class TranscriptsFragment : Fragment() {
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setContent {
                val entries = loadTranscripts()
                LifeSummaryTheme {
                    TranscriptsScreen(entries)
                }
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun TranscriptsScreen(entries: List<LogEntry>) {
        val list = remember { mutableStateListOf<LogEntry>().apply { addAll(entries) } }

        Scaffold(
            modifier = Modifier.background(primaryGradient()),
            floatingActionButton = {
                FloatingActionButton(onClick = {
                    (requireActivity() as MainActivity).loadFragment(HomeFragment())
                }) {
                    Icon(Icons.Default.Home, contentDescription = "Back to home")
                }
            }
        ) { padding ->
            Surface(modifier = Modifier.padding(padding)) {
                LogListWithSearch(list)
            }
        }

        // Broadcast receiver for transcripts updates
        val ctx = LocalContext.current
        androidx.compose.runtime.DisposableEffect(Unit) {
            val filter = IntentFilter(DriveSync.ACTION_TRANSCRIPTS_UPDATED)
            val recv = object : BroadcastReceiver() {
                override fun onReceive(c: Context?, i: Intent?) {
                    val updated = loadTranscripts()
                    list.clear(); list.addAll(updated)
                }
            }
            ctx.registerReceiver(recv, filter, android.content.Context.RECEIVER_NOT_EXPORTED)
            onDispose { ctx.unregisterReceiver(recv) }
        }
    }

    private fun loadTranscripts(): List<LogEntry> {
        val dir = requireContext().getExternalFilesDir("transcripts") ?: return emptyList()
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

    override fun onResume() {
        super.onResume()
        (requireActivity() as? AppCompatActivity)?.supportActionBar?.title = "Transcripts"
    }
} 