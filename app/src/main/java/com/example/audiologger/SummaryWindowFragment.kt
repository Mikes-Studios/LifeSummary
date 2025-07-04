package com.mikestudios.lifesummary

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.Fragment
import androidx.appcompat.app.AppCompatActivity
import com.mikestudios.lifesummary.ui.theme.LifeSummaryTheme
import com.mikestudios.lifesummary.ui.theme.primaryGradient
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.snapshots.SnapshotStateList
import android.content.IntentFilter
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class SummaryWindowFragment : Fragment() {

    companion object {
        private const val ARG_MINUTES = "minutes"
        fun create(minutes: Int): SummaryWindowFragment = SummaryWindowFragment().apply {
            arguments = Bundle().apply { putInt(ARG_MINUTES, minutes) }
        }
    }

    private val minutes: Int by lazy { arguments?.getInt(ARG_MINUTES, 30) ?: 30 }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val initial = SummaryUtils.loadSummaries(requireContext(), minutes)
        return ComposeView(requireContext()).apply {
            setContent {
                val entries = remember { mutableStateListOf<LogEntry>().apply { addAll(initial) } }
                LifeSummaryTheme {
                    SummaryWindowScreen(entries, minutes)
                }
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun SummaryWindowScreen(entries: SnapshotStateList<LogEntry>, mins: Int) {
        val ctx = LocalContext.current

        androidx.compose.runtime.DisposableEffect(Unit) {
            val filter = IntentFilter(DriveSync.ACTION_SUMMARIES_UPDATED)
            val recv = object : BroadcastReceiver() {
                override fun onReceive(c: Context?, i: Intent?) {
                    val updated = SummaryUtils.loadSummaries(ctx, mins)
                    entries.clear(); entries.addAll(updated)
                }
            }
            ctx.registerReceiver(recv, filter, android.content.Context.RECEIVER_NOT_EXPORTED)
            onDispose { ctx.unregisterReceiver(recv) }
        }

        // Determine title for Activity toolbar
        val titleText = when {
            mins % 60 == 0 -> {
                val hours = mins / 60
                if (hours == 1) "1-Hour Summaries" else "${hours}-Hour Summaries"
            }
            else -> "${mins}-min Summaries"
        }

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
                LogListWithSearch(entries, onDelete = { entry ->
                    entries.remove(entry)
                    SummaryUtils.deleteEntry(ctx, entry.timestamp)
                })
            }
        }
    }

    override fun onResume() {
        super.onResume()
        val mins = minutes
        val title = if (mins % 60 == 0) {
            val hours = mins / 60
            if (hours == 1) "1-Hour Summaries" else "${hours}-Hour Summaries"
        } else "${mins}-min Summaries"
        (requireActivity() as? AppCompatActivity)?.supportActionBar?.title = title
    }
} 